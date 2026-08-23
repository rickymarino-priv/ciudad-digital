# 0003 - Spring Modulith como arquitectura del backend

- Estado: Aceptada
- Fecha: 2026-08-23

## Contexto

El roadmap funcional contempla más de 30 módulos a mediano/largo plazo (ver
[catálogo funcional](../../producto/catalogo-funcional.md)), construidos en
etapas por un equipo chico (2-5 personas). Microservicios desde el inicio
implica un costo operativo (orquestación, observabilidad, contratos entre
servicios) que el equipo no puede sostener en esta etapa. Un monolito
"por convención", sin ninguna herramienta que vigile los límites entre
módulos, tiene alto riesgo de erosionarse a medida que crece la cantidad de
módulos y rota el equipo.

## Decisión

El backend se construye como un monolito modular usando
[Spring Modulith](https://spring.io/projects/spring-modulith). Los módulos
se definen por estructura de paquetes; los límites entre módulos se
verifican automáticamente en el build (`ApplicationModules.verify()`), y la
comunicación entre módulos se favorece vía eventos de dominio en lugar de
llamadas directas entre servicios internos.

## Alternativas consideradas

- **Monolito "por convención"** (paquetes por dominio sin verificación
  automática): descartado por alto riesgo de acoplamiento no controlado con
  30+ módulos y equipo rotando.
- **Monorepo multi-módulo Maven/Gradle** (cada módulo funcional como
  artefacto de build separado): da un camino más mecánico hacia extraer un
  módulo a microservicio, pero suma complejidad de tooling desde el día 1
  que no se justifica con equipo chico. Se descarta por ahora, no
  definitivamente.
- **Microservicios desde el inicio**: descartado en discusiones previas de
  producto por el costo operativo, incompatible con el tamaño del equipo.

## Consecuencias

- Cada módulo funcional del [catálogo](../../producto/catalogo-funcional.md)
  se implementa como un application module de Spring Modulith.
- La integración entre módulos (ej. "al cerrar un Reclamo, notificar al
  ciudadano") se modela con eventos de dominio, no con llamadas directas
  entre servicios de distintos módulos.
- Queda disponible el patrón "core + satélites": si en el futuro un módulo
  puntual necesita aislarse como servicio separado (candidato: el motor de
  IA de la Fase 7), Spring Modulith documenta un camino de extracción desde
  un application module hacia un servicio independiente.
- DB-por-tenant (ver [ADR 0001](0001-multi-tenant-con-bd-por-tenant.md)) es
  ortogonal a esta decisión: dentro de la base de un tenant, todos los
  módulos conviven en la misma base física: la separación es de código y de
  tablas, no de infraestructura.

## Pendiente de definir

- Si además se adopta convención de esquemas o prefijos de tabla por módulo
  dentro de la base de cada tenant.
