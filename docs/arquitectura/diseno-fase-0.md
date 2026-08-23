# Diseño técnico de Fase 0

Resumen consolidado de las decisiones de arquitectura para la
[Fase 0 del roadmap](../producto/roadmap-fases.md#fase-0--fundación-de-plataforma)
(fundación de plataforma). El detalle y las alternativas consideradas de
cada decisión están en su ADR correspondiente.

## 1. Arquitectura del backend

**Spring Modulith** ([ADR 0003](decisiones/0003-spring-modulith-para-el-backend.md)).
Monolito modular: módulos definidos por paquetes, límites verificados en
build, comunicación entre módulos vía eventos de dominio. Se descartan
microservicios desde el inicio por costo operativo incompatible con el
tamaño del equipo. Queda abierto el patrón "core + satélites" para aislar
un módulo puntual (candidato: motor de IA de Fase 7) el día que haga falta.

## 2. Multi-tenancy y resolución de tenant

- **Una base de datos por tenant** ([ADR 0001](decisiones/0001-multi-tenant-con-bd-por-tenant.md)),
  para dar aislamiento real de datos entre municipios.
- **Bases lógicas separadas en un mismo motor Postgres compartido**
  ([ADR 0005](decisiones/0005-aprovisionamiento-de-tenant.md)), no
  instancias dedicadas por tenant, salvo excepción contractual puntual.
- **Resolución por subdominio** (`<municipio>.tuproducto.com.ar`,
  [ADR 0004](decisiones/0004-resolucion-de-tenant-por-subdominio.md)),
  cubierto por certificado wildcard. Dominio propio del municipio queda
  como add-on futuro, ya previsto en el modelo de datos.
- Una **base de control** central (no por tenant) lista los municipios
  dados de alta con sus datos de conexión, dominio y tema
  ([ADR 0007](decisiones/0007-modelo-de-datos-del-tenant.md)).
- Un filtro/interceptor temprano en el backend resuelve el tenant a partir
  del header `Host` en cada request, consultando la base de control.

### Unidades de persistencia

La base de control y las bases de municipio tienen esquemas distintos, así
que cada una necesita su propio `EntityManagerFactory`. El reparto es por
paquete: las entidades bajo `tenants` van a la base de control y las que
están bajo `municipio` a la base del tenant en curso.

El datasource de tenants resuelve la base a partir del municipio del
request y **falla si no hay ninguno resuelto**: un valor por defecto
significaría escribir los datos de un municipio en la base de otro.

## 3. Aprovisionamiento de tenant

**Módulo interno de administración de tenants**
([ADR 0005](decisiones/0005-aprovisionamiento-de-tenant.md)), no un script
externo ni un pipeline de IaC. El alta de un municipio se modela como un
proceso con estado explícito:

```
pendiente → aprovisionando → activo
                              → error
```

Pasos: crear la base física → correr migraciones Flyway → sembrar tema
default y usuario admin del municipio → activar el tenant en la base de
control.

## 4. Theming del frontend

**Tokens dinámicos** (CSS custom properties) sobre una única build de
frontend para todos los tenants
([ADR 0006](decisiones/0006-theming-dinamico-por-tokens.md)). El tema se
resuelve en runtime vía `fetch` client-side según el tenant resuelto por
subdominio, con la configuración de tema almacenada en la base de control
(no en la base operativa del tenant). Se acepta un flash breve sin
branding correcto en el MVP; inyección server-side queda como mejora
futura, condicionada a una decisión de framework/SSR todavía no tomada.

## 5. Frontend

**React** ([ADR 0008](decisiones/0008-react-como-framework-de-frontend.md)),
elegido por la experiencia previa del equipo. Requiere apoyarse en una
librería de componentes accesibles headless para cumplir WCAG sin construir
todo desde cero, y definir explícitamente convenciones de organización del
código (React no las impone y el roadmap prevé 30+ módulos).

## 6. Entitlement de módulos

**Gating en backend** ([ADR 0009](decisiones/0009-modelo-comercial-y-entitlement.md)):
un interceptor rechaza requests a módulos que el tenant no tiene
contratados; el frontend además los oculta, pero eso es UX, no enforcement.
El módulo de Spring Modulith es la unidad de gating, y coincide con la
unidad comercial del [modelo comercial](../producto/modelo-comercial.md).

El entitlement está desacoplado del estado de pago: un módulo se apaga por
fin de contrato (decisión manual), nunca automáticamente por una factura
atrasada.

## 7. Modelo de datos del tenant

Modelo híbrido en la base de control
([ADR 0007](decisiones/0007-modelo-de-datos-del-tenant.md)): columnas
explícitas para lo estructural (`slug`, `subdominio`,
`dominio_personalizado`, `estado`, `nombre_base_datos`, `fecha_alta`) y una
columna `config` en JSON para lo variable (tema visual, módulos
habilitados por tenant). No se almacenan credenciales de base de datos por
tenant: se arman combinando credenciales compartidas de aplicación con
`nombre_base_datos` de cada fila.

## Qué queda fuera de este diseño (explícitamente diferido)

- Inyección de tokens de tema server-side (depende de decisión de
  framework/SSR aún no tomada).
- Alta de tenant self-service (el módulo de administración lo habilita a
  futuro, no se implementa en Fase 0).
- Aprovisionamiento de instancia dedicada por tenant (caso excepcional, se
  resuelve puntualmente si aparece, no como flujo general).
- Consolas comerciales (del proveedor y del municipio): diferidas a Fases
  2 y 3 respectivamente. No confundir con el módulo de administración de
  tenants, que sí es Fase 0 — ver
  [modelo comercial](../producto/modelo-comercial.md).
- Integración con facturación electrónica de ARCA.
- Librería de componentes accesibles, manejo de estado y convenciones de
  organización del frontend ([ADR 0008](decisiones/0008-react-como-framework-de-frontend.md)).
- Todo lo relacionado a IA ([ADR 0002](decisiones/0002-ia-diferida-a-fase-posterior.md)).
