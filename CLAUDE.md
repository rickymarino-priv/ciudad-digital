# Ciudad Digital — reglas del proyecto

## Antes de empezar cualquier tarea

1. **Cargar lo que la tarea necesita, no el repositorio entero** — por lo que vas a tocar
2. **Qué NO cargar de entrada**: el [`README.md`](README.md) raíz **entero** —es la visión de sistema, no el contexto de una tarea: se le lee la sección que haga falta—, ni `plans/`, `specs/` o `reports/` salvo que la tarea los nombre.
3. **Lo que no esté arriba**: [`docs/README.md`](docs/README.md), índice de toda la documentación. Diseño, arquitectura, cambio de contrato o multi-servicio → invocar las skills del repo (§Skills).


## Regla principal: avance semanal demostrable

El proyecto avanza en **rebanadas verticales**, no en tickets horizontales
de infraestructura. Cada semana tiene que haber algo que se pueda **ver
funcionando**, no un conjunto de piezas sueltas que "todavía no se
enchufan entre sí".

Una rebanada válida atraviesa todas las capas que haga falta (base de
datos, backend, frontend) para entregar algo demostrable. Ejemplo de
rebanada bien formulada:

> Dar de alta el municipio "San Martín" desde cero y ver su portal en
> `sanmartin.localhost` con su logo y colores, con login funcionando.

Ejemplo de lo que **no** es una rebanada: "implementar el routing dinámico
de datasource". Es una pieza, no un avance visible.

### Cómo se planifica

- Al planificar trabajo, la unidad es la rebanada demostrable, no el
  componente técnico.
- Las piezas técnicas existen, pero como parte de una rebanada, no como
  tickets propios que se completan aisladamente.
- Si una rebanada no se puede demostrar al final de la semana, es
  demasiado grande: hay que partirla en rebanadas más chicas que **igual
  se vean**, no en capas horizontales.

## Qué viaja dentro de cada rebanada (no se difiere)

Dos cosas son criterio de completitud de la rebanada, no tickets
posteriores:

1. **Aislamiento entre tenants.** Si la rebanada toca datos, lleva su test
   de aislamiento. No es "seguridad para después": es la propiedad de
   corrección central del producto. Una fuga de datos entre municipios no
   es un bug parcheable — es el fin comercial del producto y un problema
   legal con datos de ciudadanos. Además es lo que más fácil se rompe
   trabajando rápido en vertical, porque se toca el datasource desde
   muchos lugares.
2. **Accesibilidad (WCAG).** Si la rebanada agrega pantallas, salen
   accesibles. Retrofitear accesibilidad sobre pantallas ya construidas es
   mucho más caro que hacerlas bien de entrada, y es requisito del
   producto por tratarse de portales públicos.

## Qué sí se difiere a tickets posteriores

- Endurecimiento de seguridad: rate limiting, hardening de headers,
  revisión de dependencias, pentesting.
- Optimización de performance sin problema medido.
- Refactors de calidad que no bloquean la rebanada siguiente.

## Flujo de trabajo con git

- `main` es **producción**. No se trabaja ni se commitea directamente
  sobre `main`.
- `develop` es la rama de integración: es de donde sale y a donde vuelve
  todo el trabajo.
- Cada ticket de Jira se resuelve en su **propia rama**, creada siempre a
  partir de lo último que tenga `develop` (no de otra rama de trabajo ni
  de `main`).
- El nombre de la rama referencia al ticket: clave de Jira más una
  descripción corta en kebab-case. Por ejemplo:
  `CD-12-alta-de-municipio-desde-cero`.
- El push va sobre esa rama, y de ahí sale un **PR contra `develop`**.
  Nunca se pushea a `develop` directamente.
- El merge del PR lo hace **una persona**, no el agente. Una vez mergeado,
  la rama temporal se puede borrar local y remotamente.

### Qué unidad de trabajo mapea a una rama

La rama es la **rebanada**, no la pieza técnica. Una rama por componente
horizontal reintroduciría, disfrazados de PR, los tickets sueltos que la
regla de arriba evita: el PR tiene que ser algo que se pueda ver
funcionando.

Si una rebanada da un PR demasiado grande para revisar, se parte en ramas
apiladas sobre la rama de la rebanada, y la rebanada entra a `develop`
como un único PR.

## Contexto del proyecto

- Producto multi-tenant para municipios argentinos. Ver
  [visión y alcance](docs/producto/vision-y-alcance.md).
- Backend Java + Spring Modulith, frontend React, PostgreSQL con una base
  por tenant. Ver [diseño de Fase 0](docs/arquitectura/diseno-fase-0.md).
- Las decisiones de arquitectura se registran como ADRs en
  `docs/arquitectura/decisiones/`. Antes de proponer un cambio
  arquitectónico, revisar si ya hay un ADR que lo cubra; si la decisión
  cambia, se escribe un ADR nuevo que reemplace al anterior en lugar de
  editar el viejo.
