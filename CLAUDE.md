# Ciudad Digital — reglas del proyecto

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
