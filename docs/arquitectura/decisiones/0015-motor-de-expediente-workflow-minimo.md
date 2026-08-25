# 0015 - Motor de expediente/workflow mínimo: circuito fijo por tipo de trámite, no por municipio

- Estado: Aceptada
- Fecha: 2026-08-25

## Contexto

Fase 1 (MVP vendible) sigue con **Mesa de Entradas + subset de Trámites a
Distancia**
([roadmap](../../producto/roadmap-fases.md#fase-1--mvp-vendible--módulos-ancla)),
el módulo que el [backlog inicial](../../producto/backlog-inicial.md) (R6,
R8) viene señalando como el candidato natural para el **motor de
expediente/workflow configurable**, movido de Fase 0 a Fase 1 "junto con el
primer módulo funcional que efectivamente lo necesita"
([backlog inicial §"Movido a Fase 1"](../../producto/backlog-inicial.md#movido-a-fase-1)).

Dos rebanadas anteriores ya tratan la pregunta "¿necesita esto un motor de
workflow?" y deciden que no:

- [ADR 0014](0014-reclamos-ciudadanos-alta-publica-anonima-y-estado-propio.md)
  §3: el estado del reclamo es un ciclo fijo e **igual para todos los
  municipios**, así que un enum con una tabla de transiciones codificada en
  el servicio alcanza. Construir un motor genérico sobre ese único caso
  sería "diseñarlo sobre el único caso que no lo necesita".
- R8 (Cementerio): ni siquiera hay estado, es un alta y listo.

El [catálogo funcional](../../producto/catalogo-funcional.md) §2 describe la
versión completa de la ambición: "**Workflow configurable por circuito**
(cada municipio tiene sus propios pasos de aprobación)". Construir eso
—un editor de circuitos por municipio, con pasos y condiciones definidos en
tiempo de ejecución— es una pieza de infraestructura grande, y Mesa de
Entradas todavía no tiene un segundo ni un tercer municipio real pidiendo
circuitos *distintos entre sí* para el mismo trámite. Lo que sí tiene, a
diferencia de Reclamos, es **más de un tipo de trámite**, cada uno con su
propio circuito de estados (un certificado no se tramita igual que una
habilitación comercial), aunque ese circuito sea el mismo para todos los
municipios que lo contratan.

Esta rebanada (R9, CD-17) arranca Mesa de Entradas con un único tipo de
trámite (certificado de domicilio, ver spec CD-17), pero el motor tiene que
quedar armado para que sumar el segundo y el tercer tipo (habilitación
comercial simple, permiso de obra menor —
[roadmap](../../producto/roadmap-fases.md#fase-1--mvp-vendible--módulos-ancla))
no obligue a rehacerlo.

## Decisión

### 1. El circuito es fijo por **tipo de trámite**, definido en código y catálogo de producto — no editable por el municipio

El motor mínimo resuelve exactamente el problema que Mesa de Entradas tiene
hoy (varios tipos de trámite, cada uno con su propio circuito) y
explícitamente **no** resuelve el que todavía no tiene ningún caso real
(circuitos distintos por municipio para el mismo trámite). Mismo criterio
que el [ADR 0011](0011-autorizacion-por-roles-con-permisos-granulares.md)
usa para el catálogo de permisos y el ADR 0012 para el catálogo de módulos:
un tipo de trámite —y su circuito— existe porque hay código que lo declara,
no porque un municipio lo configuró desde una pantalla.

Agregar un tipo de trámite nuevo es, entonces, agregar código y una
migración (nunca una operación del municipio en producción):

- Un valor nuevo en el enum `TipoDeTramite`.
- Un `CircuitoDeTramite` propio (estado inicial + tabla de transiciones
  válidas) registrado para ese tipo.
- Los campos propios de ese trámite en `ExpedienteEntity` (ver Decisión 3).

### 2. `Expediente` + `MovimientoDeExpediente`: dos entidades, no una entidad de estado con historial embebido en columnas

- **`Expediente`**: el trámite en curso. Tiene `tipo` (enum), `estado`
  (enum, el estado *actual*), los datos del solicitante y los datos propios
  del trámite (Decisión 3), `creadoEn`/`actualizadoEn`.
- **`MovimientoDeExpediente`**: una fila por cada cambio de estado —incluida
  la creación, que es el primer movimiento, con `estadoAnterior = null`—,
  con `estadoNuevo`, `actorNombre`/`actorEmail` (copia del actor al momento
  del movimiento, mismo criterio "copia, no referencia" que
  [ADR 0013](0013-persistencia-de-eventos-y-mecanismo-transversal-de-notificaciones-y-auditoria.md)
  usa para `registro_auditoria` y que R7/R8 ya usan para
  `publicado_por_*`/`registrado_por_*`), `comentario` opcional y `fecha`.
  `actorNombre`/`actorEmail` son `null` en el movimiento de creación: el
  alta es pública y anónima (Decisión 4), no hay actor autenticado que la
  firme.

Es, literalmente, "quién lo hizo y cuándo" —el requisito concreto de esta
rebanada— modelado como una colección `@OneToMany` de `Expediente`
(`cascade = ALL`, `orphanRemoval = true`, ordenada por `fecha`), no como un
evento de dominio (ADR 0013): estos movimientos son el propio historial del
expediente, se leen siempre junto con el expediente que gestiona Mesa de
Entradas, no algo que otro módulo necesite reaccionar de forma desacoplada
hoy.

### 3. Sin columna JSON de "datos variables por tipo": todavía no hay un segundo tipo real que la justifique

`domicilioACertificar` (el único dato propio de "certificado de domicilio")
es una columna explícita de `expediente`, no un campo dentro de un JSON
genérico de "datos del trámite". Mismo criterio que el ADR 0014 §3 usa para
no generalizar sobre un único caso: hoy hay un solo tipo de trámite, así
que no hay información suficiente todavía para diseñar bien un esquema de
datos variables (¿JSON como `config` del [ADR 0007](0007-modelo-de-datos-del-tenant.md)?
¿tabla propia por tipo?). Se decide cuando el segundo tipo de trámite
aparezca y obligue a elegir con un caso real delante, no antes (ver
Pendiente de definir).

### 4. El alta es pública y anónima, reutilizando el mecanismo del ADR 0014 tal cual

Igual que Reclamos, un vecino no tiene por qué tener cuenta para iniciar un
trámite —el producto no tiene identidad ciudadana todavía
([visión y alcance](../../producto/vision-y-alcance.md))—, así que
`mesaentradas` declara `POST /api/mesaentradas` en
`rutasDeEscrituraPublica()` (ADR 0014 §1), sin extender ni tocar ese
mecanismo. Listar y avanzar el estado del expediente siguen protegidos por
sesión y permiso (`mesaentradas.ver`, `mesaentradas.gestionar`), igual
patrón que `reclamos.ver`/`reclamos.gestionar`.

A diferencia de Reclamos, esta rebanada **no** agrega seguimiento anónimo
por token: es exactamente el mismo pendiente que el ADR 0014 (Decisión 6)
ya dejó abierto para Reclamos, y sigue sin un segundo caso real —ahora hay
dos módulos que lo necesitarían— que obligue a resolverlo ahora. Ver
Pendiente de definir: cuando se resuelva, conviene que sea un mecanismo
único para ambos módulos, no uno por módulo.

### 5. El motor vive en el propio módulo `mesaentradas`, no en un módulo transversal nuevo

No se crea un módulo `expediente` o `workflow` del que otros módulos
dependan. `TipoDeTramite`, `EstadoDeExpediente`, `CircuitoDeTramite` y su
registro (`Map<TipoDeTramite, CircuitoDeTramite>`) son código interno de
`mesaentradas.internal`. Mismo criterio que el ADR 0013 §3 usa para no
construir una interfaz genérica de "evento auditable" con un solo
consumidor real: con un solo módulo (Mesa de Entradas) que necesita
circuitos por tipo, extraer el motor a un módulo transversal reutilizable
por otros módulos futuros sería diseñar una API pública sobre un único
consumidor. El costo declarado es que, si en el futuro otro módulo de
"expediente" (no Mesa de Entradas) necesita el mismo patrón de
estado+circuito+historial, hoy tendría que copiarlo, no reutilizarlo.

## Alternativas consideradas

- **Motor de workflow genérico con circuitos definidos en base de datos,
  editables por el municipio**: es la ambición completa del catálogo
  funcional. Prematura: ningún municipio real pidió todavía circuitos
  distintos entre sí para el mismo trámite, y construirlo a ciegas es
  exactamente el error que el ADR 0014 §3 ya señaló para Reclamos, a mayor
  escala. Descartada por ahora, no definitivamente — queda como Pendiente
  de definir.
- **Un enum de estado único y compartido por todos los tipos de trámite,
  sin `CircuitoDeTramite` por tipo** (como Reclamos): más simple, pero no
  resuelve el problema real de Mesa de Entradas, donde distintos trámites
  van a tener distintos pasos (una habilitación comercial probablemente
  necesita un paso de inspección que un certificado no tiene). Se habría
  vuelto a escribir en la próxima rebanada de todos modos. Descartada.
- **Columna JSON de datos variables por tipo desde esta rebanada**: ver
  Decisión 3. Descartada por prematura, no definitiva.
- **`MovimientoDeExpediente` como evento de dominio (ADR 0013)** en vez de
  entidad hija del propio expediente: el historial de un expediente se lee
  siempre junto con el expediente en la misma pantalla de gestión: no hay
  hoy un segundo módulo que necesite reaccionar a un cambio de estado de
  forma desacoplada. Si aparece (por ejemplo, notificar al vecino), se
  agrega un evento de dominio además de —no en lugar de— esta entidad,
  igual que R6/R7/R8 difieren la integración con notificaciones/auditoría
  hasta que el módulo la necesite de verdad.
- **Extraer el motor a un módulo transversal `expediente` desde ya**: ver
  Decisión 5. Descartada por prematura con un solo consumidor real.

## Consecuencias

- Agregar el segundo tipo de trámite (candidatos ya nombrados en el
  roadmap: habilitación comercial simple, permiso de obra menor) es
  código + migración dentro de `mesaentradas`: un valor nuevo de
  `TipoDeTramite`, su `CircuitoDeTramite`, sus campos propios y su
  formulario en el frontend — no toca el motor en sí (`Expediente`,
  `MovimientoDeExpediente`, el mecanismo de avance de estado).
- El motor no da, todavía, ninguna respuesta a "cada municipio con su
  propio circuito": todos los municipios que contraten `mesaentradas` usan
  el mismo circuito por tipo de trámite. Es una limitación conocida y
  declarada, no un olvido.
- Sin columna de datos variable por tipo, el segundo tipo de trámite real
  es quien decide esa forma (JSON vs. columnas explícitas vs. tabla
  propia), con un caso real delante en vez de a ciegas.
- El seguimiento anónimo del propio trámite por parte del vecino que lo
  inició sigue sin resolverse, ahora con dos módulos (`reclamos`,
  `mesaentradas`) que lo necesitarían: es una señal más fuerte que antes de
  que valga la pena resolverlo pronto, pero esta rebanada no lo hace.

## Pendiente de definir

- Circuitos configurables **por municipio** (no solo por tipo de trámite a
  nivel de catálogo de producto): la ambición completa del catálogo
  funcional §2, pendiente de un caso real que la justifique.
- Forma de los datos propios de un tipo de trámite cuando aparezca el
  segundo tipo real (columna JSON, tabla por tipo, u otra).
- Mecanismo único de seguimiento anónimo por token, compartido entre
  `reclamos` y `mesaentradas` (o el que aparezca después), en vez de uno
  por módulo.
- Giro entre áreas / derivación de un expediente a otro sector del
  municipio: el catálogo funcional lo menciona como parte de "Mesa de
  Entradas digital"; esta rebanada no lo construye (ver spec CD-17,
  "Fuera de alcance").
- Caratulación formal y numeración correlativa oficial del expediente
  (mismo pendiente que R7 ya dejó para la numeración de normas del Boletín
  Oficial).
- Notificación al vecino de cambios de estado de su trámite, integrando con
  el motor de notificaciones transversal (ADR 0013), cuando el módulo lo
  necesite de verdad.
- Generación del documento (PDF) del certificado u otro trámite, firma
  electrónica de actos administrativos — catálogo funcional §2, complejidad
  alta, fuera de esta rebanada.
