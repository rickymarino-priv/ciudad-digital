# 0002 - Inteligencia artificial diferida a una fase posterior

- Estado: Aceptada
- Fecha: 2026-08-23

## Contexto

El uso de inteligencia artificial (para las áreas del municipio y para los
ciudadanos) es un objetivo central del producto a mediano/largo plazo, no
un agregado cosmético. Al mismo tiempo, el equipo es chico (2-5 personas) y
el producto recién arranca, sin módulos base ni clientes reales todavía.

## Decisión

La IA no forma parte del MVP ni de las primeras fases del roadmap. Se
posterga a la [Fase 7](../../producto/roadmap-fases.md#fase-7--inteligencia-artificial),
después de haber consolidado los módulos ancla (Fase 1) y de tener volumen
de uso real en producción.

Excepción a evaluar: el clasificador de reclamos ciudadanos es candidato a
adelantarse hacia la Fase 1, por ser un caso de uso acotado y barato de
justificar frente a los módulos ancla (Reclamos/311).

## Consecuencias

- El diseño de plataforma de Fase 0 no necesita resolver infraestructura de
  IA (RAG, vector store, etc.) desde el inicio, lo que reduce el alcance
  inicial.
- Sí conviene que el modelo de datos de módulos como Reclamos y Compras
  quede preparado para acumular el historial que después va a alimentar
  casos de uso de IA (clasificación, anti-fraude), aunque esos casos de uso
  no se implementen todavía.
