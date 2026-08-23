# 0008 - React como framework de frontend

- Estado: Aceptada
- Fecha: 2026-08-23

## Contexto

El frontend es una única build multi-tenant con theming dinámico por tokens
([ADR 0006](0006-theming-dinamico-por-tokens.md)) y accesibilidad WCAG como
requisito de Fase 0. A mediano plazo la aplicación va a cubrir 30+ módulos
funcionales, mantenida por un equipo chico.

## Decisión

El frontend se construye con **React**.

El factor decisivo es la experiencia previa del equipo en React. Se evaluó
Angular como alternativa principal, pero el equipo lo conoce poco, y esa
diferencia de experiencia pesa más que las ventajas estructurales que
Angular ofrecería.

## Alternativas consideradas

- **Angular**: framework opinado con estructura impuesta (módulos, DI,
  routing y forms incluidos), lo que jugaría a favor con 30+ módulos y
  equipo rotando, y con buena analogía conceptual para quien viene de
  Java/Spring. Descartado por falta de experiencia del equipo.
- **Vue**: buen balance y curva suave, pero ecosistema de componentes
  accesibles menor y menos disponibilidad de gente con experiencia en el
  mercado local.
- **Svelte / SolidJS**: mejor rendimiento y menos código, pero ecosistema
  chico para un proyecto de esta escala y más difícil de contratar.
  Descartados por riesgo de equipo, no por calidad técnica.

## Consecuencias

- Para cumplir WCAG sin construir cada componente desde cero, conviene
  apoyarse en una librería de componentes accesibles headless (candidatos:
  Radix UI, React Aria) — headless es importante porque los componentes
  tienen que poder estilarse libremente con los tokens de cada tenant.
- React no impone estructura de proyecto: con 30+ módulos previstos, el
  equipo necesita definir e imponer sus propias convenciones de
  organización (routing, estado, formularios, límites entre módulos) como
  trabajo explícito, no emergente. Es el costo principal de esta decisión
  frente a Angular.

## Pendiente de definir

- Librería de componentes accesibles headless.
- Convenciones de organización del código por módulo funcional.
- Manejo de estado y de formularios.
