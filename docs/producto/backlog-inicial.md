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
| CD-20 | R12 · Un vecino sin sesión consulta el estado de su reclamo o trámite con el token que recibió al cargarlo — **terminada** |
| CD-21 | R13 · Un vecino paga una tasa municipal online |
| CD-22 | R14 · Una empresa se registra como proveedor del municipio y el municipio la aprueba |
| CD-23 | R15 · El proveedor de la plataforma ve y gestiona el contrato de sus municipios clientes |
| CD-24 | R16 · Un proveedor se registra y el municipio ve su situación fiscal validada contra AFIP (simulado) |
| CD-25 | R17 · Un agente de tránsito labra una multa y el vecino la paga con descuento, o la impugna (parent: CD-4) |
| CD-26 | R18 · El municipio ve su contrato y sus módulos, y pide un alta o baja (parent: CD-4) |
| CD-27 | R19 · El municipio registra una obra pública en curso y cualquiera ve su estado de avance (parent: CD-5) |
| CD-29 | R20 · El municipio registra un árbol urbano y cualquiera ve su estado sanitario (parent: CD-5) |
| CD-30 | R21 · Un vecino se inscribe a un programa social y el municipio evalúa su solicitud, sin exponerla públicamente (parent: CD-6) |
| CD-31 (placeholder, sin confirmar) | R22 · Un vecino reserva un turno para una actividad municipal con cupo limitado, y el municipio administra la agenda (parent: CD-7, sin confirmar) |
| CD-32 (placeholder, sin confirmar) | R23 · El municipio publica una gacetilla de prensa y cualquiera la encuentra (parent: CD-7, sin confirmar) |
| CD-33 (placeholder, sin confirmar) | R24 · El municipio registra una institución educativa municipal y cualquiera ve su estado (parent: CD-6, sin confirmar) |
| CD-34 (placeholder, sin confirmar) | R25 · El municipio registra un espacio verde y cualquiera ve su estado (parent: CD-5, sin confirmar) |
| CD-36 | Epic Sin fase fija — módulos sin prioridad de roadmap |
| CD-37 | R27 · El municipio publica una alerta de Defensa Civil y registra sus recursos de emergencia, y cualquiera los consulta (parent: CD-36) |
| CD-38 (placeholder, sin confirmar) | R28 · El municipio registra un comercio bromatológico y sus inspecciones, y cualquiera consulta el estado del padrón (parent: CD-36, sin confirmar) |
| CD-39 (placeholder, sin confirmar) | R29 · Framework de reportes/BI — motor mínimo y primer tablero real, con reclamos y mesaentradas como consumidores (parent: CD-1, sin confirmar) |

> Nota: falta en esta tabla la fila de R26 (rama `CD-35-...`, spec
> `specs/CD-35-agenda-eventos-cultura-turismo-deporte.md`), que quedó sin
> agregar en la rebanada que la implementó — no es un error de esta
> rebanada, se señala acá para que quien la corrija sepa que también falta
> esa fila, no solo la de R27.
>
> Nota: R20 y R21 se implementaron con las ramas `CD-28-...`/`CD-29-...`
> como placeholders, elegidos antes de saber el número real de ticket
> (el MCP de Jira estuvo bloqueado por un CAPTCHA al momento de
> implementarlas — ver memoria de proceso). Los números reales de Jira
> son los de esta tabla (CD-29 para R20, CD-30 para R21), no los que
> aparecen en el nombre de rama o en el nombre de archivo de sus specs.
> `CD-28` quedó como un Epic duplicado de CD-6, creado por error y
> cerrado sin uso — no lo uses como referencia.
>
> Nota: de R22 en adelante, el placeholder de rama/spec (elegido por
> continuidad numérica, sin acceso al MCP de Jira desde el worktree del
> tech-lead) coincidió en todos los casos con el número real de ticket
> confirmado desde la sesión principal — sin corrección necesaria.

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

El motor de expediente/workflow siguió sin consumidor hasta ahora
(`reclamos`/`mesaentradas` tienen ciclo de estado fijo, no configurable,
[ADR 0014](../arquitectura/decisiones/0014-reclamos-ciudadanos-alta-publica-anonima-y-estado-propio.md)
§3); el framework de reportes/BI se retomó recién en R29 (ver abajo),
cuando el producto ya tenía 15+ módulos funcionales con datos reales para
agregar — ver el descarte razonado de "Auditoría interna / Control de
gestión" en
[ADR 0026](../arquitectura/decisiones/0026-turnos-actividades-municipales-reserva-con-cupo-primera-rebanada-de-fase-6.md),
[ADR 0027](../arquitectura/decisiones/0027-prensa-y-comunicacion-gacetillas-segunda-rebanada-de-fase-6.md)
y
[ADR 0030](../arquitectura/decisiones/0030-agenda-de-eventos-cultura-turismo-y-deporte-tercera-rebanada-de-fase-6.md),
que señalaron tres veces seguidas que ese framework era el bloqueante real.

### R29 · El municipio ve un tablero con la cantidad de reclamos y de expedientes de Mesa de Entradas agrupados por estado

**Demo**: un administrador del municipio entra a Administración y ve la
sección "Reportes": una tabla "Reclamos por estado" y dos tablas más de
Mesa de Entradas ("Expedientes por tipo de trámite", "Expedientes por
estado"), con los conteos reales de ese municipio. Si el municipio no
tiene contratado `reclamos`, esa tabla no aparece; si tampoco tiene
`mesaentradas`, la sección avisa que no hay indicadores disponibles. Los
números de un municipio nunca aparecen en el tablero de otro.

Decisión de arquitectura completa en
[ADR 0033](../arquitectura/decisiones/0033-framework-de-reportes-bi-motor-de-metricas-agregadas-y-primer-tablero.md),
spec técnica en [spec CD-39](../../specs/CD-39-framework-de-reportes-bi.md).

Incluye:
- Módulo backend `reportes` (canon base, sin `DescriptorDeModulo`, no
  gateado por entitlement): interfaz pública `FuenteDeMetricas` (SPI) que
  un módulo funcional implementa para aportar indicadores agregados
  —consultas `group by` directas sobre su propia tabla, sin eventos de
  dominio nuevos—, recolectados por `reportes` sin que `reportes` conozca
  ni importe ningún módulo funcional (inversión de dependencia, mismo
  patrón que `entitlement.DescriptorDeModulo`,
  [ADR 0012](../arquitectura/decisiones/0012-declaracion-de-modulos-y-gating-por-ruta.md)
  §2).
- Primeros dos consumidores reales de la SPI: `reclamos` ("Reclamos por
  estado") y `mesaentradas` ("Expedientes por tipo de trámite", "por
  estado").
- `GET /api/reportes/tablero`, protegido por el permiso nuevo
  `reportes.ver` (reservado a `administrador`) y filtrado por lo que el
  tenant tiene efectivamente contratado (`entitlement.ModulosDelTenant`):
  un módulo no contratado no aparece en el tablero aunque tenga datos
  cargados.
- Pantalla `PanelDeReportes` dentro de `PanelDeAdministracion` (no es un
  módulo contratable, no entra en `frontend/src/modulos/registro.ts`).
- **Test de aislamiento**: los conteos del tablero de un municipio son
  exactamente los suyos, nunca se mezclan con los de otro, y el filtro por
  entitlement es real (deja de mostrar una fuente cuando se descontrata el
  módulo, aunque los datos sigan en la tabla).

Explícitamente fuera de alcance de esta rebanada: Auditoría interna /
Control de gestión como módulo completo (sigue siendo una rebanada
futura, ahora desbloqueada), eventos de dominio nuevos, más módulos
consumidores de la SPI (`multas`, `turnos`, etc. quedan disponibles para
sumarse después sin tocar `reportes`), gráficos/visualización, filtros por
fecha y series temporales, caché/materialización, exportación a
PDF/Excel.

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

### R13 · Un vecino paga una tasa municipal online

**Demo**: un agente municipal con sesión y el permiso `tasas.publicar` da
de alta una tasa (número de cuenta, concepto, período, monto). Un vecino,
sin sesión, busca por ese número de cuenta en el portal público de ese
municipio y ve la tasa pendiente. La paga a través de un simulador de pago
rotulado explícitamente como entorno de prueba (no un proveedor real) y,
al aprobarlo, la tasa queda "Pagada" con fecha. El mismo número de cuenta y
sus tasas no aparecen en el portal de otro municipio.

R13 abre Fase 2 con el primer ítem del roadmap para esa fase, **Tasas
municipales + pago online**, en vez de empezar por "capa de adaptadores a
sistemas legados" como pieza aislada: esa capa, acotada a lo que esta
rebanada necesita (una pasarela de pago), se construye adentro de R13 como
la decisión de arquitectura que la habilita
([ADR 0018](../arquitectura/decisiones/0018-pasarela-de-pago-simulada.md)),
no como un ticket propio sin demo. Ningún municipio piloto tiene todavía
credenciales reales de una pasarela de pago (Mercado Pago, Modo,
PagoFácil/Rapipago) ni de AFIP/ARBA — bloquear la rebanada hasta
conseguirlas repetiría el problema que el roadmap ya advierte
explícitamente que hay que evitar. Se sigue el mismo criterio que R11
(Transparencia activa: datos sembrados en vez de esperar un piloto real) y
que el motor de notificaciones (R5, SMTP real contra Mailpit en dev): la
integración con la pasarela vive detrás de una interfaz
(`PasarelaDePago`), con un adaptador simulado que aprueba o rechaza el
pago sin salir del sistema, y la integración con un proveedor real queda
explícitamente diferida.

Requiere [ADR 0018](../arquitectura/decisiones/0018-pasarela-de-pago-simulada.md):
decide la interfaz `PasarelaDePago`, un módulo canon base nuevo `pagos`
(mismo estatus que `seguimientoanonimo`, sin persistencia propia), un
único adaptador activo (`PasarelaDePagoSimulada`, sin selección por
proveedor todavía porque no existe un segundo caso real), y que el
"checkout" simulado sea una vista in-app rotulada como entorno de prueba
en vez de un sitio externo (no hay router de URLs en el frontend). No
requiere ADR propio para el módulo `tasas`: reutiliza sin extenderlos los
patrones ya fijados por
[ADR 0011](../arquitectura/decisiones/0011-autorizacion-por-roles-con-permisos-granulares.md)/
[ADR 0012](../arquitectura/decisiones/0012-declaracion-de-modulos-y-gating-por-ruta.md)
(lectura pública + escritura protegida) y
[ADR 0014](../arquitectura/decisiones/0014-reclamos-ciudadanos-alta-publica-anonima-y-estado-propio.md)
§1 (escritura pública solo `POST`, aplicada acá también al endpoint de
confirmación de pago).

Incluye:
- Módulo `tasas`, contratable por municipio. Publicar una tasa
  (`POST /api/tasas`) requiere sesión y el permiso `tasas.publicar`,
  asignado **solo a `administrador`**: es un acto fiscal del municipio
  (crea una deuda exigible), mismo nivel de sensibilidad que
  `boletin.publicar`/`transparencia.publicar`. Buscar por número de
  cuenta (`GET /api/tasas?numeroCuenta=...`, parámetro obligatorio, sin
  búsqueda abierta a todo el padrón) es público, sin sesión.
- Una tasa: número de cuenta (identificador simple sembrado por el
  municipio, sin padrón de contribuyentes real todavía), concepto,
  período (texto libre), monto, estado (`PENDIENTE`/`PAGADA`) y fecha de
  pago.
- Pago online: `POST /api/tasas/{id}/pagos` (público, inicia el pago
  contra `PasarelaDePago`) y `POST /api/tasas/pagos/confirmar` (público,
  simula el webhook de la pasarela) — ambos reutilizan el mecanismo de
  escritura pública solo-`POST` de ADR 0014 §1 para un propósito nuevo
  (confirmación de un sistema externo, no alta anónima de un vecino).
- Módulo `pagos`, canon base (no contratable): interfaz `PasarelaDePago` y
  su único adaptador, `PasarelaDePagoSimulada`.
- Pantalla pública de búsqueda y pago (accesible, sin cuenta), con la
  acción de publicar una tasa visible solo para quien tiene
  `tasas.publicar`, mismo patrón de "una pantalla, vistas según permiso"
  que `boletin`/`cementerio`/`transparencia`.
- **Test de aislamiento**: una tasa publicada en un municipio (y su pago)
  no es visible ni confirmable desde otro.

Queda fuera de R13, explícitamente diferido (ADR 0018): integración con un
proveedor real de pasarela (Mercado Pago, Modo, PagoFácil/Rapipago) y sus
credenciales, verificación de firma de webhook, padrón de contribuyentes
real, panel de gestión de tasas más allá del alta (listado completo,
edición, exención, plan de pagos/moratoria), capa de adaptadores a
AFIP/ARBA o equivalente provincial, notificación de vencimientos o de pago
al vecino, rate limiting sobre los endpoints públicos nuevos, y
conciliación contable/Tesorería (Fase 3).

### R14 · Una empresa se registra como proveedor del municipio y el municipio la aprueba

**Demo**: una empresa, sin cuenta ni sesión, entra al portal público de un
municipio y se registra como proveedor (razón social, CUIT, rubro,
contacto, domicilio) declarando qué documentación tiene (constancia de
AFIP, seguro de responsabilidad civil, certificado de antecedentes). Al
enviar el formulario recibe un código de seguimiento y queda "Pendiente".
Un agente municipal con sesión y el permiso `proveedores.gestionar` ve el
registro en la lista de proveedores y lo aprueba (o rechaza, con un
comentario). La empresa, sin sesión, vuelve más tarde con su código de
seguimiento y ve que su registro está "Aprobado". El mismo CUIT registrado
en un municipio no aparece ni es consultable en el portal de otro
municipio.

R14 es el segundo ítem de Fase 2 del roadmap, **Portal de proveedores
(registro y documentación)**. A diferencia de todo lo construido hasta
ahora, el usuario final de la escritura pública no es un vecino sino un
tipo de actor nuevo (una empresa/persona que quiere venderle al
municipio), pero el mecanismo que necesita —alta pública sin cuenta y
consulta posterior de su propio estado por posesión de un secreto— es
exactamente el que ya cubre
[ADR 0017](../arquitectura/decisiones/0017-seguimiento-anonimo-por-token-en-reclamos-y-mesa-de-entradas.md):
se reutiliza el módulo `seguimientoanonimo` tal cual, como tercer
consumidor, en vez de inventar un login de proveedor. No requiere ADR
propio: reutiliza sin extenderlos los patrones ya fijados por
[ADR 0011](../arquitectura/decisiones/0011-autorizacion-por-roles-con-permisos-granulares.md)/
[ADR 0012](../arquitectura/decisiones/0012-declaracion-de-modulos-y-gating-por-ruta.md)
(permisos y gating de módulo), [ADR 0014](../arquitectura/decisiones/0014-reclamos-ciudadanos-alta-publica-anonima-y-estado-propio.md)
§1 (escritura pública, solo `POST`) y ADR 0017 (token de seguimiento,
consulta de solo lectura).

"Documentación", en esta rebanada, es una declaración (checklist +
observaciones de texto libre), no una carga de archivos: el proyecto
todavía no tiene infraestructura de almacenamiento de archivos, y
construirla sin otro caso real que la necesite sería sobredimensionar
esta rebanada. Se difiere explícitamente a cuando exista esa
infraestructura o un segundo módulo que también la necesite.

Incluye:
- Módulo `proveedores`, contratable por municipio. Alta pública
  (`POST /api/proveedores`, sin sesión) con razón social, CUIT (formato
  validado y normalizado, único por municipio), rubro (categoría fija),
  email y teléfono de contacto, domicilio, y la documentación declarada
  (tres checkboxes — constancia de AFIP, seguro de responsabilidad civil,
  certificado de antecedentes — más observaciones de texto libre).
  Devuelve, igual que reclamos y mesa de entradas, un token de seguimiento
  en claro una única vez.
- Estado del proveedor: `PENDIENTE` → `APROBADO`/`RECHAZADO` (terminal),
  con comentario de gestión opcional — mismo patrón de estado fijo
  codificado en el servicio del módulo que ya usan `reclamos`/`tasas`, sin
  motor de workflow genérico (no varía por municipio).
- `GET /api/proveedores/seguimiento/{token}`, pública, con la misma
  minimización de datos que ya aplica ADR 0017 §5 (sin los datos de
  contacto que la propia empresa ya tiene).
- `GET /api/proveedores` (listado completo) y
  `PATCH /api/proveedores/{id}/estado` (aprobar/rechazar), protegidos.
  Permisos nuevos `proveedores.ver` y `proveedores.gestionar`, asignados a
  **ambos** roles de sistema (`administrador` y `agente`): revisar y
  aprobar un proveedor es una tarea operativa de gestión, no un acto
  fiscal como `tasas.publicar` — mismo criterio que
  `reclamos.gestionar`/`mesaentradas` (ADR 0014 §8).
- Pantalla pública de alta + consulta por token (accesible, sin cuenta) y
  panel de gestión para quien tiene `proveedores.ver`/`gestionar`, mismo
  patrón "una pantalla, vistas según permiso" que el resto de los módulos.
- **Test de aislamiento**: un proveedor registrado en un municipio (y su
  token) no es visible ni consultable desde otro; el mismo CUIT puede
  volver a registrarse en otro municipio sin conflicto de unicidad (la
  unicidad de CUIT es por base de tenant, no global).

Queda fuera de R14, explícitamente diferido: carga de archivos reales de
documentación (constancias/seguros/antecedentes en PDF u otro formato) y
la infraestructura de almacenamiento que eso requiere; verificación de
CUIT contra un padrón real (AFIP u otro); reconsideración o edición de un
registro rechazado por la propia empresa (mismo criterio que ADR 0014
para reclamos: sin cuenta no hay forma de verificar que quien edita es
quien creó); notificación a la empresa cuando cambia el estado de su
registro (motor de notificaciones, ADR 0013); vencimiento/renovación de la
documentación declarada; catálogo de licitaciones/compras y participación
del proveedor en un proceso de compra (Fase 3); consola del proveedor
cross-tenant (ver [modelo comercial](modelo-comercial.md)); y rate
limiting sobre los endpoints públicos nuevos.

### R15 · El proveedor de la plataforma ve y gestiona el contrato de sus municipios clientes

**Demo**: un usuario de plataforma entra a `admin.localhost:5173` (fuera de
cualquier portal municipal) e inicia sesión con sus credenciales de
plataforma. Ve una lista de todos los municipios dados de alta, con su
tramo poblacional, su estado de facturación y cuántos módulos tiene
contratados cada uno. Entra al detalle de uno, prende un módulo nuevo y
edita el estado de facturación con una nota. Vuelve a la lista y ve
reflejados los cambios.

El roadmap nombra este ítem "Consola del proveedor: contratos, módulos por
municipio, estado de facturación" — ambiguo porque desde R14 el producto
también tiene un actor llamado "proveedor" (la empresa que le vende al
municipio). No es ese: [modelo-comercial.md](modelo-comercial.md)
§"Superficies de administración" ya distingue la consola del proveedor
(cross-tenant, el negocio de Ciudad Digital operando sobre sus municipios
clientes) de la consola del municipio (intra-tenant, Fase 3), y R14 mismo
había diferido explícitamente "consola del proveedor cross-tenant" como
algo distinto de su propio alcance. R15 construye la primera.

Requiere [ADR 0019](../arquitectura/decisiones/0019-consola-del-proveedor-ui-cross-tenant-y-contrato-minimo.md):
resuelve dos pendientes que dejaron ADR 0009 y ADR 0012 §8 — granularidad
del contrato (plan único por tenant: tramo poblacional + estado de
facturación manual, sin fechas por módulo ni motor de facturación real,
consistente con que la emisión de facturas queda fuera del sistema) y
dónde vive la consola (mismo proyecto Vite/React del portal municipal, con
un componente raíz propio montado según el host — `admin.localhost` en
desarrollo —, no una aplicación separada). La API cross-tenant que
necesita —alta/listado de municipios (ADR 0005, R2) y catálogo/entitlement
de módulos (ADR 0012 §8, R4)— ya existía y se opera por API desde esas
rebanadas; lo que R15 agrega es la UI (que no existía) y tres columnas
mínimas de contrato/facturación.

Incluye:
- Tres columnas nuevas en `tenant` (base de control): `tramo_poblacional`
  (`CHICO`/`MEDIANO`/`GRANDE`), `estado_facturacion` (`AL_DIA`/`ATRASADO`,
  editado a mano, desacoplado del entitlement según ADR 0009), y
  `nota_facturacion` (texto libre). Nuevo endpoint `PATCH
  /api/admin/municipios/{slug}/comercial`.
- `GET /api/admin/municipios` extendido con los tres campos más
  `cantidadDeModulosContratados`, para que la lista de la consola no
  necesite un pedido por fila.
- Nueva superficie de frontend `frontend/src/plataforma/`: login de
  plataforma, lista de municipios y detalle por municipio (módulos +
  información comercial), montada en `admin.localhost` en vez del portal
  municipal.
- **Test de "quién puede llegar a esta vista"**: en vez de un test de
  aislamiento entre tenants (esta vista es legítimamente cross-tenant por
  diseño), se verifica que ni una sesión anónima ni una sesión de usuario
  de un municipio pueden operar el nuevo endpoint — solo una sesión de
  usuario de plataforma (ADR 0010), extendiendo la cobertura que ya existía
  para el resto de `/api/admin/**`.

Queda fuera de R15, explícitamente diferido (ADR 0019): motor de
facturación real (importes, vencimientos, facturas emitidas), vigencia del
contrato por módulo con fechas individuales, auditoría de quién cambió el
tramo/facturación/módulos de un municipio y cuándo, alertas proactivas
sobre municipios atrasados, permisos granulares dentro de la sesión de
plataforma (hoy todo-o-nada), UI para alta de municipios o migración de
esquema (siguen por API/`curl`), y cualquier dato operativo de un
municipio (usuarios, reclamos, tasas, proveedores) — la consola no tiene
ninguna vía para leerlos.

### R16 · Un proveedor se registra y el municipio ve su situación fiscal validada contra AFIP (simulado)

**Demo**: una empresa se registra como proveedor (mismo formulario de R14),
con un CUIT que termina en un dígito par (por ejemplo, `...78-2`). Un
agente municipal, en el panel de gestión, ve el registro con la columna
"Situación fiscal (AFIP)" en **Activo**. Otra empresa se registra con un
CUIT que termina en un dígito impar: el agente ve **Inhabilitado**, con
una nota visible de que conviene revisarlo antes de aprobar — pero el
agente puede aprobarlo igual si tiene motivos para hacerlo, el sistema no
se lo impide. Un tercer CUIT, terminado en `0`, aparece como **No
encontrado en el padrón**.

R16 cierra el último ítem de Fase 2 del roadmap: "capa de adaptadores a
sistemas legados (AFIP/ARBA o equivalente provincial, pasarelas de pago)".
La parte de pasarelas de pago ya se resolvió en R13 (ADR 0018); R16
resuelve el diferido explícito de R14: "verificación de CUIT contra un
padrón real (AFIP u otro)". No se construye una capa de adaptadores en
abstracto: se resuelve contra ese caso concreto y ya señalado en el
backlog.

Requiere [ADR 0020](../arquitectura/decisiones/0020-padron-fiscal-simulado-para-cuit-de-proveedores.md):
mismo patrón interfaz + adaptador simulado que ADR 0018 (pasarela de
pago), acá para un padrón fiscal — interfaz `PadronFiscal`, módulo canon
base nuevo `padronfiscal`, único adaptador activo `PadronFiscalSimulado`
(determinístico según el CUIT, sin llamadas de red), y una decisión de
producto explícita: el resultado es **advisory, no bloqueante** — ni el
alta ni la aprobación se impiden por la situación fiscal, el sistema solo
la muestra a quien decide. Bloquear es una decisión de negocio que ningún
municipio piloto pidió todavía (ver ADR 0020, Alternativas consideradas).

Incluye:
- Módulo `padronfiscal`, canon base (no contratable): interfaz
  `PadronFiscal` y su único adaptador, `PadronFiscalSimulado`.
- `proveedores` consulta `padronfiscal` en el alta (`GestionDeProveedores.registrar`)
  y persiste el resultado en la columna nueva `situacion_fiscal`
  (`ACTIVO`/`INHABILITADO`/`NO_ENCONTRADO`) de `proveedor`.
- `ProveedorResponse` (shape completo, `proveedores.ver`) incluye
  `situacionFiscal`; ni `ProveedorPublicoResponse` (confirmación del alta)
  ni `SeguimientoDeProveedorResponse` (consulta pública por token) lo
  exponen — es información para la decisión interna del municipio, no
  para la empresa (ADR 0020 §3).
- Panel de gestión de `proveedores`: columna nueva "Situación fiscal
  (AFIP)" con texto explícito (no solo color/ícono) para `INHABILITADO` y
  `NO_ENCONTRADO`, sin que eso oculte ni deshabilite las acciones de
  aprobar/rechazar.
- **Test de aislamiento**: extendido (no duplicado) para confirmar que
  `situacionFiscal` de un proveedor de un municipio no es visible desde
  otro, mismo criterio que el resto de los campos de `proveedor`.

Queda fuera de R16, explícitamente diferido (ADR 0020): integración con
un padrón real de AFIP/ARBA o equivalente provincial y sus credenciales,
manejo de caída/timeout/reintentos del servicio real, re-consulta de la
situación fiscal después del alta, bloqueo del alta o de la aprobación
según el resultado (decisión de negocio pendiente de un piloto real),
exponer la situación fiscal a la propia empresa registrada, y aplicar el
mismo mecanismo a `tasas` (que hoy no tiene ningún campo de identidad
fiscal del contribuyente).

## Epic: Fase 3 — Compras y áreas normativamente pesadas

Compras y Contrataciones/Licitaciones, Presupuesto y Contabilidad,
Tesorería, Legal y Técnica/Juzgado de Faltas, Tránsito y Transporte,
consola del municipio.

Fase de mayor riesgo legal/normativo del roadmap (cada provincia tiene su
propio régimen); se aborda de a un módulo por vez, priorizando el que se
puede diseñar sin inventar normativa (ver R17), y difiriendo los que sí la
necesitan hasta tener un municipio piloto real.

### R17 · Un agente de tránsito labra una multa y el vecino la paga con descuento, o la impugna

**Demo**: un agente de tránsito (con sesión y permiso `multas.labrar`)
labra una multa contra una patente, con un monto y una descripción de la
infracción. Un vecino, sin sesión, la busca por patente o DNI, ve el
monto vigente (con el 20% de descuento por pago voluntario si todavía
está dentro de los 10 días de la notificación) y la paga con el
simulador de `pagos` (ADR 0018) — queda `PAGADA`. En otro caso, el vecino
presenta un descargo de texto libre; un administrador (con
`multas.resolverDescargo`) lo revisa y confirma o anula la multa, y el
vecino ve el resultado actualizado al volver a buscarla.

Primera rebanada de Fase 3, elegida por descarte razonado de las otras
dos candidatas obvias de la fase — **Compras y Contrataciones** y
**Presupuesto y Contabilidad** quedan diferidas, ver más abajo. El motivo
completo de la elección, y el diseño de estados/permisos, está en
[ADR 0021](../arquitectura/decisiones/0021-multas-de-transito-alta-protegida-estado-propio-descuento-por-pago-temprano.md).

Es la primera rebanada del proyecto donde **el municipio inicia el
registro**, no el vecino (a diferencia de `reclamos`, `mesaentradas` y
`proveedores`): el alta (`POST /api/multas`) es protegida por sesión y
permiso, no pública.

Incluye:
- Módulo nuevo `multas`, contratable, con su propio modelo de estado
  (`NOTIFICADA → PAGADA`, `NOTIFICADA → EN_DESCARGO → CONFIRMADA/ANULADA`,
  `CONFIRMADA → PAGADA`) — decisión explícita de **no** reutilizar el
  motor de expediente/workflow de Mesa de Entradas (ADR 0015): alta
  protegida en vez de pública, y dos vías de cierre en vez de una
  progresión lineal (ADR 0021 §1/§2).
- Búsqueda pública por patente o DNI, mismo patrón que la búsqueda de
  `tasas` por número de cuenta: identificador obligatorio, sin listado
  abierto de todas las multas.
- Pago online reutilizando `pagos`/`PasarelaDePago` (ADR 0018) tal cual,
  sin extenderlo.
- Descuento por pago voluntario temprano: 20% dentro de los 10 días
  corridos desde la notificación, perdido para siempre si la multa pasó
  por un descargo (ADR 0021 §8).
- Descargo público (texto libre + contacto opcional) y su resolución
  protegida, con dos permisos nuevos de sensibilidad distinta:
  `multas.labrar` (administrador y agente, trabajo operativo cotidiano) y
  `multas.resolverDescargo` (solo administrador, acto con impacto fiscal
  y naturaleza cuasi-judicial — mismo criterio de reserva que
  `tasas.publicar`, ADR 0018).
- **Test de aislamiento**: una multa labrada en un municipio no es
  buscable ni confirmable desde otro.
- Pantalla accesible del módulo, con las mismas convenciones de foco y
  anuncios (`role="status"`/`role="alert"`) que `PantallaDeTasas`.

Especificación completa en [spec CD-25](../../specs/CD-25-multas-de-transito.md).

Queda fuera de R17, explícitamente diferido (ver ADR 0021, Pendiente de
definir): notificación al vecino de que se le labró una multa,
identificación real de patente/titular contra un padrón real, notificación
fehaciente del acta (edictos, plazos procesales provinciales), un segundo
descargo sobre la misma multa, y un segundo tipo de infracción con
circuito propio.

### Compras y Contrataciones / Licitaciones — diferido

Los montos que definen licitación pública/privada/concurso de precios/
compra directa varían por provincia y por ordenanza municipal propia. Sin
un municipio piloto real que aporte esos umbrales, cualquier valor sería
inventado — queda sin desarrollar hasta tener ese caso real (ver
ADR 0021, Contexto).

### Presupuesto y Contabilidad — diferido

La Provincia de Buenos Aires (y otras) ya proveen gratis un sistema
homologado (RAFAM), con adopción muy alta en los municipios bonaerenses.
Construirlo ahora compite con algo gratuito y adoptado, sin saber si el
municipio piloto necesita integrarse con RAFAM o reemplazarlo — queda sin
desarrollar hasta esa decisión (ver ADR 0021, Contexto).

### Tesorería — diferido

El [catálogo funcional](catalogo-funcional.md) describe "Tesorería y
Recaudación" como cobranzas, conciliación bancaria y gestión de deuda:
necesita el mismo libro contable que Presupuesto y Contabilidad (RAFAM u
homólogo) para conciliar contra algo. Misma razón de fondo que el ítem de
arriba — queda sin desarrollar hasta la misma decisión.

### R18 · El municipio ve su contrato y sus módulos, y pide un alta o baja

**Demo**: un administrador del municipio (no un agente) inicia sesión y
entra a "Administración". Adentro, junto a Usuarios, Roles y Auditoría, ve
una sección nueva "Mi municipio": la lista de módulos que tiene contratados
(de solo lectura), su tramo poblacional y su estado de facturación (de
solo lectura, sin la nota interna que solo ve la plataforma), y un
formulario para pedir el alta o la baja de un módulo con una justificación
en texto libre. Envía un pedido de alta de un módulo que no tiene
contratado y lo ve aparecer en su propio historial como "Pendiente". Un
usuario de plataforma, en la consola del proveedor (`admin.localhost`),
entra al detalle de ese municipio y ve el mismo pedido listado; lo marca
"Atendida" después de resolverlo por fuera (prendiendo el módulo con el
mecanismo ya existente de ADR 0012 §8, sin automatizar nada). Al volver a
consultar, el municipio ve su pedido como "Atendida".

Cierra el último ítem construible de Fase 3 sin un municipio piloto real:
de los cuatro puntos que el roadmap agrupa bajo "Consola del municipio",
administración de usuarios ya existía (R3) y módulos activos ya era un
dato público (`GET /api/modulos`, ADR 0012 §7); lo nuevo es mostrarle al
municipio su propio estado de facturación (que ya existía, pero solo del
lado de la plataforma, ADR 0019) y darle una forma de pedir un cambio de
módulos sin poder aplicarlo él mismo.

Requiere [ADR 0022](../arquitectura/decisiones/0022-consola-del-municipio-contrato-de-solo-lectura-y-solicitud-de-modulo.md):
resuelve dónde vive una solicitud de alta/baja de módulo (en la base de
control, asociada al tenant, porque es dato contractual — no operativo —
y es lo único que le permite a la consola del proveedor listarla sin
violar ADR 0019 §5) y qué parte del contrato ve el propio municipio
(tramo y estado de facturación sí, la nota interna de la plataforma no).

Incluye:
- Interfaces públicas nuevas de `tenants`: `ContratoDelTenant` (tramo +
  estado de facturación del tenant del request en curso) y
  `SolicitudesDeModulo` (crear y listar las solicitudes del tenant del
  request en curso), ambas consumidas por un controller nuevo en
  `municipio.internal` (canon base, mismo criterio que `ContactoController`).
- Tabla nueva `solicitud_modulo` en `db/control` (`tenant_id`, código de
  módulo, tipo `ALTA`/`BAJA`, justificación, estado
  `PENDIENTE`/`ATENDIDA`, copia del actor que la creó).
- `AdministracionDeMunicipiosController` (cross-tenant, ya existente)
  extendido con listado de solicitudes por municipio y marcarla
  `ATENDIDA`; `MunicipioResponse` suma `cantidadDeSolicitudesPendientes`.
- Dos permisos nuevos, ambos solo para `administrador` (no `agente`):
  `municipio.verContrato` y `municipio.solicitarModulo` — mismo criterio
  de reserva que `boletin.publicar`/`tasas.publicar`.
- Frontend: nueva sección dentro de `PanelDeAdministracion` (junto a
  Usuarios, Roles y Auditoría) con los módulos contratados, el contrato de
  solo lectura y el formulario de solicitud con su historial; extensión de
  `DetalleDeMunicipio` en la consola del proveedor con las solicitudes
  recibidas y la acción de marcarlas atendidas.
- **Test de aislamiento**: un municipio no ve ni puede crear solicitudes
  de otro (el `tenant_id` sale de `TenantContext`, nunca de un campo que
  mande el cliente); solo una sesión de usuario de plataforma —nunca una
  de municipio, nunca anónima— puede listar o atender solicitudes de
  cualquier municipio vía `/api/admin/municipios/**`.
- Pantalla accesible dentro de la administración, con las mismas
  convenciones de foco y anuncios (`role="status"`/`role="alert"`) que el
  resto de `PanelDeAdministracion`.

Queda fuera de R18, explícitamente diferido (ver ADR 0022, Pendiente de
definir): automatizar el alta/baja real de un módulo al crear o atender
una solicitud (sigue siendo, a propósito, una operación manual de
plataforma, ADR 0012 §8), notificación por email a la plataforma cuando
entra una solicitud nueva, auditoría transversal de estas acciones
(mismo pendiente que ya dejó ADR 0013 para la API de administración
cross-tenant), validación cruzada entre el tipo de la solicitud y el
estado actual del módulo, y edición o retiro de una solicitud ya creada.

## Epic: Fase 4 — Gestión territorial

Obras Públicas, Catastro, Planeamiento Urbano, Ambiente y Servicios
Públicos, GIS como servicio consolidado.

### R19 · El municipio registra una obra pública en curso y cualquiera ve su estado de avance

**Demo**: un agente municipal (con sesión y permiso `obras.gestionar`)
registra una obra pública nueva: nombre, tipo, ubicación, fechas
estimadas de inicio y fin. Queda creada en estado "Planificada". Un
vecino, sin sesión, entra al portal público, filtra por estado y por
tipo, busca por texto, y encuentra esa obra listada con su estado
actual. El mismo agente actualiza el estado a "En ejecución"; al volver
a consultar, el vecino ve el estado actualizado. La misma obra no
aparece en el portal de otro municipio.

Abre Fase 4 por descarte razonado de las otras candidatas del epic (ver
[ADR 0023](../arquitectura/decisiones/0023-obras-publicas-registro-publico-con-estado-propio-actualizable.md),
Contexto): Catastro depende de datos provinciales reales (el propio
roadmap ya lo señala como candidato a dejar para tarde); GIS como
servicio consolidado es una pieza de infraestructura transversal, no una
rebanada demostrable por sí sola; Planeamiento Urbano/Uso del Suelo
depende del código de zonificación propio de cada municipio, mismo tipo
de riesgo que ya llevó a diferir Compras en Fase 3. Ambiente y Servicios
Públicos queda como candidata natural de la siguiente rebanada de la
fase, no descartada por inviable. Obras Públicas es, en forma y en
riesgo, el mismo tipo de rebanada que Boletín Oficial (R7) y
Transparencia activa (R11): dato público sin inventar cifras ni régimen
legal, con la diferencia de que acá el registro sí muta después de
creado (solo el campo `estado`, con una tabla de transiciones fija) para
que tenga sentido como seguimiento de una obra en curso.

Requiere [ADR 0023](../arquitectura/decisiones/0023-obras-publicas-registro-publico-con-estado-propio-actualizable.md):
decide que el estado de una obra puede mutar después de creada —a
diferencia de Boletín/Transparencia, que no editan nunca un registro
publicado— acotando esa mutabilidad exclusivamente al campo `estado`
mediante una tabla de transiciones codificada en el servicio (mismo
patrón que Reclamos/Multas, no el motor de expediente de ADR 0015), y
que un único permiso `obras.gestionar` cubre tanto el alta como la
actualización de estado, sin la separación de sensibilidad que sí
justifica `multas.labrar`/`multas.resolverDescargo`.

Incluye:
- Módulo `obras`, contratable, sin depender de ningún otro módulo
  funcional. Registrar (`POST /api/obras`) y actualizar estado
  (`PATCH /api/obras/{id}/estado`) requieren sesión y el permiso
  `obras.gestionar`, asignado a **ambos** roles de sistema
  (`administrador` y `agente`): es trabajo operativo de seguimiento de
  obra, no un acto de gabinete. Listar (`GET /api/obras`, con filtros
  opcionales combinables por `estado`, `tipo` y texto en nombre/
  ubicación) es público, sin sesión, sin identificador obligatorio —a
  diferencia de `tasas`/`multas`, es un registro público general, no una
  consulta puntual sobre un dato del vecino.
- Datos del alta: nombre, tipo (enum fijo: vialidad, espacio público,
  edificio público, servicios, otra), ubicación (texto libre, sin
  geolocalización estructurada ni GIS — se difiere hasta que exista GIS
  como servicio o un segundo módulo lo justifique), descripción
  opcional, fechas estimadas de inicio y fin opcionales. Una vez
  registrada, estos campos no se editan por esta rebanada —igual
  criterio que Boletín/Transparencia—; solo el estado cambia.
- Estado: enum fijo `PLANIFICADA, EN_EJECUCION, PARALIZADA, FINALIZADA`
  con tabla de transiciones (`PLANIFICADA → EN_EJECUCION`,
  `EN_EJECUCION → PARALIZADA`, `EN_EJECUCION → FINALIZADA`,
  `PARALIZADA → EN_EJECUCION`; `FINALIZADA` terminal), sin entidad de
  historial ni motor de workflow.
- Sin certificaciones de avance, montos ni contratista: esos datos
  pertenecen a Presupuesto y Contabilidad, todavía diferido.
- **Test de aislamiento**: una obra registrada en un municipio no es
  visible en el listado ni actualizable desde otro.
- Pantalla pública de búsqueda/listado con filtros (accesible, sin
  cuenta), con las acciones de registrar y de cambiar estado visibles
  solo para quien tiene `obras.gestionar`, mismas convenciones de foco y
  anuncios (`role="status"`/`role="alert"`) que el resto del portal.

Especificación completa en [spec CD-27](../../specs/CD-27-obras-publicas.md).

Queda fuera de R19, explícitamente diferido (ver ADR 0023, Pendiente de
definir): certificaciones de avance/montos/contratista, geolocalización
estructurada/GIS como servicio, edición de los campos del alta después
de creada la obra, quién hizo cada cambio de estado (más allá de la
marca de tiempo), adjuntos/fotos, notificación al vecino de obras
cercanas, y las demás candidatas de Fase 4 (Catastro, Planeamiento
Urbano/Uso del Suelo, Ambiente y Servicios Públicos).

### R20 · El municipio registra un árbol urbano y cualquiera ve su estado sanitario

**Demo**: un agente municipal (con sesión y permiso `arbolado.gestionar`)
registra un árbol nuevo en el padrón: especie, ubicación, fecha de
plantación (opcional), descripción (opcional). Queda creado en estado
"Plantado". Un vecino, sin sesión, entra al portal público, filtra por
estado, busca por texto (especie o ubicación), y encuentra ese árbol
listado con su estado actual. El mismo agente actualiza el estado a
"Sano" y después a "Requiere intervención"; al volver a consultar, el
vecino ve el estado actualizado. El mismo árbol no aparece en el portal
de otro municipio.

Segunda rebanada de Fase 4, dentro de Ambiente y Servicios Públicos —área
que R19 (ADR 0023) había dejado explícitamente como "candidata natural de
la siguiente rebanada de la fase, no descartada por inviable". Elegida por
descarte razonado de las otras candidatas del área (ver
[ADR 0024](../arquitectura/decisiones/0024-arbolado-urbano-padron-publico-con-estado-sanitario-propio.md),
Contexto): recolección de residuos depende de zonas/rutas reales o de un
contrato real con una empresa recolectora, mismo riesgo que ya descartó
Planeamiento Urbano en R19; alumbrado público ya está cubierto como
categoría de reclamo del vecino y, como padrón de infraestructura física
real (postes, columnas, circuitos), tiene el mismo riesgo que Catastro;
espacios verdes es viable pero no agrega una dimensión de dominio nueva
frente a Obras Públicas (mismo patrón nombre/ubicación/tipo/estado), así
que queda disponible como candidata futura, no descartada por inviable.
Arbolado urbano aporta algo que ni Obras Públicas ni `reclamos` (R6) dan
ya: un padrón municipal de activos vivos con su propio ciclo de estado
sanitario, no una variación de "obra con estado" ni un reclamo de texto
libre del vecino.

Requiere [ADR 0024](../arquitectura/decisiones/0024-arbolado-urbano-padron-publico-con-estado-sanitario-propio.md):
decide, siguiendo el mismo mecanismo de alta protegida + lectura pública
de ADR 0023, un ciclo de estado sanitario propio de cuatro valores
(`PLANTADO, SANO, REQUIERE_INTERVENCION, RETIRADO`) con tabla de
transiciones codificada en el servicio (mismo patrón que
Obras/Multas/Reclamos, no el motor de expediente de ADR 0015); que
`especie` y `ubicacion` son texto libre, sin catálogo fijo de especies ni
geolocalización estructurada, para no inventar qué especies planta un
municipio real que todavía no existe como piloto; que un único permiso
`arbolado.gestionar` cubre alta y actualización de estado, asignado a
ambos roles de sistema, mismo criterio que `obras.gestionar`; y que, con
`arbolado` como segundo caso real del patrón "alta protegida + lectura
pública + estado propio mutable" que ADR 0023 dejó pendiente, todavía no
conviene extraer ninguna abstracción común con `obras` — se decide con un
tercer caso real delante.

Incluye:
- Módulo `arbolado`, contratable, sin depender de ningún otro módulo
  funcional (tampoco de `obras`). Registrar (`POST /api/arbolado`) y
  actualizar estado (`PATCH /api/arbolado/{id}/estado`) requieren sesión y
  el permiso `arbolado.gestionar`, asignado a **ambos** roles de sistema
  (`administrador` y `agente`): es trabajo operativo de campo, no un acto
  de gabinete. Listar (`GET /api/arbolado`, con filtros opcionales
  combinables por `estado` y texto en especie/ubicación) es público, sin
  sesión, sin identificador obligatorio.
- Datos del alta: especie (texto libre), ubicación (texto libre, sin
  geolocalización estructurada ni GIS — mismo criterio que Obras/Reclamos),
  descripción opcional, fecha de plantación opcional. Una vez registrado,
  estos campos no se editan por esta rebanada; solo el estado cambia.
- Estado: enum fijo `PLANTADO, SANO, REQUIERE_INTERVENCION, RETIRADO` con
  tabla de transiciones (`PLANTADO → SANO`, `SANO → REQUIERE_INTERVENCION`,
  `REQUIERE_INTERVENCION → SANO`, `REQUIERE_INTERVENCION → RETIRADO`),
  sin entidad de historial ni motor de workflow. Un árbol sano no pasa
  directo a retirado: siempre queda un estado intermedio que documenta
  que hubo un motivo antes del retiro.
- Sin motivo del retiro/intervención como campo propio, sin catálogo fijo
  de especies, sin geolocalización estructurada ni GIS, sin adjuntos/
  fotos: fuera de alcance a propósito (ver ADR 0024).
- **Test de aislamiento**: un árbol registrado en un municipio no es
  visible en el listado ni actualizable desde otro.
- Pantalla pública de búsqueda/listado con filtros (accesible, sin
  cuenta), con las acciones de registrar y de cambiar estado visibles
  solo para quien tiene `arbolado.gestionar`, mismas convenciones de foco
  y anuncios (`role="status"`/`role="alert"`) que el resto del portal.

Especificación completa en [spec CD-28](../../specs/CD-28-arbolado-urbano.md).

Queda fuera de R20, explícitamente diferido (ver ADR 0024, Pendiente de
definir): motivo del retiro o de la intervención, quién hizo cada cambio
de estado (más allá de la marca de tiempo), edición de los campos del
alta después de creado el registro, geolocalización estructurada/GIS
como servicio, adjuntos/fotos, y las demás áreas de Ambiente y Servicios
Públicos (recolección de residuos, alumbrado público, espacios verdes).

### R25 · El municipio registra un espacio verde y cualquiera ve su estado

**Demo**: un agente municipal (con sesión y permiso
`espaciosverdes.gestionar`) registra un espacio verde nuevo: nombre, tipo
(plaza/parque/paseo/otra), ubicación, superficie en m² (opcional),
descripción (opcional). Queda creado en estado "Disponible". Un vecino,
sin sesión, entra al portal público, filtra por estado y por tipo, busca
por texto (nombre o ubicación), y encuentra ese espacio verde listado con
su estado actual. El mismo agente actualiza el estado a "En
mantenimiento"; al volver a consultar, el vecino ve el estado
actualizado. El mismo espacio verde no aparece en el portal de otro
municipio.

Tercera rebanada de Fase 4, dentro de Ambiente y Servicios Públicos —área
que R20 (ADR 0024) había dejado "espacios verdes" explícitamente como
"candidata futura si hace falta una rebanada chica", descartada en su
momento no por inviable sino por ser, en forma, casi el mismo ejercicio
que Obras Públicas. Elegida ahora para cerrar la fase (ver
[ADR 0029](../arquitectura/decisiones/0029-espacios-verdes-padron-publico-con-estado-propio-tercera-rebanada-de-fase-4.md),
Contexto): recolección de residuos y alumbrado público siguen dependiendo
de datos reales de un municipio piloto (zonas/contrato real, inventario
de infraestructura eléctrica real), sin cambios desde R20. A diferencia de
cuando se descartó, esta rebanada suma un campo de magnitud numérica
(`superficie`, en m²) que ninguna de las otras tres instancias del patrón
tiene, y una tabla de transiciones de estado con forma propia — no es una
repetición vacía de Obras/Arbolado/Educación, aunque comparta el mismo
mecanismo general de alta protegida + lectura pública + estado mutable.

Requiere [ADR 0029](../arquitectura/decisiones/0029-espacios-verdes-padron-publico-con-estado-propio-tercera-rebanada-de-fase-4.md):
decide, siguiendo el mismo mecanismo de alta protegida + lectura pública
de ADR 0023/0024/0028, un enum cerrado de `tipo` (`PLAZA, PARQUE, PASEO,
OTRA`, chico y estable, a diferencia del texto libre de `especie` en
Arbolado), un campo `superficie` opcional en m² (primera columna numérica
del patrón), un ciclo de estado propio de tres valores (`DISPONIBLE,
EN_MANTENIMIENTO, CERRADO`) con tabla de transiciones codificada en el
servicio; que un único permiso `espaciosverdes.gestionar` cubre alta y
actualización de estado, asignado a ambos roles de sistema; y que, con
`espaciosverdes` como cuarto caso real del patrón "alta protegida +
lectura pública + estado propio mutable" — y el primero que coincide
exactamente en la forma de su tabla de transiciones con otro caso
existente (Educación, ADR 0028) —, sigue sin convenir extraer ninguna
abstracción común: el código realmente duplicado es trivial (una
comprobación de una línea) y la coincidencia es de forma, no de
contenido.

Incluye:
- Módulo `espaciosverdes`, contratable, sin depender de ningún otro
  módulo funcional (tampoco de `obras`, `arbolado` ni `educacion`).
  Registrar (`POST /api/espaciosverdes`) y actualizar estado (`PATCH
  /api/espaciosverdes/{id}/estado`) requieren sesión y el permiso
  `espaciosverdes.gestionar`, asignado a **ambos** roles de sistema
  (`administrador` y `agente`). Listar (`GET /api/espaciosverdes`, con
  filtros opcionales combinables por `estado`, `tipo` y texto en nombre/
  ubicación) es público, sin sesión, sin identificador obligatorio.
- Datos del alta: nombre, tipo (enum fijo: plaza, parque, paseo, otra),
  ubicación (texto libre, sin geolocalización estructurada ni GIS),
  superficie en m² opcional (única columna numérica del alta), descripción
  opcional. Una vez registrado, estos campos no se editan por esta
  rebanada; solo el estado cambia.
- Estado: enum fijo `DISPONIBLE, EN_MANTENIMIENTO, CERRADO` con tabla de
  transiciones (`DISPONIBLE → EN_MANTENIMIENTO`, `EN_MANTENIMIENTO →
  DISPONIBLE`, `EN_MANTENIMIENTO → CERRADO`), sin entidad de historial ni
  motor de workflow. Un espacio disponible no pasa directo a cerrado:
  siempre queda un estado intermedio que documenta que hubo un motivo
  antes del cierre.
- Sin motivo del cierre como campo propio, sin inventario de equipamiento
  (juegos, luminarias, bancos, riego), sin geolocalización estructurada ni
  GIS, sin adjuntos/fotos: fuera de alcance a propósito (ver ADR 0029).
- **Test de aislamiento**: un espacio verde registrado en un municipio no
  es visible en el listado ni actualizable desde otro.
- Pantalla pública de búsqueda/listado con filtros (accesible, sin
  cuenta), con las acciones de registrar y de cambiar estado visibles
  solo para quien tiene `espaciosverdes.gestionar`, mismas convenciones de
  foco y anuncios (`role="status"`/`role="alert"`) que el resto del
  portal.

Especificación completa en [spec CD-34](../../specs/CD-34-espacios-verdes.md).

Queda fuera de R25, explícitamente diferido (ver ADR 0029, Pendiente de
definir): motivo del cierre o del pase a mantenimiento, quién hizo cada
cambio de estado (más allá de la marca de tiempo), edición de los campos
del alta después de creado el registro, inventario de equipamiento del
espacio verde, geolocalización estructurada/GIS como servicio,
adjuntos/fotos, y las áreas de Ambiente y Servicios Públicos que siguen
sin rebanada propia (recolección de residuos, alumbrado público). Con
Obras Públicas (R19), Arbolado urbano (R20) y Espacios verdes (R25), Fase
4 — Gestión territorial queda con tres rebanadas demostrables; Catastro,
Planeamiento Urbano/Uso del Suelo y GIS como servicio consolidado siguen
diferidos sin rebanada propia todavía.

## Epic: Fase 5 — Áreas sociales

Desarrollo Social, Discapacidad, Salud municipal, Educación municipal.

### R21 · Un vecino se inscribe a un programa social y el municipio evalúa su solicitud, sin exponerla públicamente

**Demo**: un administrador (con sesión y
`desarrollosocial.gestionarProgramas`) publica un programa social,
"Refuerzo alimentario municipal", en estado "Abierto". Un vecino, sin
sesión, ve el programa en el catálogo público y se inscribe: nombre,
DNI, contacto, cantidad de integrantes del grupo familiar, situación
declarada ("Empleo informal"), sin subir ningún comprobante. Recibe un
código de seguimiento y, con él, consulta después el estado de su
inscripción sin sesión ("Recibida"). El mismo administrador (con
`desarrollosocial.revisarInscripciones`) entra a la bandeja de
inscripciones, ve los datos completos, la pasa a "En evaluación" y
después a "Aprobada" con un comentario. El vecino, al volver a
consultar, ve el nuevo estado y el comentario, pero no existe ningún
listado público de inscripciones — nadie más puede ver la suya. La misma
inscripción no aparece en el portal de otro municipio.

Primera rebanada de Fase 5 — Áreas sociales. Elegida por descarte
razonado, mismo criterio que ADR 0021/ADR 0023/ADR 0024 ya aplicaron al
abrir Fase 3 y Fase 4, sumado a un criterio nuevo que pesa igual en esta
fase: minimización de datos personales sensibles (ver
[ADR 0025](../arquitectura/decisiones/0025-desarrollo-social-inscripcion-a-programa-social-con-minimizacion-de-datos-sensibles.md),
Contexto). Salud municipal queda diferida como fase completa: un
historial clínico no tiene una forma de minimizarse que no lo vacíe de
sentido, y el producto no tiene todavía ni un municipio piloto real ni
una política de datos de salud. Discapacidad queda diferida para esta
rebanada (no descartada por inviable): un turno para la Junta Evaluadora
de CUD es, en sí mismo, un dato de salud vinculado a la identidad de
quien lo pide, sin una versión "en categorías amplias" que deje de serlo.
Educación municipal es viable sin dato personal pero no aporta una
dimensión de dominio nueva frente a Obras/Arbolado (mismo catálogo
nombre/ubicación/tipo/estado). Desarrollo Social se acota a una
inscripción/preinscripción con elegibilidad declarada en categorías
amplias — nunca un padrón de beneficiarios consultable, nunca
comprobantes de ingresos ni datos de salud.

Requiere [ADR 0025](../arquitectura/decisiones/0025-desarrollo-social-inscripcion-a-programa-social-con-minimizacion-de-datos-sensibles.md):
decide un módulo nuevo `desarrollosocial` con dos entidades — un
catálogo de programas sociales (alta protegida, lectura pública, mismo
mecanismo que Obras/Arbolado) y las inscripciones a esos programas (alta
pública anónima, mismo criterio que Reclamos, con seguimiento por token
reutilizando `seguimientoanonimo` de ADR 0017). A diferencia de todo
módulo anterior con estado propio, las inscripciones **no** tienen
ningún endpoint de lectura pública ni por listado ni por identificador
obligatorio: la única lectura sin sesión es por posesión del token
propio. El permiso de revisión (`desarrollosocial.revisarInscripciones`)
queda reservado solo a `administrador` — es el primer módulo del
proyecto en el que un permiso de gestión operativa no se asigna también
a `agente` en el seed de sistema, porque el dato detrás es más sensible
que el de cualquier módulo anterior.

Incluye:
- Módulo `desarrollosocial`, contratable, sin depender de ningún otro
  módulo funcional. `ProgramaSocialEntity`: alta (`POST
  /api/desarrollosocial/programas`) y cambio de estado
  (`PATCH .../programas/{id}/estado`, `ABIERTO ↔ CERRADO`) requieren
  sesión y `desarrollosocial.gestionarProgramas` (administrador y
  agente); listado (`GET /api/desarrollosocial/programas`, filtros
  `estado`/`q`) es público.
- `InscripcionSocialEntity`: alta (`POST
  /api/desarrollosocial/inscripciones`) pública y anónima, contra un
  programa `ABIERTO` existente; devuelve solo `id`, `estado` y un token
  de seguimiento (nunca se reexpone el resto). Datos del alta: nombre,
  DNI, contacto (obligatorio), cantidad de integrantes del grupo
  familiar (un entero, nunca nombres/edades de terceros), situación
  declarada (enum de cinco categorías amplias, nunca un monto de
  ingreso ni un comprobante), comentario adicional opcional.
- Seguimiento público por token (`GET
  /api/desarrollosocial/inscripciones/seguimiento/{token}`): devuelve
  programa, estado y comentario de resolución si existe — nunca los
  datos personales que el vecino ya escribió.
- Bandeja de gestión (`GET /api/desarrollosocial/inscripciones`,
  `PATCH .../inscripciones/{id}/estado`), reservada a
  `desarrollosocial.revisarInscripciones` (solo administrador): estado
  `RECIBIDA → EN_EVALUACION → APROBADA | RECHAZADA`, con comentario
  obligatorio al aprobar o rechazar. Sin este permiso —incluso con
  `gestionarProgramas`— no hay acceso a ningún dato personal de las
  inscripciones.
- Sin padrón de beneficiarios consultable, sin adjuntos/comprobantes,
  sin cruce con Nación/Provincia, sin geolocalización: fuera de alcance
  a propósito (ver ADR 0025).
- **Test de aislamiento**: un programa y una inscripción de un municipio
  no son visibles ni gestionables desde otro, incluido el seguimiento
  por token.
- Pantalla pública de catálogo (accesible, sin cuenta) con inscripción y
  seguimiento por token, y una bandeja de gestión de inscripciones
  visible solo para quien tiene `desarrollosocial.revisarInscripciones`,
  mismas convenciones de foco y anuncios (`role="status"`/`role="alert"`)
  que el resto del portal.

Especificación completa en
[spec CD-29](../../specs/CD-29-desarrollo-social-programas-e-inscripciones.md).

Queda fuera de R21, explícitamente diferido (ver ADR 0025, Pendiente de
definir): Salud municipal y Discapacidad como fases/rebanadas futuras
(dependen de un mecanismo de datos sensibles más maduro y de un
municipio piloto real), un rol de sistema dedicado para Desarrollo
Social distinto de `administrador`, cruces de datos con Nación/Provincia,
notificación al vecino de un cambio de estado, rate limiting, política de
retención de datos, y edición de los campos del alta después de creado
el registro.

Fase 5 quedó en una sola rebanada durante R22/R23 (mismo patrón que
Fase 3 en su momento): R22 y R23 eligieron abrir Fase 6 en vez de dar una
segunda rebanada acá — ver
[ADR 0026](../arquitectura/decisiones/0026-turnos-actividades-municipales-reserva-con-cupo-primera-rebanada-de-fase-6.md),
Contexto. R24 la cierra con una segunda rebanada, Educación municipal.

### R24 · El municipio registra una institución educativa municipal y cualquiera ve su estado

**Demo**: un agente municipal (con sesión y permiso `educacion.gestionar`)
da de alta una institución educativa municipal: nombre, tipo ("Jardín de
infantes"), ubicación, descripción opcional. Queda creada en estado
"Activa". Un vecino, sin sesión, entra al portal público, filtra por tipo
y por estado, busca por texto, y encuentra esa institución listada con su
estado actual. El mismo agente actualiza el estado a "Cerrada
temporalmente" y después a "Cerrada definitivamente"; al volver a
consultar, el vecino ve el estado actualizado y que ya no admite más
cambios. La misma institución no aparece en el portal de otro municipio.

Cierra Fase 5 — Áreas sociales con una segunda rebanada, por descarte
razonado (ver
[ADR 0028](../arquitectura/decisiones/0028-educacion-municipal-padron-de-instituciones-segunda-rebanada-de-fase-5.md),
Contexto): Salud municipal y Discapacidad siguen diferidas como fase
completa, sin cambios respecto de ADR 0025 (dependen de un mecanismo de
datos sensibles más maduro y de un municipio piloto real). Educación
municipal es la única candidata de riesgo bajo que quedaba disponible —
ADR 0025 ya la había identificado como viable sin dato personal, aunque
sin aportar un mecanismo técnico nuevo frente a Obras/Arbolado (mismo
catálogo con estado propio). Esta rebanada acota explícitamente el
alcance a la competencia municipal real en educación en Argentina:
jardines maternales/de infantes y centros de formación profesional, no
escuelas primarias/secundarias (competencia provincial, no municipal).

Requiere [ADR 0028](../arquitectura/decisiones/0028-educacion-municipal-padron-de-instituciones-segunda-rebanada-de-fase-5.md):
decide un módulo nuevo `educacion`, mismo patrón "alta protegida +
lectura pública + estado propio mutable" que Obras/Arbolado, sin
extraer ninguna abstracción común con ellos (tercer caso del patrón,
pregunta que dejó pendiente ADR 0024 §7 y que esta ADR resuelve por que
no: las reglas de transición y los campos propios de las tres entidades
siguen sin coincidir). A diferencia de Desarrollo Social, esta rebanada
no toca ningún dato personal.

Incluye:
- Módulo `educacion`, contratable, sin depender de ningún otro módulo
  funcional. Registrar (`POST /api/educacion`) y actualizar estado
  (`PATCH /api/educacion/{id}/estado`) requieren sesión y el permiso
  `educacion.gestionar`, asignado a **ambos** roles de sistema
  (`administrador` y `agente`). Listar (`GET /api/educacion`, con
  filtros opcionales combinables por `estado`, `tipo` y texto en
  nombre/ubicación) es público, sin sesión, sin identificador
  obligatorio.
- Datos del alta: nombre, tipo (enum fijo y acotado a competencia
  municipal real: jardín maternal, jardín de infantes, centro de
  formación profesional, otra — a propósito, sin escuela primaria ni
  secundaria), ubicación (texto libre, sin geolocalización estructurada
  ni GIS), descripción opcional. Una vez registrada, estos campos no se
  editan por esta rebanada; solo el estado cambia.
- Estado: enum fijo `ACTIVA, CERRADA_TEMPORALMENTE,
  CERRADA_DEFINITIVAMENTE` con tabla de transiciones (`ACTIVA →
  CERRADA_TEMPORALMENTE`, `CERRADA_TEMPORALMENTE → ACTIVA`,
  `CERRADA_TEMPORALMENTE → CERRADA_DEFINITIVAMENTE`;
  `CERRADA_DEFINITIVAMENTE` terminal), sin entidad de historial ni motor
  de workflow.
- Sin cupos/vacantes ni inscripción de personas: reabriría la pregunta
  de dato personal (y, acá, de menores) que ADR 0025 ya resolvió con
  cuidado para Desarrollo Social — fuera de alcance a propósito.
- **Test de aislamiento**: una institución registrada en un municipio no
  es visible en el listado ni actualizable desde otro.
- Pantalla pública de búsqueda/listado con filtros (accesible, sin
  cuenta), con las acciones de registrar y de cambiar estado visibles
  solo para quien tiene `educacion.gestionar`, mismas convenciones de
  foco y anuncios (`role="status"`/`role="alert"`) que el resto del
  portal.

Especificación completa en
[spec CD-33](../../specs/CD-33-educacion-municipal.md).

Queda fuera de R24, explícitamente diferido (ver ADR 0028, Pendiente de
definir): motivo del cierre, quién hizo cada cambio de estado (más allá
de la marca de tiempo), edición de los campos del alta después de
creada la institución, cupos/vacantes e inscripción a una institución,
un caso real de municipio con escuela primaria/secundaria propia, rate
limiting, y Salud municipal/Discapacidad como fases/rebanadas futuras.

## Epic: Fase 6 — Áreas de imagen y control de gestión

Cultura/Turismo/Deportes, Prensa y Comunicación, Auditoría interna y
control de gestión.

### R22 · Un vecino reserva un turno para una actividad municipal con cupo limitado, y el municipio administra la agenda

**Demo**: un agente municipal (con sesión y `turnos.gestionar`) publica
una actividad, "Cancha de Fútbol 5 — Polideportivo Municipal" (tipo
Deporte), en estado "Activa", y le agrega una franja horaria: sábado
10:00 a 11:00, cupo 2. Un vecino, sin sesión, ve la actividad y esa
franja en el catálogo público con "2 lugares disponibles", y reserva un
turno con su nombre, DNI y contacto. El cupo baja a 1. Un segundo vecino
reserva el último lugar: el cupo baja a 0 y la franja deja de aceptar
reservas — un tercer vecino que lo intenta recibe un error de "cupo
agotado", no una reserva fantasma. El mismo agente entra a la agenda y ve
las dos reservas con los datos completos de cada vecino; no existe
ningún listado público de quién se anotó. Las actividades, franjas y
reservas de un municipio no aparecen en el portal de otro.

Primera rebanada de Fase 6 — Áreas de imagen y control de gestión.
Elegida por descarte razonado sobre Educación municipal (segunda
rebanada posible de Fase 5, mismo catálogo público con estado propio que
ya demostraron Obras/Arbolado/Desarrollo Social, sin aportar una
dimensión de dominio nueva) y sobre Auditoría interna/Control de gestión
(un tablero cruzado necesitaría tocar los siete módulos funcionales ya
construidos o diseñar a las apuradas el framework de reportes/BI
pendiente desde Fase 0) — ver
[ADR 0026](../arquitectura/decisiones/0026-turnos-actividades-municipales-reserva-con-cupo-primera-rebanada-de-fase-6.md),
Contexto. Acotada a actividades recreativas (deporte/cultura/turismo):
sin dato de salud, sin trámite administrativo — turnos de salud
municipal o de atención en tránsito quedan fuera de esta rebanada por
los mismos motivos que ADR 0025 ya dio para Discapacidad.

Requiere [ADR 0026](../arquitectura/decisiones/0026-turnos-actividades-municipales-reserva-con-cupo-primera-rebanada-de-fase-6.md):
decide un módulo nuevo `turnos` con tres entidades — un catálogo de
actividades (alta protegida, lectura pública, mismo mecanismo que
Obras/Arbolado/Desarrollo Social), franjas horarias con cupo bajo cada
actividad, y las reservas de los vecinos sobre esas franjas (alta pública
anónima, mismo criterio que Reclamos/Desarrollo Social). La decisión
central de la ADR es cómo decrementar el cupo de una franja sin que dos
reservas públicas concurrentes puedan sobrevenderla: un `UPDATE`
condicional atómico (`cupo_disponible = cupo_disponible - 1 where
cupo_disponible > 0`) en una sola sentencia, no una lectura seguida de una
escritura. Primer módulo del proyecto con esta propiedad de corrección
bajo concurrencia, y primer uso de 409 Conflict como código de error de
negocio (cupo agotado, reserva duplicada del mismo DNI en la misma
franja).

Incluye:
- Módulo `turnos`, contratable, sin depender de ningún otro módulo
  funcional. `ActividadEntity`: alta (`POST /api/turnos/actividades`) y
  cambio de estado (`PATCH .../actividades/{id}/estado`, `ACTIVA ↔
  INACTIVA`) requieren sesión y `turnos.gestionar` (administrador y
  agente); listado (`GET /api/turnos/actividades`, filtros
  `tipo`/`estado`/`q`) es público.
- `FranjaHorariaEntity`: alta (`POST
  /api/turnos/actividades/{id}/franjas`) protegida, con `fecha`,
  `horaInicio`, `horaFin` y `cupoTotal`; `cupoDisponible` se inicializa
  en `cupoTotal` y de ahí en más solo lo modifica el mecanismo de
  reserva. Sin edición de una franja ya creada. Listado (`GET
  /api/turnos/franjas?actividadId=...`) es público y muestra
  `cupoDisponible`, nunca quién reservó.
- `TurnoEntity`: alta (`POST /api/turnos/reservas`) pública y anónima,
  contra una franja existente cuya actividad esté `ACTIVA` y con cupo
  disponible; nombre, DNI, contacto (obligatorio). Decremento atómico del
  cupo a nivel de base de datos (ADR 0026 §4): `CupoAgotado` (409) si no
  queda lugar, `ReservaDuplicada` (409) si ese DNI ya reservó esa franja.
  Sin lectura pública de reservas — mismo criterio de minimización que
  Desarrollo Social (ADR 0025 §6), aunque el dato acá no es sensible.
- Agenda de gestión (`GET /api/turnos/reservas?franjaId=...`), mismo
  permiso `turnos.gestionar` (sin separar por sensibilidad: el dato de
  `TurnoEntity` es del mismo nivel que Mesa de Entradas/Reclamos, no el
  de Desarrollo Social).
- Sin cobro/pagos, sin cancelación de reservas ni de franjas, sin
  notificaciones, sin seguimiento por token: fuera de alcance a propósito
  (ver ADR 0026).
- **Test de aislamiento**: una actividad, franja o reserva de un
  municipio no es visible ni gestionable desde otro.
- **Test de concurrencia**: con cupo 2 en una franja, cinco reservas
  simultáneas para DNIs distintos (lanzadas con `ExecutorService`,
  armadas antes de dispararse) terminan en exactamente dos reservas
  exitosas y tres `CupoAgotado`, nunca más reservas exitosas que cupo
  disponible.
- Pantalla pública de catálogo de actividades con sus franjas y cupo
  disponible (accesible, sin cuenta), formulario de reserva, y una
  agenda de gestión visible solo para quien tiene `turnos.gestionar`,
  mismas convenciones de foco y anuncios (`role="status"`/`role="alert"`)
  que el resto del portal.

Especificación completa en
[spec CD-31](../../specs/CD-31-turnos-actividades-municipales.md).

Queda fuera de R22, explícitamente diferido (ver ADR 0026, Pendiente de
definir): cancelación de reservas o de franjas con liberación de cupo,
edición de una franja ya publicada, seguimiento por token del vecino
sobre su propia reserva, cobro de arancel (integración con `pagos`),
notificaciones, turnos de salud municipal o de atención
administrativa/tránsito, y Auditoría interna/Control de gestión (sigue
disponible como candidata de una rebanada futura de esta misma fase).

### R23 · El municipio publica una gacetilla de prensa y cualquiera la encuentra

**Demo**: un agente municipal (con sesión y `prensa.publicar` — sin
necesitar ser administrador) publica una gacetilla, "Se inaugura la nueva
plaza del barrio Centro" (categoría Obras), con fecha de publicación de
hoy y el texto completo del comunicado. Un vecino, sin sesión, entra al
portal público, filtra por categoría "Obras" y busca por texto en el
título, y encuentra esa gacetilla. La misma gacetilla no aparece en el
portal de otro municipio.

Segunda rebanada de Fase 6 — Áreas de imagen y control de gestión. Elegida
por descarte razonado sobre Auditoría interna/Control de gestión,
reevaluada y descartada por segunda vez consecutiva: ningún módulo
funcional del proyecto publica un evento de dominio propio más allá de
`UsuarioCreado` (`acceso`, R5), así que un tablero cruzado seguiría
necesitando tocar los módulos existentes o diseñar a las apuradas el
framework de reportes/BI pendiente desde Fase 0 — ver
[ADR 0027](../arquitectura/decisiones/0027-prensa-y-comunicacion-gacetillas-segunda-rebanada-de-fase-6.md),
Contexto. Acotada a gacetillas (comunicados de prensa): "gestión de
redes" (publicación en redes sociales externas), que el catálogo
funcional también nombra bajo Prensa y Comunicación, queda fuera por ser
una integración con terceros sin ningún patrón previo en el proyecto.

Requiere [ADR 0027](../arquitectura/decisiones/0027-prensa-y-comunicacion-gacetillas-segunda-rebanada-de-fase-6.md):
decide un módulo nuevo `prensa` con una sola entidad, `GacetillaEntity`,
en forma deliberadamente igual al patrón ya usado por Boletín Oficial
(R7): alta protegida por sesión y permiso, lectura pública sin sesión, sin
estado ni edición posterior. La única decisión de permisos que se aparta
del precedente de Boletín: `prensa.publicar` se asigna a `administrador`
**y** `agente` (a diferencia de `boletin.publicar`, solo `administrador`),
porque una gacetilla no es un acto legal del municipio como una
ordenanza — es una comunicación operativa del mismo nivel que gestionar
un reclamo o dar de alta una franja de turnos.

Incluye:
- Módulo `prensa`, contratable, sin depender de ningún otro módulo
  funcional. `GacetillaEntity`: alta (`POST /api/prensa`) requiere sesión
  y `prensa.publicar` (administrador y agente); listado (`GET /api/prensa`,
  filtros `categoria`/`q` sobre el título) es público.
- Campos: `categoria` (enum cerrado: `INSTITUCIONAL`, `OBRAS`, `CULTURA`,
  `DEPORTES`, `SALUD`, `SEGURIDAD`, `OTRAS`), `titulo`, `texto`,
  `fechaPublicacion`, `publicadoPorNombre`/`publicadoPorEmail` (copia del
  actor autenticado al publicar, mismo criterio que `NormaEntity`/
  `RegistroAuditoriaEntity`). Sin `numero`: a diferencia de una norma, una
  gacetilla no tiene numeración legal que modelar. Sin edición ni
  derogación de una gacetilla ya publicada.
- Sin adjuntos/imágenes, sin integración con redes sociales externas, sin
  notificaciones a suscriptores: fuera de alcance a propósito (ver
  ADR 0027).
- **Test de aislamiento**: una gacetilla publicada en un municipio no
  aparece en el listado de otro.
- Pantalla pública de búsqueda/listado (accesible, sin cuenta) con la
  acción de publicar visible solo para quien tiene `prensa.publicar`,
  mismas convenciones de foco y anuncios que el resto del portal (mismo
  patrón de UI que `boletin`, R7: una única vista, no dos pantallas
  separadas por permiso).

Especificación completa en
[spec CD-32](../../specs/CD-32-prensa-gacetillas.md).

Queda fuera de R23, explícitamente diferido (ver ADR 0027, Pendiente de
definir): adjuntar imagen/documento a una gacetilla (depende de una
decisión de storage que el proyecto no tomó), integración con redes
sociales externas, edición/derogación de una gacetilla ya publicada,
notificación de contenido nuevo a suscriptores, y Auditoría interna/
Control de gestión y el framework de reportes/BI que necesitaría (siguen
pendientes, candidatos de una rebanada futura de esta misma fase).

### R26 · El municipio publica un evento en la agenda cultural/turística/deportiva y lo puede cancelar

**Demo**: un agente municipal (con sesión y `eventos.gestionar`) publica
un evento, "Maratón Municipal" (categoría Deporte), ubicación
"Costanera", del 15 al 15 de octubre, 9:00hs. Un vecino, sin sesión,
entra al portal público y ve la agenda ordenada por fecha (lo que viene
primero, arriba), filtra por categoría "Deporte" y por texto, y encuentra
el evento. El mismo agente lo cancela (por mal tiempo); al volver a
consultar, el vecino lo ve como "Cancelado" sin que desaparezca de la
agenda. El mismo evento no aparece en el portal de otro municipio.

Tercera y, por ahora, última rebanada construible de Fase 6 — Áreas de
imagen y control de gestión. Elegida por descarte razonado sobre
Auditoría interna/Control de gestión, reevaluada y descartada por tercera
vez consecutiva: ningún módulo funcional agregado desde R23 (`educacion`,
`espaciosverdes`) publica un evento de dominio propio, así que un tablero
cruzado seguiría necesitando tocar los módulos existentes o diseñar a las
apuradas el framework de reportes/BI pendiente desde Fase 0 — ver
[ADR 0030](../arquitectura/decisiones/0030-agenda-de-eventos-cultura-turismo-y-deporte-tercera-rebanada-de-fase-6.md),
Contexto. Cubre la parte de "Cultura, Turismo y Deportes" que Turnos (R22)
no cubrió: el catálogo funcional lista "agenda de eventos, polideportivos,
turnos deportivos" bajo esa área, y Turnos (ADR 0026) solo cubrió "turnos
deportivos" (reserva de cupo). Un catálogo de puntos de interés turístico
("polideportivos") queda fuera de esta rebanada, disponible como
candidata futura chica de la misma área.

Requiere [ADR 0030](../arquitectura/decisiones/0030-agenda-de-eventos-cultura-turismo-y-deporte-tercera-rebanada-de-fase-6.md),
que además de decidir el módulo justifica por qué esto **no** es lo mismo
que `turnos` con otro nombre: un evento es informativo (nadie se anota,
sin cupo, sin dato personal de terceros), mientras que `turnos` modela un
recurso reservable con cupo compartido bajo concurrencia. Decide también
el nombre `eventos` (no `agenda`, para no colisionar conceptualmente con
`GestionDeAgenda` de `turnos.internal`), una topología de estado nueva
para el patrón del proyecto (`PROGRAMADO → CANCELADO`, un solo salto sin
retorno — la más simple hasta ahora) y un orden de listado por
`fechaInicio` ascendente en vez de por `creadoEn` descendente (primera
desviación del criterio de orden por defecto del patrón, justificada
porque es una agenda cronológica, no un padrón).

Incluye:
- Módulo `eventos`, contratable, sin depender de `turnos` ni de ningún
  otro módulo funcional. `EventoEntity`: alta (`POST /api/eventos`)
  requiere sesión y `eventos.gestionar` (administrador y agente); listado
  (`GET /api/eventos`, filtros `categoria`/`estado`/`q`) es público,
  ordenado por `fechaInicio` ascendente.
- Campos: `categoria` (enum cerrado: `CULTURA`, `TURISMO`, `DEPORTE`,
  `OTRA`, definido desde cero, sin reutilizar `TipoDeActividad` de
  `turnos`), `nombre`, `ubicacion`, `descripcion` (opcional),
  `fechaInicio` (obligatoria), `fechaFin` (opcional, tiene que ser ≥
  `fechaInicio`), `horaInicio` (opcional), `publicadoPorNombre`/
  `publicadoPorEmail` (copia del actor autenticado al publicar).
- Cancelación (`PATCH /api/eventos/{id}/estado`, mismo permiso): única
  transición válida `PROGRAMADO → CANCELADO`, sin retorno y sin estado
  intermedio.
- **Test de aislamiento**: un evento publicado en un municipio no
  aparece ni es cancelable desde otro.
- Pantalla pública de agenda (accesible, sin cuenta) con filtros
  combinables, la acción de publicar visible solo con
  `eventos.gestionar`, y un botón de cancelar por fila (sin selector de
  destino, porque solo hay una transición posible) con confirmación
  previa, mismas convenciones de foco y anuncios
  (`role="status"`/`role="alert"`) que el resto del portal.

Especificación completa en
[spec CD-35](../../specs/CD-35-agenda-eventos-cultura-turismo-deporte.md).

Queda fuera de R26, explícitamente diferido (ver ADR 0030, Pendiente de
definir): catálogo de puntos de interés turístico, motivo de la
cancelación, eventos recurrentes, edición de los campos del alta,
integración con `turnos` para un evento puntual reservable, notificación
al vecino de eventos nuevos o cancelados, y Auditoría interna/Control de
gestión y el framework de reportes/BI que necesitaría (sin candidata
nueva de Fase 6 disponible después de esta rebanada).

## Epic: Sin fase fija — módulos sin prioridad de roadmap (CD-36)

El [roadmap](roadmap-fases.md#sin-fase-fija) deja Seguridad/Defensa Civil y
Bromatología sin fase asignada, "dependiendo de la prioridad que les dé el
municipio piloto que se consiga". Sin piloto real todavía, este Epic agrupa
las rebanadas de esos módulos a medida que se eligen por descarte razonado,
con el mismo criterio que ya usaron las rebanadas que abrieron cada fase.

### R27 · El municipio publica una alerta de Defensa Civil y registra sus recursos de emergencia, y cualquiera los consulta

**Demo**: un agente municipal (con sesión y `defensacivil.gestionar`)
publica una alerta: tipo "Meteorológica", nivel "Naranja", título "Tormenta
fuerte con caída de granizo", con su descripción y recomendaciones. Queda
"Vigente". El mismo agente registra un recurso: tipo "Refugio", nombre
"Polideportivo Municipal", dirección "Av. Libertador 1200", capacidad 200.
Queda "Activo". Un vecino, sin sesión, entra al portal público y ve la
alerta vigente junto con el listado de recursos, filtra por nivel y por
tipo, y encuentra ambos. Pasada la tormenta, el agente finaliza la alerta;
al volver a consultar, el vecino la ve como "Finalizada" (no desaparece del
listado). Ni la alerta ni el recurso aparecen en el portal de otro
municipio.

Primera rebanada del Epic Sin fase fija (CD-36). Elegida por descarte
razonado sobre Bromatología (necesita normativa de inspección específica
por municipio/provincia sin piloto real que la valide) y, dentro de
Seguridad/Defensa Civil, sobre "cámaras, monitoreo de emergencias,
protocolos" (integración de hardware/CCTV sin ningún patrón de integración
externa en el proyecto) y sobre un canal de reporte ciudadano de riesgo
(solapa con `reclamos`, R6) — ver
[ADR 0031](../arquitectura/decisiones/0031-defensa-civil-alertas-publicas-y-recursos-primera-rebanada-sin-fase-fija.md),
Contexto, que además explica por qué el módulo se llama `defensacivil` y no
`seguridad`. Sin dato de persona identificable en ninguna de las dos
entidades: ni una alerta pública ni un recurso institucional (refugio,
punto de encuentro) revelan la ubicación de una persona vulnerable, a
diferencia del riesgo que motivó la minimización de datos de Desarrollo
Social (ADR 0025) — ver ADR 0031, "Minimización de datos".

Incluye:
- Módulo `defensacivil`, contratable, con dos entidades independientes sin
  relación de esquema entre sí: `AlertaDeDefensaCivilEntity` y
  `RecursoDeDefensaCivilEntity`. Un único permiso
  `defensacivil.gestionar` (administrador y agente) cubre alta y cambio de
  estado de ambas — no hay diferencia real de sensibilidad que justifique
  separarlo (ADR 0031 §3).
- Alerta: `tipo` (Meteorológica, Inundación, Ola de calor, Incendio, Otra),
  `nivel` (Amarillo/Naranja/Rojo, la convención real del Servicio
  Meteorológico Nacional, no una escala inventada), `titulo`,
  `descripcion`, `recomendaciones`, `zonaAfectada` (texto libre, sin GIS).
  Nace `VIGENTE`; única transición posible `VIGENTE → FINALIZADA`
  (`PATCH /api/defensacivil/alertas/{id}/estado`, terminal, sin retorno).
- Recurso: `tipo` (Refugio, Punto de encuentro, Centro de acopio, Otro),
  `nombre`, `direccion` (texto libre, sin GIS), `capacidad` (opcional, sin
  relación con ninguna persona), `telefonoContacto` (opcional). Nace
  `ACTIVO`; transición libre en ambos sentidos con
  `PATCH /api/defensacivil/recursos/{id}/estado`.
- Alta protegida (`POST`, sesión + `defensacivil.gestionar`) y lectura
  pública sin sesión en ambas entidades (`GET
  /api/defensacivil/alertas`/`GET /api/defensacivil/recursos`, filtros
  combinables por tipo/nivel/estado/texto), sin ninguna ruta de escritura
  pública.
- **Test de aislamiento** (obligatorio, dos casos): una alerta y un recurso
  registrados en un municipio no son visibles ni actualizables desde otro.
- Pantalla pública única con dos secciones (Alertas y Recursos, cada una
  con su propio `<h2>`), accesible, sin cuenta para consultar, con las
  acciones de publicar/registrar y de cambiar de estado visibles solo con
  el permiso, mismas convenciones de foco y anuncios
  (`role="status"`/`role="alert"`) que el resto del portal. Las alertas
  vigentes se distinguen visualmente sin depender solo del color (el texto
  del estado sigue presente en la celda).

Especificación completa en
[spec CD-37](../../specs/CD-37-defensa-civil.md).

Queda fuera de R27, explícitamente diferido (ver ADR 0031, Pendiente de
definir): notificación push/SMS de una alerta nueva o finalizada,
geolocalización estructurada de `zonaAfectada`/`direccion`, integración con
fuentes externas de alerta temprana (SMN u otro organismo), reapertura de
una alerta finalizada, motivo de la finalización/inactivación, y cualquier
integración con cámaras, sensores o hardware de monitoreo. Bromatología
queda disponible como próxima candidata del mismo Epic sin fase fija
(CD-36).

### R28 · El municipio registra un comercio bromatológico y sus inspecciones, y cualquiera consulta el estado del padrón

**Demo**: un agente municipal (con sesión y `bromatologia.gestionar`) da de
alta un comercio: rubro "Verdulería", nombre "Verdulería Don José",
dirección "San Martín 450", habilitado desde hoy, con vencimiento en un
año. Queda "Habilitado" en el padrón. Un vecino, sin sesión, entra al
portal público, filtra por rubro "Verdulería" y lo encuentra con su estado
"Habilitado". El mismo agente registra una inspección sobre ese comercio
con resultado "Observado" y una observación interna ("faltan matayuyos en
la zona de depósito"). El padrón público pasa a mostrar el comercio como
"Observado" (la observación no es visible ahí: solo se ve con sesión). El
agente abre el historial de inspecciones del comercio (requiere sesión y
permiso) y ve la inspección registrada con su fecha, resultado y
observación. Ni el comercio ni sus inspecciones son visibles ni editables
desde otro municipio.

Segunda y última rebanada del Epic Sin fase fija (CD-36): agotados los dos
candidatos que dejaba sin fase asignada el
[roadmap](roadmap-fases.md#sin-fase-fija). [ADR 0031](../arquitectura/decisiones/0031-defensa-civil-alertas-publicas-y-recursos-primera-rebanada-sin-fase-fija.md)
ya había descartado Bromatología como primera rebanada porque el circuito
completo de una inspección real (acta, infracción, plazo de subsanación)
necesita normativa específica de cada municipio/provincia sin un piloto
real que la valide. Esta rebanada recorta ese alcance en vez de seguir
postergándolo: construye el padrón con estado y un historial de
inspecciones que **motiva** los cambios de estado, sin modelar acta,
infracción ni expediente sancionatorio — ver
[ADR 0032](../arquitectura/decisiones/0032-bromatologia-padron-de-comercios-e-historial-de-inspecciones-protegido-segunda-rebanada-sin-fase-fija.md),
Contexto. Sin dato nominal de manipuladores de alimentos (sería un dato de
salud de una persona identificable, mismo criterio de minimización que
Salud municipal/Discapacidad, ADR 0025) y sin relación de esquema con
`proveedores` (R14): son conceptos de dominio inversos, quien le vende al
municipio contra a quien el municipio fiscaliza — ver ADR 0032, Contexto.

Incluye:
- Módulo `bromatologia`, contratable, con dos entidades **relacionadas**
  por clave foránea (a diferencia de `defensacivil`):
  `ComercioBromatologicoEntity` (padrón, público) e
  `InspeccionBromatologicaEntity` (historial, protegido). Un único
  permiso `bromatologia.gestionar` (administrador y agente) cubre alta de
  comercio, alta de inspección y lectura del historial — no hay dato
  personal identificable en ninguna de las dos entidades que justifique
  separarlo (ADR 0032 §5).
- Comercio: `rubro` (Verdulería, Carnicería, Panadería, Restaurante,
  Almacén, Otro), `nombre`, `direccion` (texto libre, sin GIS),
  `fechaHabilitacion`, `fechaVencimientoHabilitacion` (posterior a la
  anterior). Nace siempre `HABILITADO`. **Sin `PATCH` directo de
  estado**: la única vía de cambio es registrar una inspección.
- Inspección: `fecha`, `resultado` (mismo enum que el estado del
  comercio: Habilitado/Observado/Clausurado), `observaciones` (texto
  libre, no pública). Al registrarse, actualiza en la misma transacción
  el `estado` del comercio al valor de `resultado` — primer módulo del
  proyecto donde el estado público de una entidad cambia como efecto de
  un historial en vez de por un `PATCH` directo. No rechaza una
  reinspección con el mismo resultado que el estado actual: es
  información de historial válida, no una acción de cambio de estado.
- `GET /api/bromatologia/comercios` es lectura pública (filtros
  combinables `rubro`/`estado`/`q`); `POST` de comercio, `POST` y **`GET`**
  de inspecciones requieren sesión y `bromatologia.gestionar` — primer
  módulo del proyecto donde el historial detrás de un estado público no
  tiene ninguna vía de lectura sin sesión (ADR 0032 §4).
- **Test de aislamiento** (obligatorio, tres casos): un comercio de un
  municipio no aparece en el padrón de otro; un `POST`/`GET` de
  inspecciones sobre el id de un comercio ajeno da 404 sin revelar que
  existe en otro tenant; una inspección de un municipio no aparece en
  ningún listado de otro.
- Pantalla pública con el padrón de comercios (filtros por rubro, estado
  y texto), alta de comercio protegida, y por cada fila un panel
  expandible (solo con el permiso) con el historial de inspecciones de
  ese comercio y el formulario para registrar una inspección nueva,
  mismas convenciones de foco y anuncios (`role="status"`/`role="alert"`,
  `aria-expanded`) que el resto del portal.

Especificación completa en
[spec CD-38](../../specs/CD-38-bromatologia.md).

Queda fuera de R28, explícitamente diferido (ver ADR 0032, Pendiente de
definir): acta de infracción, tipificación por ordenanza, plazo de
subsanación con reinspección obligatoria y expediente sancionatorio;
renovación de habilitación y vencimiento automático por fecha (no hay
infraestructura de jobs/cron en el proyecto); notificación al comercio de
un resultado adverso o de un vencimiento próximo; libreta sanitaria de
manipuladores; CUIT/titular del comercio y su eventual cruce con
`proveedores`; geolocalización estructurada de `direccion`; edición de
los campos del alta del comercio después de creado. Con esta rebanada
queda agotado el Epic Sin fase fija (CD-36): no quedan más candidatos sin
fase asignada en el roadmap actual.

## Epic: Fase 7 — Inteligencia artificial

Clasificador de reclamos (candidato a adelantarse a Fase 1), asistente
ciudadano con RAG, copiloto interno, optimización de rutas, detección de
anomalías en licitaciones.
