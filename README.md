# Ciudad Digital

Plataforma web integral para la gestión municipal en Argentina: un sistema
multi-tenant (backend Java + frontend web) pensado para atender a todas las
áreas de un municipio, a los ciudadanos y a los proveedores, con capacidad de
integrarse con sistemas que cada municipio ya tenga digitalizados.

## Estado del proyecto

En etapa de **definición de producto y arquitectura**. Todavía no hay código:
el repositorio contiene la documentación de producto y las decisiones de
arquitectura que se van tomando antes de empezar a implementar.

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
- [Decisiones de arquitectura (ADRs)](docs/arquitectura/decisiones/)

## Stack (alto nivel)

- Backend: Java
- Frontend: web

El detalle técnico (framework específico, estrategia concreta de
multi-tenancy, etc.) se define progresivamente en las
[decisiones de arquitectura](docs/arquitectura/decisiones/) a medida que se
cierran.
