# CD-33 · R24 — Educación municipal: padrón público de instituciones educativas, segunda rebanada de Fase 5

Ver [ADR 0028](../docs/arquitectura/decisiones/0028-educacion-municipal-padron-de-instituciones-segunda-rebanada-de-fase-5.md)
para el porqué de cada decisión de esta spec. Esta spec no reabre nada del
ADR: lo traduce a tareas concretas.

## Demo objetivo

Un agente municipal (con sesión y permiso `educacion.gestionar`) da de
alta una institución educativa municipal: nombre, tipo ("Jardín de
infantes"), ubicación, descripción (opcional). Queda creada en estado
"Activa". Un vecino, sin sesión, entra al portal público, filtra por
tipo y por estado, busca por texto (nombre o ubicación), y encuentra esa
institución listada con su estado actual. El mismo agente actualiza el
estado a "Cerrada temporalmente" y después a "Cerrada definitivamente";
al volver a consultar, el vecino ve el estado actualizado (y, una vez
`CERRADA_DEFINITIVAMENTE`, que ya no admite más cambios). La misma
institución no aparece en el portal de otro municipio.

## Tarea 1 (backend) — módulo `educacion`: modelo, alta protegida, lectura pública, migración, permisos

**Comportamiento observable**: con sesión y `educacion.gestionar`,
`POST /api/educacion` da de alta una institución en estado `ACTIVA` y
devuelve sus datos (201). Sin sesión, `GET /api/educacion` devuelve el
listado de instituciones del municipio en curso, con filtros opcionales
combinables `estado`, `tipo` y `q` (coincidencia `ILIKE` en `nombre` u
`ubicacion`), ordenado por `creadoEn` descendente. Un municipio sin el
módulo `educacion` contratado rechaza ambas rutas con 403
`MODULO_NO_CONTRATADO`, con o sin sesión. Sin sesión, `POST /api/educacion`
da 401/403 (mismo comportamiento que cualquier ruta protegida del
proyecto que no está en `rutasDeEscrituraPublica()`); con sesión pero sin
`educacion.gestionar`, 403.

**Modelo** (`educacion.internal`, módulo nuevo, prefijo `/api/educacion`):

- `TipoDeInstitucionEducativa`: enum `JARDIN_MATERNAL,
  JARDIN_DE_INFANTES, CENTRO_DE_FORMACION_PROFESIONAL, OTRA`. **No
  agregues `ESCUELA_PRIMARIA` ni `ESCUELA_SECUNDARIA`** ni ningún literal
  equivalente — a propósito, ver ADR 0028 §3: en Argentina esa es
  competencia provincial, no municipal, y este enum no la modela.
- `EstadoDeInstitucion`: enum `ACTIVA, CERRADA_TEMPORALMENTE,
  CERRADA_DEFINITIVAMENTE`.
- `InstitucionEducativaEntity` (tabla `institucion_educativa`), sin
  columna de tenant (mismo criterio que `ObraPublicaEntity`/
  `ArbolUrbanoEntity`: vive en la base del municipio, aislada por base
  física, no por columna):
  - `id`, `nombre` (`varchar(200)`, not null), `tipo` (`varchar(35)`, not
    null, `check` de valores válidos), `ubicacion` (`varchar(300)`, not
    null, texto libre — sin geolocalización estructurada, ADR 0028 §3/§6),
    `descripcion` (`text`, nullable).
  - `estado` (`varchar(25)`, not null, default `'ACTIVA'`, `check` de
    valores válidos).
  - Copia del actor que registra (ADR 0013, mismo criterio que
    `publicadoPorNombre`/`publicadoPorEmail` en `ObraPublicaEntity`/
    `ArbolUrbanoEntity`): `publicadoPorNombre` (`varchar(150)`, not
    null), `publicadoPorEmail` (`varchar(200)`, not null).
  - `creadoEn`, `actualizadoEn` (`timestamptz`, not null, default
    `now()`).
  - Sin más columnas: nada de cupos/vacantes, nada de fecha (a diferencia
    de Obras/Arbolado, esta entidad no tiene ningún campo de fecha
    propio), nada de inscripción de personas, sin adjuntos (ADR 0028 §6,
    Pendiente de definir) — no las agregues aunque te parezcan naturales,
    están fuera de alcance a propósito.
  - Índices: `institucion_educativa_creado_en_idx on
    institucion_educativa (creado_en desc)` (orden del listado, mismo
    criterio que `obra_publica_creado_en_idx`/`arbol_urbano_creado_en_idx`),
    `institucion_educativa_estado_idx on institucion_educativa (estado)`
    (filtro más usado del portal público).

- `GestionDeEducacion` (`@Service`), con
  `@Transactional("tenantTransactionManager")` en los métodos de
  escritura:
  - `registrar(nombre, tipo, ubicacion, descripcion, publicadoPorNombre, publicadoPorEmail)`:
    valida `nombre`/`ubicacion` no-blank y largos máximos (mismos límites
    de columna), `tipo` no nulo. `descripcion` es opcional, sin más
    validación que el tipo. Guarda con `estado = ACTIVA`; el estado
    inicial **no** es un parámetro que reciba el cliente, es siempre
    `ACTIVA`.
  - `buscar(estado, tipo, q)`: los tres parámetros opcionales y
    combinables (AND entre los que vengan). `estado`/`tipo` inválidos
    (que no matcheen ningún valor del enum correspondiente) → 400
    `SolicitudInvalida`, no se tratan como "sin filtro". `q` es `ILIKE
    '%valor%'` contra `nombre` **o** `ubicacion` (coincide en cualquiera
    de los dos). Ordena por `creadoEn` descendente, sin paginado (fuera
    de alcance, mismo criterio que Obras/Arbolado).

Creá también, desde cero en `educacion.internal` (son clases
package-private, no se pueden reutilizar entre módulos):
`SolicitudInvalida` (mismo texto/patrón que
`obras.internal.SolicitudInvalida`/`arbolado.internal.SolicitudInvalida`)
y `package-info.java` del paquete `educacion` con el resumen del módulo
(mismo estilo que `ar.com.ciudaddigital.obras.package-info`/
`ar.com.ciudaddigital.arbolado.package-info`).

**Fuera de alcance de esta tarea**: actualización de estado (Tarea 2),
permisos y `DescriptorDeModulo` (van también en esta tarea, ver abajo —
no hay una Tarea separada solo para eso, mismo criterio que Obras/
Arbolado).

**Migración** (`V24__crear_educacion.sql`, tenant): tabla
`institucion_educativa` completa (todas las columnas de arriba),
catálogo de permisos:

```sql
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('educacion.gestionar', 'Educación municipal', 'educacion', 'gestionar',
     'Registrar una institución educativa municipal y actualizar su estado.');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo from rol r, permiso p
where r.codigo in ('administrador', 'agente') and p.codigo = 'educacion.gestionar';
```

(Ver ADR 0028 §5 para el porqué de un único permiso y de asignarlo a
ambos roles de sistema — no lo reabras.)

**`DescriptorDelModuloEducacion`** (`educacion.internal`, `@Component`):
- `codigo() = "educacion"`, `nombre() = "Educación municipal"`, prefijo
  `/api/educacion`.
- `rutasDeLecturaPublica() = List.of("/api/educacion")` (el listado con
  filtros).
- `rutasDeEscrituraPublica()`: **no la sobrescribas** (default vacío) —
  este módulo no tiene ninguna escritura pública/anónima, mismo criterio
  que Obras/Arbolado (ADR 0028 §2).

**Controller** (`EducacionController`, `/api/educacion`): seguí el
estilo de `ObrasController`/`ArboladoController` (mismos nombres de
patrón: `ErrorResponse`, `@ExceptionHandler` por tipo de excepción,
records para request/response). `POST /api/educacion` con
`@PreAuthorize("hasAuthority('educacion.gestionar')")`, usa
`ActorAutenticado` (mismo mecanismo que `ObrasController.actorDe`) para
`publicadoPorNombre`/`publicadoPorEmail`. `GET /api/educacion` sin
`@PreAuthorize` (pública). Response único `InstitucionEducativaResponse`
con todos los campos.

## Tarea 2 (backend) — actualización de estado

**Comportamiento observable**: con sesión y `educacion.gestionar`,
`PATCH /api/educacion/{id}/estado` con body `{estadoNuevo}` cambia el
estado de la institución si la transición es válida (`ACTIVA →
CERRADA_TEMPORALMENTE`, `CERRADA_TEMPORALMENTE → ACTIVA`,
`CERRADA_TEMPORALMENTE → CERRADA_DEFINITIVAMENTE`) y actualiza
`actualizadoEn`. Transición no válida (incluida cualquier intento sobre
`CERRADA_DEFINITIVAMENTE`, terminal, y `ACTIVA → CERRADA_DEFINITIVAMENTE`
directo) → 400 `SolicitudInvalida` con mensaje claro indicando el estado
actual y el pedido. `id` inexistente → 404
(`InstitucionEducativaNoEncontrada`, mismo patrón que
`ObraNoEncontrada`/`ArbolNoEncontrado`). Sin sesión o sin
`educacion.gestionar` → 401/403, no está en `rutasDeEscrituraPublica()`.

**Implementación**:
- `GestionDeEducacion.actualizarEstado(Long id, EstadoDeInstitucion estadoNuevo)`:
  busca la institución (o `InstitucionEducativaNoEncontrada`), valida la
  transición contra una tabla `Map<EstadoDeInstitucion,
  Set<EstadoDeInstitucion>>` codificada en el servicio (mismo patrón que
  `GestionDeObras.TRANSICIONES_VALIDAS`/
  `GestionDeArbolado.TRANSICIONES_VALIDAS` — **no reutilices código de
  `obras` ni de `arbolado`**, `educacion` no depende de esos módulos, ADR
  0028 §1/Contexto), aplica el cambio y `actualizadoEn = Instant.now()`.
  Podés poner el chequeo de transición en
  `InstitucionEducativaEntity.actualizarEstado(EstadoDeInstitucion)` como
  segunda barrera (mismo criterio que
  `ObraPublicaEntity.actualizarEstado`), a tu criterio de dónde queda más
  claro.
- No se agrega ninguna columna nueva para esta tarea: no hay campo de
  motivo del cierre ni copia de quién actualizó el estado (ADR 0028,
  Pendiente de definir) — no lo agregues por iniciativa propia.

**Fuera de alcance**: edición de `nombre`/`tipo`/`ubicacion`/
`descripcion` después de creado el registro — no la construyas ni la
bloquees con validación extra, simplemente no existe ese endpoint. Cupos,
vacantes, e inscripción de personas: no existen en esta rebanada (ADR
0028 §6) — no agregues ningún campo ni endpoint relacionado.

## Tarea 3 (backend) — test de aislamiento entre tenants

**Obligatorio, no diferible (CLAUDE.md).** Crear
`backend/src/test/java/ar/com/ciudaddigital/educacion/EducacionTest.java`
(extiende `SoporteDeIntegracion`, mismo patrón que
`ObrasTest`/`ArboladoTest`), con un test `@DisplayName("aislamiento: una
institución registrada en un municipio no es visible ni actualizable
desde otro")` que: registra una institución en el tenant A, verifica que
`GET /api/educacion` (con y sin filtros) desde el tenant B no la incluye,
y que `PATCH /api/educacion/{id}/estado` ejecutado contra el tenant B con
el `id` de la institución del tenant A da 404 (no "la encuentra y la
actualiza").

Cubrí además, en tests normales (no de aislamiento): alta con
`educacion.gestionar` (queda `ACTIVA`), alta sin el permiso (403), alta
con `tipo` inválido (400), listado público sin sesión con cada filtro
(`estado`, `tipo`, `q`) por separado y combinados, circuito completo de
transiciones (`ACTIVA → CERRADA_TEMPORALMENTE → ACTIVA →
CERRADA_TEMPORALMENTE → CERRADA_DEFINITIVAMENTE`), transiciones
inválidas (por ejemplo `ACTIVA → CERRADA_DEFINITIVAMENTE` directo, o
cualquier transición desde `CERRADA_DEFINITIVAMENTE`) → 400, y
`MODULO_NO_CONTRATADO` en `POST`/`GET`/`PATCH` cuando el tenant no tiene
`educacion` contratado.

## Tarea 4 (frontend) — pantalla del módulo `educacion`

**Comportamiento observable**: pantalla nueva `PantallaDeEducacion.tsx`
en `frontend/src/modulos/educacion/`, registrada en
`frontend/src/modulos/registro.ts` igual que el resto (mismo mecanismo
que `obras`/`arbolado`).

Es una única vista para todos (sin router, mismo patrón por estado local
que `PantallaDeObras`/`PantallaDeArbolado`): búsqueda/listado público
siempre visible, con la acción de "Registrar institución" y las acciones
de cambio de estado apareciendo condicionadas al permiso, mismo patrón
exacto que esas dos pantallas (no repliques el patrón de
`PantallaDeReclamos`, que muestra vistas *alternativas* según permiso —
acá, igual que Obras/Arbolado, el listado es el mismo para todos, solo
cambia qué acciones se ven).

1. **Búsqueda/listado** (default, pública, sin sesión): filtros
   combinables — `<select>` de estado (con opción "Todos"), `<select>` de
   tipo (con opción "Todos"), campo de texto para `q` ("Buscar en nombre
   o ubicación"). Tabla con columnas Nombre, Tipo, Ubicación, Estado.
   Etiquetas legibles en español para `tipo` (sugerido: Jardín maternal,
   Jardín de infantes, Centro de formación profesional, Otra) y para
   `estado` (sugerido: Activa, Cerrada temporalmente, Cerrada
   definitivamente), mismo patrón `ETIQUETA_TIPO`/`ETIQUETA_ESTADO` que
   `PantallaDeObras`.

2. **Registrar institución** (visible solo con
   `usuario?.permisos.includes('educacion.gestionar')`, mismo patrón que
   la sección "Registrar una obra" de `PantallaDeObras`/"Registrar
   árbol" de `PantallaDeArbolado`): formulario con Nombre (`input`,
   obligatorio), Tipo (`<select>`, obligatorio, sin opción vacía
   seleccionable por default salvo el placeholder "Elegí un tipo"),
   Ubicación (`input`, obligatorio), Descripción (opcional,
   `textarea`). Al confirmar, recarga el listado con los filtros
   aplicados y cierra el formulario (mismo flujo que `registrarObra` en
   `PantallaDeObras`).

3. **Cambiar estado** (visible por fila, solo con `educacion.gestionar`,
   y solo si la institución tiene alguna transición válida desde su
   estado actual — usá el mismo mapa de transiciones que el backend,
   replicado en el frontend igual que `TRANSICIONES_VALIDAS` en
   `PantallaDeObras`, con el mismo comentario de que el enforcement real
   es del backend): un `<select>` por fila con las transiciones válidas
   desde el estado actual de esa institución, y un botón "Actualizar
   estado" que dispara `PATCH /api/educacion/{id}/estado`. Al confirmar,
   recargá el listado completo con los filtros aplicados, mismo criterio
   de simplicidad que `PantallaDeObras`/`PantallaDeArbolado`.

**Accesibilidad (obligatorio, no diferible, CLAUDE.md)**: seguí al pie de
la letra los patrones ya usados en `PantallaDeObras.tsx`/
`PantallaDeArbolado.tsx` — foco gestionado por `useRef`+`tabIndex={-1}`
al montar/cambiar de fila en edición, anuncios con
`role="status"`/`role="alert"`, `aria-invalid`/`aria-describedby` en
campos con error, `aria-busy` en botones de acción en curso, `<label
htmlFor>` en todo input/select, tabla con `<caption>` y
`scope="col"`/`scope="row"`. No inventes un patrón nuevo de
accesibilidad: replicá el que ya existe.

**Fuera de alcance**: routing de URLs (no existe en este frontend),
edición de los campos del alta, mapa/geolocalización, adjuntos/fotos,
paginado, cupos/vacantes, inscripción de personas.

## Qué NO tocar

- El módulo `obras` ni el módulo `arbolado` (código, tabla, permisos):
  `educacion` no depende de ninguno de los dos ni reutiliza su código
  (ADR 0028 §1/Contexto).
- El motor de `mesaentradas` (`Expediente`, `MovimientoDeExpediente`,
  `CircuitoDeTramite`): no se extiende, no se le agrega un tipo
  `INSTITUCION_EDUCATIVA`.
- El módulo `desarrollosocial` (código, tabla, permisos, patrón de
  minimización de datos personales): no aplica acá, esta rebanada no
  toca dato personal de nadie.
- Los permisos de `boletin`, `reclamos`, `transparencia`, `multas`,
  `obras`, `arbolado` u otro módulo existente.
- `modulosHabilitados` de los tenants de prueba `sanmartin`/`moron`
  (`db/control/V2__sembrar_municipios_de_prueba.sql`): si hace falta que
  `educacion` esté contratado para demostrarlo manualmente, hacelo
  sembrando la contratación con el mecanismo ya existente (mismo criterio
  que la spec de CD-27/CD-28 dejó para `obras`/`arbolado`). Los tests de
  integración contratan el módulo directamente contra la base de control
  de test, mismo patrón que `ObrasTest`/`ArboladoTest`.

## Instrucciones para los agentes implementadores

No hagas commit, push ni abras PR por tu cuenta: dejá los cambios en el
working tree. El tech lead revisa, commitea y coordina el PR.
