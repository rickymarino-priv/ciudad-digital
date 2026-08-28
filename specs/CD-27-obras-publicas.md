# CD-27 · R19 — Obras Públicas: registro público con estado propio, primera rebanada de Fase 4

Ver [ADR 0023](../docs/arquitectura/decisiones/0023-obras-publicas-registro-publico-con-estado-propio-actualizable.md)
para el porqué de cada decisión de esta spec. Esta spec no reabre nada del
ADR: lo traduce a tareas concretas.

## Demo objetivo

Un agente municipal (con sesión y permiso `obras.gestionar`) registra una
obra pública nueva: nombre, tipo, ubicación, fechas estimadas de inicio y
fin. Queda creada en estado "Planificada". Un vecino, sin sesión, entra al
portal público, filtra por estado y por tipo, busca por texto, y encuentra
esa obra listada con su estado actual. El mismo agente (u otro con el
mismo permiso) actualiza el estado a "En ejecución"; al volver a
consultar, el vecino ve el estado actualizado sin recargar la página de
otra forma que refrescando el listado. La misma obra no aparece en el
portal de otro municipio.

## Tarea 1 (backend) — módulo `obras`: modelo, alta protegida, lectura pública, migración, permisos

**Comportamiento observable**: con sesión y `obras.gestionar`,
`POST /api/obras` da de alta una obra pública en estado `PLANIFICADA` y
devuelve sus datos (201). Sin sesión, `GET /api/obras` devuelve el listado
de obras del municipio en curso, con filtros opcionales combinables
`estado`, `tipo` y `q` (coincidencia `ILIKE` en `nombre` u `ubicacion`),
ordenado por `creadoEn` descendente. Un municipio sin el módulo `obras`
contratado rechaza ambas rutas con 403 `MODULO_NO_CONTRATADO`, con o sin
sesión. Sin sesión, `POST /api/obras` da 401/403 (mismo comportamiento que
cualquier ruta protegida del proyecto que no está en
`rutasDeEscrituraPublica()`); con sesión pero sin `obras.gestionar`, 403.

**Modelo** (`obras.internal`, módulo nuevo, prefijo `/api/obras`):

- `TipoDeObra`: enum `VIALIDAD, ESPACIO_PUBLICO, EDIFICIO_PUBLICO, SERVICIOS, OTRA`.
- `EstadoDeObra`: enum `PLANIFICADA, EN_EJECUCION, PARALIZADA, FINALIZADA`.
- `ObraPublicaEntity` (tabla `obra_publica`), sin columna de tenant (mismo
  criterio que `NormaEntity`/`ReclamoEntity`: vive en la base del
  municipio, aislada por base física, no por columna):
  - `id`, `nombre` (`varchar(200)`, not null), `tipo` (`varchar(20)`, not
    null, `check` de valores válidos), `ubicacion` (`varchar(300)`, not
    null, texto libre — sin geolocalización estructurada, ADR 0023 §6),
    `descripcion` (`text`, nullable).
  - `estado` (`varchar(20)`, not null, default `'PLANIFICADA'`, `check` de
    valores válidos).
  - `fechaInicioEstimada`, `fechaFinEstimada` (`date`, ambas nullable).
  - Copia del actor que registra (ADR 0013, mismo criterio que
    `publicadoPorNombre`/`publicadoPorEmail` en `NormaEntity`):
    `publicadoPorNombre` (`varchar(150)`, not null), `publicadoPorEmail`
    (`varchar(200)`, not null).
  - `creadoEn`, `actualizadoEn` (`timestamptz`, not null, default `now()`).
  - Sin más columnas: nada de monto, contratista, certificaciones de
    avance, ni adjuntos (ADR 0023 §7/§8) — no las agregues aunque te
    parezcan naturales, están fuera de alcance a propósito.
  - Índices: `obra_publica_creado_en_idx on obra_publica (creado_en desc)`
    (orden del listado, mismo criterio que `norma_fecha_publicacion_idx`),
    `obra_publica_estado_idx on obra_publica (estado)` (filtro más usado
    del portal público).

- `GestionDeObras` (`@Service`), con `@Transactional("tenantTransactionManager")`
  en los métodos de escritura:
  - `registrar(nombre, tipo, ubicacion, descripcion, fechaInicioEstimada, fechaFinEstimada, publicadoPorNombre, publicadoPorEmail)`:
    valida `nombre`/`ubicacion` no-blank y largos máximos (mismos límites
    de columna), `tipo` es uno de los valores del enum (400
    `SolicitudInvalida` si no). `descripcion`, `fechaInicioEstimada`,
    `fechaFinEstimada` son opcionales, sin más validación que el tipo.
    Si vienen ambas fechas y `fechaFinEstimada` es anterior a
    `fechaInicioEstimada`, 400 (`SolicitudInvalida`, mensaje claro). Guarda
    con `estado = PLANIFICADA`; el estado inicial **no** es un parámetro
    que reciba el cliente, es siempre `PLANIFICADA`.
  - `buscar(estado, tipo, q)`: todos los parámetros opcionales y
    combinables (AND entre los que vengan). `estado`/`tipo` inválidos (que
    no matcheen ningún valor del enum) → 400 `SolicitudInvalida`, no se
    tratan como "sin filtro". `q` es `ILIKE '%valor%'` contra `nombre` **o**
    `ubicacion` (coincide en cualquiera de los dos). Ordena por `creadoEn`
    descendente, sin paginado (fuera de alcance, mismo criterio que
    Boletín).

**Fuera de alcance de esta tarea**: actualización de estado (Tarea 2),
permisos y `DescriptorDeModulo` (van también en esta tarea, ver abajo —
no hay una Tarea separada solo para eso, a diferencia de Multas: acá el
alcance es chico y no amerita partirlo).

**Migración** (`V19__crear_obras.sql`, tenant): tabla `obra_publica`
completa (todas las columnas de arriba), catálogo de permisos:

```sql
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('obras.gestionar', 'Obras Públicas', 'obras', 'gestionar',
     'Registrar una obra pública y actualizar su estado de avance.');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo from rol r, permiso p
where r.codigo in ('administrador', 'agente') and p.codigo = 'obras.gestionar';
```

(Ver ADR 0023 §5 para el porqué de un único permiso y de asignarlo a
ambos roles de sistema — no lo reabras.)

**`DescriptorDelModuloObras`** (`obras.internal`, `@Component`):
- `codigo() = "obras"`, `nombre() = "Obras Públicas"`, prefijo `/api/obras`.
- `rutasDeLecturaPublica() = List.of("/api/obras")` (el listado con
  filtros).
- `rutasDeEscrituraPublica()`: **no la sobrescribas** (default vacío) —
  este módulo no tiene ninguna escritura pública/anónima, a diferencia de
  Reclamos y Multas (ADR 0023 §2).

**Controller** (`ObrasController`, `/api/obras`): seguí el estilo de
`BoletinController`/`MultasController` (mismos nombres de patrón:
`ErrorResponse`, `@ExceptionHandler` por tipo de excepción, records para
request/response). `POST /api/obras` con `@PreAuthorize("hasAuthority('obras.gestionar')")`,
usa `ActorAutenticado` (mismo mecanismo que `MultasController.actorDe`)
para `publicadoPorNombre`/`publicadoPorEmail`. `GET /api/obras` sin
`@PreAuthorize` (pública). Response único `ObraPublicaResponse` con todos
los campos.

## Tarea 2 (backend) — actualización de estado

**Comportamiento observable**: con sesión y `obras.gestionar`,
`PATCH /api/obras/{id}/estado` con body `{estadoNuevo}` cambia el estado
de la obra si la transición es válida (`PLANIFICADA → EN_EJECUCION`,
`EN_EJECUCION → PARALIZADA`, `EN_EJECUCION → FINALIZADA`,
`PARALIZADA → EN_EJECUCION`) y actualiza `actualizadoEn`. Transición no
válida (incluida cualquier intento sobre `FINALIZADA`, terminal) → 400
`SolicitudInvalida` con mensaje claro indicando el estado actual y el
pedido. `id` inexistente → 404 (`ObraNoEncontrada`, mismo patrón que
`MultaNoEncontrada`). Sin sesión o sin `obras.gestionar` → 401/403, no
está en `rutasDeEscrituraPublica()`.

**Implementación**:
- `GestionDeObras.actualizarEstado(Long id, EstadoDeObra estadoNuevo)`:
  busca la obra (o `ObraNoEncontrada`), valida la transición contra una
  tabla `Map<EstadoDeObra, Set<EstadoDeObra>>` codificada en el servicio
  (mismo patrón que la tabla de transiciones de `GestionDeMultas`/reclamos
  — no reutilices código de esos módulos, `obras` no depende de ellos),
  aplica el cambio y `actualizadoEn = Instant.now()`. Podés poner el
  chequeo de transición en `ObraPublicaEntity.actualizarEstado(EstadoDeObra)`
  como segunda barrera (mismo criterio que `MultaEntity.confirmarPago`),
  a tu criterio de dónde queda más claro.
- No se agrega ninguna columna nueva para esta tarea: no hay
  `comentarioGestion` ni copia de quién actualizó el estado (ADR 0023,
  Pendiente de definir) — no lo agregues por iniciativa propia.

**Fuera de alcance**: edición de `nombre`/`tipo`/`ubicacion`/`descripcion`/
fechas después de creada la obra (ADR 0023 §4) — no la construyas ni la
bloquees con validación extra, simplemente no existe ese endpoint.

## Tarea 3 (backend) — test de aislamiento entre tenants

**Obligatorio, no diferible (CLAUDE.md).** Crear
`backend/src/test/java/ar/com/ciudaddigital/obras/ObrasTest.java`
(extiende `SoporteDeIntegracion`, mismo patrón que `MultasTest`/
`BoletinTest`), con un test `@DisplayName("aislamiento: una obra
registrada en un municipio no es visible ni actualizable desde otro")`
que: registra una obra en el tenant A, verifica que `GET /api/obras`
(con y sin filtros) desde el tenant B no la incluye, y que
`PATCH /api/obras/{id}/estado` ejecutado contra el tenant B con el `id`
de la obra del tenant A da 404 (no "la encuentra y la actualiza").

Cubrí además, en tests normales (no de aislamiento): alta con
`obras.gestionar` (queda `PLANIFICADA`), alta sin el permiso (403), alta
con `fechaFinEstimada` anterior a `fechaInicioEstimada` (400), listado
público sin sesión con cada filtro (`estado`, `tipo`, `q`) por separado y
combinados, circuito completo de transiciones
(`PLANIFICADA → EN_EJECUCION → PARALIZADA → EN_EJECUCION → FINALIZADA`),
transición inválida (por ejemplo `PLANIFICADA → FINALIZADA` directo, o
cualquier transición desde `FINALIZADA`) → 400, y `MODULO_NO_CONTRATADO`
en `POST`/`GET`/`PATCH` cuando el tenant no tiene `obras` contratado.

## Tarea 4 (frontend) — pantalla del módulo `obras`

**Comportamiento observable**: pantalla nueva `PantallaDeObras.tsx` en
`frontend/src/modulos/obras/`, registrada en
`frontend/src/modulos/registro.ts` igual que el resto (mismo mecanismo
que `boletin`/`multas`).

Es una única vista para todos (sin router, mismo patrón por estado local
que `PantallaDeBoletin`): búsqueda/listado público siempre visible, con
la acción de "Registrar obra" y las acciones de cambio de estado
apareciendo condicionadas al permiso, mismo patrón exacto que
`PantallaDeBoletin` (no repliques el patrón de `PantallaDeReclamos`, que
muestra vistas *alternativas* según permiso — acá, igual que Boletín, el
listado es el mismo para todos, solo cambia qué acciones se ven).

1. **Búsqueda/listado** (default, pública, sin sesión): filtros
   combinables — `<select>` de estado (con opción "Todos"), `<select>` de
   tipo (con opción "Todos"), campo de texto para `q`. Tabla con columnas
   Nombre, Tipo, Ubicación, Estado, Fecha estimada de inicio, Fecha
   estimada de fin (formatear fechas igual que `PantallaDeBoletin`:
   `formatearFecha` para `AAAA-MM-DD` sin desfasaje de huso horario;
   fechas ausentes muestran "—" o similar). Etiquetas legibles en español
   para `tipo` y `estado` (mismo patrón `ETIQUETA_TIPO`/`ETIQUETA_ESTADO`
   que `PantallaDeBoletin`/`PantallaDeReclamos`).

2. **Registrar obra** (visible solo con
   `usuario?.permisos.includes('obras.gestionar')`, mismo patrón que la
   sección "Publicar una norma" de `PantallaDeBoletin`): formulario con
   Nombre, Tipo (`<select>`, obligatorio), Ubicación, Descripción
   (opcional, `textarea`), Fecha estimada de inicio (opcional,
   `type="date"`), Fecha estimada de fin (opcional, `type="date"`). Al
   confirmar, recarga el listado con los filtros aplicados y cierra el
   formulario (mismo flujo que `publicarNorma` en `PantallaDeBoletin`).

3. **Cambiar estado** (visible por fila, solo con `obras.gestionar`,
   y solo si la obra tiene alguna transición válida desde su estado
   actual — usá el mismo mapa de transiciones que el backend, replicado
   en el frontend igual que `TRANSICIONES_VALIDAS` en
   `PantallaDeReclamos`, con el mismo comentario de que el enforcement
   real es del backend): un `<select>` por fila (o un botón que abre un
   `<select>` — a tu criterio de UX) con las transiciones válidas desde
   el estado actual de esa obra, y un botón "Actualizar estado" que
   dispara `PATCH /api/obras/{id}/estado`. Al confirmar, actualiza esa
   fila en el listado sin recargar toda la página (podés recargar el
   listado completo con los filtros aplicados si es más simple, mismo
   criterio que el resto del frontend prioriza simplicidad sobre
   optimización prematura).

**Accesibilidad (obligatorio, no diferible, CLAUDE.md)**: seguí al pie de
la letra los patrones ya usados en `PantallaDeBoletin.tsx` — foco
gestionado por `useRef`+`tabIndex={-1}` al montar/cambiar de estado,
anuncios con `role="status"`/`role="alert"`, `aria-invalid`/
`aria-describedby` en campos con error, `aria-busy` en botones de acción
en curso, `<label htmlFor>` en todo input/select, tabla con `<caption>` y
`scope="col"`/`scope="row"`. No inventes un patrón nuevo de
accesibilidad: replicá el que ya existe.

**Fuera de alcance**: routing de URLs (no existe en este frontend),
edición de los campos del alta, mapa/geolocalización, adjuntos/fotos,
paginado.

## Qué NO tocar

- El motor de `mesaentradas` (`Expediente`, `MovimientoDeExpediente`,
  `CircuitoDeTramite`): no se extiende, no se le agrega un tipo `OBRA`
  (ADR 0023 §3).
- Los permisos de `boletin`, `reclamos`, `transparencia`, `multas` u otro
  módulo existente.
- `modulosHabilitados` de los tenants de prueba `sanmartin`/`moron`
  (`db/control/V2__sembrar_municipios_de_prueba.sql`): si hace falta que
  `obras` esté contratado para demostrarlo manualmente, hacelo sembrando
  la contratación con el mecanismo ya existente (mirá cómo quedaron
  contratados `multas`/`transparencia` en esos tenants antes de tocar
  nada — si no encontrás un mecanismo de siembra de contratación en
  migraciones, es porque no lo hay: los tests de integración contratan el
  módulo que necesitan directamente contra la base de control de test,
  mismo patrón que `MultasTest`/`BoletinTest`; replicá ese patrón, no
  inventes uno nuevo).

## Instrucciones para los agentes implementadores

No hagas commit, push ni abras PR por tu cuenta: dejá los cambios en el
working tree. El tech lead revisa, commitea y coordina el PR.
