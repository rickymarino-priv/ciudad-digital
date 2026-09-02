# 0034 - Auditoría interna / Control de gestión: ampliación del tablero de reportes, sin módulo nuevo ni visibilidad por rol

- Estado: Aceptada
- Fecha: 2026-09-02

## Contexto

"Auditoría interna / Control de gestión" es la última candidata pendiente
de Fase 6 (catálogo funcional: "tableros de cumplimiento, seguimiento de
indicadores — complejidad media, alto valor para intendente/gabinete") y
se descartó por descarte razonado tres veces seguidas (ADR 0026, ADR 0027,
ADR 0030, todas en su sección Contexto): un tablero cruzado necesitaba, o
bien tocar cada módulo funcional existente, o bien un mecanismo de
agregación nuevo, y ninguna rebanada previa podía absorber ese costo sin
dejar de ser demostrable en una semana.

[ADR 0033](0033-framework-de-reportes-bi-motor-de-metricas-agregadas-y-primer-tablero.md)
(R29) resolvió el bloqueante real: construyó el motor (`reportes.FuenteDeMetricas`,
inversión de dependencia, sin eventos de dominio nuevos) con dos
consumidores reales, `reclamos` y `mesaentradas`, y dejó explícitamente
pendiente en su sección "Pendiente de definir":

> Auditoría interna / Control de gestión como módulo propio, con más
> fuentes, filtros y probablemente su propia decisión de a qué roles se
> les muestra cada indicador — candidata de una rebanada futura, ahora
> desbloqueada.

Esta ADR es esa rebanada futura. También existe, desde
[ADR 0013](0013-persistencia-de-eventos-y-mecanismo-transversal-de-notificaciones-y-auditoria.md)
(R5), un mecanismo transversal distinto: el registro de auditoría de
eventos de dominio (`auditoria.RegistroAuditoriaEntity`, `GET /api/auditoria`),
con un único consumidor real hoy (`UsuarioCreado`). Antes de decidir el
alcance de esta rebanada hace falta decidir explícitamente qué relación
tiene con esos dos mecanismos ya existentes.

## Decisión

### 1. No se crea un módulo `controldegestion` nuevo — se amplía `reportes`

"Auditoría interna / Control de gestión" no se convierte en un tercer
módulo canon base junto a `auditoria` y `reportes`. Se resuelve ampliando
la cobertura de `reportes` (ADR 0033), que ya es exactamente la pieza de
infraestructura que el catálogo funcional describe bajo ese nombre
("tableros de cumplimiento, seguimiento de indicadores"): agregar un
módulo nuevo solo para agrupar lo mismo bajo otro paquete no aportaría
nada, y contradice el motivo por el que ADR 0033 invirtió la dependencia
(que sumar un consumidor nuevo no toque `reportes` ni el frontend).

### 2. El registro de auditoría de eventos (ADR 0013) queda sin cambios en esta rebanada

`auditoria` sigue auditando lo mismo que audita hoy: el único evento de
dominio real del sistema es `UsuarioCreado`. Ampliar ese mecanismo (más
tipos de acción auditada, filtros por rango de fecha o por tipo de
entidad) requeriría, para ser útil, un segundo y un tercer tipo de acción
auditada real —hoy no existe ninguno— y el propio ADR 0013 §3 ya señala
el criterio del proyecto para esto: "no hay una interfaz común [...] con
un solo evento real en todo el sistema, generalizar el contrato es
diseñar a ciegas sobre una forma que ningún segundo caso todavía obligó a
tener". Construir filtros (por fecha, por tipo de acción) sobre una tabla
con un único tipo de fila sería la misma clase de decisión especulativa,
sin nada real que filtrar en la demo. Se deja explícitamente fuera (ver
"Fuera de alcance").

### 3. Ampliación real de `reportes`: tres consumidores nuevos, mismo patrón exacto de ADR 0033 §2/§3

Sin negociar de nuevo la SPI ni el mecanismo (inversión de dependencia,
consulta agregada directa sobre la tabla propia del módulo, sin eventos),
se agregan tres `FuenteDeMetricas` nuevas, elegidas por representar áreas
municipales distintas de las dos que ya cubre R29 (atención al vecino y
trámites) y por encajar con la lectura literal de "tableros de
cumplimiento" del catálogo funcional — control fiscal/de tránsito y
control sanitario/de habilitaciones son, de los módulos existentes, los
dos con más carga semántica de "cumplimiento":

- `multas.internal.FuenteDeMetricasDeMultas`: una serie, "Multas por
  estado" (`NOTIFICADA`/`EN_DESCARGO`/`CONFIRMADA`/`ANULADA`/`PAGADA`,
  ADR 0021 §2) — cuántas multas están efectivamente cobradas frente a
  las que siguen en trámite o impugnadas.
- `bromatologia.internal.FuenteDeMetricasDeBromatologia`: dos series,
  "Comercios bromatológicos por estado sanitario" (`HABILITADO`/
  `OBSERVADO`/`CLAUSURADO`, ADR 0032) y "Inspecciones por resultado"
  (mismo enum, sobre `InspeccionBromatologicaEntity.resultado`) — el
  padrón vigente y el historial de controles que lo sostiene, dos ángulos
  de la misma área de cumplimiento.
- `turnos.internal.FuenteDeMetricasDeTurnos`: una serie, "Turnos
  reservados por actividad" (`group by` el nombre de la actividad, join
  contra `FranjaHorariaEntity`/`ActividadEntity`, mismo módulo — ADR 0026)
  — no hay un campo `estado` útil en `TurnoEntity` (ADR 0026 §8, sin
  estado ni cancelación) ni demasiada señal en `ActividadEntity.estado`
  (solo `ACTIVA`/`INACTIVA`); la demanda real por actividad es el
  indicador de gestión que tiene sentido mostrarle a un director de área.

Con esto el tablero pasa de dos a cinco módulos representados (reclamos,
mesaentradas, multas, bromatologia, turnos), cubriendo atención al
vecino, trámites, tránsito/fiscal, salud/comercio y agenda de servicios —
una muestra representativa de "control de gestión" transversal, no
exhaustiva (ver "Fuera de alcance").

Ninguna de las tres fuentes requiere tabla nueva, migración, ni tocar
`ConfiguracionDePersistencia`: consultan repositorios ya existentes de
sus propios módulos (agregando el método de consulta que haga falta), el
mismo criterio exacto que ya usan `reclamos`/`mesaentradas` en R29.

### 4. No se introduce visibilidad de indicadores por rol

ADR 0033 dejó como posible pendiente "su propia decisión de a qué roles
se les muestra cada indicador" (ej. que un director de área vea solo el
indicador de su propia área, y no los de las demás). No se resuelve acá:
hoy no hay ningún caso real de un municipio que use `PanelDeAdministracion`
con roles intermedios entre "ve todo lo administrativo" y "no ve nada" —
la separación fina que existe (`usuarios.ver` vs `usuarios.administrar`,
etc.) es "ver vs. administrar" sobre la misma información, no "ver
recorte A vs. recorte B" de datos distintos. Inventar ese recorte ahora,
sin una municipalidad real pidiéndolo, es la misma clase de decisión
especulativa que el proyecto viene evitando sistemáticamente (ADR 0013
§3, ADR 0033 "Reutilizar `auditoria.ver`..."). `reportes.ver` sigue
reservado a `administrador`, sin cambios respecto de ADR 0033 §5: quien
lo tiene ve las cinco fuentes habilitadas por entitlement, igual que hoy
ve dos.

### 5. Sin cambios en frontend

`PanelDeReportes.tsx` (R29) ya itera sobre una lista arbitraria de
fuentes y de series devueltas por `GET /api/reportes/tablero` sin asumir
cantidad ni nombres fijos (confirmado leyendo el componente): las tres
fuentes nuevas aparecen automáticamente, con la misma tabla accesible
(`<caption>`, `scope="col"`/`scope="row"`) que ya usan `"Reclamos por
estado"` y `"Expedientes por tipo de trámite"`/`"Expedientes por estado"`.
No hay pantalla nueva que auditar por accesibilidad: es la misma pantalla
de R29, con más contenido dentro de la misma estructura ya accesible.

## Alternativas consideradas

- **Módulo nuevo `controldegestion` con su propio endpoint**: descartado
  — ver Decisión 1. Duplicaría exactamente lo que `reportes` ya hace.
- **Ampliar `auditoria` con filtros por fecha/tipo de entidad**: descartado
  — ver Decisión 2. Sin un segundo tipo de acción auditada real, un
  filtro por tipo de entidad no tiene nada que discriminar, y un filtro
  por fecha obligaría a decidir una convención de zona horaria que
  ningún ADR fija hoy, para un problema que la demo no necesita resolver.
- **Indicador derivado "vigencia de habilitación" en bromatologia**
  (`fechaVencimientoHabilitacion` vs. fecha actual, agrupado en memoria):
  evaluado como cuarta fuente candidata. Descartado por ahora: sería el
  primer indicador del tablero que no es un `group by` directo sobre un
  campo existente sino un cálculo derivado con la fecha del día, lo cual
  abre una decisión de diseño nueva (¿qué reloj/zona horaria usa "hoy"?)
  sin que ningún caso de uso concreto la exija todavía. "Inspecciones por
  resultado" ya cubre la misma área (salud/comercio) sin esa complejidad.
- **Cubrir todos los módulos con campo `estado` que ADR 0033 lista como
  pendientes** (`desarrollosocial`, `obras`, `arbolado`, `espaciosverdes`,
  `educacion`, `eventos`, `defensacivil`, y los ya elegidos): descartado
  para esta rebanada por volumen — ampliar a diez módulos en una semana,
  con su cobertura de test de aislamiento cada uno, excede lo que se
  puede revisar como un único PR razonable, y no aporta nada nuevo al
  patrón ya demostrado con cinco. Quedan disponibles para sumarse
  después, sin tocar `reportes` (mismo punto que ya hace ADR 0033).
- **Visibilidad de indicadores por rol (director de área ve solo su
  fuente)**: descartado por ahora — ver Decisión 4.
- **Rango de fechas / comparación entre períodos en el tablero de
  reportes**: sigue sin haber datos históricos que agregar (ADR 0033
  "Fuera de alcance", sin cambios): ningún módulo nuevo de esta rebanada
  registra historial de transición de estado.

## Consecuencias

- El tablero de `GET /api/reportes/tablero` pasa de dos a cinco fuentes
  posibles, cada una filtrada por el entitlement vigente del municipio,
  exactamente como ya ocurre con `reclamos`/`mesaentradas`.
- `multas`, `bromatologia` y `turnos` pasan a depender de `reportes`
  (implementan su SPI), sin que `reportes` conozca ninguno de los tres:
  `ModularityTests` verifica que no hay ciclo, mismo criterio que ADR
  0033.
- El catálogo funcional puede considerar "Auditoría interna / Control de
  gestión" resuelto en el nivel de madurez de esta rebanada (un tablero
  agregado de cumplimiento cruzando cinco áreas, solo para
  `administrador`), no en el nivel más ambicioso que el propio ítem del
  catálogo sugiere (alertas, umbrales, comparación entre períodos,
  visibilidad segmentada por rol): eso queda para cuando un caso real lo
  pida, igual que el resto de los pendientes de ADR 0033.
- `auditoria` (ADR 0013) no cambia: sigue siendo el registro de "quién
  hizo qué", sin filtros, con un único tipo de evento auditado.

## Pendiente de definir

- Sumar `FuenteDeMetricas` a los módulos restantes con estado propio
  (`desarrollosocial`, `obras`, `arbolado`, `espaciosverdes`, `educacion`,
  `eventos`, `defensacivil`, `cementerio`, `tasas`, `proveedores`, etc.):
  disponible sin tocar `reportes`, no se hace en esta rebanada.
- Visibilidad de indicadores por rol (ej. director de área ve solo su
  propia fuente): pendiente hasta que un caso real la pida.
- Indicadores derivados de fecha/vencimiento (ej. habilitaciones
  vencidas): pendiente hasta que se fije una convención de reloj/zona
  horaria para "hoy" en el backend, útil más allá de un único indicador.
- Filtros y series temporales en `auditoria` y en `reportes`: siguen
  pendientes de un segundo tipo de evento auditado real y de historial de
  transición de estado, respectivamente (sin cambios respecto de ADR
  0013 y ADR 0033).
- Alertas/umbrales de cumplimiento (ej. "más de N multas sin cobrar"):
  no evaluados en esta rebanada.
