# CD-37 · R27 — Defensa Civil: alertas públicas y recursos, primera rebanada sin fase fija

Ver [ADR 0031](../docs/arquitectura/decisiones/0031-defensa-civil-alertas-publicas-y-recursos-primera-rebanada-sin-fase-fija.md)
para el porqué de cada decisión de esta spec, en particular por qué el
módulo se llama `defensacivil` y no `seguridad`, por qué **no** se
construye un canal de reporte ciudadano (eso ya es `reclamos`), por qué el
nivel de alerta usa la convención real del SMN, y por qué un único permiso
cubre las dos entidades. Esta spec no reabre nada del ADR: lo traduce a
tareas concretas.

## Demo objetivo

Un agente municipal (con sesión y permiso `defensacivil.gestionar`) publica
una alerta: tipo "Meteorológica", nivel "Naranja", título "Tormenta fuerte
con caída de granizo", descripción de la situación, recomendaciones ("Evitar
circular, retirar objetos sueltos de balcones y terrazas"), zona afectada
"Zona norte del partido". Queda `VIGENTE`. El mismo agente registra un
recurso: tipo "Refugio", nombre "Polideportivo Municipal", dirección "Av.
Libertador 1200", capacidad 200. Queda `ACTIVO`. Un vecino, sin sesión, entra
al portal público y ve la alerta vigente destacada junto con el listado de
recursos disponibles, filtra alertas por nivel "Naranja" y recursos por tipo
"Refugio", y encuentra ambos. Pasada la tormenta, el agente finaliza la
alerta; al volver a consultar, el vecino la ve como "Finalizada" (no
desaparece del listado). Ni la alerta ni el recurso aparecen en el portal de
otro municipio.

## Tarea 1 (backend) — módulo `defensacivil`, entidad Alerta: modelo, alta, lectura pública, finalización, migración, permisos

**Comportamiento observable**: con sesión y `defensacivil.gestionar`, `POST
/api/defensacivil/alertas` da de alta una alerta en estado `VIGENTE` y
devuelve sus datos (201). Sin sesión, `GET /api/defensacivil/alertas`
devuelve las alertas del municipio en curso, con filtros opcionales
combinables `tipo`, `nivel`, `estado` y `q` (ILIKE en `titulo` o
`descripcion`), ordenadas por `creadoEn` descendente. Con sesión y el
permiso, `PATCH /api/defensacivil/alertas/{id}/estado` con body
`{estadoNuevo: "FINALIZADA"}` finaliza una alerta `VIGENTE`. Un municipio
sin el módulo `defensacivil` contratado rechaza las tres rutas con 403
`MODULO_NO_CONTRATADO`, con o sin sesión. Sin sesión o sin el permiso, alta
y finalización dan 401/403.

**Modelo** (`defensacivil.internal`, módulo nuevo, prefijo
`/api/defensacivil`):

- `TipoDeAlerta`: enum `METEOROLOGICA, INUNDACION, OLA_DE_CALOR, INCENDIO,
  OTRA`.
- `NivelDeAlerta`: enum `AMARILLO, NARANJA, ROJO`.
- `EstadoDeAlerta`: enum `VIGENTE, FINALIZADA`.
- `AlertaDeDefensaCivilEntity` (tabla `alerta_defensa_civil`), sin columna
  de tenant (mismo criterio que `ObraPublicaEntity`/`EventoEntity`: vive en
  la base del municipio, aislada por base física):
  - `id`, `tipo` (`varchar(20)`, not null, `check`), `nivel` (`varchar(10)`,
    not null, `check`).
  - `titulo` (`varchar(300)`, not null), `descripcion` (`text`, not null),
    `recomendaciones` (`text`, not null), `zonaAfectada` (`varchar(300)`,
    nullable, texto libre).
  - `estado` (`varchar(15)`, not null, default `'VIGENTE'`, `check`).
  - `publicadoPorNombre` (`varchar(150)`, not null), `publicadoPorEmail`
    (`varchar(200)`, not null) — copia del actor, mismo criterio que
    `ObraPublicaEntity` (ADR 0013).
  - `creadoEn`, `actualizadoEn` (`timestamptz`, not null, default `now()`).
  - Sin geolocalización estructurada, sin adjuntos, sin campo de motivo de
    finalización (ADR 0031 §7, Pendiente de definir) — no los agregues.
  - Índices: `alerta_defensa_civil_creado_en_idx on alerta_defensa_civil
    (creado_en desc)` (orden del listado), `alerta_defensa_civil_estado_idx
    on alerta_defensa_civil (estado)` (filtro más usado del portal
    público).

- `GestionDeAlertas` (`@Service`), con
  `@Transactional("tenantTransactionManager")` en los métodos de escritura:
  - `publicar(tipo, nivel, titulo, descripcion, recomendaciones,
    zonaAfectada, publicadoPorNombre, publicadoPorEmail)`: valida `tipo` y
    `nivel` no nulos, `titulo`/`descripcion`/`recomendaciones` no-blank y
    dentro de sus largos máximos (`titulo` ≤ 300, `zonaAfectada` ≤ 300 si
    viene). `publicadoPorNombre`/`publicadoPorEmail` salen del actor
    autenticado, no de la solicitud (mismo criterio que
    `GestionDeArbolado#registrar`: si faltaran sería un problema del
    mecanismo de autenticación, no una solicitud inválida). Guarda con
    `estado = VIGENTE`; el estado inicial no es un parámetro que reciba el
    cliente.
  - `buscar(tipo, nivel, estado, q)`: los cuatro parámetros opcionales y
    combinables (AND). `tipo`/`nivel`/`estado` inválidos (no matchean
    ningún valor del enum) → 400 `SolicitudInvalida`, no se tratan como
    "sin filtro" (mismo criterio que `GestionDeArbolado#buscar`). `q` es
    `ILIKE '%valor%'` contra `titulo` **o** `descripcion`. Ordena por
    `creadoEn` descendente, sin paginado.
  - `finalizar(Long id)`: busca la alerta (o `AlertaNoEncontrada`, 404),
    valida que su estado actual sea `VIGENTE` (si no, `SolicitudInvalida`
    400 — cualquier intento sobre una alerta ya `FINALIZADA` rechaza,
    incluido pedir `estadoNuevo: "VIGENTE"`), aplica `FINALIZADA` y
    `actualizadoEn = Instant.now()`. Con una única transición posible, no
    hace falta una tabla `Map<EstadoDeAlerta, Set<EstadoDeAlerta>>` como en
    Obras/Arbolado: alcanza con el chequeo directo (mismo criterio que
    `GestionDeEventos#cancelar`, ADR 0030 §7).

Creá también, desde cero en `defensacivil.internal` (son clases
package-private, no se pueden reutilizar entre módulos): `SolicitudInvalida`
(mismo texto/patrón que `arbolado.internal.SolicitudInvalida`),
`AlertaNoEncontrada` y `RecursoNoEncontrado` (mismo patrón que
`ArbolNoEncontrado` — creá los dos ya en esta tarea aunque `RecursoNoEncontrado`
se use recién en la Tarea 2, para no duplicar el archivo), y
`package-info.java` del paquete `defensacivil` con el resumen del módulo
(mismo estilo que `ar.com.ciudaddigital.eventos.package-info`, citando ADR
0031).

**Registro de persistencia (obligatorio, se suele olvidar)**: agregá
`PAQUETE_DEFENSACIVIL = "ar.com.ciudaddigital.defensacivil"` a
`backend/src/main/java/ar/com/ciudaddigital/persistencia/ConfiguracionDePersistencia.java`
y sumalo tanto a `basePackages` de `RepositoriosDeTenant` como a
`setPackagesToScan(...)` de `tenantEntityManagerFactory` (mismo patrón que
`PAQUETE_EVENTOS`, agregado en R26).

**Migración** (`V27__crear_defensacivil.sql`, tenant): tabla
`alerta_defensa_civil` completa (todas las columnas de arriba) **y** tabla
`recurso_defensa_civil` (Tarea 2 — va en la misma migración, una sola
migración para todo el módulo, mismo criterio que módulos anteriores de una
sola migración por rebanada), catálogo de permisos:

```sql
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('defensacivil.gestionar', 'Defensa Civil', 'defensacivil', 'gestionar',
     'Publicar y finalizar alertas de Defensa Civil, y registrar y actualizar el estado de recursos (refugios, puntos de encuentro).');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo from rol r, permiso p
where r.codigo in ('administrador', 'agente') and p.codigo = 'defensacivil.gestionar';
```

(Ver ADR 0031 §3 para el porqué de un único permiso para las dos entidades y
de asignarlo a ambos roles de sistema — no lo reabras.)

**`DescriptorDelModuloDefensaCivil`** (`defensacivil.internal`,
`@Component`):
- `codigo() = "defensacivil"`, `nombre() = "Defensa Civil"` (no
  "Seguridad", ADR 0031 §1), `descripcion()` breve (alertas públicas y
  recursos de Defensa Civil del municipio), prefijo `/api/defensacivil`.
- `rutasDeLecturaPublica() = List.of("/api/defensacivil/alertas",
  "/api/defensacivil/recursos")` (ambos listados, aunque `recursos` recién
  se implemente en la Tarea 2 — declarala ya en esta tarea, es una sola
  clase para todo el módulo).
- `rutasDeEscrituraPublica()`: **no la sobrescribas** (default vacío) —
  ninguna escritura pública/anónima en este módulo (ADR 0031 §2).

**Controller** (`DefensaCivilController`, `/api/defensacivil`): seguí el
estilo de `ArboladoController`/`EventosController` (mismos nombres de
patrón: `ErrorResponse`, `@ExceptionHandler` por tipo de excepción, records
para request/response, helpers `tipoDeAlertaDe(String)`/
`nivelDe(String)`/`estadoDeAlertaDe(String)` que parsean o tiran
`SolicitudInvalida`). Subrutas `/alertas` y `/recursos` (Tarea 2) en el
mismo controller (mismo patrón que `mesaentradas`/`turnos`, que agrupan
varias sub-rutas de un mismo módulo en un controller único cuando comparten
prefijo — si preferís separar en dos `@RestController` con
`@RequestMapping("/api/defensacivil/alertas")` y
`@RequestMapping("/api/defensacivil/recursos")` respectivamente, también es
válido: elegí lo que quede más legible, no hay un precedente único y
obligatorio en el proyecto para este caso). `POST
/api/defensacivil/alertas` con
`@PreAuthorize("hasAuthority('defensacivil.gestionar')")`, usa
`ActorAutenticado` (mismo mecanismo que `ArboladoController.actorDe`) para
`publicadoPorNombre`/`publicadoPorEmail`. `GET /api/defensacivil/alertas`
sin `@PreAuthorize` (pública). `PATCH
/api/defensacivil/alertas/{id}/estado` con el mismo `@PreAuthorize`, body
`{estadoNuevo}`, solo acepta `"FINALIZADA"` como valor (cualquier otro,
incluido `"VIGENTE"`, → 400). Response único `AlertaResponse` con todos los
campos.

**Fuera de alcance de esta tarea**: entidad Recurso completa (Tarea 2), test
de aislamiento (Tarea 3), frontend (Tarea 4).

## Tarea 2 (backend) — entidad Recurso: modelo, alta, lectura pública, cambio de estado

**Comportamiento observable**: con sesión y `defensacivil.gestionar`, `POST
/api/defensacivil/recursos` da de alta un recurso en estado `ACTIVO` y
devuelve sus datos (201). Sin sesión, `GET /api/defensacivil/recursos`
devuelve los recursos del municipio en curso, con filtros opcionales
combinables `tipo`, `estado` y `q` (ILIKE en `nombre` o `direccion`),
ordenados por `creadoEn` descendente. Con sesión y el permiso, `PATCH
/api/defensacivil/recursos/{id}/estado` con body `{estadoNuevo: "ACTIVO"}` o
`{estadoNuevo: "INACTIVO"}` cambia el estado del recurso — transición libre
en ambos sentidos, salvo pedir el mismo estado en el que ya está (→ 400
`SolicitudInvalida`, "El recurso ya está en ese estado."). Un municipio sin
`defensacivil` contratado rechaza las tres rutas con 403
`MODULO_NO_CONTRATADO`. Sin sesión o sin el permiso, alta y cambio de estado
dan 401/403.

**Modelo** (`defensacivil.internal`):

- `TipoDeRecurso`: enum `REFUGIO, PUNTO_DE_ENCUENTRO, CENTRO_DE_ACOPIO,
  OTRO`.
- `EstadoDeRecurso`: enum `ACTIVO, INACTIVO`.
- `RecursoDeDefensaCivilEntity` (tabla `recurso_defensa_civil`, definida en
  la misma migración `V27__crear_defensacivil.sql` de la Tarea 1), sin
  columna de tenant:
  - `id`, `tipo` (`varchar(20)`, not null, `check`).
  - `nombre` (`varchar(200)`, not null), `direccion` (`varchar(300)`, not
    null, texto libre), `capacidad` (`integer`, nullable, sin `check` de
    rango: si viene, tiene que ser `>= 0`, validado en el servicio, no en
    la base), `telefonoContacto` (`varchar(50)`, nullable),
    `descripcion` (`text`, nullable).
  - `estado` (`varchar(10)`, not null, default `'ACTIVO'`, `check`).
  - `publicadoPorNombre` (`varchar(150)`, not null), `publicadoPorEmail`
    (`varchar(200)`, not null).
  - `creadoEn`, `actualizadoEn` (`timestamptz`, not null, default `now()`).
  - Índices: `recurso_defensa_civil_creado_en_idx on recurso_defensa_civil
    (creado_en desc)`, `recurso_defensa_civil_estado_idx on
    recurso_defensa_civil (estado)`.

- `GestionDeRecursos` (`@Service`),
  `@Transactional("tenantTransactionManager")` en escritura:
  - `registrar(tipo, nombre, direccion, capacidad, telefonoContacto,
    descripcion, publicadoPorNombre, publicadoPorEmail)`: valida `tipo` no
    nulo, `nombre`/`direccion` no-blank y dentro de sus largos máximos,
    `capacidad` si viene tiene que ser `>= 0` (si no, `SolicitudInvalida`).
    Guarda con `estado = ACTIVO`.
  - `buscar(tipo, estado, q)`: mismos criterios de combinación/validación
    que `GestionDeAlertas#buscar`, `q` sobre `nombre` **o** `direccion`.
  - `actualizarEstado(Long id, EstadoDeRecurso estadoNuevo)`: busca el
    recurso (o `RecursoNoEncontrado`, 404), si `estadoNuevo == null` →
    `SolicitudInvalida`; si `estadoNuevo == estado actual` →
    `SolicitudInvalida` ("El recurso ya está en ese estado."); si no,
    aplica el cambio y `actualizadoEn = Instant.now()`. Sin tabla de
    transiciones: con solo dos valores y transición libre en ambos
    sentidos, el chequeo directo alcanza (mismo espíritu que
    `GestionDeEventos#cancelar`, pero acá con dos destinos posibles en vez
    de uno).

Agregá las subrutas `/api/defensacivil/recursos` al mismo
`DefensaCivilController` de la Tarea 1 (o al segundo controller, si elegiste
separar — ver Tarea 1). `rutasDeLecturaPublica()` ya declaró
`/api/defensacivil/recursos` en la Tarea 1: no la vuelvas a tocar acá.
Response `RecursoResponse` con todos los campos (`capacidad`/
`telefonoContacto`/`descripcion` como `null` si no se cargaron).

**Fuera de alcance**: relación entre una alerta y un recurso (no existe,
ADR 0031 §1), edición de campos del alta en ninguna de las dos entidades.

## Tarea 3 (backend) — test de aislamiento entre tenants y tests funcionales

**Obligatorio, no diferible (CLAUDE.md).** Crear
`backend/src/test/java/ar/com/ciudaddigital/defensacivil/DefensaCivilTest.java`
(extiende `SoporteDeIntegracion`, mismo patrón que `EventosTest`/
`ArboladoTest`), con dos tests de aislamiento:

- `@DisplayName("aislamiento: una alerta publicada en un municipio no es
  visible ni finalizable desde otro")`: publica una alerta en el tenant A,
  verifica que `GET /api/defensacivil/alertas` (con y sin filtros) desde el
  tenant B no la incluye, y que `PATCH
  /api/defensacivil/alertas/{id}/estado` contra el tenant B con el `id` de
  la alerta de A da 404 (no "la encuentra y la finaliza").
- `@DisplayName("aislamiento: un recurso registrado en un municipio no es
  visible ni actualizable desde otro")`: mismo patrón que el anterior,
  aplicado a `RecursoDeDefensaCivilEntity` y `PATCH
  /api/defensacivil/recursos/{id}/estado`.

Cubrí además, en tests normales (no de aislamiento):

- Alta de alerta con el permiso (queda `VIGENTE`), alta sin el permiso
  (403).
- Listado público de alertas sin sesión, con cada filtro (`tipo`, `nivel`,
  `estado`, `q`) por separado y combinados, filtro con valor inválido → 400.
- Finalización exitosa (`VIGENTE → FINALIZADA`), finalización de una
  alerta ya `FINALIZADA` → 400, intento de `estadoNuevo: "VIGENTE"` → 400.
- Alta de recurso con el permiso (queda `ACTIVO`), alta sin el permiso
  (403), alta con `capacidad` negativa → 400.
- Listado público de recursos sin sesión, con cada filtro por separado y
  combinados.
- Cambio de estado de recurso en ambos sentidos (`ACTIVO → INACTIVO` y
  `INACTIVO → ACTIVO`), intento de pedir el mismo estado en el que ya está
  → 400.
- `MODULO_NO_CONTRATADO` en `POST`/`GET`/`PATCH` de ambas entidades cuando
  el tenant no tiene `defensacivil` contratado.

## Tarea 4 (frontend) — pantalla del módulo `defensacivil`

**Comportamiento observable**: pantalla nueva `PantallaDeDefensaCivil.tsx`
en `frontend/src/modulos/defensacivil/`, registrada en
`frontend/src/modulos/registro.ts` igual que el resto (agregando la entrada
`defensacivil: PantallaDeDefensaCivil`).

Es una única vista para todos (sin router, mismo patrón por estado local que
`PantallaDeArbolado`/`PantallaDeEventos`), con **dos secciones
independientes** (Alertas y Recursos), cada una con su propio listado
público, su propio formulario de alta condicionado al permiso y su propia
acción de cambio de estado por fila — mismo patrón exacto que
`PantallaDeArbolado`/`PantallaDeEventos` (no el de `PantallaDeReclamos`, que
muestra vistas *alternativas* según permiso: acá el listado es el mismo
para todos, solo cambia qué acciones se ven).

1. **Sección Alertas** (primera, arriba de la pantalla — es lo más urgente
   de mostrar):
   - Listado público con filtros combinables: `<select>` de tipo (con
     opción "Todos"), `<select>` de nivel (con opción "Todos"), `<select>`
     de estado (con opción "Todas", default sin filtrar — **no** filtres
     por `VIGENTE` de entrada: mostrá todas, el vecino tiene que poder ver
     también las finalizadas si quiere), campo de texto para `q` ("Buscar
     en título o descripción"). Tabla con columnas Tipo, Nivel, Título,
     Zona afectada, Estado. Las alertas `VIGENTE` se destacan visualmente
     (por ejemplo con una clase CSS distinta en la fila o un badge de
     texto "Vigente" bien visible) — no uses solo color para distinguirlas
     (WCAG 1.4.1, no transmitir información solo por color): agregá
     también el texto del estado en la celda. Etiquetas legibles en
     español para `tipo` (Meteorológica, Inundación, Ola de calor,
     Incendio, Otra), `nivel` (Amarillo, Naranja, Rojo) y `estado`
     (Vigente, Finalizada), mismo patrón `ETIQUETA_*` que
     `PantallaDeArbolado`.
   - Al expandir una fila (o con un botón "Ver detalle", a tu criterio de
     qué quede más simple sin romper el patrón de tabla del resto del
     proyecto) se puede ver `descripcion` y `recomendaciones` completos —
     si preferís mostrarlos directo en columnas de la tabla en vez de un
     detalle expandible, también es válido: elegí lo que quede más legible
     con textos largos, no hay precedente único para "detalle" en el
     proyecto todavía.
   - **Publicar alerta** (visible solo con
     `usuario?.permisos.includes('defensacivil.gestionar')`, mismo patrón
     que "Registrar árbol" de `PantallaDeArbolado`): formulario con Tipo
     (`<select>`, obligatorio, sin opción "Todos" acá), Nivel (`<select>`,
     obligatorio), Título (`input`, obligatorio), Descripción
     (`textarea`, obligatorio), Recomendaciones (`textarea`, obligatorio),
     Zona afectada (`input`, opcional). Al confirmar, recarga el listado de
     alertas con los filtros aplicados y cierra el formulario.
   - **Finalizar alerta** (visible por fila, solo con
     `defensacivil.gestionar`, y solo si la alerta está `VIGENTE` — una
     alerta `FINALIZADA` no muestra ninguna acción): un botón "Finalizar
     alerta" por fila (sin `<select>` de destino, un solo destino posible,
     mismo patrón que "Cancelar evento" de `PantallaDeEventos`), con
     confirmación previa (`window.confirm` con el título de la alerta,
     mismo criterio mínimo que `PantallaDeEventos`). Al confirmar, dispara
     `PATCH .../estado` con `{estadoNuevo: "FINALIZADA"}` y recarga el
     listado.

2. **Sección Recursos** (debajo de Alertas):
   - Listado público con filtros combinables: `<select>` de tipo (con
     opción "Todos"), `<select>` de estado (con opción "Todos"), campo de
     texto para `q` ("Buscar en nombre o dirección"). Tabla con columnas
     Tipo, Nombre, Dirección, Capacidad (mostrar "—" si es `null`),
     Teléfono (mostrar "—" si es `null`), Estado. Etiquetas legibles en
     español para `tipo` (Refugio, Punto de encuentro, Centro de acopio,
     Otro) y `estado` (Activo, Inactivo).
   - **Registrar recurso** (visible solo con `defensacivil.gestionar`,
     mismo patrón que arriba): formulario con Tipo (`<select>`,
     obligatorio), Nombre (`input`, obligatorio), Dirección (`input`,
     obligatorio), Capacidad (`input type="number"`, opcional, `min="0"`),
     Teléfono de contacto (`input`, opcional), Descripción (`textarea`,
     opcional).
   - **Cambiar estado** (visible por fila, solo con
     `defensacivil.gestionar`): mismo patrón exacto de `<select>` +
     "Actualizar estado"/"Cancelar" por fila que usa
     `PantallaDeArbolado` para su cambio de estado sanitario (acá el
     `<select>` solo ofrece el estado contrario al actual, ya que la
     transición es siempre "al otro estado").

**Accesibilidad (obligatorio, no diferible, CLAUDE.md)**: seguí al pie de la
letra los patrones ya usados en `PantallaDeArbolado.tsx`/
`PantallaDeEventos.tsx` — foco gestionado por `useRef`+`tabIndex={-1}` al
montar/cambiar de estado (el `<h1>` de la pantalla, y cada formulario al
abrirse), anuncios con `role="status"` (cargando) / `role="alert"`
(errores), `aria-invalid`/`aria-describedby` en campos con error,
`aria-busy` en botones de acción en curso, `<label htmlFor>` en todo
input/select, cada tabla con su propio `<caption>` y `scope="col"`/
`scope="row"`, cada sección con su propio `<h2>` y `aria-labelledby`. Con
dos secciones en la misma pantalla, usá encabezados `<h2>` separados
("Alertas de Defensa Civil", "Recursos de Defensa Civil") para que la
navegación por encabezados de un lector de pantalla distinga claramente
las dos partes — no un único `<h2>` compartido. No inventes un patrón
nuevo de accesibilidad: replicá el que ya existe.

**Fuera de alcance**: routing de URLs, edición de los campos del alta en
cualquiera de las dos entidades, mapa/geolocalización, adjuntos/fotos,
paginado, notificación push/SMS, vínculo entre una alerta y un recurso.

## Qué NO tocar

- El módulo `reclamos` (código, tablas, permisos): `defensacivil` no lo
  extiende ni lo reemplaza para el caso de reporte ciudadano de riesgo —
  esa función sigue siendo de `reclamos` (ADR 0031, Contexto).
- Los módulos `obras`, `arbolado`, `educacion`, `espaciosverdes`, `eventos`,
  `boletin`, `prensa` (código, tablas, permisos): `defensacivil` no depende
  de ninguno ni reutiliza su código.
- `modulosHabilitados` de los tenants de prueba `sanmartin`/`moron`
  (`db/control/V2__sembrar_municipios_de_prueba.sql`): si hace falta que
  `defensacivil` esté contratado para demostrarlo manualmente, hacelo
  sembrando la contratación con el mecanismo ya existente (mismo criterio
  que dejaron las specs de CD-34/CD-35). Los tests de integración
  contratan el módulo directamente contra la base de control de test,
  mismo patrón que `EventosTest`/`ArboladoTest`.
- Ninguna integración con cámaras, sensores, hardware de monitoreo ni APIs
  externas de alerta temprana (SMN u otro organismo): fuera de alcance a
  propósito (ADR 0031, Contexto y Pendiente de definir).

## Instrucciones para los agentes implementadores

No hagas commit, push ni abras PR por tu cuenta: dejá los cambios en el
working tree. El tech lead revisa, commitea y coordina el PR.
