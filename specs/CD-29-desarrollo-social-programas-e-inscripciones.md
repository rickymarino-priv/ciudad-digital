# CD-29 · R21 — Desarrollo Social: catálogo de programas sociales e inscripción con datos minimizados, primera rebanada de Fase 5

Ver [ADR 0025](../docs/arquitectura/decisiones/0025-desarrollo-social-inscripcion-a-programa-social-con-minimizacion-de-datos-sensibles.md)
para el porqué de cada decisión de esta spec. Esta spec no reabre nada del
ADR: lo traduce a tareas concretas. Leé el ADR completo antes de
implementar — en particular Decisión 6 y Decisión 7, que son las que más
se apartan del patrón de módulos anteriores (Obras/Arbolado/Multas).

## Demo objetivo

Un administrador (con sesión y `desarrollosocial.gestionarProgramas`)
publica un programa social: "Refuerzo alimentario municipal", estado
`ABIERTO`. Un vecino, sin sesión, entra al portal público, ve el programa
en el catálogo, y se inscribe: nombre, DNI, contacto, cantidad de
integrantes del grupo familiar, situación declarada ("Empleo informal"),
sin subir ningún comprobante. Recibe un código de seguimiento. Con ese
código, en otro momento, consulta el estado de su inscripción sin
sesión y ve "Recibida". El mismo administrador (ahora con
`desarrollosocial.revisarInscripciones`) entra a la bandeja de
inscripciones, ve los datos completos de esa inscripción, la pasa a "En
evaluación" y después a "Aprobada" con un comentario. El vecino, al
volver a consultar con su código, ve el nuevo estado y el comentario,
pero no ve las inscripciones de nadie más — no existe ningún listado
público de inscripciones. La misma inscripción no aparece en el portal
de otro municipio.

## Tarea 1 (backend) — módulo `desarrollosocial`: catálogo de programas sociales

**Comportamiento observable**: con sesión y
`desarrollosocial.gestionarProgramas`, `POST /api/desarrollosocial/programas`
da de alta un programa en estado `ABIERTO` y devuelve sus datos (201).
Sin sesión, `GET /api/desarrollosocial/programas` devuelve el listado de
programas del municipio en curso, con filtros opcionales combinables
`estado` y `q` (`ILIKE` en `nombre` o `descripcion`), ordenado por
`creadoEn` descendente. Con sesión y el mismo permiso,
`PATCH /api/desarrollosocial/programas/{id}/estado` cambia el estado
(`ABIERTO ↔ CERRADO`, en ambos sentidos). Un municipio sin el módulo
`desarrollosocial` contratado rechaza las tres rutas con 403
`MODULO_NO_CONTRATADO`, con o sin sesión. Sin sesión o sin el permiso,
alta y cambio de estado dan 401/403 (no están en
`rutasDeEscrituraPublica()`).

**Modelo** (`desarrollosocial.internal`, módulo nuevo, prefijo
`/api/desarrollosocial`):

- `EstadoDePrograma`: enum `ABIERTO, CERRADO`.
- `ProgramaSocialEntity` (tabla `programa_social`), sin columna de
  tenant (mismo criterio que `ObraPublicaEntity`/`ArbolUrbanoEntity`):
  - `id`, `nombre` (`varchar(150)`, not null), `descripcion` (`text`,
    nullable), `criteriosDeElegibilidad` (`text`, nullable — texto libre
    descriptivo, no un motor de reglas, ADR 0025 §3).
  - `estado` (`varchar(15)`, not null, default `'ABIERTO'`, `check` de
    valores válidos).
  - Copia del actor que publica (ADR 0013, mismo criterio que
    `publicadoPorNombre`/`publicadoPorEmail` en `ObraPublicaEntity`):
    `publicadoPorNombre` (`varchar(150)`, not null), `publicadoPorEmail`
    (`varchar(200)`, not null).
  - `creadoEn`, `actualizadoEn` (`timestamptz`, not null, default
    `now()`).
  - Sin más columnas: nada de monto de subsidio, cupo máximo ni fechas de
    convocatoria — no las agregues, están fuera de alcance a propósito
    (no hay decisión del ADR que las cubra).
  - Índices: `programa_social_creado_en_idx on programa_social (creado_en desc)`,
    `programa_social_estado_idx on programa_social (estado)`.

- `GestionDeProgramasSociales` (`@Service`), con
  `@Transactional("tenantTransactionManager")` en los métodos de
  escritura:
  - `publicar(nombre, descripcion, criteriosDeElegibilidad, publicadoPorNombre, publicadoPorEmail)`:
    valida `nombre` no-blank y largo máximo (mismo límite de columna);
    `descripcion`/`criteriosDeElegibilidad` opcionales, sin más
    validación que el tipo. Guarda con `estado = ABIERTO`; no es un
    parámetro que reciba el cliente.
  - `buscar(estado, q)`: ambos parámetros opcionales y combinables (AND
    entre los que vengan). `estado` inválido → 400 `SolicitudInvalida`.
    `q` es `ILIKE '%valor%'` contra `nombre` **o** `descripcion`. Ordena
    por `creadoEn` descendente, sin paginado.
  - `cambiarEstado(Long id, EstadoDePrograma estadoNuevo)`: busca el
    programa (o `ProgramaNoEncontrado`, 404) y aplica el cambio
    (`ABIERTO ↔ CERRADO`, cualquiera de los dos sentidos es válido — no
    hay transición inválida entre estos dos valores, a diferencia de
    Obras/Arbolado no hace falta tabla de transiciones). Actualiza
    `actualizadoEn`.

Creá desde cero en `desarrollosocial.internal` (no reutilices nada de
`obras`/`arbolado`, ADR 0025 §1): `SolicitudInvalida`,
`ProgramaNoEncontrado`, y `package-info.java` del paquete
`desarrollosocial` con el resumen del módulo.

**Migración** (`V21__crear_desarrollosocial.sql`, tenant): tabla
`programa_social` completa. El catálogo de permisos va completo en esta
tarea (los dos permisos del módulo, aunque `desarrollosocial.revisarInscripciones`
recién se usa en la Tarea 2 — mismo criterio que Obras/Arbolado de no
partir el catálogo de permisos en dos migraciones):

```sql
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('desarrollosocial.gestionarProgramas', 'Desarrollo Social', 'desarrollosocial', 'gestionarProgramas',
     'Publicar un programa social y abrir o cerrar su convocatoria.'),
    ('desarrollosocial.revisarInscripciones', 'Desarrollo Social', 'desarrollosocial', 'revisarInscripciones',
     'Ver las inscripciones a programas sociales, con sus datos personales, y resolverlas.');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo from rol r, permiso p
where r.codigo in ('administrador', 'agente') and p.codigo = 'desarrollosocial.gestionarProgramas';

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo from rol r, permiso p
where r.codigo = 'administrador' and p.codigo = 'desarrollosocial.revisarInscripciones';
```

(Ver ADR 0025 §7 para el porqué de dos permisos separados y de que
`revisarInscripciones` **no** se asigna a `agente` — no lo reabras, no lo
"corrijas" para que sea simétrico con otros módulos.)

**`DescriptorDelModuloDesarrolloSocial`** (`desarrollosocial.internal`,
`@Component`):
- `codigo() = "desarrollosocial"`, `nombre() = "Desarrollo Social"`,
  prefijo `/api/desarrollosocial`.
- `rutasDeLecturaPublica() = List.of("/api/desarrollosocial/programas")`
  en esta tarea (el listado de programas). La Tarea 2 le agrega la ruta
  de seguimiento por token — hacelo en esa tarea, no adelantes acá una
  lista que todavía no existe.
- `rutasDeEscrituraPublica()`: **no la declares en esta tarea** (el alta
  de programa es protegida). La Tarea 2 la agrega para el alta de
  inscripciones.

**Controller** (`ProgramasSocialesController` o unificado en un único
`DesarrolloSocialController` que después la Tarea 2/3 extienden — a tu
criterio si separás por entidad o no, siempre que termine con prefijo
único `/api/desarrollosocial` y estilo `ObrasController`): `POST
/api/desarrollosocial/programas` con
`@PreAuthorize("hasAuthority('desarrollosocial.gestionarProgramas')")`,
usa `ActorAutenticado` para `publicadoPorNombre`/`publicadoPorEmail`.
`GET /api/desarrollosocial/programas` sin `@PreAuthorize`. `PATCH
/api/desarrollosocial/programas/{id}/estado` con el mismo permiso.
Response `ProgramaSocialResponse` con todos los campos.

## Tarea 2 (backend) — alta pública de inscripciones y seguimiento por token

**Comportamiento observable**: sin sesión, `POST
/api/desarrollosocial/inscripciones` da de alta una inscripción en
estado `RECIBIDA` contra un programa `ABIERTO` existente, y devuelve
(201) un objeto mínimo con `id`, `estado` y `tokenDeSeguimiento` — **sin**
reexponer ningún otro campo enviado (ADR 0025 §6, mismo criterio que
`ReclamoPublicoResponse`). `programaId` inexistente o de un programa
`CERRADO` → 400 `SolicitudInvalida` con mensaje claro ("El programa no
existe o no admite inscripciones en este momento" — un único mensaje
para ambos casos, no distingas "no existe" de "cerrado": no le des a
quien prueba ids al azar más información de la necesaria, mismo espíritu
que ADR 0017 §4 aplica al token). Sin sesión, `GET
/api/desarrollosocial/inscripciones/seguimiento/{token}` devuelve el
estado de una inscripción puntual; un token que no matchea ninguna fila
da 404 con el mismo mensaje genérico sin importar el motivo (ADR 0017
§4).

**Modelo**:

- `EstadoDeInscripcion`: enum `RECIBIDA, EN_EVALUACION, APROBADA, RECHAZADA`.
- `SituacionDeclarada`: enum `DESOCUPADO, EMPLEO_INFORMAL, EMPLEO_FORMAL, JUBILADO_O_PENSIONADO, OTRO`
  (ADR 0025 §4 — categorías amplias, nunca un monto).
- `InscripcionSocialEntity` (tabla `inscripcion_social`), sin columna de
  tenant:
  - `id`, `programaId` (`bigint`, not null, FK a `programa_social.id`).
  - `nombreSolicitante` (`varchar(150)`, not null), `dniSolicitante`
    (`varchar(20)`, not null), `contacto` (`varchar(200)`, not null —
    a diferencia de `contactoDelVecino` en Reclamos, acá es obligatorio,
    ADR 0025 §4).
  - `cantidadIntegrantesGrupoFamiliar` (`integer`, not null, `check > 0`).
  - `situacionDeclarada` (`varchar(30)`, not null, `check` de valores
    válidos del enum).
  - `comentarioAdicional` (`varchar(2000)`, nullable).
  - `estado` (`varchar(15)`, not null, default `'RECIBIDA'`, `check` de
    valores válidos).
  - `tokenHash` (`varchar(64)`, not null) — hash SHA-256 del token de
    seguimiento (ADR 0017 §2), con índice único
    `inscripcion_social_token_hash_idx`.
  - `comentarioDeResolucion` (`varchar(2000)`, nullable),
    `resueltoPorNombre` (`varchar(150)`, nullable), `resueltoPorEmail`
    (`varchar(200)`, nullable), `resueltoEn` (`timestamptz`, nullable) —
    se completan en la Tarea 3.
  - `creadoEn`, `actualizadoEn` (`timestamptz`, not null, default
    `now()`).
  - Sin más columnas: nada de ingresos, adjuntos, ni datos de los
    integrantes del grupo familiar más allá de la cantidad (ADR 0025 §4)
    — no las agregues.
  - Índices: `inscripcion_social_creado_en_idx on inscripcion_social (creado_en desc)`,
    `inscripcion_social_programa_id_idx on inscripcion_social (programa_id)`,
    `inscripcion_social_estado_idx on inscripcion_social (estado)`.

- `GestionDeInscripcionesSociales` (`@Service`), inyecta
  `ProgramaSocialRepository` además de su propio repositorio (mismo
  módulo, ambos en `desarrollosocial.internal`, no cruza límite de
  Spring Modulith):
  - `inscribir(programaId, nombreSolicitante, dniSolicitante, contacto, cantidadIntegrantesGrupoFamiliar, situacionDeclarada, comentarioAdicional)`:
    valida todos los campos requeridos no-blank/positivos y largos
    máximos (mismos límites de columna); busca el programa y valida que
    exista y esté `ABIERTO` (si no, `SolicitudInvalida` con el mensaje
    genérico de arriba). Genera el token con
    `TokenDeSeguimiento.generar()` (paquete `seguimientoanonimo`,
    reutilizado tal cual, ADR 0017 §3), guarda `tokenHash =
    TokenDeSeguimiento.hash(token)`, `estado = RECIBIDA`. Devuelve un
    record `InscripcionCreada(InscripcionSocialEntity inscripcion, String tokenDeSeguimiento)`
    (mismo patrón que `GestionDeReclamos.ReclamoCreado`): el token en
    claro solo vive en este valor de retorno, nunca se vuelve a poder
    leer.
  - `consultarPorToken(String token)`: calcula el hash y busca por
    `tokenHash`; si no hay fila, lanza `TokenNoEncontrado` (crear esta
    excepción en `desarrollosocial.internal`, mismo patrón que la de
    `reclamos.internal`).

**`DescriptorDelModuloDesarrolloSocial`** (edición de la Tarea 1):
- `rutasDeLecturaPublica()` pasa a
  `List.of("/api/desarrollosocial/programas", "/api/desarrollosocial/inscripciones/seguimiento/{token}")`.
- `rutasDeEscrituraPublica() = List.of("/api/desarrollosocial/inscripciones")`.

**Controller**: `POST /api/desarrollosocial/inscripciones` sin
`@PreAuthorize` (ruta de escritura pública). `GET
/api/desarrollosocial/inscripciones/seguimiento/{token}` sin
`@PreAuthorize`. Dos responses nuevos:

- `InscripcionPublicaResponse(Long id, String estado, String tokenDeSeguimiento)`:
  lo único que devuelve el alta (ver arriba).
- `SeguimientoDeInscripcionResponse(Long id, String nombrePrograma, String estado, String comentarioDeResolucion, Instant creadoEn, Instant actualizadoEn)`:
  lo que devuelve la consulta por token — necesitás el nombre del
  programa (join contra `ProgramaSocialRepository` en el service, no en
  el controller), y **nada más** de lo que el vecino envió (ADR 0025
  §6): sin `nombreSolicitante`, `dniSolicitante`, `contacto`,
  `cantidadIntegrantesGrupoFamiliar`, `situacionDeclarada` ni
  `comentarioAdicional`.

**Fuera de alcance de esta tarea**: listado protegido de inscripciones y
cambio de su estado (Tarea 3).

## Tarea 3 (backend) — bandeja de gestión de inscripciones (permiso reservado)

**Comportamiento observable**: con sesión y
`desarrollosocial.revisarInscripciones`, `GET
/api/desarrollosocial/inscripciones` devuelve el listado completo de
inscripciones del municipio (con todos los campos, incluidos los
personales), con filtros opcionales combinables `programaId` y `estado`,
ordenado por `creadoEn` descendente. Sin sesión, o con sesión pero sin
ese permiso específico —**incluida una sesión con
`desarrollosocial.gestionarProgramas` pero sin
`revisarInscripciones`**—, da 403: no hay lectura pública ni protegida
por el otro permiso del módulo (ADR 0025 §6/§7, es la pieza central de
esta rebanada, no la debilites).

Con el mismo permiso, `PATCH
/api/desarrollosocial/inscripciones/{id}/estado` cambia el estado:
`RECIBIDA → EN_EVALUACION` sin comentario obligatorio;
`EN_EVALUACION → APROBADA` y `EN_EVALUACION → RECHAZADA` **exigen**
`comentarioDeResolucion` no vacío (400 `SolicitudInvalida` si falta).
Cualquier otra transición (incluida `RECIBIDA → APROBADA` directo, o
cualquier intento sobre `APROBADA`/`RECHAZADA`, terminales) → 400
`SolicitudInvalida`. `id` inexistente → 404 (`InscripcionNoEncontrada`).

**Implementación**:
- `GestionDeInscripcionesSociales.listarParaGestion(Long programaId, EstadoDeInscripcion estado)`:
  ambos filtros opcionales y combinables; `estado` inválido → 400.
- `GestionDeInscripcionesSociales.actualizarEstado(Long id, EstadoDeInscripcion estadoNuevo, String comentarioDeResolucion, String resueltoPorNombre, String resueltoPorEmail)`:
  busca la inscripción (o `InscripcionNoEncontrada`), valida la
  transición contra una tabla `Map<EstadoDeInscripcion, Set<EstadoDeInscripcion>>`
  codificada en el servicio (mismo patrón que
  `GestionDeObras`/`GestionDeArbolado`, sin reutilizar código de esos
  módulos). Si el destino es `APROBADA` o `RECHAZADA`, exige
  `comentarioDeResolucion` no vacío y completa
  `resueltoPorNombre`/`resueltoPorEmail`/`resueltoEn`; si el destino es
  `EN_EVALUACION`, el comentario es opcional y no se pisan los campos de
  resolución. Podés poner el chequeo de transición también en
  `InscripcionSocialEntity` como segunda barrera, mismo criterio que
  `ObraPublicaEntity`/`ArbolUrbanoEntity`.

**Controller**: `GET /api/desarrollosocial/inscripciones` y `PATCH
/api/desarrollosocial/inscripciones/{id}/estado`, ambos con
`@PreAuthorize("hasAuthority('desarrollosocial.revisarInscripciones')")`
— **no** `hasAnyAuthority` con `gestionarProgramas`: son permisos
distintos a propósito (ADR 0025 §7). Response
`InscripcionResponse` con **todos** los campos (esta es la única vista
que los expone completos, y ya está detrás del permiso correcto).

## Tarea 4 (backend) — test de aislamiento entre tenants

**Obligatorio, no diferible (CLAUDE.md).** Crear
`backend/src/test/java/ar/com/ciudaddigital/desarrollosocial/DesarrolloSocialTest.java`
(extiende `SoporteDeIntegracion`, mismo patrón que `ObrasTest`/
`ArboladoTest`/`MultasTest`), con un test `@DisplayName("aislamiento: un
programa y una inscripción de un municipio no son visibles ni
gestionables desde otro")` que: publica un programa en el tenant A, se
inscribe a él (tenant A), y verifica que:
- `GET /api/desarrollosocial/programas` desde el tenant B no incluye el
  programa del tenant A.
- `PATCH /api/desarrollosocial/programas/{id}/estado` contra el tenant B
  con el `id` del programa del tenant A da 404.
- `GET /api/desarrollosocial/inscripciones` (con
  `desarrollosocial.revisarInscripciones` en el tenant B) no incluye la
  inscripción del tenant A.
- `GET /api/desarrollosocial/inscripciones/seguimiento/{token}` con el
  token real generado en el tenant A, consultado contra el tenant B, da
  404 (el hash del token vive en la base del tenant A, no existe fila en
  la base del tenant B con ese `tokenHash`).
- `PATCH /api/desarrollosocial/inscripciones/{id}/estado` contra el
  tenant B con el `id` de la inscripción del tenant A da 404.

Cubrí además, en tests normales (no de aislamiento):
- Alta de programa con `gestionarProgramas` (queda `ABIERTO`); alta sin
  el permiso (403); listado público con cada filtro por separado y
  combinados; `PATCH` de estado en ambos sentidos.
- Alta de inscripción pública contra un programa `ABIERTO` (queda
  `RECIBIDA`, devuelve token); contra un programa `CERRADO` → 400;
  contra un `programaId` inexistente → 400 con el mismo mensaje que el
  caso anterior.
- Seguimiento por token válido devuelve el shape minimizado; token
  inválido/inexistente → 404 con mensaje genérico.
- `GET /api/desarrollosocial/inscripciones` con `gestionarProgramas`
  pero **sin** `revisarInscripciones` → 403 (verificá explícitamente
  este caso, es la barrera central de la rebanada — ADR 0025 §7).
- Circuito completo de transiciones de inscripción (`RECIBIDA →
  EN_EVALUACION → APROBADA`, y por separado `RECIBIDA → EN_EVALUACION →
  RECHAZADA`), cada resolución sin comentario → 400, `RECIBIDA →
  APROBADA` directo → 400, cualquier transición desde `APROBADA`/
  `RECHAZADA` → 400.
- `MODULO_NO_CONTRATADO` en todas las rutas cuando el tenant no tiene
  `desarrollosocial` contratado.

## Tarea 5 (frontend) — pantalla del módulo `desarrollosocial`

**Comportamiento observable**: pantalla nueva
`PantallaDeDesarrolloSocial.tsx` en
`frontend/src/modulos/desarrollosocial/`, registrada en
`frontend/src/modulos/registro.ts` (clave `desarrollosocial`).

A diferencia de `PantallaDeObras`/`PantallaDeArbolado` (una única vista
con acciones condicionadas por permiso) y de `PantallaDeReclamos` (vistas
alternativas según permiso), acá hace falta **combinar ambos patrones**
porque hay tres audiencias reales: el vecino anónimo, quien solo
gestiona programas (`gestionarProgramas`), y quien además revisa
inscripciones (`revisarInscripciones`). Usá navegación por estado local
(sin router, ADR 0008), con una vista principal y sub-vistas:

```
const puedeGestionarProgramas = usuario?.permisos.includes('desarrollosocial.gestionarProgramas') ?? false
const puedeRevisarInscripciones = usuario?.permisos.includes('desarrollosocial.revisarInscripciones') ?? false
const [vista, setVista] = useState<'catalogo' | 'inscripcion' | 'seguimiento' | 'bandeja'>('catalogo')
```

1. **`catalogo`** (default, visible para todos, pública): listado de
   programas sociales — mismo patrón exacto que la sección de búsqueda
   de `PantallaDeObras` (filtros `estado`/`q`, tabla con `<caption>`,
   `scope="col"`/`scope="row"`). Columnas: Nombre, Descripción, Estado.
   Botones de navegación siempre visibles: "Inscribirme a un programa"
   (→ `inscripcion`) y "¿Ya te inscribiste? Consultá el estado" (→
   `seguimiento`), mismo texto/patrón que el botón de consulta de
   `PantallaDeReclamos`. Si `puedeGestionarProgramas`: sección adicional
   "Publicar un programa" (formulario: Nombre obligatorio, Descripción
   opcional `textarea`, Criterios de elegibilidad opcional `textarea`) y,
   por fila de la tabla, botón "Cambiar estado" (`<select>` con la única
   opción contraria a la actual — `ABIERTO`→`CERRADO` o viceversa, sin
   necesidad de replicar un mapa de transiciones porque solo hay un
   destino posible desde cada estado) — mismo patrón de fila+edición que
   `PantallaDeObras`. Si `puedeRevisarInscripciones`: un botón adicional
   "Ver inscripciones recibidas" (→ `bandeja`).

2. **`inscripcion`**: formulario público de alta (mismo patrón que
   `FormularioDeAlta` de `PantallaDeReclamos`). Campos: Programa
   (`<select>`, obligatorio, poblado con los programas en estado
   `ABIERTO` del catálogo ya cargado — si no hay ninguno abierto,
   mostrá un mensaje y no el formulario), Nombre y apellido
   (obligatorio), DNI (obligatorio), Contacto — teléfono o email
   (obligatorio), Cantidad de integrantes del grupo familiar (`type="number"`,
   `min="1"`, obligatorio), Situación declarada (`<select>` obligatorio
   con las 5 opciones del enum, etiquetas: "Desocupado/a", "Empleo
   informal", "Empleo formal", "Jubilado/a o pensionado/a", "Otra"),
   Comentario adicional (opcional, `textarea`). Al confirmar, mostrá el
   `id` y el `tokenDeSeguimiento` con el mismo bloque de "guardá este
   código" + botón copiar que `PantallaDeReclamos` (reutilizá el mismo
   texto de advertencia, adaptado a "inscripción"). Botón "Volver al
   catálogo" (→ `catalogo`).

3. **`seguimiento`**: consulta pública por token — mismo patrón exacto
   que `ConsultaDeSeguimiento` en `PantallaDeReclamos.tsx` (`GET
   /api/desarrollosocial/inscripciones/seguimiento/{token}`), adaptada al
   shape de `SeguimientoDeInscripcionResponse`: mostrá programa, estado
   (con etiquetas legibles: Recibida, En evaluación, Aprobada,
   Rechazada), y el comentario de resolución si existe. Botón "Volver al
   catálogo".

4. **`bandeja`** (solo alcanzable si `puedeRevisarInscripciones`; si
   alguien sin el permiso llega a este estado por cualquier motivo,
   redirigí a `catalogo` en vez de renderizarla — la protección real es
   el backend, pero no le muestres la sección a quien no la va a poder
   usar): `GET /api/desarrollosocial/inscripciones` con filtros
   `programaId` (`<select>` con los programas cargados) y `estado`.
   Tabla con columnas Nombre, DNI, Contacto, Integrantes, Situación
   declarada (con las mismas etiquetas legibles), Comentario adicional,
   Estado, Comentario de resolución. Por fila, si el estado admite una
   transición: `<select>` con las opciones válidas (mismo mapa de
   transiciones que el backend, replicado en el frontend con el mismo
   comentario de "el enforcement real es del backend" que
   `PantallaDeObras`) más, **solo cuando el destino elegido es
   `APROBADA` o `RECHAZADA`**, un `textarea` obligatorio para el
   comentario de resolución (si el destino es `EN_EVALUACION`, no
   mostrés el campo de comentario). Botón "Volver al catálogo".

**Accesibilidad (obligatorio, no diferible, CLAUDE.md)**: replicá al pie
de la letra los patrones ya usados en `PantallaDeObras.tsx` y
`PantallaDeReclamos.tsx` — foco gestionado por `useRef`+`tabIndex={-1}`
al montar/cambiar de vista, anuncios con `role="status"`/`role="alert"`,
`aria-invalid`/`aria-describedby` en campos con error, `aria-busy` en
botones de acción en curso, `<label htmlFor>` en todo input/select/
textarea, tabla con `<caption>` y `scope="col"`/`scope="row"`. No
inventes un patrón nuevo de accesibilidad.

**Fuera de alcance**: routing de URLs, edición de los campos del alta
(programa o inscripción) después de creados, mapa/geolocalización,
adjuntos, paginado, exportación de la bandeja de inscripciones.

## Qué NO tocar

- Los módulos `obras`, `arbolado`, `reclamos`, `mesaentradas`: código,
  tablas, permisos. `desarrollosocial` no depende de ninguno de ellos
  (ADR 0025 §1).
- `seguimientoanonimo.TokenDeSeguimiento`: se reutiliza tal cual, no se
  extiende ni se le agrega estado (ADR 0017 §3).
- `modulosHabilitados` de los tenants de prueba `sanmartin`/`moron`
  (`db/control/V2__sembrar_municipios_de_prueba.sql`): si hace falta
  `desarrollosocial` contratado para una demo manual, sembralo con el
  mecanismo ya existente (mismo criterio que CD-27/CD-28). Los tests de
  integración contratan el módulo directamente contra la base de control
  de test, mismo patrón que `ObrasTest`/`ArboladoTest`.

## Instrucciones para los agentes implementadores

No hagas commit, push ni abras PR por tu cuenta: dejá los cambios en el
working tree. El tech lead revisa, commitea y coordina el PR.
