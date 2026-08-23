# 0004 - Resolución de tenant por subdominio, con dominio propio como add-on futuro

- Estado: Aceptada
- Fecha: 2026-08-23

## Contexto

Con DB-por-tenant ([ADR 0001](0001-multi-tenant-con-bd-por-tenant.md)) hace
falta un mecanismo para que, en cada request, el sistema sepa a qué
municipio corresponde: qué base de datos usar y qué configuración visual
servir. Esto requiere una **base de control** central (una sola, no por
tenant) que liste los municipios dados de alta y sus datos de conexión,
dominio y tema.

## Decisión

- El mecanismo de resolución de tenant para el MVP es **subdominio**
  (`<municipio>.tuproducto.com.ar`), cubierto por un certificado wildcard
  único que aplica a todo tenant presente y futuro sin trabajo adicional de
  alta.
- El modelo de datos del tenant incluye desde el día 1 un campo de
  **dominio personalizado** (nullable), aunque no se implemente la
  validación/emisión de certificado por dominio propio todavía. Esto evita
  tener que migrar tenants ya en producción el día que un municipio pida su
  propio dominio.
- La resolución se implementa como un filtro/interceptor temprano en el
  pipeline del backend, que lee el header `Host`, consulta la base de
  control, y deja el tenant resuelto disponible para el resto del request
  (datasource router y config de tema del frontend).

## Alternativas consideradas

- **Dominio propio del municipio desde el MVP**: mejor imagen para un
  organismo público, pero cada alta nueva requiere validación de dominio y
  certificado propio — se descarta para el MVP por el costo de
  aprovisionamiento, no definitivamente (queda como add-on).
- **Header o path-based** (`/sanmartin/...`): más simple de resolver en el
  backend, pero peor para un portal ciudadano público (no se ve "propio",
  complica cookies/sesión). Descartado.

## Consecuencias

- El pipeline de aprovisionamiento de tenant (ver diseño pendiente) no
  necesita automatizar emisión de certificados por dominio en el MVP.
- Cuando un municipio pida dominio propio, el trabajo es incremental
  (agregar validación + emisión de certificado), no un rediseño del modelo
  de datos del tenant.
