# Backlog inicial

Estructura de Epics e historias para cargar en Jira. Los Epics siguen las
fases del [roadmap](roadmap-fases.md). Solo la Fase 0 tiene historias
detalladas: el resto son Epics contenedores, a detallar cuando se diseñe
cada fase.

---

## Epic: Fase 0 — Fundación de plataforma

Base técnica multi-tenant sobre la que se construyen todos los módulos
funcionales. No es vendible por sí sola. Diseño técnico completo en
[diseño de Fase 0](../arquitectura/diseno-fase-0.md).

### Historias

**F0-1 · Esqueleto del proyecto backend con Spring Modulith**
Proyecto Java con Spring Boot y Spring Modulith configurado, incluyendo el
test de verificación de límites entre módulos (`ApplicationModules.verify()`)
corriendo en el build. Referencia:
[ADR 0003](../arquitectura/decisiones/0003-spring-modulith-para-el-backend.md).

**F0-2 · Base de control y modelo de datos del tenant**
Esquema de la base de control con la tabla de tenants: columnas explícitas
(`id`, `slug`, `nombre_municipio`, `subdominio`, `dominio_personalizado`,
`estado`, `nombre_base_datos`, `fecha_alta`) y columna `config` JSON (tema,
módulos habilitados). Migraciones Flyway de la base de control. Referencia:
[ADR 0007](../arquitectura/decisiones/0007-modelo-de-datos-del-tenant.md).

**F0-3 · Resolución de tenant por subdominio**
Filtro/interceptor que resuelve el tenant a partir del header `Host`,
consulta la base de control y deja el tenant disponible para el resto del
request. Manejo del caso "tenant inexistente o inactivo". Referencia:
[ADR 0004](../arquitectura/decisiones/0004-resolucion-de-tenant-por-subdominio.md).

**F0-4 · Routing dinámico de datasource por tenant**
Datasource que enruta a la base del tenant resuelto, armando la conexión
con credenciales compartidas de aplicación + `nombre_base_datos`. Incluye
la estrategia de pooling y su límite de pools activos. Referencia:
[ADR 0001](../arquitectura/decisiones/0001-multi-tenant-con-bd-por-tenant.md).

**F0-5 · Migraciones de esquema por tenant**
Ejecución de migraciones Flyway contra las N bases de tenants, con reporte
de estado por tenant (no asumir migración atómica de todos). Referencia:
[ADR 0001](../arquitectura/decisiones/0001-multi-tenant-con-bd-por-tenant.md).

**F0-6 · Módulo de administración de tenants: alta de municipio**
Proceso de aprovisionamiento con estado explícito (`pendiente →
aprovisionando → activo → error`): crear base física, correr migraciones,
sembrar tema default y usuario admin, activar en la base de control.
Referencia:
[ADR 0005](../arquitectura/decisiones/0005-aprovisionamiento-de-tenant.md).
*Nota: es la mecánica de aprovisionamiento, no la consola comercial (Fase 2).*

**F0-7 · Identidad y accesos**
Autenticación y modelo de roles/permisos granulares por área y módulo,
scopeado por tenant.

**F0-8 · Entitlement de módulos con gating en backend**
Interceptor que rechaza requests a módulos no contratados por el tenant,
leyendo `config.modulos_habilitados`. Desacoplado del estado de pago.
Referencia:
[ADR 0009](../arquitectura/decisiones/0009-modelo-comercial-y-entitlement.md).

**F0-9 · Esqueleto del frontend React**
Proyecto React con convenciones de organización por módulo definidas,
librería de componentes accesibles headless elegida, routing y manejo de
estado. Referencia:
[ADR 0008](../arquitectura/decisiones/0008-react-como-framework-de-frontend.md).

**F0-10 · Theming dinámico por tokens**
Endpoint que sirve el tema del tenant desde la base de control, y
aplicación de tokens (CSS custom properties) en el frontend al arrancar.
Referencia:
[ADR 0006](../arquitectura/decisiones/0006-theming-dinamico-por-tokens.md).

**F0-11 · Estándar de accesibilidad WCAG**
Definición del nivel objetivo de conformidad, checklist para componentes y
verificación automatizada en el pipeline.

**F0-12 · Motor de notificaciones multicanal**
Servicio transversal de notificaciones (email como mínimo en Fase 0; SMS,
WhatsApp y push como canales incorporables después).

**F0-13 · Motor de expediente/workflow configurable (base mínima)**
Núcleo del expediente electrónico y workflow por circuito, sobre el que se
apoyan los módulos funcionales de fases siguientes.

**F0-14 · Framework de reportes/BI**
Motor de reportes reutilizable, sin los tableros concretos de cada área.

**F0-15 · Auditoría y trazabilidad transversal**
Registro de quién hizo qué, cuándo y sobre qué expediente. Requisito casi
obligatorio en sector público.

---

## Epic: Fase 1 — MVP vendible / módulos ancla

Reclamos ciudadanos (311), Mesa de Entradas + subset de Trámites a
Distancia, Boletín Oficial digital, Transparencia activa básica,
Cementerio. Bajo acoplamiento con sistemas legados: no depende de la capa
de adaptadores.

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
