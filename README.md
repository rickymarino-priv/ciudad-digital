# Ciudad Digital

Plataforma web integral para la gestión municipal en Argentina: un sistema
multi-tenant (backend Java + frontend web) pensado para atender a todas las
áreas de un municipio, a los ciudadanos y a los proveedores, con capacidad de
integrarse con sistemas que cada municipio ya tenga digitalizados.

## Estado del proyecto

En **Fase 0** (fundación de plataforma). Está terminada la rebanada **R1 ·
Dos municipios, dos marcas**: una única build sirve portales con la
identidad visual de cada municipio, resuelto por subdominio.

Para levantarlo, ver [entorno de desarrollo](docs/desarrollo.md).

## Para quién es

Municipios pequeños y medianos de Argentina que necesitan digitalizar su
gestión (áreas internas, atención al ciudadano, proveedores y licitaciones)
sin depender de un desarrollo a medida por municipio.

## Enfoque

- **Multi-tenant desde el día 1**: un solo producto, cada municipio como
  tenant con su propia base de datos y su propia identidad visual.
- **Modular por etapas**: no se construye todo el municipio de una vez — ver
  el [roadmap por fases](docs/producto/roadmap-fases.md).
- **Interoperable**: pensado para convivir con sistemas que el municipio ya
  tiene digitalizados, no para reemplazarlos de un día para el otro.
- **IA como etapa posterior**: se prioriza tener una base sólida de producto
  antes de incorporar inteligencia artificial en las áreas del municipio y en
  la atención al ciudadano.

## Documentación

- [Visión y alcance del producto](docs/producto/vision-y-alcance.md)
- [Catálogo funcional](docs/producto/catalogo-funcional.md)
- [Roadmap por fases](docs/producto/roadmap-fases.md)
- [Modelo comercial](docs/producto/modelo-comercial.md)
- [Backlog inicial](docs/producto/backlog-inicial.md)
- [Entorno de desarrollo](docs/desarrollo.md)
- [Diseño técnico de Fase 0](docs/arquitectura/diseno-fase-0.md)
- [Decisiones de arquitectura (ADRs)](docs/arquitectura/decisiones/)

## Stack (alto nivel)

- Backend: Java con [Spring Modulith](https://spring.io/projects/spring-modulith)
  (monolito modular)
- Persistencia: PostgreSQL, una base por tenant
- Frontend: React, build única con theming dinámico por tenant

El detalle está en el [diseño técnico de Fase 0](docs/arquitectura/diseno-fase-0.md)
y en las [decisiones de arquitectura](docs/arquitectura/decisiones/), que se
completan a medida que se cierran.
