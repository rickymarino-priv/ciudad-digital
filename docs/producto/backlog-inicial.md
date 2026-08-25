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
