# 0028 - Educación municipal: padrón público de instituciones educativas de gestión municipal, segunda rebanada de Fase 5

- Estado: Aceptada
- Fecha: 2026-08-31

## Contexto

[ADR 0025](0025-desarrollo-social-inscripcion-a-programa-social-con-minimizacion-de-datos-sensibles.md)
abrió Fase 5 — Áreas sociales con Desarrollo Social (R21, CD-29) y dejó
**Educación municipal** explícitamente como candidata futura, descartada
solo para esa rebanada: "viable sin dato personal pero no aporta una
dimensión de dominio nueva frente a Obras/Arbolado (mismo catálogo
nombre/ubicación/tipo/estado)" (ADR 0025, Contexto). R22 y R23 abrieron
Fase 6 en su lugar (turnos, prensa), así que Fase 5 sigue con una sola
rebanada. Toca cerrarla con una segunda, mismo criterio de descarte
razonado que ya usaron ADR 0021/0023/0024/0025 al abrir cada fase nueva.

De las cuatro candidatas que listó ADR 0025 (Contexto), solo dos siguen
disponibles: Salud municipal y Discapacidad quedaron diferidas como
**fase completa**, no rebanada — dependen de un mecanismo de datos
sensibles (cifrado por columna, auditoría de lectura) que el producto no
tiene y de un municipio piloto real, nada de eso cambió desde ADR 0025.
Quedan Educación municipal (la única candidata de riesgo bajo sin
resolver) y, en rigor, una segunda vuelta sobre Desarrollo Social — pero
esta ADR no reabre Desarrollo Social, ya tiene su rebanada.

### Alcance real de competencia municipal en educación (Argentina)

El [catálogo funcional](../../producto/catalogo-funcional.md) §3 no
detalla Educación municipal más allá de "si el municipio tiene
competencia educativa" — una condicional que hay que resolver con
criterio, no ignorar. En Argentina, la educación primaria y secundaria es
**competencia provincial** desde la Ley Federal de Educación (1993),
consolidada por la Ley de Educación Nacional 26.206: cada provincia
gestiona su propio sistema a través de su Dirección General de Escuelas
o Consejo General de Educación. Un municipio argentino, en general,
**no** administra escuelas primarias ni secundarias — diseñar este módulo
como un padrón de "escuelas" municipales inventaría una competencia que
la enorme mayoría de los municipios del país no tiene, mismo riesgo que
ya llevó a diferir Catastro y Planeamiento Urbano (ADR 0023, Contexto).

Lo que sí es competencia municipal real y frecuente, sin depender de
normativa provincial ni de un municipio piloto para nombrarlo:

- **Jardines maternales y jardines de infantes municipales**: la primera
  infancia (habitualmente desde los 45 días hasta los 3-4 años, antes de
  la sala de 4/5 que sí es parte del sistema educativo provincial
  obligatorio) es un área donde muchísimos municipios argentinos operan
  sus propios jardines, complementando o supliendo la oferta provincial.
- **Centros de Formación Profesional / escuelas de oficios**: educación
  no formal, capacitación laboral (electricidad, gastronomía, oficios
  varios), un área clásica de gestión municipal directa, muchas veces
  con articulación provincial/nacional pero de administración local.

Esta rebanada modela un padrón de **instituciones** de ese tipo (jardines
maternales/de infantes y centros de formación profesional municipales),
nunca un registro de escuelas primarias/secundarias ni nada que sugiera
que el municipio administra el sistema educativo formal.

### Por qué de todos modos vale la pena, aunque no aporte mecanismo técnico nuevo

Un padrón de instituciones (nombre, tipo, ubicación, estado) es, en
forma, el mismo patrón "alta protegida + lectura pública + estado propio
mutable" que ya demostraron Obras Públicas (ADR 0023) y Arbolado urbano
(ADR 0024) — exactamente lo que ADR 0025 señaló al descartarla para R21.
Esta ADR no pretende lo contrario: no hay un mecanismo técnico nuevo acá.
Lo que aporta es **cobertura de dominio**, no técnica: cierra Fase 5 con
dos rebanadas demostrables (mismo criterio que ya cerró Fase 3 con una
sola rebanada por falta de candidata viable, y Fase 4 con dos), sin
inventar normativa ni datos de un municipio piloto que no existe, y sin
tocar dato personal de nadie — a diferencia de Desarrollo Social, acá no
hay ninguna tensión de minimización que resolver.

### Tercer caso del patrón "alta protegida + lectura pública + estado propio mutable": ¿se extrae abstracción común?

[ADR 0024 §7](0024-arbolado-urbano-padron-publico-con-estado-sanitario-propio.md)
dejó pendiente esta pregunta explícitamente para "un tercer caso que
repita el patrón". Este es ese tercer caso. Se decide, otra vez, **no
extraer nada**: las reglas de transición de estado siguen sin ser
iguales entre los tres (Obras permite un salto directo a estado
terminal desde `EN_EJECUCION`; Arbolado no permite el equivalente directo
a `RETIRADO`; Educación tiene un ciclo de solo tres estados, sin
paralelo real con los otros dos) y el conjunto de campos propios de cada
entidad tampoco coincide (Obras tiene fechas estimadas, Arbolado tiene
fecha de plantación, Educación no tiene ninguna fecha propia). Extraer
una abstracción genérica ahora ahorraría poco código de por sí muy legible
a costa de generalizar sobre reglas de negocio que siguen divergiendo.
Se revisita esta misma pregunta si aparece un cuarto caso.

## Decisión

### 1. Módulo nuevo `educacion`, contratable, sin depender de otros módulos

`educacion` es un módulo funcional propio
([ADR 0009](0009-modelo-comercial-y-entitlement.md)), con su propio
`DescriptorDeModulo` y prefijo `/api/educacion`. No depende de `obras`,
`arbolado` ni de ningún otro módulo funcional — mismo criterio de
independencia que ambos, verificado por el mismo test de modularidad de
Spring Modulith.

### 2. Mismo mecanismo de alta protegida / lectura pública que Obras/Arbolado, sin ADR nuevo para esa parte

`POST /api/educacion` requiere sesión y el permiso `educacion.gestionar`:
el registro lo origina el municipio, nunca un vecino ni la institución
misma — mismo criterio que Obras (ADR 0023 §2) y Arbolado (ADR 0024 §2).
`GET /api/educacion` es lectura pública sin sesión
(`rutasDeLecturaPublica()`, [ADR 0012](0012-declaracion-de-modulos-y-gating-por-ruta.md)
§1), con filtro opcional por `estado`, por `tipo` y por texto (`q`) sobre
`nombre`/`ubicacion`, mismo patrón `ILIKE` que Obras/Arbolado. Sin
identificador obligatorio de búsqueda: es un registro público general
(¿qué instituciones educativas municipales hay?), no una consulta
puntual sobre un dato de un vecino.

No hay `rutasDeEscrituraPublica()`: ninguna mutación pública/anónima,
igual que Obras/Arbolado — a diferencia de Reclamos/Multas/Desarrollo
Social.

### 3. `tipo` es un enum cerrado acotado a la competencia municipal real (Contexto), `ubicacion` es texto libre

`TipoDeInstitucionEducativa`: enum `JARDIN_MATERNAL`,
`JARDIN_DE_INFANTES`, `CENTRO_DE_FORMACION_PROFESIONAL`, `OTRA`. A
diferencia de `especie` en Arbolado (texto libre, ADR 0024 §3), acá sí
hay un enum cerrado porque no se está inventando una taxonomía específica
de un municipio: son las dos categorías reales de competencia municipal
identificadas en el Contexto, más una salida genérica (`OTRA`) para no
bloquear un caso real que no encaje. **A propósito, no hay
`ESCUELA_PRIMARIA` ni `ESCUELA_SECUNDARIA` en este enum**: agregarlos
sugeriría una competencia que el municipio, en general, no tiene
(Contexto) — si un municipio piloto real gestiona una escuela primaria o
secundaria por una situación excepcional, es una decisión de producto
aparte, con ese caso real delante, no una opción más de este enum.

`ubicacion` (dirección de la institución) es texto libre, mismo criterio
que `ubicacion` en Obras (ADR 0023 §6) y Arbolado (ADR 0024 §3): sin
geolocalización estructurada ni GIS.

### 4. Estado de la institución: enum de tres valores + tabla de transiciones, mismo patrón que Obras/Arbolado

`EstadoDeInstitucion`: `ACTIVA`, `CERRADA_TEMPORALMENTE`,
`CERRADA_DEFINITIVAMENTE`.

```
ACTIVA                 → CERRADA_TEMPORALMENTE
CERRADA_TEMPORALMENTE  → ACTIVA
CERRADA_TEMPORALMENTE  → CERRADA_DEFINITIVAMENTE
```

`CERRADA_DEFINITIVAMENTE` es terminal. Una institución `ACTIVA` no pasa
directo a `CERRADA_DEFINITIVAMENTE`: tiene que pasar primero por
`CERRADA_TEMPORALMENTE` — mismo espíritu que Arbolado (ADR 0024 §4): una
institución no desaparece del padrón sin un estado intermedio que
documente que hubo un cierre transitorio antes de la baja definitiva. El
estado inicial no es un parámetro del alta: siempre nace `ACTIVA`.

`PATCH /api/educacion/{id}/estado` requiere sesión y
`educacion.gestionar`; no está en `rutasDeEscrituraPublica()`.

### 5. Permiso único `educacion.gestionar`, asignado a `administrador` y `agente`

Un solo permiso cubre alta y actualización de estado, mismo criterio que
`obras.gestionar`/`arbolado.gestionar` (ADR 0023 §5, ADR 0024 §5): dar de
alta una institución y actualizar su estado son la misma clase de
trabajo operativo de gabinete, sin ninguna diferencia real de
sensibilidad fiscal, discrecional o de dato personal que amerite separar
los permisos (a diferencia de Desarrollo Social, ADR 0025 §7, donde la
sensibilidad del dato personal sí lo justificaba). Se asigna a **ambos**
roles de sistema. Un municipio que quiera restringirlo más compone su
propio rol ([ADR 0011](0011-autorizacion-por-roles-con-permisos-granulares.md)).

### 6. Sin geolocalización, sin cupos/vacantes, sin inscripción de personas, sin adjuntos

- Sin geolocalización estructurada ni GIS: mismos motivos que Obras/
  Arbolado (ADR 0023 §6, ADR 0024 §3/§6).
- Sin cupos ni vacantes disponibles: modelar cupo implicaría, tarde o
  temprano, modelar un proceso de inscripción/lista de espera —
  exactamente el tipo de funcionalidad que reabriría la pregunta de dato
  personal que ADR 0025 ya resolvió con cuidado para Desarrollo Social.
  Esta rebanada es deliberadamente un padrón de instituciones, no un
  sistema de inscripción a ellas.
- Sin inscripción de niños/estudiantes a ninguna institución: sería dato
  personal de un menor, la categoría de dato más sensible que el
  producto podría tocar, muy por encima incluso de lo que ADR 0025
  decidió minimizar para Desarrollo Social. Ni siquiera se plantea como
  candidata para esta rebanada.
- Sin adjuntos/fotos: mismo criterio que Obras/Arbolado.

## Alternativas consideradas

- **Modelar escuelas primarias/secundarias municipales**: descartada —
  ver Contexto. Inventaría una competencia municipal que, en Argentina,
  no existe en la enorme mayoría de los casos.
- **Elegir Salud municipal o Discapacidad para esta rebanada**: siguen
  diferidas como fase completa, sin cambios desde ADR 0025 (Contexto).
- **Una segunda rebanada de Desarrollo Social en vez de abrir Educación**:
  no evaluada en profundidad — esta ADR resuelve qué cierra Fase 5, no
  reabre Desarrollo Social, que ya tiene su rebanada (R21) completa.
- **Enum abierto/texto libre para `tipo`, mismo criterio que `especie` en
  Arbolado**: descartada — ver Decisión 3. A diferencia de una especie de
  árbol (hay miles, ninguna lista cerrada tendría sentido), acá sí hay
  una lista corta y real de qué tipo de institución educativa gestiona
  un municipio argentino, y dejarla abierta facilitaría cargar por error
  una "escuela primaria municipal" que en la enorme mayoría de los casos
  no existe como tal.
- **Modelar cupos/vacantes disponibles**: descartada — ver Decisión 6.
  Reabre la pregunta de dato personal que ADR 0025 ya resolvió con
  cuidado.
- **Extraer una abstracción común con `obras`/`arbolado` para el patrón
  "alta protegida + lectura pública + estado propio mutable"**:
  descartada — ver Contexto, última sección. Las reglas de transición y
  los campos propios siguen sin ser iguales entre los tres casos.

## Consecuencias

- `educacion` no depende de ningún otro módulo funcional; el test de
  modularidad de Spring Modulith lo verifica en el build.
- No hay ninguna ruta pública de escritura en este módulo, igual que
  `obras`/`arbolado`.
- Fase 5 — Áreas sociales queda con dos rebanadas demostrables
  (Desarrollo Social, Educación municipal). Salud municipal y
  Discapacidad siguen diferidas como fase/rebanada completa, sin cambios
  respecto de ADR 0025.
- Con `obras`, `arbolado` y `educacion` como tres instancias del mismo
  patrón sin abstracción común extraída, un cuarto caso que lo repita
  debería revisar de nuevo si ya conviene extraer algo — no se anticipa
  en esta ADR.
- El registro no lleva quién hizo cada cambio de estado ni motivo del
  cierre (solo `actualizadoEn`), mismo criterio que Obras/Arbolado.

## Pendiente de definir

- Motivo del cierre temporal o definitivo (texto libre asociado al
  cambio de estado): no existe en esta rebanada.
- Quién hizo cada cambio de estado y cuándo, más allá de
  `actualizadoEn` (sin historial de movimientos, mismo criterio que
  Obras/Arbolado).
- Edición de `nombre`/`tipo`/`ubicacion`/`descripcion` después de creado
  el registro.
- Cupos/vacantes disponibles e inscripción a una institución (Decisión
  6): depende de una decisión de producto separada, con el mismo cuidado
  de minimización de dato personal (y, acá, de menores) que ADR 0025 ya
  aplicó a Desarrollo Social — no se resuelve en esta ADR.
- Un caso real de municipio que gestione una escuela primaria/secundaria
  por una situación excepcional (Decisión 3): fuera de alcance mientras
  no exista ese caso real.
- Rate limiting sobre las rutas de `educacion` (endurecimiento de
  seguridad diferido por [CLAUDE.md](../../../CLAUDE.md), aunque acá no
  hay ruta pública de escritura para abusar).
- Salud municipal y Discapacidad como fases/rebanadas futuras: sin
  cambios respecto de ADR 0025.
