# 0005 - Aprovisionamiento de tenant: bases compartidas + módulo interno de administración

- Estado: Aceptada
- Fecha: 2026-08-23

## Contexto

Con DB-por-tenant ([ADR 0001](0001-multi-tenant-con-bd-por-tenant.md)) el
alta de cada municipio nuevo implica crear una base de datos física,
migrarla y sembrarla con configuración inicial. Con equipo chico (2-5
personas) esto no puede depender de pasos manuales sostenidos a mano a
medida que crece la cantidad de tenants.

## Decisión

**Infraestructura física de las bases**: las bases de los tenants viven
como bases lógicas separadas dentro de un mismo motor Postgres gestionado
(mismo servidor/cluster), no en instancias dedicadas por tenant. Se admite
como excepción mover un tenant puntual a instancia dedicada si un contrato
específico lo exige — no es el diseño general.

**Orquestación del alta**: el aprovisionamiento se implementa como un
módulo interno de administración de tenants dentro del propio backend (un
application module más de Spring Modulith, ver
[ADR 0003](0003-spring-modulith-para-el-backend.md)), no como un script
externo ni como pipeline de infraestructura como código. El alta se modela
como un proceso con estado explícito: `pendiente → aprovisionando → activo
→ error`.

Pasos que orquesta el módulo:
1. Crear la base física del tenant.
2. Correr las migraciones (Flyway) contra esa base.
3. Sembrar configuración inicial: tema por defecto, usuario admin del
   municipio.
4. Activar el tenant en la base de control — recién ahí queda visible para
   el mecanismo de resolución de tenant
   ([ADR 0004](0004-resolucion-de-tenant-por-subdominio.md)).

## Alternativas consideradas

- **Script manual corrido a mano**: suficiente para los primeros 2-3
  pilotos, pero propenso a error humano y sin trazabilidad de en qué paso
  falló un alta. Descartado como mecanismo permanente.
- **Pipeline de infraestructura como código (Terraform u otro) para cada
  alta**: se justifica solo si el aprovisionamiento involucra
  infraestructura real (ej. el caso excepcional de instancia dedicada), no
  para el caso general de bases lógicas compartidas.
- **Instancia/cluster dedicado por tenant como default**: aislamiento de
  recursos superior, pero costo operativo inviable para el equipo actual
  como diseño general.

## Consecuencias

- El estado explícito del alta (`pendiente/aprovisionando/activo/error`)
  da trazabilidad real y es la base natural para, en el futuro, ofrecer
  alta self-service si el negocio lo pide (no se implementa todavía).
- El backend necesita permisos para crear bases de datos nuevas en el
  motor Postgres compartido, no solo para operar sobre bases existentes.
- Si más adelante un tenant necesita instancia dedicada, ese caso se
  resuelve como una extensión puntual del proceso de alta, no como un
  rediseño del mecanismo general.
