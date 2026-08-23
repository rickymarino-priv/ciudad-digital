# 0001 - Multi-tenant en backend y frontend, con base de datos por tenant

- Estado: Aceptada
- Fecha: 2026-08-23

## Contexto

El producto se ofrece desde el día 1 a múltiples municipios de Argentina
como SaaS (no como desarrollo a medida por cliente). Varios de esos
municipios son organismos públicos con requisitos de aislamiento de datos
(pliegos, Tribunal de Cuentas) que hacen deseable poder garantizar que los
datos de un municipio no conviven en la misma base que los de otro.

## Decisión

- El backend es multi-tenant: un único código base sirve a todos los
  municipios.
- El frontend es multi-tenant: una única aplicación, con identidad visual
  (estilo/branding) distinta por tenant.
- Cada tenant (municipio) tiene **su propia base de datos**, no un esquema
  compartido con discriminador de tenant.

## Alternativas consideradas

- **Shared schema con `tenant_id`**: más simple y barato de operar con un
  equipo chico, pero no da aislamiento real de datos entre municipios y
  dificulta cumplir pliegos que exigen separación de datos. Descartada por
  no alinear con el modelo de negocio (venta a organismos públicos).
- **Schema-per-tenant en una misma base**: aislamiento intermedio, pero
  complica igual la gestión de migraciones a gran escala sin dar el mismo
  nivel de garantía que una base separada.

## Consecuencias

- Se necesita un pipeline de aprovisionamiento de tenant (alta de municipio
  → creación de base, migraciones, seed de configuración) desde etapas
  tempranas: no es viable de forma manual pasado el segundo o tercer
  tenant.
- El backend necesita resolución dinámica de datasource por tenant
  (identificar el tenant en cada request y enrutar a su base
  correspondiente).
- El costo operativo de pooling de conexiones (una base por municipio)
  crece con la cantidad de tenants; queda pendiente de diseño el mecanismo
  de gestión de pools a escala (no bloquea el arranque con pocos tenants
  piloto).
- Las migraciones de esquema deben ejecutarse contra N bases en cada
  release; se necesita un mecanismo que reporte el estado de la migración
  por tenant en vez de asumir que todas migran de forma atómica.

## Pendiente de definir

- Mecanismo concreto de resolución de tenant (subdominio, dominio propio,
  header) — a definir en el diseño técnico de la Fase 0.
- Estrategia de theming del frontend (tokens dinámicos vs. build por
  tenant) — propuesta preliminar: tokens dinámicos cargados en runtime
  desde la configuración del tenant, pendiente de confirmación.
- Arquitectura del backend (monolito modular vs. microservicios) — todavía
  no es una decisión formal, solo una recomendación en discusión.
