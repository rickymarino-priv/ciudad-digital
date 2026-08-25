# Backlog inicial

Estructura de Epics e historias, cargada en Jira en el proyecto `CD`
([ciudad-digital](https://rickymarino.atlassian.net/browse/CD-1)). Los
Epics siguen las fases del [roadmap](roadmap-fases.md). Solo la Fase 0
está detallada: el resto son Epics contenedores, a detallar cuando se
diseñe cada fase.

## Mapeo a Jira

| Ticket | Trabajo |
| --- | --- |
| CD-1 | Epic Fase 0 — Fundación de plataforma |
| CD-2 | Epic Fase 1 — MVP vendible / módulos ancla |
| CD-3 | Epic Fase 2 — Recaudación e integración |
| CD-4 | Epic Fase 3 — Compras y áreas normativamente pesadas |
| CD-5 | Epic Fase 4 — Gestión territorial |
| CD-6 | Epic Fase 5 — Áreas sociales |
| CD-7 | Epic Fase 6 — Áreas de imagen y control de gestión |
| CD-8 | Epic Fase 7 — Inteligencia artificial |
| CD-9 | R1 · Dos municipios, dos marcas — **terminada** |
| CD-10 | R2 · Un municipio se da de alta desde cero — **terminada** |
| CD-11 | R3 · Un usuario entra a su municipio — **terminada** |
| CD-12 | R4 · Un módulo se prende y se apaga — **terminada** |
| CD-13 | R5 · Algo pasa y queda registrado — **terminada** |
| CD-14 | R6 · Un vecino carga un reclamo y el municipio lo atiende — **terminada** |
| CD-15 | R7 · El municipio publica una norma en el Boletín Oficial y cualquiera la encuentra |
| CD-16 | R8 · Un vecino busca dónde está sepultado un familiar y el municipio administra el registro del cementerio |
| CD-17 | R9 · Un vecino inicia un trámite en Mesa de Entradas y el municipio lo tramita — **terminada** |
| CD-18 | R10 · Mesa de Entradas suma habilitación comercial simple y permiso de obra menor |
| CD-19 | R11 · Transparencia activa básica: presupuesto y escala salarial públicos |
| CD-20 | R12 · Un vecino sin sesión consulta el estado de su reclamo o trámite con el token que recibió al cargarlo |

La Fase 0 está organizada en **rebanadas verticales demostrables**, según
la regla del proyecto (ver [CLAUDE.md](../../CLAUDE.md)): cada rebanada
atraviesa todas las capas necesarias y termina en algo que se puede ver
funcionando. Las piezas técnicas figuran dentro de cada rebanada, no como
tickets propios.

Recordatorio de lo que **viaja dentro** de cada rebanada y no se difiere:
tests de aislamiento entre tenants (si toca datos) y accesibilidad WCAG
(si agrega pantallas).

---

## Epic: Fase 0 — Fundación de plataforma

Base técnica multi-tenant sobre la que se construyen todos los módulos
funcionales. No es vendible por sí sola. Diseño técnico completo en
[diseño de Fase 0](../arquitectura/diseno-fase-0.md).

### R1 · Dos municipios, dos marcas

**Demo**: entrar a `sanmartin.localhost` y a `moron.localhost` y ver el
mismo portal con logo, colores y nombre distintos en cada uno.

Incluye:
- Proyecto backend con Spring Boot + Spring Modulith, con verificación de
  límites entre módulos corriendo en el build
  ([ADR 0003](../arquitectura/decisiones/0003-spring-modulith-para-el-backend.md)).
- Base de control con la tabla de tenants y sus migraciones
  ([ADR 0007](../arquitectura/decisiones/0007-modelo-de-datos-del-tenant.md)).
- Resolución de tenant por header `Host`, con manejo de tenant inexistente
  o inactivo
  ([ADR 0004](../arquitectura/decisiones/0004-resolucion-de-tenant-por-subdominio.md)).
- Endpoint que sirve el tema del tenant resuelto.
- Proyecto frontend React con convenciones de organización definidas y
  librería de componentes accesibles elegida
  ([ADR 0008](../arquitectura/decisiones/0008-react-como-framework-de-frontend.md)).
- Aplicación de tokens de tema en runtime
  ([ADR 0006](../arquitectura/decisiones/0006-theming-dinamico-por-tokens.md)).
- Tenants sembrados por migración (todavía sin alta automatizada).

### R2 · Un municipio se da de alta desde cero

**Demo**: dar de alta un municipio nuevo por API, y verlo funcionando en
su subdominio con datos guardados en **su propia** base.

Incluye:
- Módulo de administración de tenants con el proceso de alta y sus
  estados: `pendiente → aprovisionando → activo → error`
  ([ADR 0005](../arquitectura/decisiones/0005-aprovisionamiento-de-tenant.md)).
- Creación de la base física del tenant, migraciones y seed inicial.
- Routing dinámico de datasource por tenant resuelto, con estrategia de
  pooling
  ([ADR 0001](../arquitectura/decisiones/0001-multi-tenant-con-bd-por-tenant.md)).
- Ejecución de migraciones contra las N bases con reporte de estado por
  tenant.
- **Test de aislamiento**: datos escritos en un tenant no son visibles
  desde otro.

### R3 · Un usuario entra a su municipio

**Demo**: iniciar sesión en dos municipios distintos con usuarios
distintos, y verificar que un usuario de un municipio no puede acceder al
otro.

Incluye:
- Autenticación y sesión scopeada por tenant.
- Modelo de roles y permisos granulares por área y módulo.
- Pantalla de login accesible.
- **Test de aislamiento**: credenciales de un tenant no sirven en otro.

### R4 · Un módulo se prende y se apaga

**Demo**: activar y desactivar un módulo para un municipio y ver que
desaparece de la navegación y que la API lo rechaza.

Incluye:
- Entitlement leído de la lista de módulos habilitados en `config`
  ([ADR 0007](../arquitectura/decisiones/0007-modelo-de-datos-del-tenant.md)).
- Interceptor de gating en backend que rechaza requests a módulos no
  contratados
  ([ADR 0009](../arquitectura/decisiones/0009-modelo-comercial-y-entitlement.md)),
  con el mecanismo de declaración de módulos y gating por prefijo de ruta
  del [ADR 0012](../arquitectura/decisiones/0012-declaracion-de-modulos-y-gating-por-ruta.md).
- Navegación del frontend construida a partir de los módulos habilitados.
- Módulo `ejemplo` como sujeto de prueba del mecanismo: no es funcionalidad
  de producto y se elimina cuando el primer módulo real de Fase 1 pueda
  ocupar su lugar.
- **Test de aislamiento**: un municipio no lee ni modifica los módulos
  contratados de otro, y prender un módulo en uno no lo prende en el resto.

### R5 · Algo pasa y queda registrado

**Demo**: ejecutar una acción en el sistema, recibir la notificación por
email y ver el registro de auditoría correspondiente.

Incluye:
- Motor de notificaciones multicanal, con email como primer canal.
- Auditoría transversal: quién hizo qué, cuándo y sobre qué.

### Movido a Fase 1

El **motor de expediente/workflow configurable** y el **framework de
reportes/BI** salen de la Fase 0: no tienen consumidor todavía y no se
pueden construir como rebanada demostrable. Construirlos sin un módulo
real que los use es diseñar a ciegas. Se construyen en Fase 1, junto con
el primer módulo funcional que efectivamente los necesita.

---

## Epic: Fase 1 — MVP vendible / módulos ancla

Reclamos ciudadanos (311), Mesa de Entradas + subset de Trámites a
Distancia, Boletín Oficial digital, Transparencia activa básica,
Cementerio. Bajo acoplamiento con sistemas legados: no depende de la capa
de adaptadores.

Incluye además el motor de expediente/workflow y el framework de
reportes/BI, construidos junto al primer módulo que los consume — que
**no** es Reclamos (R6): su ciclo de estado es fijo e igual para todos los
municipios, no necesita circuitos configurables por área
([ADR 0014](../arquitectura/decisiones/0014-reclamos-ciudadanos-alta-publica-anonima-y-estado-propio.md)
§3). El motor sigue pendiente hasta que aparezca el primer módulo que sí
lo necesite (candidato: Mesa de Entradas).

Reclamos ciudadanos (311) es el módulo ancla que abre la fase, por ser el
primero listado en el roadmap, de complejidad baja-media y alto impacto,
y por ser el candidato natural para ocupar el lugar de `ejemplo` (R4)
como sujeto real de los tests de gating (ADR 0012 §10). R6 **no** retira
`ejemplo` ni migra `EntitlementDeModulosTest` a `reclamos`: ver "Queda
fuera de R6", más abajo.

### R6 · Un vecino carga un reclamo y el municipio lo atiende

**Demo**: un vecino anónimo carga un reclamo (bache, alumbrado, poda,
residuos, animales sueltos) desde el portal público de un municipio, sin
iniciar sesión. Un agente de ese municipio inicia sesión, ve el reclamo
entrar en el panel de reclamos, lo pasa a "en proceso" y después a
"resuelto". El mismo reclamo no aparece en el portal del otro municipio.

Incluye:
- Módulo `reclamos`, contratable por municipio, con alta anónima vía
  `POST /api/reclamos` sin sesión
  ([ADR 0014](../arquitectura/decisiones/0014-reclamos-ciudadanos-alta-publica-anonima-y-estado-propio.md)
  §1) y listado/detalle/cambio de estado protegidos por sesión y permiso
  (`reclamos.ver`, `reclamos.gestionar`).
- Categoría fija (bache, alumbrado, poda/arbolado, residuos, animales
  sueltos, otro), descripción, dirección en texto libre y datos de
  contacto opcionales del vecino.
- Estado con transiciones fijas (nuevo → en proceso → resuelto/
  rechazado), sin motor de workflow configurable (ADR 0014 §3).
- Pantalla pública de alta (accesible, sin cuenta) y panel de gestión
  para el personal del municipio.
- **Test de aislamiento**: un reclamo cargado en un municipio no es
  visible ni editable desde otro.

Queda fuera de R6, explícitamente diferido (ADR 0014): geolocalización
estructurada, seguimiento del reclamo por el vecino anónimo, integración
con notificaciones/auditoría transversal, asignación a un agente
particular, y rate limiting sobre el alta pública.

También queda fuera de R6, como tarea de limpieza aparte que no cambia
comportamiento de producto: **retirar el módulo `ejemplo`** (backend,
frontend, migración de baja de su permiso) y migrar
`EntitlementDeModulosTest` para que use `reclamos` como sujeto del test
del mecanismo de gating en lugar de `ejemplo`. Mantenerla separada evita
que R6 mezcle una rebanada de producto con una tarea de housekeeping sin
valor de demo propio; `ejemplo` sigue activo y sin tocar hasta que esa
tarea se haga. Con R7 sumando un segundo módulo real, esta limpieza pierde
aún más urgencia (el mecanismo ya tiene sujetos de prueba reales de sobra)
y sigue sin programarse.

### R7 · El municipio publica una norma en el Boletín Oficial y cualquiera la encuentra

**Demo**: un agente del municipio con el permiso `boletin.publicar` inicia
sesión y publica una norma (ordenanza, decreto, resolución o comunicado)
con tipo, número, título, fecha y texto. Un vecino, sin sesión, entra al
portal público, filtra por tipo y busca por texto en el título, y
encuentra esa norma. La misma norma no aparece en el portal de otro
municipio.

No requiere ADR nuevo: la lectura pública (`GET /api/boletin`) reutiliza
tal cual el mecanismo `rutasDeLecturaPublica()` de
[ADR 0012](../arquitectura/decisiones/0012-declaracion-de-modulos-y-gating-por-ruta.md)
§1 —el mismo que ya usa `ejemplo` para su ping—, y los permisos siguen el
modelo de [ADR 0011](../arquitectura/decisiones/0011-autorizacion-por-roles-con-permisos-granulares.md)
sin extenderlo. Es, a propósito, el complemento de R6: ahí la escritura
era pública y la lectura protegida; acá la lectura es pública y la
escritura protegida — las dos combinaciones ya cubiertas por los ADRs
existentes, sin mecanismo nuevo.

Incluye:
- Módulo `boletin`, contratable por municipio. Publicar (`POST
  /api/boletin`) requiere sesión y el permiso `boletin.publicar`, asignado
  solo a `administrador` (no a `agente`): publicar una norma es un acto
  legal del municipio, de mayor confianza que gestionar un reclamo —más
  cerca, en sensibilidad, de administrar usuarios/roles que de operar el
  día a día—. Listar (`GET /api/boletin`, con filtro opcional por `tipo`
  y por texto en el título) es público, sin sesión: es exactamente lo que
  "Boletín Oficial digital" significa.
- Tipo fijo (ordenanza, decreto, resolución, comunicado), número (texto
  libre que asigna el municipio: la numeración oficial correlativa es un
  proceso legal fuera del alcance de esta rebanada, no se genera sola),
  título, fecha de publicación, texto completo. Una vez publicada, una
  norma no se edita ni se borra por esta rebanada: es un registro público
  que se corrige publicando una norma nueva, no mutando la vieja
  (coherente con diferir el versionado/derogación, más abajo).
- Nombre y correo de quien publicó, capturados del actor autenticado al
  momento de publicar (mismo criterio de "copia, no referencia" que ya usa
  `registro_auditoria`, ADR 0013) — es la firma pública de la norma, no
  una relación que haya que mantener viva.
- Búsqueda simple: filtro por tipo y coincidencia de texto en el título
  (`ILIKE`), sin motor de búsqueda full-text.
- Pantalla pública de búsqueda/listado (accesible, sin cuenta) con la
  acción de publicar visible solo para quien tiene `boletin.publicar`.
- **Test de aislamiento**: una norma publicada en un municipio no es
  visible desde otro.

Queda fuera de R7, explícitamente diferido: adjuntos/archivos (el texto
vive como contenido en la base, no como PDF), motor de búsqueda
full-text, numeración correlativa automática, edición/derogación/
versionado de una norma ya publicada, notificaciones de nuevas
publicaciones, y —igual que en R6— cualquier integración con
auditoría/notificaciones transversal más allá de lo que ya cubre este
propio módulo.

### R8 · Un vecino busca dónde está sepultado un familiar y el municipio administra el registro del cementerio

**Demo**: un vecino, sin sesión, busca en el portal público de un
municipio el nombre de un familiar fallecido y encuentra dónde está
sepultado (tipo de parcela, sector, fila, número). Un agente municipal,
con sesión y el permiso `cementerio.registrar`, carga un nuevo registro
de inhumación. El registro aparece de inmediato en la búsqueda pública.
El mismo registro no aparece en el portal de otro municipio.

De los tres candidatos que quedaban de Fase 1 (Mesa de Entradas + subset
de Trámites a Distancia, Transparencia activa básica, Cementerio), R8
elige **Cementerio**: el [catálogo funcional](catalogo-funcional.md) lo
califica de "complejidad baja-media, buen módulo chico con valor real",
mientras que Mesa de Entradas es "complejidad alta, columna vertebral del
sistema" y cada trámite de Trámites a Distancia "tiene su propio circuito
y requisitos" — exactamente el problema que el motor de expediente/
workflow configurable existe para resolver, y que sigue sin construirse.
Forzar Mesa de Entradas a esta rebanada habría significado, otra vez,
diseñar el motor genérico a las apuradas sobre un caso que lo necesita de
verdad, o resolverlo con un campo de estado propio que le queda chico (a
diferencia de Reclamos, ADR 0014 §3, acá sí hay circuitos que varían por
tipo de trámite). Transparencia activa básica, por su parte, depende de
"si el municipio tiene los datos digitalizados"
([roadmap](roadmap-fases.md#fase-1--mvp-vendible--módulos-ancla)): la
forma de los datos de presupuesto/sueldos es un riesgo de producto que
todavía no tiene municipio piloto que lo valide, no algo que convenga
inventar en el vacío. El motor de expediente/workflow sigue pendiente;
Mesa de Entradas sigue como su candidato natural para cuando se construya.

No requiere ADR nuevo: es, otra vez, el mismo patrón que R7 (lectura
pública sin sesión + escritura protegida por sesión y permiso), cubierto
por [ADR 0011](../arquitectura/decisiones/0011-autorizacion-por-roles-con-permisos-granulares.md)
y [ADR 0012](../arquitectura/decisiones/0012-declaracion-de-modulos-y-gating-por-ruta.md)
§1 sin extenderlos. A diferencia de Reclamos, ni siquiera hay estado ni
transiciones: registrar una sepultura es un alta y listo, más simple
todavía.

Incluye:
- Módulo `cementerio`, contratable por municipio. Registrar (`POST
  /api/cementerio`) requiere sesión y el permiso `cementerio.registrar`,
  asignado a **ambos** roles de sistema (administrador y agente): es
  funcionalidad operativa real del personal del cementerio desde el día
  uno, mismo criterio que `reclamos.gestionar` (ADR 0014 §8), no un acto
  legal como `boletin.publicar`. Buscar (`GET /api/cementerio`, con
  filtro opcional por tipo de parcela y por nombre del difunto) es
  público, sin sesión.
- Un registro de sepultura por inhumación: tipo de parcela (nicho,
  panteón, parcela, bóveda), sector, fila (opcional), número, nombre del
  difunto, fecha de fallecimiento, fecha de inhumación, y —privados,
  fuera de la búsqueda pública— nombre y contacto del titular de la
  concesión y observaciones. Sin motor de workflow ni entidad de parcela
  separada: modelo flat, mismo criterio que ADR 0014 §3 usa para
  Reclamos, aplicado acá de entrada porque tampoco hace falta un segundo
  caso real que lo justifique.
- La búsqueda pública devuelve un DTO reducido, sin los datos privados
  del titular ni de quien registró el alta: minimización de datos de
  terceros, no un descuido — el titular/contacto/observaciones solo se
  ven en la respuesta del alta, a quien la acaba de cargar.
- Pantalla pública de búsqueda (accesible, sin cuenta) con la acción de
  registrar visible solo para quien tiene `cementerio.registrar`, mismo
  patrón de UI que `boletin` (R7): una única vista, no dos pantallas
  alternativas.
- **Test de aislamiento**: una sepultura registrada en un municipio no es
  visible desde otro.

Queda fuera de R8, explícitamente diferido: gestión de concesiones
(renovación, transferencia de titularidad, vencimiento), más de un
registro de inhumación por parcela a lo largo del tiempo (no hay entidad
de parcela normalizada todavía), un panel protegido con los datos
completos más allá del momento del alta, edición/borrado de un registro
ya cargado, adjuntos/documentación, geolocalización del cementerio o de
las parcelas, y —igual que en R6/R7— cualquier integración con
auditoría/notificaciones transversal.

### R9 · Un vecino inicia un trámite en Mesa de Entradas y el municipio lo tramita

**Demo**: un vecino, sin sesión, entra al portal público de un municipio e
inicia un trámite de certificado de domicilio, indicando su nombre y el
domicilio a certificar. Un agente de Mesa de Entradas, con sesión y el
permiso `mesaentradas.gestionar`, inicia sesión, ve el trámite entrar a la
cola en estado "Iniciado", lo pasa a "En revisión" y después a "Aprobado"
(o "Rechazado"), quedando registrado en cada paso quién lo hizo y cuándo.
El mismo trámite no aparece en el portal de otro municipio.

R9 arranca Mesa de Entradas — el módulo ancla que el
[roadmap](roadmap-fases.md#fase-1--mvp-vendible--módulos-ancla) describe
como "columna vertebral del sistema"— y con él el **motor de
expediente/workflow configurable**, movido de Fase 0 a Fase 1 desde que la
Fase 0 se organizó (ver ["Movido a Fase 1"](#movido-a-fase-1), más arriba)
y señalado como pendiente por R6 (ADR 0014 §3) y R8. Es la primera
rebanada que sí lo necesita: a diferencia de Reclamos, Mesa de Entradas
tiene más de un tipo de trámite en el roadmap (certificados, habilitación
comercial simple, permiso de obra menor), cada uno con su propio circuito
de estados.

Requiere ADR nuevo: [ADR 0015](../arquitectura/decisiones/0015-motor-de-expediente-workflow-minimo.md)
decide la forma mínima del motor — circuito fijo **por tipo de trámite**,
definido en código y catálogo de producto, no editable por el municipio
(la ambición completa de "cada municipio con sus propios pasos" queda
pendiente hasta que un caso real la pida) — y dos entidades,
`Expediente`/`MovimientoDeExpediente`, en vez de un módulo transversal
nuevo del que otros módulos dependan.

De los 3-5 trámites que el roadmap menciona como subset de Trámites a
Distancia, R9 construye **uno solo**: certificado de domicilio, el más
simple de los tres nombrados. Habilitación comercial simple y permiso de
obra menor quedan para una rebanada siguiente que sume su propio
`TipoDeTramite` y `CircuitoDeTramite` sin tocar el motor (ADR 0015,
Consecuencias) — partir así, en vez de construir los tres tipos de una,
es lo que mantiene esta rebanada demostrable en una semana (regla de
CLAUDE.md).

El alta del trámite reutiliza tal cual el mecanismo de escritura pública
anónima del [ADR 0014](../arquitectura/decisiones/0014-reclamos-ciudadanos-alta-publica-anonima-y-estado-propio.md)
§1 (`rutasDeEscrituraPublica()`): el producto no tiene identidad ciudadana
todavía, así que iniciar un trámite no puede exigir cuenta. Gestionarlo
—listar y avanzar el estado— requiere sesión y permiso, mismo patrón que
`reclamos.ver`/`reclamos.gestionar`.

Incluye:
- Módulo `mesaentradas`, contratable por municipio, con alta anónima vía
  `POST /api/mesaentradas` sin sesión y listado/avance de estado
  protegidos por sesión y permiso (`mesaentradas.ver`,
  `mesaentradas.gestionar`, en ambos roles de sistema — funcionalidad
  operativa real desde el día uno, mismo criterio que `reclamos`/
  `cementerio`).
- Un único tipo de trámite (`CERTIFICADO_DOMICILIO`) con su circuito fijo:
  `INICIADO → EN_REVISION → APROBADO/RECHAZADO`.
- `MovimientoDeExpediente`: una fila por cada cambio de estado, con quién
  lo hizo (actor autenticado, copia de nombre/email, `null` en el
  movimiento de alta porque es anónima) y cuándo.
- Pantalla pública de alta (accesible, sin cuenta) y panel de gestión para
  el personal de Mesa de Entradas, mismo patrón de "una pantalla, dos
  vistas según permiso" que ya usa `reclamos`.
- **Test de aislamiento**: un expediente iniciado en un municipio no es
  visible ni gestionable desde otro.

Queda fuera de R9, explícitamente diferido (ADR 0015): los demás tipos de
trámite del subset (habilitación comercial simple, permiso de obra
menor), circuitos configurables por municipio, seguimiento del trámite
por el vecino anónimo con un token (mismo pendiente que ADR 0014 ya dejó
para reclamos), giro entre áreas/derivación, caratulación y numeración
correlativa oficial, generación del documento del certificado y firma
electrónica, notificaciones al vecino de cambios de estado, y —igual que
R6/R7/R8— cualquier integración con auditoría/notificaciones transversal
más allá de lo que este propio módulo cubre.

### R10 · Mesa de Entradas suma habilitación comercial simple y permiso de obra menor

**Demo**: un vecino, sin sesión, entra al portal público de un municipio e
inicia cualquiera de los tres trámites de Mesa de Entradas —certificado de
domicilio, habilitación comercial simple o permiso de obra menor—,
completando los campos propios de cada uno. Un agente de Mesa de Entradas,
con sesión y el permiso `mesaentradas.gestionar`, ve cada trámite entrar a
la cola y lo avanza por el circuito de estados que corresponde a su tipo
—habilitación comercial simple pasa por un paso extra de inspección antes
de aprobarse o rechazarse, los otros dos van directo de "en revisión" a un
estado final—, quedando registrado en cada paso quién lo hizo y cuándo. El
mismo trámite no aparece en el portal de otro municipio.

R10 completa el subset de Trámites a Distancia que el
[roadmap](roadmap-fases.md#fase-1--mvp-vendible--módulos-ancla) nombra
para Fase 1 (certificado de domicilio + estos dos), sumando el segundo y
tercer tipo de trámite al motor de expediente/workflow mínimo que R9
(ADR 0015) dejó armado para esto. Confirma la premisa de ADR 0015: agregar
un tipo de trámite —incluso uno con un paso adicional de circuito, como la
inspección de habilitación comercial— es código y migración dentro de
`mesaentradas`, sin tocar `Expediente`/`MovimientoDeExpediente`/
`GestionDeExpedientes.avanzar`.

Requiere ADR nuevo: [ADR 0016](../arquitectura/decisiones/0016-datos-propios-por-tipo-de-tramite-columnas-explicitas.md)
resuelve el pendiente que ADR 0015 §3 había dejado abierto ("forma de los
datos propios de un tipo de trámite cuando aparezca el segundo tipo
real"): columnas explícitas y nullable en la propia tabla `expediente`,
con un `check` de base de datos que las exige solo para el tipo que las
usa — no JSON, no tabla propia por tipo, con 3 tipos y 1-2 campos propios
cada uno delante.

Incluye:
- `TipoDeTramite.HABILITACION_COMERCIAL_SIMPLE`, con campos propios `rubro
  comercial` y `dirección del local`, y su circuito propio: `INICIADO →
  EN_REVISION → {INSPECCION, RECHAZADO}`, `INSPECCION → {APROBADO,
  RECHAZADO}` — el nuevo estado `INSPECCION` se suma al enum común
  `EstadoDeExpediente` (ADR 0015 §1: el enum es compartido, cada
  `CircuitoDeTramite` decide cuáles usa).
- `TipoDeTramite.PERMISO_OBRA_MENOR`, con campos propios `dirección de la
  obra` y `descripción de la obra`, y el mismo circuito que certificado de
  domicilio: `INICIADO → EN_REVISION → APROBADO/RECHAZADO`.
- El formulario público de alta deja elegir el tipo de trámite y muestra
  los campos propios de cada uno; el panel de gestión muestra el tipo y el
  detalle propio de cada trámite en su fila, con el circuito de estados
  correcto según el tipo de cada expediente.
- **Test de aislamiento**: extendido (no duplicado) para probar que
  también los campos propios de los tipos nuevos quedan aislados por
  tenant.

Queda fuera de R10, explícitamente diferido (igual criterio que R9,
ADR 0015): circuitos configurables por municipio, seguimiento anónimo del
trámite por token, giro entre áreas/derivación, caratulación y numeración
correlativa oficial, generación de documentos y firma electrónica,
notificaciones al vecino de cambios de estado, y cualquier integración con
auditoría/notificaciones transversal más allá de lo que el propio módulo
cubre.

### R11 · Transparencia activa básica: presupuesto y escala salarial públicos

**Demo**: un agente del municipio con sesión y el permiso
`transparencia.publicar` publica una partida presupuestaria (año, área,
número, concepto, monto asignado y, opcionalmente, monto ejecutado) y una
entrada de escala salarial (año, área, cargo/función, cantidad de cargos y
monto bruto mensual). Un vecino, sin sesión, entra al portal público de
ese municipio, filtra por año y por texto, y encuentra ambos registros. El
mismo dato no aparece en el portal de otro municipio.

R11 cierra la lista original de candidatos de Fase 1
([roadmap](roadmap-fases.md#fase-1--mvp-vendible--módulos-ancla)): con
R6-R10 ya construidos, Transparencia activa básica es el único que
quedaba sin construir, diferido en R8 porque dependía de "si el municipio
tiene los datos digitalizados" y de un municipio piloto real que todavía
no existe. Para esta primera rebanada se adopta el mismo criterio que ya
rige para todo el producto en esta etapa: los municipios de demostración
(`sanmartin`, `moron`, y los que arma cada test) se cargan con datos
**sembrados/de ejemplo** vía el propio endpoint de publicación, igual que
ya se demuestran `boletin` y `cementerio` — no hace falta esperar a un
piloto real para tener algo demostrable. La forma real que exija un
municipio piloto (otro layout de partidas, otro nomenclador) es un
problema para cuando ese piloto exista, no algo que convenga inventar en
el vacío ahora.

No requiere ADR nuevo: mismo patrón que R7/R8, ya cubierto por
[ADR 0011](../arquitectura/decisiones/0011-autorizacion-por-roles-con-permisos-granulares.md)
(permisos) y
[ADR 0012](../arquitectura/decisiones/0012-declaracion-de-modulos-y-gating-por-ruta.md)
§1 (`rutasDeLecturaPublica()`): lectura pública sin sesión, escritura
protegida por sesión y permiso. Sin estado ni transiciones, igual que
`cementerio` (R8): publicar un dato de transparencia es un alta y listo.

Incluye:
- Módulo `transparencia`, contratable por municipio, con dos recursos
  independientes bajo el mismo prefijo de API: **presupuesto**
  (`POST`/`GET /api/transparencia/presupuesto`) y **sueldos**
  (`POST`/`GET /api/transparencia/sueldos`). Publicar cualquiera de los
  dos requiere sesión y el permiso `transparencia.publicar`, asignado
  **solo a `administrador`** (no a `agente`): publicar cifras oficiales de
  presupuesto o de masa salarial es un acto de transparencia institucional
  del municipio, mismo criterio de sensibilidad que `boletin.publicar`
  (R7) — más cerca de un acto legal/institucional que de la operación
  diaria de `reclamos`/`cementerio`. Buscar es público, sin sesión, en
  ambos recursos.
- Partida presupuestaria: año, área/dependencia, número de partida (texto
  libre que asigna el municipio, mismo criterio que el número de norma en
  `boletin`), concepto, monto asignado y monto ejecutado (opcional: no
  todos los municipios lo llevan al día). Una vez publicada, no se edita
  ni se borra por esta rebanada — se corrige publicando un registro nuevo,
  mismo criterio que `boletin`.
- Entrada de escala salarial: año, área/dependencia, cargo o función,
  cantidad de cargos que ocupan esa posición y monto bruto mensual **por
  cargo**, no por persona. **Decisión deliberada de minimización de
  datos**: esta rebanada publica sueldos agregados por cargo/función
  (cuántos cargos hay y cuánto gana cada uno en bruto), nunca un monto
  vinculado al nombre de una persona concreta — ni siquiera de
  funcionarios políticos, cuyo nombre público sí requeriría una decisión
  de producto propia (qué funcionarios se nombran, con qué respaldo legal
  por provincia) que no conviene tomar en esta primera rebanada sin un
  municipio real que la valide. Mismo criterio de minimización que ya usó
  `cementerio` (R8) con el titular/contacto de la concesión, aplicado acá
  a que el dato salarial nunca se registra vinculado a un nombre de
  persona en la base, no solo que se oculte en la respuesta.
- Quien publica cada registro queda identificado en la respuesta
  (`publicadoPorNombre`/`publicadoPorEmail`, copia del actor autenticado
  al momento de publicar, mismo criterio "copia, no referencia" que
  `registro_auditoria`/`norma`, ADR 0013): es la firma pública del acto
  administrativo de publicar el dato, no un dato de un tercero — mismo
  criterio que `boletin`, a diferencia de `cementerio` donde sí se oculta
  en la respuesta pública.
- Búsqueda simple en ambos recursos: filtro por año (exacto) y texto libre
  en área/concepto (presupuesto) o área/cargo (sueldos), vía `ILIKE`, sin
  motor de búsqueda full-text.
- Pantalla pública única (accesible, sin cuenta) con dos secciones —
  Presupuesto y Sueldos— cada una con su propia búsqueda y, para quien
  tiene `transparencia.publicar`, su propia acción de publicar, mismo
  patrón de "una pantalla, vistas según permiso" que `boletin`/`cementerio`.
- **Test de aislamiento**: un dato de presupuesto o de escala salarial
  publicado en un municipio no es visible desde otro, para ambos recursos.

Queda fuera de R11, explícitamente diferido: sueldos vinculados a
funcionarios nombrados (ver la decisión de minimización, arriba),
ejecución presupuestaria detallada por partida a lo largo del año
(seguimiento temporal, gráficos), licitaciones abiertas y declaraciones
juradas (otros ítems que el catálogo agrupa bajo "Transparencia activa"
pero que son módulos propios, no esta rebanada), adjuntos/documentos
(presupuesto en PDF oficial), motor de búsqueda full-text, edición/borrado
de un registro ya publicado, y —igual que R6/R7/R8/R9/R10— cualquier
integración con auditoría/notificaciones transversal más allá de lo que
el propio módulo cubre.

### R12 · Un vecino sin sesión consulta el estado de su reclamo o trámite con el token que recibió al cargarlo

**Demo**: un vecino, sin sesión, carga un reclamo (o inicia un trámite de
Mesa de Entradas) y recibe, en la propia confirmación del alta, un código
largo y no adivinable que se le indica explícitamente guardar. Con ese
código, en una pantalla pública nueva, puede volver más tarde a consultar
en qué quedó: estado actual y, en Mesa de Entradas, el historial de
movimientos. El mismo código no sirve para ver el reclamo o trámite de
otro vecino, y un código de un municipio no tiene sentido probarlo contra
otro (cada base es la de su propio tenant).

R12 cierra el pendiente que dos rebanadas anteriores dejaron con el mismo
nombre y el mismo motivo: [ADR 0014](../arquitectura/decisiones/0014-reclamos-ciudadanos-alta-publica-anonima-y-estado-propio.md)
§6 (Reclamos, R6) y [ADR 0015](../arquitectura/decisiones/0015-motor-de-expediente-workflow-minimo.md)
§4 (Mesa de Entradas, R9) difirieron explícitamente el seguimiento anónimo
por token, señalando además que convenía resolverlo como mecanismo único
para ambos módulos en vez de uno por módulo. Con los dos módulos ya
construidos, R12 es el primer caso con dos consumidores reales y
simultáneos, así que no hace falta esperar a un tercero para justificar
compartir el mecanismo.

Requiere ADR nuevo: [ADR 0017](../arquitectura/decisiones/0017-seguimiento-anonimo-por-token-en-reclamos-y-mesa-de-entradas.md)
decide la forma del token —256 bits de `SecureRandom`, Base64 URL-safe,
guardado hasheado (SHA-256, nunca en claro)— y su alcance compartido: un
módulo canon base nuevo y chico, `seguimientoanonimo`, con una única
utilidad sin estado (generar/hashear) y sin persistencia ni entidades
propias; cada módulo sigue dueño de su propia columna `token_hash` y de
su propia consulta pública.

Incluye:
- Módulo `seguimientoanonimo`: `TokenDeSeguimiento.generar()` /
  `TokenDeSeguimiento.hash(...)`, sin Spring, sin tabla propia.
- `reclamo` y `expediente` ganan `token_hash` (not null, índice único).
  El alta pública (`POST /api/reclamos`, `POST /api/mesaentradas`, ya
  existentes) devuelve el token en claro una única vez, en la respuesta
  de esa llamada.
- `GET /api/reclamos/seguimiento/{token}` y
  `GET /api/mesaentradas/seguimiento/{token}`, públicos (declarados en
  `rutasDeLecturaPublica()` de cada módulo, ADR 0012 §1, sin tocar la
  cadena de seguridad compartida), con `404` genérico si el token no
  matchea ninguna fila.
- Respuesta de consulta minimizada por módulo (ADR 0017 §5): en
  `reclamos`, el mismo shape del alta más `comentarioGestion` y
  `actualizadoEn`; en `mesaentradas`, el mismo shape del alta más los
  campos propios del tipo de trámite, `actualizadoEn` y el historial
  `movimientos` sin el actor de cada movimiento. Ninguna de las dos
  expone datos que solo debería ver el municipio (contacto, quién
  gestionó cada paso).
- La pantalla de confirmación del alta, en ambos módulos, muestra el
  token y reemplaza el texto actual ("todavía no hay una pantalla para
  volver a consultarlo") por la instrucción de guardarlo. Pantalla
  pública nueva de consulta por token, accesible, sin cuenta.
- **Test de aislamiento**: un token válido de un municipio no encuentra
  nada en la base de otro municipio (la consulta corre contra el
  datasource del tenant resuelto, igual que el resto de cada módulo).

Queda fuera de R12, explícitamente diferido (ADR 0017): rate limiting
sobre el endpoint de consulta pública y sobre las altas en general,
reenvío del token por email/SMS al vecino, expiración del token, y
extender el mecanismo a otros módulos que todavía no lo necesitan.

## Epic: Fase 2 — Recaudación e integración

Tasas municipales + pago online, portal de proveedores, capa de adaptadores
a sistemas legados, consola del proveedor (comercial).

## Epic: Fase 3 — Compras y áreas normativamente pesadas

Compras y Contrataciones/Licitaciones, Presupuesto y Contabilidad,
Tesorería, Legal y Técnica/Juzgado de Faltas, Tránsito y Transporte,
consola del municipio.

## Epic: Fase 4 — Gestión territorial

Obras Públicas, Catastro, Planeamiento Urbano, Ambiente y Servicios
Públicos, GIS como servicio consolidado.

## Epic: Fase 5 — Áreas sociales

Desarrollo Social, Discapacidad, Salud municipal, Educación municipal.

## Epic: Fase 6 — Áreas de imagen / periféricas

Cultura/Turismo/Deportes, Prensa y Comunicación, Auditoría interna y
control de gestión.

## Epic: Fase 7 — Inteligencia artificial

Clasificador de reclamos (candidato a adelantarse a Fase 1), asistente
ciudadano con RAG, copiloto interno, optimización de rutas, detección de
anomalías en licitaciones.
