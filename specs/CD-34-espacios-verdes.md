# CD-34 · R25 — Espacios verdes: padrón público con estado propio, tercera rebanada de Fase 4

Ver [ADR 0029](../docs/arquitectura/decisiones/0029-espacios-verdes-padron-publico-con-estado-propio-tercera-rebanada-de-fase-4.md)
para el porqué de cada decisión de esta spec. Esta spec no reabre nada del
ADR: lo traduce a tareas concretas.

## Demo objetivo

Un agente municipal (con sesión y permiso `espaciosverdes.gestionar`)
registra un espacio verde nuevo: nombre, tipo (plaza/parque/paseo/otra),
ubicación, superficie en m² (opcional), descripción (opcional). Queda
creado en estado "Disponible". Un vecino, sin sesión, entra al portal
público, filtra por estado y por tipo, busca por texto (nombre o
ubicación), y encuentra ese espacio verde listado con su estado actual. El
mismo agente actualiza el estado a "En mantenimiento"; al volver a
consultar, el vecino ve el estado actualizado. El mismo espacio verde no
aparece en el portal de otro municipio.

## Tarea 1 (backend) — módulo `espaciosverdes`: modelo, alta protegida, lectura pública, migración, permisos

**Comportamiento observable**: con sesión y `espaciosverdes.gestionar`,
`POST /api/espaciosverdes` da de alta un espacio verde en estado
`DISPONIBLE` y devuelve sus datos (201). Sin sesión, `GET
/api/espaciosverdes` devuelve el listado de espacios verdes del municipio
en curso, con filtros opcionales combinables `estado`, `tipo` y `q`
(coincidencia `ILIKE` en `nombre` u `ubicacion`), ordenado por `creadoEn`
descendente. Un municipio sin el módulo `espaciosverdes` contratado
rechaza ambas rutas con 403 `MODULO_NO_CONTRATADO`, con o sin sesión. Sin
sesión, `POST /api/espaciosverdes` da 401/403 (mismo comportamiento que
cualquier ruta protegida del proyecto que no está en
`rutasDeEscrituraPublica()`); con sesión pero sin
`espaciosverdes.gestionar`, 403.

**Modelo** (`espaciosverdes.internal`, módulo nuevo, prefijo
`/api/espaciosverdes`):

- `TipoDeEspacioVerde`: enum `PLAZA, PARQUE, PASEO, OTRA`.
- `EstadoDeEspacioVerde`: enum `DISPONIBLE, EN_MANTENIMIENTO, CERRADO`.
- `EspacioVerdeEntity` (tabla `espacio_verde`), sin columna de tenant
  (mismo criterio que `ObraPublicaEntity`/`ArbolUrbanoEntity`: vive en la
  base del municipio, aislada por base física, no por columna):
  - `id`, `nombre` (`varchar(150)`, not null), `tipo` (`varchar(20)`, not
    null, `check` de valores válidos — enum cerrado, ADR 0029 §3),
    `ubicacion` (`varchar(300)`, not null, texto libre — sin
    geolocalización estructurada, ADR 0029 §6), `descripcion` (`text`,
    nullable).
  - `superficie` (`numeric(10,2)`, nullable, `check (superficie is null or
    superficie > 0)` — única columna numérica de esta tarea, ADR 0029 §4).
  - `estado` (`varchar(25)`, not null, default `'DISPONIBLE'`, `check` de
    valores válidos).
  - Copia del actor que registra (ADR 0013, mismo criterio que
    `publicadoPorNombre`/`publicadoPorEmail` en `ObraPublicaEntity`/
    `ArbolUrbanoEntity`): `publicadoPorNombre` (`varchar(150)`, not null),
    `publicadoPorEmail` (`varchar(200)`, not null).
  - `creadoEn`, `actualizadoEn` (`timestamptz`, not null, default
    `now()`).
  - Sin más columnas: nada de motivo del cierre, inventario de
    equipamiento, ni adjuntos (ADR 0029 §6, Pendiente de definir) — no las
    agregues aunque te parezcan naturales, están fuera de alcance a
    propósito.
  - Índices: `espacio_verde_creado_en_idx on espacio_verde (creado_en
    desc)` (orden del listado, mismo criterio que
    `obra_publica_creado_en_idx`), `espacio_verde_estado_idx on
    espacio_verde (estado)` (filtro más usado del portal público).

- `GestionDeEspaciosVerdes` (`@Service`), con
  `@Transactional("tenantTransactionManager")` en los métodos de
  escritura:
  - `registrar(nombre, tipo, ubicacion, descripcion, superficie,
    publicadoPorNombre, publicadoPorEmail)`: valida `nombre`/`ubicacion`
    no-blank y largos máximos (mismos límites de columna), `tipo` no nulo
    (igual que `GestionDeObras#registrar` valida `TipoDeObra`).
    `descripcion` es opcional sin más validación que el tipo. `superficie`
    es opcional; si viene, tiene que ser `> 0` (`BigDecimal.compareTo`,
    mismo patrón que `GestionDeTasas#publicar` valida `monto`) — si no,
    400 `SolicitudInvalida` con mensaje claro ("La superficie tiene que
    ser mayor a cero."). Guarda con `estado = DISPONIBLE`; el estado
    inicial **no** es un parámetro que reciba el cliente, es siempre
    `DISPONIBLE`.
  - `buscar(estado, tipo, q)`: los tres parámetros opcionales y
    combinables (AND entre los que vengan). `estado`/`tipo` inválidos (que
    no matcheen ningún valor del enum) → 400 `SolicitudInvalida`, no se
    tratan como "sin filtro". `q` es `ILIKE '%valor%'` contra `nombre`
    **o** `ubicacion` (coincide en cualquiera de los dos). Ordena por
    `creadoEn` descendente, sin paginado (fuera de alcance, mismo criterio
    que Obras/Arbolado/Educación).

Creá también, desde cero en `espaciosverdes.internal` (son clases
package-private, no se pueden reutilizar entre módulos):
`SolicitudInvalida` (mismo texto/patrón que
`obras.internal.SolicitudInvalida`), `EspacioVerdeNoEncontrado` (mismo
patrón que `ObraNoEncontrada`/`ArbolNoEncontrado`), y `package-info.java`
del paquete `espaciosverdes` con el resumen del módulo (mismo estilo que
`ar.com.ciudaddigital.arbolado.package-info`, citando ADR 0029).

**Fuera de alcance de esta tarea**: actualización de estado (Tarea 2),
permisos y `DescriptorDeModulo` (van también en esta tarea, ver abajo —
no hay una Tarea separada solo para eso, mismo criterio que
Obras/Arbolado).

**Migración** (`V25__crear_espaciosverdes.sql`, tenant): tabla
`espacio_verde` completa (todas las columnas de arriba), catálogo de
permisos:

```sql
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('espaciosverdes.gestionar', 'Ambiente y Servicios Públicos', 'espaciosverdes', 'gestionar',
     'Registrar un espacio verde y actualizar su estado.');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo from rol r, permiso p
where r.codigo in ('administrador', 'agente') and p.codigo = 'espaciosverdes.gestionar';
```

(Ver ADR 0029 §7 para el porqué de un único permiso y de asignarlo a
ambos roles de sistema — no lo reabras.)

**`DescriptorDelModuloEspaciosVerdes`** (`espaciosverdes.internal`,
`@Component`):
- `codigo() = "espaciosverdes"`, `nombre() = "Espacios Verdes"`, prefijo
  `/api/espaciosverdes`.
- `rutasDeLecturaPublica() = List.of("/api/espaciosverdes")` (el listado
  con filtros).
- `rutasDeEscrituraPublica()`: **no la sobrescribas** (default vacío) —
  este módulo no tiene ninguna escritura pública/anónima, mismo criterio
  que Obras/Arbolado/Educación (ADR 0029 §2).

**Controller** (`EspaciosVerdesController`, `/api/espaciosverdes`): seguí
el estilo de `ObrasController`/`ArboladoController` (mismos nombres de
patrón: `ErrorResponse`, `@ExceptionHandler` por tipo de excepción,
records para request/response, helpers `tipoDe(String)`/`estadoDe(String)`
que parsean o tiran `SolicitudInvalida`). `POST /api/espaciosverdes` con
`@PreAuthorize("hasAuthority('espaciosverdes.gestionar')")`, usa
`ActorAutenticado` (mismo mecanismo que `ObrasController.actorDe`) para
`publicadoPorNombre`/`publicadoPorEmail`. `GET /api/espaciosverdes` sin
`@PreAuthorize` (pública). Response único `EspacioVerdeResponse` con todos
los campos (`superficie` como `BigDecimal`, `null` si no se cargó).

## Tarea 2 (backend) — actualización de estado

**Comportamiento observable**: con sesión y `espaciosverdes.gestionar`,
`PATCH /api/espaciosverdes/{id}/estado` con body `{estadoNuevo}` cambia el
estado del espacio verde si la transición es válida (`DISPONIBLE →
EN_MANTENIMIENTO`, `EN_MANTENIMIENTO → DISPONIBLE`, `EN_MANTENIMIENTO →
CERRADO`) y actualiza `actualizadoEn`. Transición no válida (incluida
cualquier intento sobre `CERRADO`, terminal, y `DISPONIBLE → CERRADO`
directo) → 400 `SolicitudInvalida` con mensaje claro indicando el estado
actual y el pedido. `id` inexistente → 404 (`EspacioVerdeNoEncontrado`,
mismo patrón que `ObraNoEncontrada`/`ArbolNoEncontrado`). Sin sesión o sin
`espaciosverdes.gestionar` → 401/403, no está en
`rutasDeEscrituraPublica()`.

**Implementación**:
- `GestionDeEspaciosVerdes.actualizarEstado(Long id, EstadoDeEspacioVerde
  estadoNuevo)`: busca el espacio verde (o `EspacioVerdeNoEncontrado`),
  valida la transición contra una tabla
  `Map<EstadoDeEspacioVerde, Set<EstadoDeEspacioVerde>>` codificada en el
  servicio (mismo patrón que `GestionDeObras.TRANSICIONES_VALIDAS`/
  `GestionDeArbolado.TRANSICIONES_VALIDAS` — **no reutilices código de
  `obras`, `arbolado` ni `educacion`**, `espaciosverdes` no depende de
  esos módulos, ADR 0029 §1/§8), aplica el cambio y `actualizadoEn =
  Instant.now()`. Podés poner el chequeo de transición en
  `EspacioVerdeEntity.actualizarEstado(EstadoDeEspacioVerde)` como segunda
  barrera (mismo criterio que `ObraPublicaEntity.actualizarEstado`), a tu
  criterio de dónde queda más claro.
- No se agrega ninguna columna nueva para esta tarea: no hay campo de
  motivo del cierre ni copia de quién actualizó el estado (ADR 0029,
  Pendiente de definir) — no lo agregues por iniciativa propia.

**Fuera de alcance**: edición de `nombre`/`tipo`/`ubicacion`/
`descripcion`/`superficie` después de creado el registro — no la
construyas ni la bloquees con validación extra, simplemente no existe ese
endpoint.

## Tarea 3 (backend) — test de aislamiento entre tenants

**Obligatorio, no diferible (CLAUDE.md).** Crear
`backend/src/test/java/ar/com/ciudaddigital/espaciosverdes/EspaciosVerdesTest.java`
(extiende `SoporteDeIntegracion`, mismo patrón que
`ObrasTest`/`ArboladoTest`), con un test `@DisplayName("aislamiento: un
espacio verde registrado en un municipio no es visible ni actualizable
desde otro")` que: registra un espacio verde en el tenant A, verifica que
`GET /api/espaciosverdes` (con y sin filtros) desde el tenant B no lo
incluye, y que `PATCH /api/espaciosverdes/{id}/estado` ejecutado contra el
tenant B con el `id` del espacio verde del tenant A da 404 (no "lo
encuentra y lo actualiza").

Cubrí además, en tests normales (no de aislamiento): alta con
`espaciosverdes.gestionar` (queda `DISPONIBLE`), alta sin el permiso
(403), alta con `superficie` inválida (`0` o negativa) → 400, listado
público sin sesión con cada filtro (`estado`, `tipo`, `q`) por separado y
combinados, circuito completo de transiciones (`DISPONIBLE →
EN_MANTENIMIENTO → DISPONIBLE → EN_MANTENIMIENTO → CERRADO`), transiciones
inválidas (por ejemplo `DISPONIBLE → CERRADO` directo, o cualquier
transición desde `CERRADO`) → 400, y `MODULO_NO_CONTRATADO` en
`POST`/`GET`/`PATCH` cuando el tenant no tiene `espaciosverdes`
contratado.

## Tarea 4 (frontend) — pantalla del módulo `espaciosverdes`

**Comportamiento observable**: pantalla nueva
`PantallaDeEspaciosVerdes.tsx` en `frontend/src/modulos/espaciosverdes/`,
registrada en `frontend/src/modulos/registro.ts` igual que el resto
(mismo mecanismo que `obras`/`arbolado`/`educacion`, agregando la entrada
`espaciosverdes: PantallaDeEspaciosVerdes`).

Es una única vista para todos (sin router, mismo patrón por estado local
que `PantallaDeObras`/`PantallaDeArbolado`): búsqueda/listado público
siempre visible, con la acción de "Registrar espacio verde" y las
acciones de cambio de estado apareciendo condicionadas al permiso, mismo
patrón exacto que `PantallaDeObras`/`PantallaDeArbolado` (no repliques el
patrón de `PantallaDeReclamos`, que muestra vistas *alternativas* según
permiso).

1. **Búsqueda/listado** (default, pública, sin sesión): filtros
   combinables — `<select>` de estado (con opción "Todos"), `<select>` de
   tipo (con opción "Todos"), campo de texto para `q` ("Buscar en nombre
   o ubicación"). Tabla con columnas Nombre, Tipo, Ubicación, Superficie
   (mostrar `"— m²"` si no se cargó, o el valor formateado con `Intl`,
   mismo criterio de formateo que fechas en `PantallaDeObras`/
   `PantallaDeArbolado`), Estado. Etiquetas legibles en español para
   `tipo` (Plaza, Parque, Paseo, Otra) y `estado` (mismo patrón
   `ETIQUETA_ESTADO`/`ETIQUETA_TIPO` que `PantallaDeObras`; sugerido:
   Disponible, En mantenimiento, Cerrado).

2. **Registrar espacio verde** (visible solo con
   `usuario?.permisos.includes('espaciosverdes.gestionar')`, mismo patrón
   que la sección "Registrar una obra" de `PantallaDeObras`): formulario
   con Nombre (`input`, obligatorio), Tipo (`<select>`, obligatorio, sin
   opción "Todos" acá — a diferencia del filtro, el alta exige elegir
   uno), Ubicación (`input`, obligatorio), Superficie en m² (opcional,
   `input type="number" min="0" step="0.01"`), Descripción (opcional,
   `textarea`). Al confirmar, recarga el listado con los filtros
   aplicados y cierra el formulario (mismo flujo que `registrarObra` en
   `PantallaDeObras`).

3. **Cambiar estado** (visible por fila, solo con
   `espaciosverdes.gestionar`, y solo si el espacio verde tiene alguna
   transición válida desde su estado actual — usá el mismo mapa de
   transiciones que el backend, replicado en el frontend igual que
   `TRANSICIONES_VALIDAS` en `PantallaDeObras`/`PantallaDeArbolado`, con
   el mismo comentario de que el enforcement real es del backend): un
   `<select>` por fila con las transiciones válidas desde el estado
   actual de ese espacio verde, y un botón "Actualizar estado" que
   dispara `PATCH /api/espaciosverdes/{id}/estado`. Al confirmar, recargá
   el listado completo con los filtros aplicados, mismo criterio de
   simplicidad que `PantallaDeObras`/`PantallaDeArbolado`.

**Accesibilidad (obligatorio, no diferible, CLAUDE.md)**: seguí al pie de
la letra los patrones ya usados en `PantallaDeArbolado.tsx` — foco
gestionado por `useRef`+`tabIndex={-1}` al montar/cambiar de estado,
anuncios con `role="status"`/`role="alert"`, `aria-invalid`/
`aria-describedby` en campos con error, `aria-busy` en botones de acción
en curso, `<label htmlFor>` en todo input/select, tabla con `<caption>` y
`scope="col"`/`scope="row"`. No inventes un patrón nuevo de
accesibilidad: replicá el que ya existe.

**Fuera de alcance**: routing de URLs (no existe en este frontend),
edición de los campos del alta, mapa/geolocalización, adjuntos/fotos,
paginado, inventario de equipamiento.

## Qué NO tocar

- El motor de `mesaentradas` (`Expediente`, `MovimientoDeExpediente`,
  `CircuitoDeTramite`): no se extiende, no se le agrega un tipo
  `ESPACIO_VERDE` (ADR 0029 §5/§8).
- Los módulos `obras`, `arbolado`, `educacion` (código, tablas, permisos):
  `espaciosverdes` no depende de ninguno ni reutiliza su código (ADR 0029
  §1/§8).
- Los permisos de `boletin`, `reclamos`, `transparencia`, `multas`,
  `obras`, `arbolado`, `educacion` u otro módulo existente.
- `modulosHabilitados` de los tenants de prueba `sanmartin`/`moron`
  (`db/control/V2__sembrar_municipios_de_prueba.sql`): si hace falta que
  `espaciosverdes` esté contratado para demostrarlo manualmente, hacelo
  sembrando la contratación con el mecanismo ya existente (mismo criterio
  que la spec de CD-27/CD-28 dejó para `obras`/`arbolado`). Los tests de
  integración contratan el módulo directamente contra la base de control
  de test, mismo patrón que `ObrasTest`/`ArboladoTest`.

## Instrucciones para los agentes implementadores

No hagas commit, push ni abras PR por tu cuenta: dejá los cambios en el
working tree. El tech lead revisa, commitea y coordina el PR.
