# 0014 - Reclamos ciudadanos: alta pública anónima y estado propio, sin motor de workflow genérico

- Estado: Aceptada
- Fecha: 2026-08-24

## Contexto

Fase 1 (MVP vendible / módulos ancla) arranca con **Reclamos ciudadanos
(311)**: baches, alumbrado, poda/arbolado, recolección de residuos,
animales sueltos ([catálogo funcional](../../producto/catalogo-funcional.md)
§1). Es el primero de la lista en el
[roadmap](../../producto/roadmap-fases.md#fase-1--mvp-vendible--módulos-ancla),
de complejidad baja-media y alto impacto, y el candidato natural para
reemplazar a `ejemplo` como sujeto real de los tests de gating
([ADR 0012](0012-declaracion-de-modulos-y-gating-por-ruta.md) §10,
[backlog inicial](../../producto/backlog-inicial.md)).

Un 311 solo funciona si un vecino puede cargar un reclamo **sin cuenta**:
exigir registro para reportar un bache mata el caso de uso. El
[ADR 0012](0012-declaracion-de-modulos-y-gating-por-ruta.md) ya identificó
este vacío y lo dejó pendiente a propósito: *"Un módulo que necesite
exponer escritura anónima —un trámite que un vecino inicia sin cuenta— no
está contemplado acá y requiere su propia decisión cuando aparezca; no se
anticipa."* Ese momento es este.

El [roadmap](../../producto/roadmap-fases.md#fase-1--mvp-vendible--módulos-ancla)
también dice que Fase 1 trae "el motor de expediente/workflow configurable
... junto con el primer módulo que efectivamente los necesita". Reclamos,
tal como se aborda en esta rebanada, tiene un ciclo de vida **fijo e
igual para todos los municipios** (nuevo → en proceso → resuelto/
rechazado), no "circuitos propios de aprobación que cada municipio
define distinto" ([catálogo funcional](../../producto/catalogo-funcional.md)
§2), que es el problema que el motor configurable existe para resolver.
Hay que decidir explícitamente si ese motor entra ya con Reclamos o si el
primer consumidor real que lo justifica es otro módulo.

Ningún ADR previo cubre escritura pública anónima ni motor de
expediente/workflow.

## Decisión

### 1. `DescriptorDeModulo` gana `rutasDeEscrituraPublica()`, solo para `POST`

Nuevo método `default List<String> rutasDeEscrituraPublica()` (default
vacío, igual criterio que `rutasDeLecturaPublica()`). La cadena de
seguridad (`acceso.internal.ConfiguracionDeSeguridad.cadenaDeApi`) itera
el catálogo agregando `permitAll()` para **`POST`** sobre esas rutas,
igual que ya hace con `GET` para las de lectura pública. Un módulo con
alta anónima declara esto y no toca la cadena de seguridad, que es código
de otro módulo — mismo principio que el ADR 0012 ya aplica a lectura.

Deliberadamente **solo `POST`**: nunca se generaliza a un método
parametrizable. Ninguna ruta pública puede ser `PUT`/`PATCH`/`DELETE` —
editar o borrar algo ya creado, sin cuenta que lo respalde, no tiene forma
de verificar que quien edita es quien creó. Alta sí, todo lo demás no.

Entitlement sigue corriendo antes que esta regla: un municipio sin
`reclamos` contratado sigue rechazando con 403 `MODULO_NO_CONTRATADO`
incluso para esta ruta y sin sesión — la escritura pública no es una
puerta trasera al gating por módulo.

No hace falta ningún cambio de CSRF: `CsrfCookieFilter` ya fuerza la
cookie en cada request a `/api/**`, y el frontend ya hace `GET
/api/sesion` y `GET /api/tenant/**` al montar (`useSesion`, `useTenant`),
así que cualquier visitante anónimo ya tiene la cookie antes de que exista
un formulario para enviar.

`reclamos` declara `rutasDeEscrituraPublica() = List.of("/api/reclamos")`
(solo la creación). Listar, ver el detalle y cambiar el estado siguen
protegidos por sesión + permiso, sin entrar en esta lista.

### 2. `reclamos` es módulo contratable, no canon base

A diferencia de `auditoria`/`notificaciones` (ADR 0013 §4), `reclamos`
publica `DescriptorDeModulo` con prefijo `/api/reclamos` y entra al
catálogo comercial: un municipio puede no contratarlo, igual que
cualquier otro módulo de área.

### 3. El estado del reclamo es un campo de dominio propio, no el motor de workflow configurable

`estado` es un enum fijo de cuatro valores (`nuevo`, `en_proceso`,
`resuelto`, `rechazado`) con una tabla de transiciones válidas codificada
en el servicio del módulo `reclamos` — no una entidad de un motor de
workflow genérico.

Motivo: el motor configurable que el roadmap prevé para Fase 1 existe
para resolver que "cada municipio tiene sus propios pasos de aprobación"
(catálogo funcional §2). Reclamos no varía ese circuito por municipio —
es el mismo ciclo para todos—, así que construir el motor genérico ahora
sería diseñarlo sobre el único caso que **no** lo necesita. Mismo criterio
que el [ADR 0013](0013-persistencia-de-eventos-y-mecanismo-transversal-de-notificaciones-y-auditoria.md)
§3 usa para diferir la interfaz genérica de evento auditable hasta que
haya un segundo caso real con el que generalizar sin adivinar.

El motor de expediente/workflow configurable sigue pendiente de Fase 1;
se construye cuando el primer módulo que sí necesita circuitos que varíen
por municipio lo requiera (candidato: Mesa de Entradas, con giro entre
áreas).

### 4. Datos de contacto del vecino son opcionales y sin cuenta detrás

`nombreDelVecino` y `contactoDelVecino` (email o teléfono, texto libre)
son opcionales: no hay cuenta que los respalde ni verifique, así que no
hay más validación posible que un límite de largo de columna. Se guardan
como dato informativo para que el municipio pueda volver a contactar al
vecino si hace falta, no como identidad.

### 5. Sin geolocalización estructurada en esta rebanada

La ubicación del reclamo es `direccion`, texto libre. El catálogo
funcional pide geolocalización, pero "GIS como servicio" es una pieza
transversal ([catálogo funcional](../../producto/catalogo-funcional.md)
§5) que todavía no construyó ningún módulo. Acoplar la primera rebanada
de Reclamos a una decisión de GIS que no existe la sobredimensiona.
Se difiere hasta que GIS como servicio exista o un módulo distinto lo
justifique antes.

### 6. Sin seguimiento por parte del vecino anónimo

No se expone un `GET` público de un reclamo puntual por id o por
cualquier otro identificador. Un id secuencial es adivinable: exponerlo
sin control de acceso dejaría ver el detalle (incluido el contacto) de
cualquier reclamo de cualquier vecino. Habilitar seguimiento anónimo
necesitaría un token no adivinable y su propia pantalla — se difiere
explícitamente, no es un olvido.

### 7. Sin integración con notificaciones/auditoría de R5 en esta rebanada

El [ADR 0013](0013-persistencia-de-eventos-y-mecanismo-transversal-de-notificaciones-y-auditoria.md)
§3 ya anticipa que el próximo módulo que quiera auditoría o notificación
agrega su propio evento y su propio listener cuando le haga falta.
Reclamos no lo hace en R6: sumarlo ahora es alcance extra sin necesidad
para la demo de esta rebanada. Se difiere explícitamente.

### 8. Permisos: `reclamos.ver` y `reclamos.gestionar`, en ambos roles de sistema

`reclamos.ver` (listar y ver detalle) y `reclamos.gestionar` (cambiar
estado), área "Reclamos". A diferencia de `ejemplo.usar` y
`auditoria.ver` —asignados solo a `administrador` a propósito, como
sujeto de prueba de "módulo contratado pero sin permiso"—, acá se
asignan a **ambos** roles de sistema (`administrador` y `agente`): es
funcionalidad real que el personal de atención al vecino necesita operar
el día uno, no una demostración del mecanismo.

## Alternativas consideradas

- **Exigir cuenta ciudadana para cargar un reclamo**: mata el caso de uso
  principal de un 311 (mínima fricción), y el producto todavía no tiene
  identidad ciudadana (el roadmap la deja como integración futura con
  MiArgentina/RENAPER). Descartada para esta rebanada.
- **Generalizar `DescriptorDeModulo` a una lista de rutas públicas con
  método HTTP explícito**, en vez de un método nuevo separado: más
  flexible en abstracto, pero mezclaría en una sola lista capacidades de
  riesgo muy distinto (leer vs. crear) y rompería el default vacío que ya
  usa `ejemplo` sin necesidad. Se prefiere sumar un método explícito por
  adición, sin tocar el contrato existente. Descartada por ahora.
- **Permitir también `PUT`/`PATCH` públicos** para que el vecino edite su
  reclamo después de cargarlo: sin cuenta no hay forma de verificar que
  quien edita es quien creó. Descartada.
- **Construir ya el motor de expediente/workflow configurable**: ver
  Decisión 3. Prematuro sobre un único caso que no lo necesita.
  Descartada por ahora, no definitivamente.

## Consecuencias

- Un módulo con alta pública anónima declara explícitamente qué rutas de
  creación abre (`rutasDeEscrituraPublica()`); agregar uno nuevo no toca
  la cadena de seguridad compartida, mismo principio que el ADR 0012 ya
  fija para lectura pública.
- El costo de abuso del endpoint de creación pública (spam de reclamos
  falsos) queda sin mitigación en R6: no hay captcha ni rate limiting —
  es endurecimiento de seguridad, diferido explícitamente por
  [CLAUDE.md](../../../CLAUDE.md).
- El próximo módulo que necesite estado con transiciones (candidato:
  Mesa de Entradas, o el propio Reclamos si algún día necesita variar su
  circuito por municipio) tiene que decidir si generaliza el patrón de
  Reclamos o construye el motor de workflow ya diferido; esta decisión no
  lo resuelve, lo pospone con nombre y con motivo.
- Reclamos queda, a propósito, sin geolocalización estructurada, sin
  seguimiento anónimo por código y sin auditoría/notificación
  transversal — tres pendientes explícitos, no olvidos.

## Pendiente de definir

- Motor de expediente/workflow configurable (Decisión 3), para cuando
  aparezca el primer módulo que sí necesite circuitos que varíen por
  municipio.
- GIS como servicio / geolocalización estructurada de reclamos
  (Decisión 5).
- Seguimiento del estado de un reclamo por parte del vecino anónimo que
  lo cargó, con un mecanismo de token no adivinable (Decisión 6).
- Rate limiting / anti-abuso sobre escritura pública (endurecimiento de
  seguridad diferido por CLAUDE.md).
- Integración de Reclamos con notificaciones/auditoría transversal
  (ADR 0013), cuando el módulo lo necesite.
- Asignación de un reclamo a un agente en particular: hoy cualquiera con
  `reclamos.gestionar` puede operar cualquier reclamo del municipio.
- **Retiro del módulo `ejemplo`** (backend, frontend, y la migración de
  limpieza de su permiso que el ADR 0012 ya anticipa como costo de dar de
  baja un módulo) y migración de `EntitlementDeModulosTest` para que use
  `reclamos` como sujeto del test del mecanismo de gating. R6 no lo hace:
  es una tarea de limpieza que no cambia comportamiento de producto ni
  aporta nada a la demo de esta rebanada, así que se mantiene separada en
  vez de agrandar R6. Hasta que se haga, `ejemplo` sigue activo, sin
  tocar, y `EntitlementDeModulosTest` lo sigue usando como sujeto.
