# 0017 - Seguimiento anónimo por token en Reclamos y Mesa de Entradas

- Estado: Aceptada
- Fecha: 2026-08-25

## Contexto

Dos ADRs de Fase 1 dejaron, con el mismo nombre, el mismo pendiente:

- [ADR 0014](0014-reclamos-ciudadanos-alta-publica-anonima-y-estado-propio.md)
  §6, sobre `reclamos`: *"No se expone un `GET` público de un reclamo
  puntual por id [...] Habilitar seguimiento anónimo necesitaría un token
  no adivinable y su propia pantalla — se difiere explícitamente, no es un
  olvido."*
- [ADR 0015](0015-motor-de-expediente-workflow-minimo.md) §4 y "Pendiente
  de definir": *"esta rebanada no agrega seguimiento anónimo por token
  [...] cuando se resuelva, conviene que sea un mecanismo único para ambos
  módulos, no uno por módulo."*

El vecino que carga un reclamo o inicia un trámite de Mesa de Entradas lo
hace sin cuenta (ADR 0014 §1, reutilizado tal cual por ADR 0015 §4): el
producto no tiene identidad ciudadana todavía
([visión y alcance](../../producto/vision-y-alcance.md)). Sin una cuenta
que lo identifique, la única forma de que ese mismo vecino vuelva a
consultar en qué quedó su reclamo o trámite es un secreto que se le
entregó una vez y que él mismo conserva: un token.

Un id secuencial no sirve (ADR 0014 §6 ya lo descarta: es adivinable y
expondría el reclamo de cualquiera). Hace falta un mecanismo de posesión
de un secreto no adivinable — es, de hecho, un mecanismo de autenticación
débil: quien tiene el token puede leer el estado de ese reclamo o trámite
puntual sin ninguna otra verificación. Se lo trata con el mismo cuidado
que cualquier credencial (comparar con
[ADR 0010](0010-autenticacion-por-sesion-scopeada-al-tenant.md)), no como
un dato más del reclamo.

Ningún ADR previo decide generación, almacenamiento ni alcance de un
token de este tipo.

## Decisión

### 1. Token aleatorio de 256 bits, codificado Base64 URL-safe sin padding

`TokenDeSeguimiento.generar()` obtiene 32 bytes de `java.security.SecureRandom`
(generador criptográficamente seguro, no `Random`/`Math.random()`) y los
codifica con `Base64.getUrlEncoder().withoutPadding()`, dando un token de
43 caracteres compuesto solo por `[A-Za-z0-9_-]` — seguro para viajar en
una URL o pegarse en un campo de texto sin escapar.

256 bits de entropía deja el espacio de búsqueda fuera de alcance de
fuerza bruta con cualquier volumen de requests que este producto vaya a
recibir (muy por encima de los 128 bits que OWASP recomienda como piso
para tokens de sesión/recuperación): no hace falta ajustar el tamaño por
economía, así que se elige el tamaño simple de razonar (32 bytes, un
`long` de dos veces el ancho de un hash SHA-256) antes que optimizar
caracteres a costa de claridad.

### 2. Se guarda hasheado (SHA-256), nunca en claro

La base guarda `token_hash` (SHA-256 en hexadecimal, 64 caracteres), no el
token. Motivo: el token da acceso de lectura a datos de un reclamo o
trámite ajeno — nombre y contacto del solicitante, dirección, el detalle
completo del trámite. Si la base se filtra (backup expuesto, acceso no
autorizado a la base de un tenant), un atacante con los tokens en claro
tendría lectura inmediata de todos los reclamos/trámites anónimos del
municipio; con solo los hashes, no tiene nada explotable sin además
invertir SHA-256, inviable sobre una entrada de 256 bits de entropía real.

Mismo principio que una contraseña, con una diferencia deliberada en el
algoritmo: las contraseñas se hashean con un algoritmo lento y adaptativo
(bcrypt/argon2/scrypt) porque un atacante puede probar diccionarios de
valores *plausibles* elegidos por una persona. Un token de este mecanismo
no lo elige una persona: es 256 bits de salida de `SecureRandom`, así que
no hay diccionario que probar y un hash rápido (SHA-256) ya deja el
ataque de fuerza bruta fuera de alcance sin pagar el costo de cómputo de
un hash lento en cada consulta pública. Aplicar bcrypt/argon2 acá sería
copiar la solución de "contraseña elegida por una persona" a un problema
distinto ("secreto de alta entropía generado por máquina") que no lo
necesita.

La búsqueda es por igualdad exacta del hash contra una columna con índice
único (`token_hash`, ver Decisión 4): no hace falta comparación en tiempo
constante para evitar timing attacks porque no hay información parcial
que filtrar por byte —a diferencia de comparar carácter por carácter un
secreto en claro—, la consulta solo puede responder "existe" o "no
existe" sobre un espacio de 256 bits.

### 3. Mecanismo compartido: módulo `seguimientoanonimo`, canon base, sin persistencia propia

`TokenDeSeguimiento` (generación y hash) vive en un módulo nuevo,
`seguimientoanonimo`, con el mismo estatus que `persistencia`/`acceso`/
`entitlement`: canon base, no contratable, sin `DescriptorDeModulo`. Es
una clase pública sin estado (métodos estáticos, constructor privado —
mismo patrón que `RespuestasJson` en `acceso.internal`), sin entidad ni
repositorio propios: cada módulo que lo usa guarda su propio
`token_hash` en su propia tabla (`reclamo`, `expediente`) y hace su propia
consulta. El módulo nuevo no persiste nada ni conoce a `reclamos` ni a
`mesaentradas`.

A diferencia del criterio que [ADR 0013](0013-persistencia-de-eventos-y-mecanismo-transversal-de-notificaciones-y-auditoria.md)
§3 aplica para no generalizar sobre un único consumidor ("con un solo
evento real, generalizar el contrato es diseñar a ciegas"), acá hay
**dos consumidores reales, concretos y simultáneos** desde el día uno de
esta rebanada —`reclamos` y `mesaentradas`—, y ambos ADRs que los
preceden ya señalaron con nombre que "conviene que sea un mecanismo único
... no uno por módulo" (ADR 0015 §4). No es generalizar a ciegas
esperando un segundo caso: el segundo caso ya está sobre la mesa, así que
duplicar `SecureRandom` + `MessageDigest` en dos módulos no evitaría
ningún error de diseño, solo daría dos copias del mismo algoritmo de
seguridad para mantener sincronizadas — precisamente el tipo de
duplicación que, tratándose de un mecanismo de autenticación débil, es
más riesgosa mantener separada que compartida.

Se prefiere un módulo nuevo y chico (dos métodos estáticos, sin
dependencias de Spring) a agregarlo a un módulo existente: `persistencia`
es infraestructura de datasource/tenant, no seguridad; `entitlement` es
catálogo de módulos; `acceso` es sesión y autorización por permisos, un
mecanismo distinto (con cuenta) del que este token no depende ni debe
depender. Ninguno es un lugar semánticamente correcto para "generar y
hashear un secreto no ligado a una cuenta". Mismo criterio que ya usa el
proyecto para separar preocupaciones en módulos chicos y con un único
motivo de cambio (ADR 0003).

### 4. Cada módulo agrega su propia columna `token_hash` y su propia consulta pública

`reclamo` y `expediente` ganan `token_hash varchar(64) not null`, con
índice único (`reclamo_token_hash_idx`, `expediente_token_hash_idx`):
todo alta genera un token — no es opcional, es la única vía de
seguimiento que existe. El índice único, además de acelerar la búsqueda,
es una segunda barrera de integridad ante una colisión (con 256 bits de
entropía real, la probabilidad es despreciable, pero la restricción de
base de datos no cuesta nada y convierte una colisión hipotética en un
error explícito en vez de una fila pisada en silencio).

`GestionDeReclamos.cargar(...)` y `GestionDeExpedientes.iniciar(...)`
generan el token, calculan y guardan el hash, y devuelven el token en
claro **solo en el valor de retorno de esa llamada** — nunca se vuelve a
poder leer en claro después: ni la entidad ni el repositorio lo exponen.
Cada módulo agrega:

- `POST /api/reclamos` y `POST /api/mesaentradas` (ya públicos, ADR 0014
  §1/ADR 0015 §4) devuelven, además de lo que ya devolvían, el campo
  nuevo `tokenDeSeguimiento` con el valor en claro. Es la única vez que
  aparece.
- `GET /api/reclamos/seguimiento/{token}` y
  `GET /api/mesaentradas/seguimiento/{token}`: cada módulo calcula
  `TokenDeSeguimiento.hash(token)` y busca por ese valor. Se declaran en
  `rutasDeLecturaPublica()` del propio `DescriptorDeModulo` —mismo
  mecanismo ya existente (ADR 0012 §1), sin tocar
  `acceso.internal.ConfiguracionDeSeguridad`— porque `requestMatchers`
  soporta variables de path (`{token}`) igual que cualquier otro patrón de
  Spring Security ya usado en el proyecto. El gating por entitlement
  sigue corriendo antes: un municipio sin el módulo contratado sigue
  rechazando con 403, con o sin token válido.
- Un token que no matchea ninguna fila (no existe, o el string no es un
  token de este mecanismo) devuelve `404`, siempre con el mismo mensaje
  genérico: no se distingue "no existe" de "formato inválido", para no
  darle a quien prueba tokens al azar ninguna señal adicional sobre por
  qué falló.

### 5. Qué devuelve la consulta pública: mismo criterio de minimización que R8/R11, con el historial que cada módulo ya tiene

- `reclamos`: la respuesta agrega al mismo shape ya usado por
  `ReclamoPublicoResponse` (`id`, `categoria`, `estado`, `creadoEn`) los
  campos `comentarioGestion` y `actualizadoEn`. `comentarioGestion` es,
  en este módulo, el único registro de lo que el municipio le quiere
  comunicar al vecino sobre su reclamo (ADR 0014 §3 no modela un
  historial de movimientos separado). Sigue **sin** exponer
  `descripcion`, `direccion`, `nombreContacto` ni `contacto`: son los
  datos que el propio vecino ya tiene (los escribió él) o que no aportan
  a "en qué quedó", mismo criterio de no convertir esto en una vista de
  gestión que ya aplica `ReclamoPublicoResponse`.
- `mesaentradas`: la respuesta agrega al shape de
  `ExpedientePublicoResponse` (`id`, `tipo`, `estado`, `creadoEn`) los
  campos propios del tipo de trámite que el propio vecino ya cargó
  (`domicilioACertificar`/`rubroComercial`+`direccionLocal`/
  `direccionObra`+`descripcionObra`, según corresponda), `actualizadoEn`
  y el historial `movimientos` — pero cada movimiento **sin**
  `actorNombre`/`actorEmail`: quién de la planta municipal atendió el
  trámite es un dato interno del municipio, no algo que el vecino
  necesite para saber en qué quedó. Sigue sin exponer
  `solicitanteContacto` (dato propio, redundante para el vecino que ya
  lo escribió).

Ninguna de las dos respuestas revela nada que el municipio (con sesión y
permiso) no vea ya en su propio panel de gestión: son subconjuntos, nunca
información nueva.

### 6. Dónde se muestra el token: una única vez, en la confirmación del alta

El token se muestra en la pantalla de confirmación que ya existe tras
`POST /api/reclamos`/`POST /api/mesaentradas` — hoy esa pantalla dice
explícitamente "en esta rebanada todavía no hay una pantalla para volver
a consultarlo más adelante" (`PantallaDeReclamos.tsx`,
`PantallaDeMesaDeEntradas.tsx`): ese texto se reemplaza por el token y la
instrucción de guardarlo. No se reenvía por otro canal (email/SMS): el
contacto del vecino es opcional y sin verificar (ADR 0014 §4), así que no
es un canal confiable para entregar un secreto — mismo motivo por el que
no se contempla acá integrarlo con notificaciones (ADR 0013), que sigue
fuera de alcance de ambos módulos.

## Alternativas consideradas

- **UUID v4 como token**: más corto y una librería estándar lo genera
  solo, pero un UUID v4 tiene 122 bits de aleatoriedad real (6 bits fijos
  de versión/variante), no 256. Sigue siendo un tamaño defendible (por
  encima del piso de 128 bits de OWASP), pero se prefirió no introducir
  una dependencia conceptual en el formato UUID —con su propia superficie
  de "parece un id, no un secreto"— cuando `SecureRandom` + Base64 da un
  control más directo y explícito sobre la entropía real. Descartada por
  preferencia de claridad, no por inseguridad.
- **Hashear con bcrypt/argon2, igual que las contraseñas**: ver Decisión
  2. Es la respuesta "por defecto" para cualquier secreto guardado, pero
  ignora que el modelo de amenaza es distinto (sin diccionario posible
  sobre una entrada de 256 bits generada por máquina): pagaría un costo
  de cómputo real en cada consulta pública sin ganar seguridad adicional
  frente a SHA-256 en este caso puntual. Descartada por desproporcionada.
- **Guardar el token en claro**: simplifica la consulta (comparación
  directa) pero convierte cualquier filtro de la base en una filtración
  directa de acceso de lectura a todos los reclamos/trámites anónimos del
  municipio. Descartada: es exactamente el riesgo que este ADR existe
  para evitar.
- **Un token por módulo, sin mecanismo compartido**: ver Decisión 3.
  Descartada porque ya hay dos consumidores reales simultáneos y ambos
  ADRs previos señalaron la conveniencia de compartirlo; duplicar un
  mecanismo de seguridad no reduce acoplamiento, solo dificulta
  mantenerlo consistente.
- **Un módulo `expediente`/`seguimiento` transversal que además
  persista una tabla propia de tokens, referenciando genéricamente a
  cualquier "cosa seguible"**: más "reutilizable" en abstracto, pero
  introduce una relación entre módulos (quién referencia a quién) y una
  entidad genérica sin un tercer caso real que valide su forma —mismo
  error que ADR 0013 §3 y ADR 0015 §3 ya evitaron para otros mecanismos.
  Descartada: cada módulo sigue dueño de su propia tabla y de su propia
  columna `token_hash`, el módulo compartido no persiste nada.
- **Exponer el token también reenviado por email cuando el vecino deja
  contacto**: ver Decisión 6. Descartada por depender de un contacto
  opcional y no verificado.
- **Devolver 400 en vez de 404 para un token que no matchea**: 404 es
  semánticamente correcto (el recurso "reclamo/trámite con este token" no
  existe) y no distingue formato inválido de no encontrado, que es
  exactamente el comportamiento que se quiere (Decisión 4). Descartada la
  distinción de código de error por tipo de falla.

## Consecuencias

- `reclamos` y `mesaentradas` quedan con una tercera vía de acceso a un
  registro puntual, además de alta pública y gestión protegida: consulta
  pública por posesión de un secreto. Es, a propósito, un mecanismo de
  autenticación débil y deliberadamente acotado (un solo registro, de
  solo lectura, sin acción posible).
- El costo de fuerza bruta contra el endpoint de consulta pública queda
  sin mitigación explícita en esta rebanada (sin rate limiting): con 256
  bits de entropía, el costo de intentarlo ya es prohibitivo sin
  necesidad de throttling adicional, pero un límite de tasa sigue siendo
  buena práctica de defensa en profundidad — es endurecimiento de
  seguridad, diferido explícitamente por
  [CLAUDE.md](../../../CLAUDE.md), igual que ya se diferó para el alta
  pública (ADR 0014, Consecuencias).
- El próximo módulo que necesite el mismo patrón (alta anónima + consulta
  posterior por token) depende de `seguimientoanonimo` en vez de
  reimplementar generación y hash: el costo de este ADR es mantener ese
  módulo chico como código canon base, con la misma disciplina de "sin
  lógica de negocio de ningún módulo funcional" que ya rige para
  `persistencia`/`entitlement`.
- Perder el token es irrecuperable por diseño: no hay "reenviar token" ni
  "recuperar por otro dato" en esta rebanada —el reclamo/trámite sigue
  gestionable por el municipio con sesión, solo se pierde la vía de
  consulta anónima—. Es una limitación conocida, coherente con no tener
  todavía un canal de contacto verificado del vecino (ADR 0014 §4).

## Pendiente de definir

- Rate limiting sobre `GET /api/{modulo}/seguimiento/{token}` y sobre las
  altas públicas en general (endurecimiento de seguridad diferido por
  CLAUDE.md).
- Reenvío del token por un canal del vecino, si en el futuro el producto
  suma verificación de contacto (email/teléfono confirmado) que lo
  justifique.
- Extender este mismo mecanismo a otros módulos con alta pública anónima
  que puedan sumarse más adelante (ninguno concreto todavía).
- Expiración del token (hoy no vence: un reclamo/trámite resuelto sigue
  siendo consultable indefinidamente). No se decide acá por no tener
  todavía una política de retención de datos del producto en general.
