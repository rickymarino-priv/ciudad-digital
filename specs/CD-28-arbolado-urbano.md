# CD-28 · R20 — Arbolado urbano: padrón público con estado sanitario propio, segunda rebanada de Fase 4

Ver [ADR 0024](../docs/arquitectura/decisiones/0024-arbolado-urbano-padron-publico-con-estado-sanitario-propio.md)
para el porqué de cada decisión de esta spec. Esta spec no reabre nada del
ADR: lo traduce a tareas concretas.

## Demo objetivo

Un agente municipal (con sesión y permiso `arbolado.gestionar`) registra
un árbol nuevo en el padrón: especie, ubicación, fecha de plantación
(opcional), descripción (opcional). Queda creado en estado "Plantado". Un
vecino, sin sesión, entra al portal público, filtra por estado, busca por
texto (especie o ubicación), y encuentra ese árbol listado con su estado
actual. El mismo agente actualiza el estado a "Sano" y después a "Requiere
intervención"; al volver a consultar, el vecino ve el estado actualizado.
El mismo árbol no aparece en el portal de otro municipio.

## Tarea 1 (backend) — módulo `arbolado`: modelo, alta protegida, lectura pública, migración, permisos

**Comportamiento observable**: con sesión y `arbolado.gestionar`,
`POST /api/arbolado` da de alta un árbol en estado `PLANTADO` y devuelve
sus datos (201). Sin sesión, `GET /api/arbolado` devuelve el listado de
árboles del municipio en curso, con filtros opcionales combinables
`estado` y `q` (coincidencia `ILIKE` en `especie` u `ubicacion`), ordenado
por `creadoEn` descendente. Un municipio sin el módulo `arbolado`
contratado rechaza ambas rutas con 403 `MODULO_NO_CONTRATADO`, con o sin
sesión. Sin sesión, `POST /api/arbolado` da 401/403 (mismo comportamiento
que cualquier ruta protegida del proyecto que no está en
`rutasDeEscrituraPublica()`); con sesión pero sin `arbolado.gestionar`,
403.

**Modelo** (`arbolado.internal`, módulo nuevo, prefijo `/api/arbolado`):

- `EstadoDeArbol`: enum `PLANTADO, SANO, REQUIERE_INTERVENCION, RETIRADO`.
- `ArbolUrbanoEntity` (tabla `arbol_urbano`), sin columna de tenant (mismo
  criterio que `ObraPublicaEntity`/`ReclamoEntity`: vive en la base del
  municipio, aislada por base física, no por columna):
  - `id`, `especie` (`varchar(150)`, not null, texto libre — sin catálogo
    fijo de especies, ADR 0024 §3), `ubicacion` (`varchar(300)`, not null,
    texto libre — sin geolocalización estructurada, ADR 0024 §6),
    `descripcion` (`text`, nullable).
  - `estado` (`varchar(25)`, not null, default `'PLANTADO'`, `check` de
    valores válidos).
  - `fechaDePlantacion` (`date`, nullable).
  - Copia del actor que registra (ADR 0013, mismo criterio que
    `publicadoPorNombre`/`publicadoPorEmail` en `ObraPublicaEntity`):
    `publicadoPorNombre` (`varchar(150)`, not null), `publicadoPorEmail`
    (`varchar(200)`, not null).
  - `creadoEn`, `actualizadoEn` (`timestamptz`, not null, default `now()`).
  - Sin más columnas: nada de motivo del retiro, tipo/especie
    estructurado, ni adjuntos (ADR 0024 §6, Pendiente de definir) — no las
    agregues aunque te parezcan naturales, están fuera de alcance a
    propósito.
  - Índices: `arbol_urbano_creado_en_idx on arbol_urbano (creado_en desc)`
    (orden del listado, mismo criterio que `obra_publica_creado_en_idx`),
    `arbol_urbano_estado_idx on arbol_urbano (estado)` (filtro más usado
    del portal público).

- `GestionDeArbolado` (`@Service`), con `@Transactional("tenantTransactionManager")`
  en los métodos de escritura:
  - `registrar(especie, ubicacion, descripcion, fechaDePlantacion, publicadoPorNombre, publicadoPorEmail)`:
    valida `especie`/`ubicacion` no-blank y largos máximos (mismos límites
    de columna). `descripcion` y `fechaDePlantacion` son opcionales, sin
    más validación que el tipo. No hay validación cruzada de fechas (a
    diferencia de Obras, acá hay una sola fecha). Guarda con
    `estado = PLANTADO`; el estado inicial **no** es un parámetro que
    reciba el cliente, es siempre `PLANTADO`.
  - `buscar(estado, q)`: ambos parámetros opcionales y combinables (AND
    entre los que vengan). `estado` inválido (que no matchee ningún valor
    del enum) → 400 `SolicitudInvalida`, no se trata como "sin filtro".
    `q` es `ILIKE '%valor%'` contra `especie` **o** `ubicacion` (coincide
    en cualquiera de los dos). Ordena por `creadoEn` descendente, sin
    paginado (fuera de alcance, mismo criterio que Obras/Boletín).

No hay un enum `tipo` en este módulo (ADR 0024 §3): no lo agregues.

Creá también, desde cero en `arbolado.internal` (son clases
package-private, no se pueden reutilizar entre módulos): `SolicitudInvalida`
(mismo texto/patrón que `obras.internal.SolicitudInvalida`) y
`package-info.java` del paquete `arbolado` con el resumen del módulo
(mismo estilo que `ar.com.ciudaddigital.obras.package-info`).

**Fuera de alcance de esta tarea**: actualización de estado (Tarea 2),
permisos y `DescriptorDeModulo` (van también en esta tarea, ver abajo —
no hay una Tarea separada solo para eso, mismo criterio que Obras).

**Migración** (`V20__crear_arbolado.sql`, tenant): tabla `arbol_urbano`
completa (todas las columnas de arriba), catálogo de permisos:

```sql
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('arbolado.gestionar', 'Ambiente y Servicios Públicos', 'arbolado', 'gestionar',
     'Registrar un árbol urbano y actualizar su estado sanitario.');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo from rol r, permiso p
where r.codigo in ('administrador', 'agente') and p.codigo = 'arbolado.gestionar';
```

(Ver ADR 0024 §5 para el porqué de un único permiso y de asignarlo a ambos
roles de sistema — no lo reabras.)

**`DescriptorDelModuloArbolado`** (`arbolado.internal`, `@Component`):
- `codigo() = "arbolado"`, `nombre() = "Arbolado Urbano"`, prefijo
  `/api/arbolado`.
- `rutasDeLecturaPublica() = List.of("/api/arbolado")` (el listado con
  filtros).
- `rutasDeEscrituraPublica()`: **no la sobrescribas** (default vacío) —
  este módulo no tiene ninguna escritura pública/anónima, mismo criterio
  que Obras (ADR 0024 §2).

**Controller** (`ArboladoController`, `/api/arbolado`): seguí el estilo de
`ObrasController` (mismos nombres de patrón: `ErrorResponse`,
`@ExceptionHandler` por tipo de excepción, records para request/response).
`POST /api/arbolado` con `@PreAuthorize("hasAuthority('arbolado.gestionar')")`,
usa `ActorAutenticado` (mismo mecanismo que `ObrasController.actorDe`)
para `publicadoPorNombre`/`publicadoPorEmail`. `GET /api/arbolado` sin
`@PreAuthorize` (pública). Response único `ArbolUrbanoResponse` con todos
los campos.

## Tarea 2 (backend) — actualización de estado sanitario

**Comportamiento observable**: con sesión y `arbolado.gestionar`,
`PATCH /api/arbolado/{id}/estado` con body `{estadoNuevo}` cambia el
estado del árbol si la transición es válida (`PLANTADO → SANO`,
`SANO → REQUIERE_INTERVENCION`, `REQUIERE_INTERVENCION → SANO`,
`REQUIERE_INTERVENCION → RETIRADO`) y actualiza `actualizadoEn`.
Transición no válida (incluida cualquier intento sobre `RETIRADO`,
terminal, y `SANO → RETIRADO` directo) → 400 `SolicitudInvalida` con
mensaje claro indicando el estado actual y el pedido. `id` inexistente →
404 (`ArbolNoEncontrado`, mismo patrón que `ObraNoEncontrada`). Sin sesión
o sin `arbolado.gestionar` → 401/403, no está en
`rutasDeEscrituraPublica()`.

**Implementación**:
- `GestionDeArbolado.actualizarEstado(Long id, EstadoDeArbol estadoNuevo)`:
  busca el árbol (o `ArbolNoEncontrado`), valida la transición contra una
  tabla `Map<EstadoDeArbol, Set<EstadoDeArbol>>` codificada en el servicio
  (mismo patrón que `GestionDeObras.TRANSICIONES_VALIDAS` — **no
  reutilices código de `obras`**, `arbolado` no depende de ese módulo, ADR
  0024 §1/§7), aplica el cambio y `actualizadoEn = Instant.now()`. Podés
  poner el chequeo de transición en
  `ArbolUrbanoEntity.actualizarEstado(EstadoDeArbol)` como segunda
  barrera (mismo criterio que `ObraPublicaEntity.actualizarEstado`), a tu
  criterio de dónde queda más claro.
- No se agrega ninguna columna nueva para esta tarea: no hay campo de
  motivo del retiro/intervención ni copia de quién actualizó el estado
  (ADR 0024, Pendiente de definir) — no lo agregues por iniciativa propia.

**Fuera de alcance**: edición de `especie`/`ubicacion`/`descripcion`/
`fechaDePlantacion` después de creado el registro — no la construyas ni la
bloquees con validación extra, simplemente no existe ese endpoint.

## Tarea 3 (backend) — test de aislamiento entre tenants

**Obligatorio, no diferible (CLAUDE.md).** Crear
`backend/src/test/java/ar/com/ciudaddigital/arbolado/ArboladoTest.java`
(extiende `SoporteDeIntegracion`, mismo patrón que `ObrasTest`/`MultasTest`),
con un test `@DisplayName("aislamiento: un árbol registrado en un
municipio no es visible ni actualizable desde otro")` que: registra un
árbol en el tenant A, verifica que `GET /api/arbolado` (con y sin
filtros) desde el tenant B no lo incluye, y que
`PATCH /api/arbolado/{id}/estado` ejecutado contra el tenant B con el `id`
del árbol del tenant A da 404 (no "lo encuentra y lo actualiza").

Cubrí además, en tests normales (no de aislamiento): alta con
`arbolado.gestionar` (queda `PLANTADO`), alta sin el permiso (403),
listado público sin sesión con cada filtro (`estado`, `q`) por separado y
combinados, circuito completo de transiciones
(`PLANTADO → SANO → REQUIERE_INTERVENCION → SANO → REQUIERE_INTERVENCION
→ RETIRADO`), transiciones inválidas (por ejemplo `PLANTADO →
REQUIERE_INTERVENCION` directo, `SANO → RETIRADO` directo, o cualquier
transición desde `RETIRADO`) → 400, y `MODULO_NO_CONTRATADO` en
`POST`/`GET`/`PATCH` cuando el tenant no tiene `arbolado` contratado.

## Tarea 4 (frontend) — pantalla del módulo `arbolado`

**Comportamiento observable**: pantalla nueva `PantallaDeArbolado.tsx` en
`frontend/src/modulos/arbolado/`, registrada en
`frontend/src/modulos/registro.ts` igual que el resto (mismo mecanismo
que `obras`/`boletin`).

Es una única vista para todos (sin router, mismo patrón por estado local
que `PantallaDeObras`): búsqueda/listado público siempre visible, con la
acción de "Registrar árbol" y las acciones de cambio de estado
apareciendo condicionadas al permiso, mismo patrón exacto que
`PantallaDeObras`/`PantallaDeBoletin` (no repliques el patrón de
`PantallaDeReclamos`, que muestra vistas *alternativas* según permiso —
acá, igual que Obras, el listado es el mismo para todos, solo cambia qué
acciones se ven).

1. **Búsqueda/listado** (default, pública, sin sesión): filtros
   combinables — `<select>` de estado (con opción "Todos"), campo de
   texto para `q` ("Buscar en especie o ubicación"). Tabla con columnas
   Especie, Ubicación, Estado, Fecha de plantación (formatear fechas
   igual que `PantallaDeObras#formatearFecha`: `AAAA-MM-DD` sin
   desfasaje de huso horario; fecha ausente muestra "—"). Etiquetas
   legibles en español para `estado` (mismo patrón `ETIQUETA_ESTADO` que
   `PantallaDeObras`; sugerido: Plantado, Sano, Requiere intervención,
   Retirado).

2. **Registrar árbol** (visible solo con
   `usuario?.permisos.includes('arbolado.gestionar')`, mismo patrón que la
   sección "Registrar una obra" de `PantallaDeObras`): formulario con
   Especie (`input`, obligatorio), Ubicación (`input`, obligatorio),
   Descripción (opcional, `textarea`), Fecha de plantación (opcional,
   `type="date"`). Al confirmar, recarga el listado con los filtros
   aplicados y cierra el formulario (mismo flujo que `registrarObra` en
   `PantallaDeObras`).

3. **Cambiar estado** (visible por fila, solo con `arbolado.gestionar`, y
   solo si el árbol tiene alguna transición válida desde su estado
   actual — usá el mismo mapa de transiciones que el backend, replicado
   en el frontend igual que `TRANSICIONES_VALIDAS` en `PantallaDeObras`,
   con el mismo comentario de que el enforcement real es del backend): un
   `<select>` por fila con las transiciones válidas desde el estado
   actual de ese árbol, y un botón "Actualizar estado" que dispara
   `PATCH /api/arbolado/{id}/estado`. Al confirmar, recargá el listado
   completo con los filtros aplicados, mismo criterio de simplicidad que
   `PantallaDeObras`.

**Accesibilidad (obligatorio, no diferible, CLAUDE.md)**: seguí al pie de
la letra los patrones ya usados en `PantallaDeObras.tsx` — foco
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
  `CircuitoDeTramite`): no se extiende, no se le agrega un tipo `ARBOL`
  (ADR 0024 §4).
- El módulo `obras` (código, tabla, permisos): `arbolado` no depende de
  `obras` ni reutiliza su código (ADR 0024 §1/§7).
- Los permisos de `boletin`, `reclamos`, `transparencia`, `multas`,
  `obras` u otro módulo existente.
- `modulosHabilitados` de los tenants de prueba `sanmartin`/`moron`
  (`db/control/V2__sembrar_municipios_de_prueba.sql`): si hace falta que
  `arbolado` esté contratado para demostrarlo manualmente, hacelo
  sembrando la contratación con el mecanismo ya existente (mismo criterio
  que la spec de CD-27 dejó para `obras`). Los tests de integración
  contratan el módulo directamente contra la base de control de test,
  mismo patrón que `ObrasTest`/`MultasTest`.

## Instrucciones para los agentes implementadores

No hagas commit, push ni abras PR por tu cuenta: dejá los cambios en el
working tree. El tech lead revisa, commitea y coordina el PR.
