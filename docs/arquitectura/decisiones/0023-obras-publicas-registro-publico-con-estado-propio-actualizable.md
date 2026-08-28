# 0023 - Obras Públicas: registro público de obras en curso, con estado propio actualizable, sin GIS ni motor de workflow

- Estado: Aceptada
- Fecha: 2026-08-28

## Contexto

Con R18 (CD-26, consola del municipio) cerrada, Fase 3 queda efectivamente
terminada por ahora: sus dos ítems restantes, Compras y Contrataciones y
Presupuesto/Contabilidad (y por extensión Tesorería), siguen diferidos
porque necesitan datos reales de un municipio piloto que todavía no existe
([ADR 0021](0021-multas-de-transito-alta-protegida-estado-propio-descuento-por-pago-temprano.md),
Contexto).

Toca abrir Fase 4 — Gestión territorial, que el
[roadmap](../../producto/roadmap-fases.md#fase-4--gestión-territorial) y el
[backlog inicial](../../producto/backlog-inicial.md) listan hoy solo como
un título con temas: Obras Públicas, Catastro, Planeamiento Urbano/Uso del
Suelo, Ambiente y Servicios Públicos, GIS como servicio consolidado. No hay
detalle de producto todavía; hay que elegir la primera rebanada demostrable
de la fase, por descarte razonado, mismo criterio que
[ADR 0021](0021-multas-de-transito-alta-protegida-estado-propio-descuento-por-pago-temprano.md)
usó para elegir Multas al abrir Fase 3: preferir lo que se puede diseñar
sin inventar normativa o datos reales específicos de un municipio, y
diferir lo que sí los necesita hasta tener un piloto real.

Candidatas descartadas para esta rebanada:

- **Catastro**: el propio roadmap ya lo señala — "suele depender de datos
  provinciales, conviene tenerlo tarde, cuando ya haya patrones de
  integración probados". Parcelas, valuaciones y nomenclatura catastral no
  se pueden diseñar sin datos reales de un municipio sin inventarlos.
  Descartada.
- **GIS como servicio consolidado**: es una pieza de infraestructura
  transversal ("capa de mapas reutilizable para Obras, Reclamos, Catastro,
  Ambiente", [catálogo funcional](../../producto/catalogo-funcional.md)
  §5), no una rebanada vertical demostrable por sí sola — el mismo tipo de
  error que [CLAUDE.md](../../../CLAUDE.md) señala explícitamente con el
  ejemplo "implementar el routing dinámico de datasource no es una
  rebanada". Construirla ahora, sin un segundo módulo real que la necesite
  todavía, es diseñar sobre un caso hipotético. Descartada; además
  [ADR 0014](0014-reclamos-ciudadanos-alta-publica-anonima-y-estado-propio.md)
  §5 ya difirió la geolocalización estructurada de Reclamos precisamente
  porque GIS como servicio no existe — este ADR reafirma el mismo criterio
  para Obras Públicas (Decisión 6).
- **Planeamiento Urbano / Uso del Suelo**: zonificación, factibilidad y
  habilitaciones de construcción dependen del código de zonificación y la
  ordenanza propia de cada municipio — mismo tipo de riesgo que
  [ADR 0021](0021-multas-de-transito-alta-protegida-estado-propio-descuento-por-pago-temprano.md)
  ya usó para diferir Compras (los umbrales varían por ordenanza
  municipal propia). Sin un municipio piloto real, cualquier código de
  zonificación sería inventado. Descartada para esta rebanada.
- **Ambiente y Servicios Públicos**: recolección de residuos, arbolado
  urbano, espacios verdes y alumbrado público son, en principio, tan
  viables como Obras Públicas sin inventar datos reales (mismo perfil de
  riesgo bajo). No se descarta por inviable, sino porque una fase no
  arranca con dos rebanadas a la vez: queda como candidata natural para la
  siguiente rebanada de Fase 4, no para esta.
- **Obras Públicas**: un registro público de obras en curso (nombre,
  ubicación, tipo, estado, fechas estimadas) es información pública que
  cualquier municipio puede tener sin depender de normativa provincial ni
  de montos/presupuesto (eso queda en Presupuesto y Contabilidad, ya
  diferido). Es, en forma y en riesgo, el mismo tipo de rebanada que
  Boletín Oficial (R7) y Transparencia activa (R11): alta protegida por el
  municipio, lectura pública sin sesión, sin inventar cifras ni régimen
  legal. Elegida.

A diferencia de Boletín (R7) y Transparencia (R11) — donde un registro
publicado no se edita, se corrige publicando uno nuevo
([backlog](../../producto/backlog-inicial.md), R7/R11) —, una obra pública
"en curso" solo tiene valor de seguimiento si su estado refleja el avance
real: una obra que nunca puede pasar de "Planificada" a "En ejecución" no
es un registro de seguimiento, es un anuncio estático que ya cubre
Boletín. Ningún ADR previo decide si (ni cómo) un registro público de este
producto puede mutar después de creado.

## Decisión

### 1. Módulo nuevo `obras`, contratable

`obras` es un módulo funcional propio ([ADR 0009](0009-modelo-comercial-y-entitlement.md)),
con su propio `DescriptorDeModulo` y prefijo `/api/obras`. No depende de
ningún otro módulo funcional.

### 2. Alta protegida, lectura pública — mismo mecanismo que Boletín/Transparencia, sin ADR nuevo para esa parte

`POST /api/obras` requiere sesión y el permiso `obras.gestionar` (Decisión
5): el registro lo origina el municipio, nunca el vecino — mismo criterio
que Multas ([ADR 0021](0021-multas-de-transito-alta-protegida-estado-propio-descuento-por-pago-temprano.md)
§3), a diferencia de Reclamos. `GET /api/obras` es lectura pública sin
sesión (`rutasDeLecturaPublica()`, [ADR 0012](0012-declaracion-de-modulos-y-gating-por-ruta.md)
§1), con filtro opcional por `estado`, por `tipo` y por texto (`q`) sobre
nombre/ubicación — mismo patrón `ILIKE` que Boletín. A diferencia de
Multas/Tasas, no exige un identificador obligatorio de búsqueda: es un
registro público general (¿qué obras hay en el municipio?), no una
consulta puntual sobre un dato del vecino, así que un listado abierto no
expone nada sensible — mismo criterio que Boletín/Transparencia/
Cementerio.

### 3. Estado propio: enum fijo + tabla de transiciones en el servicio, mismo patrón que Reclamos/Multas, no el motor de ADR 0015

`EstadoDeObra` es un enum de cuatro valores con una tabla de transiciones
codificada en el servicio, sin entidad de historial:

```
PLANIFICADA  → EN_EJECUCION
EN_EJECUCION → PARALIZADA
EN_EJECUCION → FINALIZADA
PARALIZADA   → EN_EJECUCION
```

`FINALIZADA` es terminal. `PARALIZADA` solo vuelve a `EN_EJECUCION`: una
obra paralizada no se da por finalizada directamente en esta rebanada, se
reanuda primero (evita la ambigüedad de "finalizada" para algo que nunca
se retomó). `PATCH /api/obras/{id}/estado` requiere sesión y
`obras.gestionar`; no está en `rutasDeEscrituraPublica()` — no hay
mutación pública/anónima de ningún tipo en este módulo, ni siquiera de
alta (a diferencia de Reclamos y de la búsqueda/pago de Multas).

Mismo criterio que [ADR 0014](0014-reclamos-ciudadanos-alta-publica-anonima-y-estado-propio.md)
§3 y [ADR 0021](0021-multas-de-transito-alta-protegida-estado-propio-descuento-por-pago-temprano.md)
§2 ya aplicaron: un único ciclo de vida fijo e igual para todos los
municipios no amerita el motor de expediente/workflow configurable de
[ADR 0015](0015-motor-de-expediente-workflow-minimo.md) (que además vive en
`mesaentradas.internal`, inalcanzable para otro módulo por decisión
explícita de esa misma ADR).

### 4. A diferencia de Boletín/Transparencia, el registro sí muta — pero solo el campo `estado`

Es la pieza nueva que ningún ADR previo cubría: Boletín y Transparencia
declaran explícitamente que un registro publicado no se edita ni se borra
("se corrige publicando uno nuevo"). Obras Públicas necesita lo opuesto
para tener sentido como *seguimiento*: una obra "en curso" que nunca puede
pasar a "en ejecución" o "finalizada" no aporta más que un anuncio
estático, que Boletín ya cubre.

Se acota la mutabilidad al mínimo que resuelve esto: solo el campo
`estado` cambia después del alta, exclusivamente vía `PATCH
/api/obras/{id}/estado` con la tabla de transiciones de la Decisión 3. El
resto de los datos cargados al crear (`nombre`, `tipo`, `ubicacion`,
`descripcion`, fechas estimadas) no se edita en esta rebanada — mismo
criterio de Boletín para todo lo que no sea el estado: si el municipio se
equivocó al cargar una obra, la corrige dando de alta un registro nuevo,
no mutando el viejo.

### 5. Permiso único `obras.gestionar`, asignado a `administrador` y `agente`

Un solo permiso cubre alta y actualización de estado. A diferencia de
Multas ([ADR 0021](0021-multas-de-transito-alta-protegida-estado-propio-descuento-por-pago-temprano.md)
§3/§4, que separa `multas.labrar` de `multas.resolverDescargo` por una
diferencia real de sensibilidad fiscal/cuasi-judicial), acá no hay una
acción con impacto fiscal ni discrecional que amerite reservarla aparte:
registrar una obra nueva y actualizar su estado de avance son la misma
clase de trabajo operativo de seguimiento — el catálogo funcional
describe Obras Públicas como "seguimiento de obra, certificaciones de
avance, inspecciones", tarea de campo, no de gabinete.

Se asigna a **ambos** roles de sistema, mismo criterio que
`reclamos.gestionar` y `multas.labrar` (no `boletin.publicar`, reservado
solo a administrador por ser acto legal/institucional): es trabajo
operativo cotidiano de quien hace seguimiento de obra en el municipio, no
un acto de mayor confianza que justifique reservarlo. Un municipio que
quiera restringirlo más puede componer su propio rol
([ADR 0011](0011-autorizacion-por-roles-con-permisos-granulares.md)); el
seed de sistema no lo anticipa sin un caso real.

### 6. Sin geolocalización estructurada ni GIS

`ubicacion` es texto libre, igual que `direccion` en Reclamos
([ADR 0014](0014-reclamos-ciudadanos-alta-publica-anonima-y-estado-propio.md)
§5). Reafirma, no repite, la misma decisión: GIS como servicio consolidado
sigue sin existir en el producto (Decisión descartada en el Contexto), así
que acoplar esta rebanada a una decisión de GIS que no existe la
sobredimensiona. Se difiere hasta que GIS como servicio exista o un
tercer módulo lo justifique antes.

### 7. Sin certificaciones de avance, montos ni contratista

El catálogo funcional describe Obras Públicas con "certificaciones de
avance" e "inspecciones"; esta rebanada no las incluye a propósito: una
certificación de avance es, en esencia, un dato de ejecución
presupuestaria (cuánto se pagó de una obra a medida que avanza), y eso es
exactamente el tipo de dato que Presupuesto y Contabilidad diferido
([ADR 0021](0021-multas-de-transito-alta-protegida-estado-propio-descuento-por-pago-temprano.md),
Contexto) todavía no resolvió. Incluir monto o contratista acá sería
inventar una forma de vincular obra con presupuesto sin que el módulo de
presupuesto exista. El registro de esta rebanada es puramente informativo
(qué obra, dónde, en qué estado), sin dato financiero.

### 8. Sin adjuntos/fotos

Mismo criterio que Boletín ([backlog](../../producto/backlog-inicial.md),
R7): el contenido vive como texto en la base, no hay carga de archivos en
esta rebanada.

## Alternativas consideradas

- **Adoptar el patrón "no se edita, se corrige publicando de nuevo" de
  Boletín/Transparencia tal cual, sin estado mutable**: descartada — ver
  Decisión 4. Vacía el valor de "seguimiento de obra en curso" que motivó
  elegir este módulo en primer lugar.
- **Reutilizar el motor de expediente de Mesa de Entradas
  ([ADR 0015](0015-motor-de-expediente-workflow-minimo.md))**: descartada
  por los mismos dos motivos que ya usó
  [ADR 0021](0021-multas-de-transito-alta-protegida-estado-propio-descuento-por-pago-temprano.md)
  (alternativas): el motor vive en `mesaentradas.internal`, inalcanzable
  para otro módulo por decisión explícita; y una obra pública tiene un
  ciclo fijo igual para todos los municipios, no "circuitos propios de
  aprobación" que varíen por municipio, que es el problema que el motor
  configurable existe para resolver.
- **Permitir editar todos los campos del alta, no solo el estado**: más
  flexible, pero reabre exactamente el problema que Boletín/Transparencia
  evitaron a propósito (un registro público que cambia de contenido sin
  dejar rastro de qué decía antes). Descartada; si aparece una necesidad
  real de corregir nombre/ubicación después de publicada, es una decisión
  aparte con ese caso real delante.
- **Separar `obras.publicar` de `obras.actualizarEstado`** como dos
  permisos, mismo patrón que Multas: descartada por Decisión 5 — no hay
  una diferencia real de sensibilidad entre las dos acciones que la
  justifique, a diferencia de Multas.
- **Alta pública (el vecino reporta una obra o su avance)**: descartada.
  El registro lo controla el municipio porque es información oficial de
  gestión (qué obra existe y en qué estado la declara el municipio), no un
  reporte ciudadano como Reclamos — mismo criterio que Multas ADR 0021 §3
  para por qué el alta la origina el municipio y no el vecino.
- **Elegir Catastro, Planeamiento Urbano, Ambiente y Servicios Públicos o
  GIS como primera rebanada de Fase 4**: ver Contexto.

## Consecuencias

- `obras` no depende de ningún otro módulo funcional; el test de
  modularidad de Spring Modulith lo verifica en el build.
- No hay ninguna ruta pública de escritura en este módulo (a diferencia de
  Reclamos y Multas): el costo de abuso de escritura pública que esos ADRs
  ya aceptan y difieren no aplica acá.
- El registro no lleva quién hizo cada cambio de estado (solo
  `actualizado_en`), a diferencia de `publicado_por_nombre`/
  `publicado_por_email` que sí se capturan en el alta — ver Pendiente de
  definir.
- Si en el futuro Ambiente y Servicios Públicos (o cualquier otro módulo)
  necesita el mismo patrón "alta protegida + lectura pública + estado
  propio mutable", se decide en ese momento si vale la pena extraer algo
  común con `obras`, con ese segundo caso real delante — no se anticipa
  acá, mismo criterio que ADR 0015 §5 y ADR 0021 §2 ya aplicaron.

## Pendiente de definir

- Certificaciones de avance, montos y contratista (Decisión 7): depende de
  que exista Presupuesto y Contabilidad o un municipio piloto real que
  defina cómo vincular obra y ejecución presupuestaria.
- Geolocalización estructurada / GIS como servicio (Decisión 6): depende
  de que un segundo o tercer módulo (Reclamos, Ambiente) lo justifique
  antes, o de que el propio roadmap lo priorice.
- Edición de los campos del alta (nombre, tipo, ubicación, fechas,
  descripción) después de creada la obra (Decisión 4).
- Quién hizo cada cambio de estado y cuándo, más allá de
  `actualizado_en` (sin historial de movimientos en esta rebanada).
- Adjuntos/fotos de avance de obra (Decisión 8).
- Notificación al vecino de obras cercanas a su domicilio o que afectan
  una dirección puntual: requiere el motor de notificaciones
  ([ADR 0013](0013-persistencia-de-eventos-y-mecanismo-transversal-de-notificaciones-y-auditoria.md))
  y, probablemente, geolocalización estructurada.
- Rate limiting sobre las rutas de `obras` (endurecimiento de seguridad
  diferido por [CLAUDE.md](../../../CLAUDE.md), aunque acá no hay ruta
  pública de escritura para abusar).
- Catastro, Planeamiento Urbano/Uso del Suelo y Ambiente y Servicios
  Públicos: siguientes candidatas de Fase 4, ninguna resuelta por esta
  ADR (ver Contexto).
