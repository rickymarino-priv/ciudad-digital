# Visión y alcance del producto

## Pitch

Un sistema integral de gestión municipal para Argentina, con frontend web y
backend Java, que cubra todas las áreas de un municipio (internas, atención
al ciudadano, proveedores y licitaciones), ofrecido como producto
multi-tenant a municipios pequeños y medianos, construido en etapas, con uso
extensivo de inteligencia artificial una vez consolidada la base del
producto.

## Mercado objetivo

Municipios pequeños y medianos de Argentina. Se asume que varios ya tienen
parte de su gestión digitalizada con sistemas propios o de terceros, por lo
que la interoperabilidad con esos sistemas es un requisito de producto desde
el diseño inicial, no un agregado posterior.

## Modelo de negocio

Producto multi-tenant (SaaS) desde el día 1: un único código base, cada
municipio se da de alta como tenant. No se plantea como un desarrollo a
medida por municipio, aunque el primer cliente real probablemente funcione
como piloto/diseño-partner para validar flujos y conseguir acceso a
normativa real.

## Equipo y forma de trabajo

Equipo chico (2-5 personas). Esto condiciona directamente el alcance de cada
etapa: se prioriza lo que un equipo así puede sostener en producción
(monolito modular antes que microservicios, por ejemplo) por sobre lo que
sería "ideal" con más recursos.

## Alcance

El objetivo de largo plazo es cubrir el municipio completo: todas las áreas
internas, el ciudadano y los proveedores/licitaciones (ver
[catálogo funcional](catalogo-funcional.md)). El volumen de alcance no es un
freno — se aborda **en etapas** (ver [roadmap por fases](roadmap-fases.md)),
no de una sola vez.

## Decisiones de producto ya tomadas

- **Multi-tenant en backend y en frontend** desde el día 1.
- **Una base de datos por tenant** (no schema compartido), para poder
  ofrecer aislamiento real de datos entre municipios — relevante para
  requisitos de organismos públicos (Tribunal de Cuentas, pliegos que exigen
  separación de datos).
- **Frontend con identidad visual distinta por tenant** (estilo/branding
  propio por municipio, no necesariamente estructura o layout distintos).
- **Inteligencia artificial en una etapa posterior**, no en el MVP. Es un
  objetivo central del producto a mediano plazo, tanto para las áreas del
  municipio como para el ciudadano, pero se prioriza consolidar los módulos
  base antes de invertir en IA.

El detalle de cómo se implementan estas decisiones (mecanismo de
multi-tenancy, estrategia de theming, etc.) se registra como
[decisiones de arquitectura](../arquitectura/decisiones/) a medida que se
cierran — algunas todavía están abiertas.

## Riesgos y supuestos a validar

- La carga normativa de módulos como Compras/Licitaciones y
  Presupuesto/Contabilidad varía por provincia — el diseño de esos módulos
  necesita feedback de municipios reales, no solo investigación previa.
- La integración con sistemas legados de cada municipio es un riesgo
  recurrente de venta: municipios chicos suelen no tener equipo técnico
  propio para integrar por su cuenta.
- El aislamiento por base de datos por tenant es una decisión de producto
  tomada; su costo operativo a medida que crece la cantidad de municipios es
  un tema abierto de arquitectura (ver ADRs).
