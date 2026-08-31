# 0025 - Desarrollo Social: catálogo público de programas sociales e inscripción con minimización de datos sensibles, primera rebanada de Fase 5

- Estado: Aceptada
- Fecha: 2026-08-28

## Contexto

Con R19/R20 (ADR 0023, ADR 0024) Fase 4 — Gestión territorial queda
abierta y con dos rebanadas demostrables; lo que resta (Catastro, GIS
consolidado, Planeamiento Urbano, recolección de residuos, alumbrado
público, espacios verdes) sigue diferido por depender de datos reales de
un municipio piloto o por no aportar una dimensión de dominio nueva.

Toca abrir **Fase 5 — Áreas sociales**
([roadmap](../../producto/roadmap-fases.md#fase-5--áreas-sociales)), que
el [backlog inicial](../../producto/backlog-inicial.md) y el
[catálogo funcional](../../producto/catalogo-funcional.md) §3 listan hoy
solo como un título con temas: Desarrollo Social, Discapacidad, Salud
municipal, Educación municipal. Mismo criterio de descarte razonado que
ADR 0021/ADR 0023/ADR 0024 ya aplicaron para abrir Fase 3 y Fase 4:
preferir lo que se puede diseñar sin inventar normativa o datos reales
específicos de un municipio.

A ese criterio se suma uno nuevo, que pesa igual: **minimización de datos
personales sensibles**. A diferencia de todo lo construido hasta ahora
(datos administrativos del municipio, o datos de contacto/identificación
de un vecino sobre un trámite puntual — nombre, DNI, dirección, patente),
Fase 5 es la primera que roza naturalmente datos de salud, discapacidad o
situación socioeconómica de una persona identificable. El proyecto ya
tiene precedente de tratar esto con cuidado desde el diseño, no después:
[ADR 0019](0019-consola-del-proveedor-ui-cross-tenant-y-contrato-minimo.md)
(Transparencia, R11) nunca vincula el sueldo publicado a la identidad de
una persona en la base; [Cementerio](../../producto/backlog-inicial.md)
(R8) oculta campos privados solo en la respuesta pública. Este ADR aplica
el mismo criterio, de entrada, a un módulo que si se diseñara sin
cuidado terminaría guardando datos de salud o socioeconómicos ligados a
un nombre y un DNI reales.

Candidatas dentro de Fase 5:

- **Salud municipal**: el catálogo funcional lo describe con "historia
  clínica básica". Un historial clínico es, por definición, un registro
  de diagnósticos de una persona identificable — no hay una forma de
  minimizarlo que no lo vacíe de sentido (una "historia clínica sin
  datos clínicos" no es una historia clínica). Descartada para esta fase
  completa, no solo para esta rebanada: cualquier rebanada de Salud
  municipal que valga la pena construir necesita ese dato, y este
  producto no tiene todavía ni un municipio piloto real ni una política
  de datos de salud (cifrado en reposo, control de acceso reforzado,
  auditoría de cada lectura) que un historial clínico exige por ley
  (Ley 26.529 de Derechos del Paciente, Ley 25.326 de Protección de
  Datos Personales — dato "sensible" en su Art. 2). Se difiere hasta
  tener ambas cosas.
- **Discapacidad**: el catálogo funcional la describe con "turnos y
  seguimiento para Junta Evaluadora de CUD" como ítem principal. A
  diferencia de Salud municipal, acá no hace falta un diagnóstico
  detallado — pero el problema no desaparece: el solo hecho de que una
  persona identificable (nombre + DNI) pida un turno para la Junta
  Evaluadora de Discapacidad ya es, en sí mismo, un dato de salud
  vinculado a su identidad (revela que esa persona gestiona o sospecha
  una discapacidad), sin ninguna forma de generalizarlo a una categoría
  amplia como si hace este ADR con "situación declarada" en Desarrollo
  Social (Decisión 4) — no hay una versión "en categorías amplias" de
  "pidió turno para evaluar su discapacidad" que no siga siendo,
  igual, un dato de salud identificable. El resto del catálogo para esta
  área ("registro de instituciones y programas de inclusión", "exenciones
  de tasas", "transporte accesible") o bien no toca datos personales
  (es un catálogo de instituciones, mismo patrón ya demostrado por
  Obras/Arbolado, sin aportar una dimensión de dominio nueva) o bien
  vuelve a depender de vincular una exención/beneficio a una condición de
  discapacidad de una persona real. Descartada para esta rebanada, no
  para la fase: es candidata futura para cuando el producto tenga un
  mecanismo de datos sensibles más maduro (cifrado a nivel de columna,
  auditoría de acceso de lectura) que hoy no existe.
- **Educación municipal**: el catálogo funcional no la detalla más allá
  del título ("si el municipio tiene competencia educativa"). Un
  registro de instituciones educativas municipales sería viable sin
  datos personales (mismo perfil de riesgo bajo que Obras/Arbolado), pero
  no aportaría una dimensión de dominio nueva: sería, en forma, el mismo
  catálogo nombre/ubicación/tipo/estado que Obras y Arbolado ya
  demostraron — mismo motivo por el que ADR 0024 descartó "Espacios
  verdes" al abrir la segunda rebanada de Fase 4. Descartada para esta
  rebanada, disponible como candidata futura si hace falta una rebanada
  chica sin dato personal.
- **Desarrollo Social**: el catálogo funcional la describe con
  "programas, comedores, subsidios, padrón de beneficiarios". Un "padrón
  de beneficiarios" tal cual (una lista consultable de quién recibe qué
  subsidio) sí sería un problema de minimización sin resolver — es
  exactamente el tipo de registro que este ADR evita construir (Decisión
  2). Pero el catálogo no obliga a construir un padrón: una
  **inscripción/preinscripción a un programa social**, con datos de
  contacto y elegibilidad **declarada en categorías amplias** (no
  comprobantes, no cifras de ingreso, no composición nominal del grupo
  familiar), es información análoga en sensibilidad a lo que Mesa de
  Entradas (R9/R10) ya maneja hoy (nombre, DNI, dirección, contacto sobre
  un trámite puntual) más un dato nuevo pero acotable: una categoría
  socioeconómica amplia. Es, en forma, el mismo tipo de rebanada que Mesa
  de Entradas — alta pública anónima, estado propio, seguimiento por
  token — aplicado a un dominio nuevo (asistencia social, no trámite
  administrativo), y con controles de acceso más estrictos que cualquier
  módulo anterior porque el dato es más sensible. Elegida.

Ningún ADR previo decide (a) un módulo con alta pública que además
requiere un dato de categoría "situación socioeconómica", (b) un módulo
en el que la lectura protegida no se asigna a `agente` además de
`administrador`, ni (c) un módulo cuyo listado interno de registros con
datos personales queda **sin** lectura pública, a diferencia de todos los
módulos con estado propio construidos hasta ahora (Obras, Arbolado,
Multas — todos con algún GET público de listado o búsqueda).

## Decisión

### 1. Módulo nuevo `desarrollosocial`, contratable, con dos entidades independientes

`desarrollosocial` es un módulo funcional propio
([ADR 0009](0009-modelo-comercial-y-entitlement.md)), con su propio
`DescriptorDeModulo` y prefijo `/api/desarrollosocial`. No depende de
ningún otro módulo funcional. Dos entidades en `desarrollosocial.internal`:

- `ProgramaSocialEntity` (tabla `programa_social`): el catálogo de
  programas que el municipio ofrece — no personal, público, mismo perfil
  de riesgo que `ObraPublicaEntity`/`ArbolUrbanoEntity`.
- `InscripcionSocialEntity` (tabla `inscripcion_social`): una
  inscripción de un vecino a un programa — datos personales, con los
  controles de esta ADR.

### 2. Sin padrón de beneficiarios: `desarrollosocial` modela inscripciones, no un registro consultable de quién recibe qué

Ninguna ruta de este módulo, en ningún permiso, devuelve "todas las
inscripciones aprobadas de un programa" como una vista de beneficiarios
para consumo externo o cruzado. El único listado con datos personales
(`GET /api/desarrollosocial/inscripciones`, Decisión 6) es una bandeja de
trabajo para quien evalúa solicitudes, no un padrón — no se expone en
ningún reporte agregado, exportación ni integración en esta rebanada.
Cruces con Nación/Provincia (que el catálogo funcional menciona para
Desarrollo Social) quedan explícitamente fuera de alcance: necesitan un
municipio piloto real y un acuerdo de intercambio de datos que no existen
hoy.

### 3. Catálogo de programas: mismo mecanismo de alta protegida / lectura pública que Obras/Arbolado, sin dato personal

`POST /api/desarrollosocial/programas` requiere sesión y el permiso
`desarrollosocial.gestionarProgramas`; `GET /api/desarrollosocial/programas`
es lectura pública (`rutasDeLecturaPublica()`,
[ADR 0012](0012-declaracion-de-modulos-y-gating-por-ruta.md) §1), con
filtro opcional por `estado` y por texto (`q`) sobre `nombre`/
`descripcion`, mismo patrón `ILIKE` que Obras/Arbolado/Boletín. Un
programa social (nombre, descripción, criterios de elegibilidad en texto
libre, estado `ABIERTO`/`CERRADO`) no contiene ningún dato de una persona
identificable — es información institucional, mismo perfil que Obras —
así que no hay tensión entre este catálogo y la minimización que sí
aplica a las inscripciones (Decisión 4 en adelante).

`EstadoDePrograma`: enum `ABIERTO`, `CERRADO`, transición libre en ambos
sentidos (`PATCH /api/desarrollosocial/programas/{id}/estado`, mismo
permiso): un municipio abre y cierra una convocatoria, no hay una
progresión unidireccional que modelar. Un programa `CERRADO` sigue
visible públicamente (para que quede constancia de que existió) pero
rechaza nuevas inscripciones (Decisión 5).

### 4. Inscripción: elegibilidad declarada en categorías amplias, nunca ingresos, comprobantes ni composición nominal del grupo familiar

`InscripcionSocialEntity` guarda, del vecino: `nombreSolicitante`,
`dniSolicitante`, `contacto` (teléfono o email, texto libre, obligatorio
— a diferencia de `contactoDelVecino` en Reclamos, ADR 0014 §4, que es
opcional porque ahí el contacto es solo informativo; acá el municipio
necesita poder contactar a la familia para gestionar la ayuda, así que es
un dato operativo necesario, no un agregado opcional), y dos datos de
elegibilidad, ambos deliberadamente pobres en detalle:

- `cantidadIntegrantesGrupoFamiliar`: un entero. Nunca se piden nombres,
  edades ni DNI de los convivientes — el número alcanza para que el
  municipio dimensione la ayuda, y no genera una base de datos de
  terceros (el resto de la familia) que nadie dio su consentimiento para
  registrar.
- `situacionDeclarada`: un enum cerrado de categorías amplias
  (`DESOCUPADO`, `EMPLEO_INFORMAL`, `EMPLEO_FORMAL`,
  `JUBILADO_O_PENSIONADO`, `OTRO`), autodeclarado por el vecino, sin
  verificación. Nunca un monto de ingreso, un CBU, un recibo de sueldo ni
  un comprobante adjunto: mismo criterio que
  [ADR 0019](0019-consola-del-proveedor-ui-cross-tenant-y-contrato-minimo.md)
  nunca vincula un sueldo a una identidad, aplicado acá a la elegibilidad
  en vez de al sueldo. Es, otra vez, un criterio de producto sin
  normativa real detrás (mismo tipo de decisión "provisoria pero
  necesaria" que ADR 0021 §8 ya asume para el descuento de multas): si un
  municipio piloto real necesita más precisión para evaluar elegibilidad,
  esa necesidad se resuelve con ese caso delante, no inventando hoy un
  esquema de verificación socioeconómica real.

Sin adjuntos ni carga de documentación de ningún tipo en esta rebanada —
mismo criterio que Obras/Arbolado (ADR 0023 §8, ADR 0024 §6), reforzado
acá: un comprobante de ingresos o un certificado sería exactamente el
tipo de dato sensible que este ADR existe para evitar en esta rebanada.

`comentarioAdicional` (texto libre, opcional): espacio para que el
vecino agregue contexto por su cuenta, mismo criterio que
`descargoTexto`/`descargoContacto` en Multas (ADR 0021 §5) — es
informativo, no estructurado, y el municipio no lo exige.

### 5. Alta pública anónima, mismo criterio que Reclamos, con una validación nueva: el programa tiene que existir y estar `ABIERTO`

`POST /api/desarrollosocial/inscripciones` no requiere sesión
(`rutasDeEscrituraPublica()`, [ADR 0012](0012-declaracion-de-modulos-y-gating-por-ruta.md)
§1, mismo mecanismo que Reclamos/Mesa de Entradas/Multas — pago): el
vecino se inscribe sin cuenta, mismo motivo que Reclamos
([ADR 0014](0014-reclamos-ciudadanos-alta-publica-anonima-y-estado-propio.md)
§1) — el producto no tiene identidad ciudadana todavía. A diferencia de
Reclamos, acá el alta referencia un recurso que el municipio controla (el
programa): `programaId` inexistente o con `estado = CERRADO` rechaza con
`SolicitudInvalida` (400) — no tiene sentido aceptar una inscripción a
algo que no existe o ya no acepta inscripciones.

### 6. Sin lectura pública de inscripciones: el único acceso de lectura es por token propio o por sesión con permiso — no hay `GET` de listado ni de búsqueda abierto

Esta es la decisión central de minimización de esta ADR, y la que aparta
a `desarrollosocial` del patrón de todos los módulos con estado propio
construidos hasta ahora: **`InscripcionSocialEntity` no tiene ningún
endpoint de lectura pública sin credencial.** Ni un listado abierto (como
Obras/Arbolado/Boletín) ni una búsqueda por identificador obligatorio
(como Multas por patente/DNI, ADR 0021 §6): una búsqueda pública por DNI
sobre este módulo, aunque exigiera el identificador exacto como hace
Multas, expondría igual la situación socioeconómica declarada de
cualquiera cuyo DNI se conozca o se adivine (a diferencia de una multa,
que es un hecho ya público en sí mismo — una infracción de tránsito
labrada por el Estado — la elegibilidad a un programa social no lo es).

Dos únicas vías de lectura, ambas ya usadas por el proyecto para otros
fines, aplicadas acá con el mismo criterio de minimización:

- **Seguimiento por token** (mismo mecanismo que
  [ADR 0017](0017-seguimiento-anonimo-por-token-en-reclamos-y-mesa-de-entradas.md),
  reutilizando `seguimientoanonimo.TokenDeSeguimiento` tal cual, sin
  extenderlo): `POST /api/desarrollosocial/inscripciones` devuelve
  `tokenDeSeguimiento` una única vez, igual que Reclamos/Mesa de
  Entradas. `GET /api/desarrollosocial/inscripciones/seguimiento/{token}`
  es lectura pública (declarada en `rutasDeLecturaPublica()`, variable de
  path, mismo mecanismo que ADR 0017 §4) y devuelve **solo**: nombre del
  programa, `estado`, `creadoEn`, `actualizadoEn`, y
  `comentarioDeResolucion` si la inscripción ya fue evaluada — nunca
  vuelve a exponer `nombreSolicitante`, `dniSolicitante`, `contacto`,
  `cantidadIntegrantesGrupoFamiliar`, `situacionDeclarada` ni
  `comentarioAdicional`: son datos que el propio vecino ya tiene (los
  escribió él), mismo criterio exacto que ADR 0017 §5 ya aplica a
  Reclamos/Mesa de Entradas. Un token que no matchea ninguna fila da 404
  genérico, sin distinguir formato inválido de no encontrado (ADR 0017
  §4).
- **Bandeja de gestión con sesión** (Decisión 7): `GET
  /api/desarrollosocial/inscripciones`, protegida, con todos los campos —
  es la única vista que ve el dato completo, y queda detrás de un
  permiso más restrictivo que cualquier otro módulo con estado propio del
  proyecto (Decisión 7).

### 7. Dos permisos separados por sensibilidad: `desarrollosocial.gestionarProgramas` (operativo, admin+agente) y `desarrollosocial.revisarInscripciones` (dato personal sensible + decisión, solo `administrador`)

Mismo criterio de separación por sensibilidad real que
[ADR 0021](0021-multas-de-transito-alta-protegida-estado-propio-descuento-por-pago-temprano.md)
§3/§4 ya aplicó entre `multas.labrar` (operativo, ambos roles) y
`multas.resolverDescargo` (impacto + discrecionalidad, solo
administrador) — acá la sensibilidad no es fiscal sino de datos
personales:

- `desarrollosocial.gestionarProgramas` cubre alta y cambio de estado de
  `ProgramaSocialEntity` (Decisión 3): no toca ningún dato personal, es
  trabajo de gabinete equivalente a publicar una convocatoria. Asignado a
  `administrador` y `agente`, mismo criterio que `obras.gestionar`/
  `arbolado.gestionar`.
- `desarrollosocial.revisarInscripciones` cubre listar
  `InscripcionSocialEntity` (con todos sus campos personales) y
  actualizar su estado (Decisión 8). **Asignado solo a `administrador`**,
  a diferencia de todos los permisos operativos de campo que el proyecto
  vino asignando a ambos roles (`reclamos.gestionar`, `multas.labrar`,
  `obras.gestionar`, `arbolado.gestionar`): el rol de sistema `agente` es
  genérico y compartido por cualquier área operativa del municipio (un
  agente de tránsito, un agente de obras); darle acceso por defecto a la
  situación socioeconómica declarada de vecinos identificables excede lo
  que ese rol genérico necesita para su trabajo, incluso aunque en la
  práctica quien opere Desarrollo Social sea, con frecuencia, alguien con
  el rol `agente`. Un municipio que tenga un equipo dedicado de
  Desarrollo Social compone su propio rol con este permiso
  ([ADR 0011](0011-autorizacion-por-roles-con-permisos-granulares.md)): el
  seed de sistema no le da acceso a datos personales sensibles a un rol
  genérico sin que un caso real lo pida, mismo mecanismo que el proyecto
  ya usa para restringir de más, no para resolver el problema con un rol
  nuevo en el seed (que necesitaría, de nuevo, un municipio piloto real
  para nombrarlo bien: "trabajador social", "referente de Desarrollo
  Social", etc. — no se inventa acá).

### 8. Estado de la inscripción: enum fijo + tabla de transiciones, mismo patrón que Obras/Arbolado/Multas, con resolución que exige comentario

`EstadoDeInscripcion`: `RECIBIDA → EN_EVALUACION → APROBADA |
RECHAZADA`. `APROBADA`/`RECHAZADA` son terminales. A diferencia de
Multas (que permite resolver un descargo directamente desde el estado en
curso), acá se exige pasar primero por `EN_EVALUACION` antes de aprobar o
rechazar: no hay una vía de "aprobar sin marcar que se está evaluando",
mismo espíritu de dejar rastro de que hubo una revisión deliberada antes
de una decisión sobre una ayuda social, análogo al motivo por el que
Arbolado (ADR 0024 §4) exige pasar por `REQUIERE_INTERVENCION` antes de
`RETIRADO`.

`PATCH /api/desarrollosocial/inscripciones/{id}/estado` requiere
`desarrollosocial.revisarInscripciones`. La transición hacia
`EN_EVALUACION` no exige comentario (solo marca que alguien empezó a
mirarla); las transiciones hacia `APROBADA`/`RECHAZADA` exigen
`comentarioDeResolucion` no vacío, mismo criterio de accountability que
`GestionDeMultas.resolverDescargo` (ADR 0021 §5) — una decisión sobre una
ayuda social deja registro de por qué.

### 9. Sin geolocalización, sin adjuntos, sin integración con Nación/Provincia

Mismos motivos que Obras/Arbolado (ADR 0023 §6/§7/§8, ADR 0024 §6): sin
GIS, sin certificaciones, sin fotos. Los cruces con Nación/Provincia que
el catálogo funcional menciona para Desarrollo Social quedan fuera de
alcance (Decisión 2): necesitan un acuerdo de intercambio real que no
existe.

## Alternativas consideradas

- **Elegir Salud municipal o Discapacidad como esta rebanada**: ver
  Contexto.
- **Elegir Educación municipal**: ver Contexto — no aporta dimensión de
  dominio nueva frente a Obras/Arbolado.
- **Construir un padrón de beneficiarios consultable** (quién recibe qué
  programa, filtrable): descartada — ver Decisión 2. Es el ejemplo
  explícito de lo que este ADR evita construir sin un piloto real y un
  mecanismo de datos sensibles más maduro.
- **Pedir monto de ingresos o comprobante de ingresos en el alta**: más
  preciso para evaluar elegibilidad real, pero exactamente el tipo de
  dato que este ADR minimiza — ver Decisión 4. Descartada; revisable con
  un municipio piloto real que lo requiera y con una política de datos
  sensibles que hoy no existe.
- **Búsqueda pública por DNI, mismo patrón que Multas (ADR 0021 §6)**:
  descartada — ver Decisión 6. A diferencia de una multa (un hecho ya
  público per se), la elegibilidad a un programa social no lo es;
  aceptar un identificador exacto no cambia que expondría datos
  socioeconómicos de la persona a quien lo adivine o lo conozca.
- **Listado público de inscripciones sin filtro obligatorio, mismo
  patrón que Obras/Arbolado**: descartada por el mismo motivo, con más
  razón — sin ni siquiera exigir un identificador.
- **`desarrollosocial.revisarInscripciones` asignado también a
  `agente`, mismo criterio que el resto de los permisos operativos del
  proyecto**: descartada — ver Decisión 7. Es la pieza central de
  minimización de esta ADR a nivel de control de acceso, no solo a nivel
  de qué campos se guardan.
- **Un solo permiso `desarrollosocial.gestionar` para programas e
  inscripciones, mismo patrón que `obras.gestionar`/
  `arbolado.gestionar`**: descartada — a diferencia de esos módulos, acá
  sí hay una diferencia real de sensibilidad entre gestionar el catálogo
  (no personal) y revisar inscripciones (personal, sensible): mismo
  argumento que ya separó `multas.labrar` de
  `multas.resolverDescargo` (ADR 0021 §3/§4), aplicado por sensibilidad de
  dato en vez de por impacto fiscal.
- **Exigir pasar por `EN_EVALUACION` no obligatoriamente antes de
  `APROBADA`/`RECHAZADA`** (permitir resolver directo desde `RECIBIDA`):
  descartada — ver Decisión 8.

## Consecuencias

- `desarrollosocial` no depende de ningún otro módulo funcional; el test
  de modularidad de Spring Modulith lo verifica en el build.
- Es el primer módulo del proyecto sin ningún endpoint de lectura pública
  sobre su entidad principal (`InscripcionSocialEntity`): a diferencia de
  todo lo construido hasta ahora, "leer sin sesión" solo es posible con
  el token exacto de una inscripción puntual, nunca por listado ni
  búsqueda por identificador.
- Es también el primer módulo del proyecto en el que un permiso de
  gestión operativa **no** se asigna a `agente` en el seed de sistema —
  un municipio que quiera que su equipo de Desarrollo Social opere sin
  pasar por `administrador` tiene que componer su propio rol con
  `desarrollosocial.revisarInscripciones` (ADR 0011).
- Salud municipal y Discapacidad quedan diferidas como fase/rebanada
  completa (no solo pospuestas para "después de esta"): necesitan, además
  de un municipio piloto real, un mecanismo de datos sensibles (cifrado
  en reposo por columna, auditoría de cada lectura) que hoy el producto
  no tiene. Construirlas antes de eso repetiría, a mayor escala, el
  riesgo que este ADR evita en Desarrollo Social.
- El costo de abuso de la ruta de escritura pública (inscripciones falsas
  a un programa) es el mismo, ya aceptado y diferido, que ADR 0014/ADR
  0021 ya documentan para Reclamos/Multas.

## Pendiente de definir

- Rol de sistema dedicado ("trabajador social" o similar) para operar
  `desarrollosocial.revisarInscripciones` sin pasar por `administrador`:
  depende de un municipio piloto real que lo nombre y lo justifique.
- Cruces de datos con Nación/Provincia para validar elegibilidad real
  (Decisión 2/9): depende de un acuerdo de intercambio real, fuera de
  alcance de este producto por ahora.
- Mecanismo de datos sensibles más maduro (cifrado por columna, auditoría
  de lectura) que habilitaría reabrir Salud municipal/Discapacidad como
  candidatas de una rebanada futura.
- Notificación al vecino (email/SMS) de un cambio de estado en su
  inscripción: mismo pendiente que ADR 0021/ADR 0023 ya dejan para sus
  respectivos módulos — integrarlo con el motor de notificaciones (ADR
  0013) queda para cuando el módulo lo necesite de verdad.
- Rate limiting sobre las rutas públicas de `desarrollosocial`
  (endurecimiento de seguridad diferido por CLAUDE.md, mismo criterio que
  el resto del proyecto) — acá con una razón adicional a considerar en el
  futuro: el costo de abuso de fuerza bruta contra
  `GET .../seguimiento/{token}` es el mismo que ADR 0017 ya acepta
  (256 bits de entropía), pero el dato detrás es más sensible que en
  Reclamos/Mesa de Entradas.
- Política de retención de datos de inscripciones rechazadas o vencidas
  (hoy no hay borrado ni anonimización automática): depende de una
  política de datos personales del producto en general, no decidida
  todavía.
- Edición de los campos del alta (`nombreSolicitante`, `contacto`,
  `situacionDeclarada`, etc.) después de creada la inscripción: no existe
  en esta rebanada, mismo criterio que Obras/Arbolado para sus propios
  campos de alta.
