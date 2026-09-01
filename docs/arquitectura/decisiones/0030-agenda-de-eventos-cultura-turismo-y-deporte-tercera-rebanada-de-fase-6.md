# 0030 - Agenda de eventos de Cultura, Turismo y Deportes: agenda pública, tercera rebanada de Fase 6

- Estado: Aceptada
- Fecha: 2026-09-01

## Contexto

[ADR 0027](0027-prensa-y-comunicacion-gacetillas-segunda-rebanada-de-fase-6.md)
dejó Fase 6 — Áreas de imagen y control de gestión con dos rebanadas
construidas (Turnos, R22; Prensa y Comunicación, R23) y una sola candidata
sin explorar: **Cultura, Turismo y Deportes**, el ítem que abrió la fase
(ADR 0026, Contexto) pero que Turnos solo cubrió parcialmente — acotado a
"turnos deportivos", una de las tres capacidades que el
[catálogo funcional](../../producto/catalogo-funcional.md) lista bajo esa
área ("agenda de eventos, polideportivos, turnos deportivos").

Toca decidir R26 con el mismo criterio de descarte razonado que vienen
aplicando ADR 0021/0023/0024/0025/0026/0027/0028/0029 al planificar cada
rebanada nueva.

### Se reevalúa Auditoría interna / Control de gestión, y se descarta por tercera vez

El argumento de ADR 0026/0027 era que un tablero cruzado necesitaría, o
bien que cada módulo funcional existente exponga su propio conteo, o bien
un mecanismo de agregación nuevo sobre `registro_auditoria`
([ADR 0013](0013-persistencia-de-eventos-y-mecanismo-transversal-de-notificaciones-y-auditoria.md))
que hoy no discrimina "cantidad de X en estado Y". Se verificó otra vez
contra el código actual si la premisa cambió desde R23: **no cambió**.
Ningún módulo agregado desde entonces (`educacion`, `espaciosverdes`)
publica un evento de dominio propio; `registro_auditoria` sigue recibiendo
solo `UsuarioCreado` de `acceso`. Sigue sin ser una rebanada de una semana
con algo nuevo para mostrar; sigue mejor candidata para cuando el
framework de reportes/BI (Fase 0, pendiente) tenga un consumidor real.
Queda, otra vez, como única candidata razonable de Fase 6: **Cultura,
Turismo y Deportes**, en la parte que Turnos no cubrió.

### Qué falta de "Cultura, Turismo y Deportes" después de Turnos, y qué se elige acá

El catálogo funcional lista tres capacidades bajo esta área:
"**agenda de eventos**, polideportivos, turnos deportivos". Turnos (ADR
0026) cubrió "turnos deportivos" generalizado a las tres sub-áreas
(deporte/cultura/turismo) — un mecanismo de **reserva de cupo** sobre una
franja horaria de una actividad recurrente (una cancha, un taller). Quedan
sin cubrir "agenda de eventos" y "polideportivos" (entendido como catálogo
de instalaciones/puntos de interés). Se elige **agenda de eventos**, y se
descarta explícitamente sumar un catálogo de puntos de interés turístico
en la misma rebanada:

- **Agenda de eventos** (elegida): un evento (festival, maratón, muestra,
  feria) es informativo, no reservable — el vecino lo consulta para saber
  qué hay y cuándo, no reserva un lugar. Encaja en el mismo patrón "alta
  protegida + lectura pública" que ya usan seis módulos del proyecto
  (`obras`, `arbolado`, `educacion`, `espaciosverdes`, `boletin`,
  `prensa`), pero aporta dos dimensiones que ninguno de esos seis tiene
  (ver Decisión 4/5).
- **Catálogo de puntos de interés turístico** (museo, mirador, monumento):
  descartada para esta rebanada, no por inviable — es, en esencia, un
  padrón informativo sin fecha, más cercano todavía a Obras/Arbolado/
  Espacios verdes (un recurso fijo con ubicación) que a una agenda. Sumarlo
  a la misma rebanada mezclaría dos modelos de datos distintos (uno con
  fecha propia y ciclo de vida corto, otro sin fecha y de vida larga) bajo
  una sola entidad, o forzaría dos entidades en la misma rebanada sin
  necesidad real de demostrar las dos juntas. Queda como candidata futura
  si hace falta una rebanada chica adicional en esta área (mismo criterio
  que ADR 0024 dejó pendiente a Espacios verdes hasta que ADR 0029 la
  retomó).

### Por qué esto no es "lo mismo pero con otro nombre" que `turnos` (ADR 0026)

La diferencia no es de área temática (las dos tocan deporte/cultura/
turismo) sino de **forma del dato y del compromiso que genera**:

| | `turnos` (ADR 0026) | `eventos` (acá) |
|---|---|---|
| Qué modela | Un recurso con cupo limitado, reservable por franja | Un hecho público que ocurre en una fecha, sin cupo |
| Quién participa | El vecino se anota con su nombre/DNI/contacto | Nadie se anota; es informativo, como Obras/Prensa |
| Dato personal | Sí (`TurnoEntity`, sin lectura pública, ADR 0026 §5) | No, ninguno |
| Problema de corrección | Concurrencia bajo cupo compartido (ADR 0026 §4) | Ninguno — es un padrón simple |
| Mutación posterior | Ninguna (el turno reservado no se libera, ADR 0026 §8) | Cancelación del evento completo (Decisión 3) |
| Quién lo da de alta | El municipio publica la actividad; el vecino reserva la franja | Solo el municipio; el vecino solo lee |

Un evento del calendario cultural/turístico/deportivo (una muestra de
arte, una maratón, una feria de artesanos) no tiene cupo ni reserva
individual: se anuncia y cualquiera puede ir. Modelarlo como una
"actividad de turnos sin franjas ni reservas" sería forzar un mecanismo
diseñado para otra cosa (cupo compartido bajo concurrencia) sobre un caso
que no lo necesita, y dejaría el módulo `turnos` con una responsabilidad
mixta (reservable + no reservable) que hoy no tiene. Se decide, en cambio,
un módulo nuevo e independiente.

### Elección de nombre: `eventos`, no `agenda`

`turnos.internal` ya usa el nombre `GestionDeAgenda` para el servicio que
administra actividades y franjas (la agenda de turnos disponibles) — mismo
criterio de nombres de servicio descriptivos que el resto del proyecto.
Nombrar este módulo `agenda` (o `agendapublica`) sería una colisión
conceptual real: dos "agendas" distintas en el mismo backend, una de cupo
reservable y otra de eventos informativos, confundiría a quien lea el
código o la documentación sin ganar nada a cambio. `eventos` nombra
directamente lo que el módulo modela (una lista de eventos), sin
ambigüedad con `turnos`, y no colisiona con ningún paquete existente.

## Decisión

### 1. Módulo nuevo `eventos`, contratable, sin depender de `turnos` ni de ningún otro módulo funcional

`eventos` es un módulo funcional propio
([ADR 0009](0009-modelo-comercial-y-entitlement.md)), con su propio
`DescriptorDeModulo` y prefijo `/api/eventos`. No depende de `turnos`,
`prensa` ni de ningún otro módulo funcional — mismo criterio de
independencia que todos los anteriores, verificado por el test de
modularidad de Spring Modulith.

Una única entidad, `eventos.internal.EventoEntity` (tabla `evento`), en la
base del tenant, sin columna de tenant explícita (mismo criterio que todos
los módulos anteriores, [ADR 0001](0001-multi-tenant-con-bd-por-tenant.md)).

### 2. Campos: `categoria` cerrada, `ubicacion` libre, fecha de inicio obligatoria y fecha de fin opcional (rango de días), hora opcional

`CategoriaDeEvento`: enum `CULTURA`, `TURISMO`, `DEPORTE`, `OTRA` — mismo
criterio que `TipoDeEspacioVerde`/`TipoDeInstitucionEducativa` (ADR 0028
§3, ADR 0029 §3): conjunto chico y estable, más una salida genérica para
no bloquear un caso real que no encaje. No reutiliza el enum
`TipoDeActividad` de `turnos` (`DEPORTE, CULTURA, TURISMO`, sin `OTRA`):
son enums de módulos distintos, cada uno definido desde cero, mismo
criterio de independencia de Decisión 1 — que coincidan en tres de sus
valores es casualidad de dominio, no motivo para compartir código
([ADR 0029](0029-espacios-verdes-padron-publico-con-estado-propio-tercera-rebanada-de-fase-4.md),
Contexto, último punto, aplica el mismo argumento a una coincidencia
topológica).

`ubicacion` (texto libre, ej. "Plaza San Martín" o "Costanera"): mismo
criterio que `ubicacion` en Obras/Arbolado/Educación/Espacios
verdes/Turnos — sin geolocalización estructurada ni catálogo cerrado de
sedes.

`fechaInicio` (fecha, obligatoria) y `fechaFin` (fecha, opcional): a
diferencia de los cinco casos anteriores del patrón (todos con, a lo sumo,
una fecha estimada suelta), un evento cultural/turístico/deportivo real
frecuentemente dura más de un día (un festival de fin de semana, una
muestra itinerante). Cuando `fechaFin` está presente, tiene que ser mayor
o igual a `fechaInicio` — si no, `SolicitudInvalida` (400). Sin
`fechaFin`, el evento es de un solo día. `horaInicio` (hora del día,
opcional): dato de conveniencia para el vecino ("a las 19hs"), sin
`horaFin` — no es una franja reservable como en `turnos`, alcanza con
saber cuándo empieza.

### 3. Estado propio: la topología de transición más simple del patrón hasta ahora — un único salto, sin retorno

`EstadoDeEvento`: enum `PROGRAMADO`, `CANCELADO`.

```
PROGRAMADO → CANCELADO
```

Sin retorno (`CANCELADO → PROGRAMADO` no existe) y sin ningún estado
intermedio. Todo evento nace `PROGRAMADO` (no es un parámetro del alta).
`PATCH /api/eventos/{id}/estado` requiere sesión y `eventos.gestionar`; no
está en `rutasDeEscrituraPublica()`.

Esta es la quinta instancia del patrón "alta protegida + lectura pública +
estado propio mutable" (después de Obras, Arbolado, Educación y Espacios
verdes — ver la tabla comparativa de
[ADR 0029 §5](0029-espacios-verdes-padron-publico-con-estado-propio-tercera-rebanada-de-fase-4.md)),
pero con una tabla de transiciones más simple que las cuatro anteriores
(todas de tres o cuatro estados): acá hay un solo camino posible, sin
ida y vuelta. El motivo de dominio es distinto de los cuatro casos previos
(que modelan recursos físicos de vida larga con estados operativos
intermedios — en mantenimiento, en ejecución) y coherente con lo que es un
evento: ocurre una vez y puede suspenderse, pero no tiene un estado
"a medio camino" real que documentar. No se agrega un tercer estado
(`FINALIZADO`, por ejemplo) sin necesidad real: nada en esta rebanada
necesita distinguir "ya pasó" de "programado", y agregarlo requeriría además
una tarea periódica que hoy no existe en el proyecto (ver Pendiente de
definir).

### 4. Orden del listado público por fecha de evento, no por fecha de creación — primera desviación del criterio de orden del patrón

Los cinco módulos anteriores del patrón (Obras, Arbolado, Educación,
Espacios verdes, y también Boletín/Prensa fuera del patrón de estado)
ordenan su listado público por `creadoEn` descendente: son padrones o
publicaciones donde lo último cargado es lo más relevante. Una agenda de
eventos es distinta: lo relevante para el vecino es **qué pasa primero**,
no qué se cargó último. `GET /api/eventos` ordena por `fechaInicio`
ascendente (y, a igual fecha, por `nombre` para un orden determinístico),
sin paginado (mismo criterio de "fuera de alcance" que todo el patrón).
Es una desviación deliberada del criterio de orden por defecto del
proyecto, justificada por la naturaleza cronológica del dato — no una
inconsistencia.

### 5. Alta protegida / lectura pública, mismo mecanismo que el resto del patrón

`POST /api/eventos` requiere sesión y el permiso `eventos.gestionar`.
`GET /api/eventos` es lectura pública (`rutasDeLecturaPublica()`,
[ADR 0012](0012-declaracion-de-modulos-y-gating-por-ruta.md) §1), con
filtro opcional por `categoria`, por `estado` y por texto (`q`) sobre
`nombre`/`ubicacion`, mismo patrón `ILIKE` que el resto del patrón. Sin
endpoint de detalle por id, igual que `boletin`/`prensa`.

### 6. Permiso único `eventos.gestionar`, asignado a `administrador` y `agente`

Cubre alta y cambio de estado (cancelación). Mismo criterio que
`turnos.gestionar`/`prensa.publicar`/`espaciosverdes.gestionar`: publicar
un evento cultural/turístico/deportivo y cancelarlo es tarea operativa sin
ninguna diferencia real de sensibilidad que amerite separar el permiso o
restringirlo solo a `administrador`.

### 7. Quinto caso del patrón: se revisa la extracción de abstracción otra vez, se decide otra vez que no

[ADR 0029 §8](0029-espacios-verdes-padron-publico-con-estado-propio-tercera-rebanada-de-fase-4.md)
dejó pendiente revisar la extracción "si aparece un quinto caso". Este es
el quinto caso, y se decide otra vez que no, por los mismos dos motivos
que ya pesaron en ADR 0029 más un tercero propio de este caso:

1. El código que se repite (`if (!transicionesValidas.get(actual).contains(nuevo)) throw ...`
   sobre un mapa de transiciones) sigue siendo trivial — acá, además,
   todavía más chico (un solo par origen→destino en vez de una tabla de
   tres o cuatro).
2. La coincidencia sigue siendo de forma, no de contenido: `EventoEntity`
   no comparte ningún campo propio con `EspacioVerdeEntity`/
   `ObraPublicaEntity`/`ArbolUrbanoEntity`/`InstitucionEducativaEntity`
   más allá de lo que ya comparten todos los módulos del proyecto
   (`id`, `creadoEn`, copia del actor).
3. **Este caso, además, tiene una topología de transición distinta a los
   cuatro anteriores** (Decisión 3: un salto sin retorno, no una red de
   tres o cuatro estados con idas y vueltas) y un criterio de orden
   distinto (Decisión 4). Si algo, este quinto caso confirma que el
   patrón sigue divergiendo en el contenido real cada vez que se aplica,
   no convergiendo hacia una forma común — lo que hace la extracción
   todavía menos atractiva que en ADR 0029, no más.

### 8. Sin geolocalización, sin adjuntos, sin recurrencia, sin notificaciones, sin integración con `turnos`

Mismos motivos que todo el patrón (ADR 0023 §6/§7/§8, ADR 0024 §6, ADR
0025 §9, ADR 0026 §8, ADR 0027 §4, ADR 0029 §6): sin GIS, sin fotos/
documentos adjuntos. Sin eventos recurrentes (una "feria todos los
domingos" se carga como eventos individuales en esta rebanada, no como una
regla de recurrencia — modelar recurrencia es una decisión de producto
propia que ningún módulo de este proyecto resolvió todavía). Sin
notificación al vecino de eventos nuevos o cancelados (mismo pendiente que
arrastra el proyecto desde R6). Sin ningún acoplamiento con `turnos`: un
evento de esta rebanada no puede tener franjas reservables ni cupo — si un
caso real pide que un evento puntual (ej. una clase abierta de un
festival) sea reservable, es una decisión de producto futura sobre cómo
combinar los dos módulos, no algo que se resuelve acá.

## Alternativas consideradas

- **Elegir Auditoría interna / Control de gestión**: ver Contexto —
  descartada por tercera vez consecutiva, mismo motivo que ADR 0026/0027.
- **Sumar un catálogo de puntos de interés turístico en la misma
  rebanada**: descartada — ver Contexto. Mezclaría dos modelos de datos
  (con fecha y de vida corta vs. sin fecha y de vida larga) sin necesidad
  real de demostrarlos juntos. Queda como candidata futura de esta misma
  área.
- **Modelar el evento como una `ActividadEntity` de `turnos` sin franjas
  reservables**: descartada — ver Contexto, "Por qué esto no es 'lo mismo
  pero con otro nombre'...". Forzaría un mecanismo diseñado para cupo
  reservable sobre un caso que no lo necesita, y mezclaría dos
  responsabilidades en `turnos`.
- **Nombrar el módulo `agenda` o `agendapublica`**: descartada — ver
  Contexto, "Elección de nombre". Colisión conceptual real con
  `GestionDeAgenda` de `turnos.internal`.
- **Agregar un tercer estado `FINALIZADO`, calculado o transicionado**:
  descartada — ver Decisión 3. No hace falta para esta rebanada y
  requeriría una tarea periódica que el proyecto no tiene.
- **Ordenar el listado público por `creadoEn` descendente, por
  consistencia con el resto del patrón**: descartada — ver Decisión 4. El
  orden cronológico por fecha del evento es lo que hace útil una agenda;
  mantener el criterio por defecto solo por uniformidad superficial le
  restaría valor real a la pantalla.
- **Reutilizar el enum `TipoDeActividad` de `turnos`**: descartada — ver
  Decisión 2. Cada módulo define sus propios tipos desde cero, mismo
  criterio de independencia que el resto del proyecto.
- **Extraer una abstracción compartida para el patrón "alta protegida +
  lectura pública + estado propio mutable" ahora que hay cinco casos**:
  descartada — ver Decisión 7.

## Consecuencias

- `eventos` no depende de `turnos` ni de ningún otro módulo funcional; el
  test de modularidad de Spring Modulith lo verifica en el build.
- Cierra, por ahora, las rebanadas construibles de Fase 6: Turnos (R22),
  Prensa y Comunicación (R23) y Agenda de eventos (R26) cubren las tres
  capacidades del catálogo funcional bajo "Cultura, Turismo y Deportes" +
  "Prensa y Comunicación" salvo "polideportivos" (catálogo de puntos de
  interés, disponible como candidata futura chica) y Auditoría interna /
  Control de gestión, que sigue bloqueada por falta de eventos de dominio
  propios en los módulos existentes — sin cambios respecto de ADR
  0026/0027, tercera vez que se descarta por el mismo motivo.
- Primer módulo del patrón "alta protegida + lectura pública + estado
  propio mutable" con una topología de transición de un solo salto sin
  retorno, y primer módulo del proyecto que ordena su listado público por
  un campo de fecha propio en vez de por `creadoEn`.
- Quinto caso del patrón sin abstracción compartida extraída (Decisión 7);
  un sexto caso futuro debería revisar la pregunta otra vez con este delante.

## Pendiente de definir

- Catálogo de puntos de interés turístico ("polideportivos" y similares
  del catálogo funcional): candidata futura de esta misma área, no
  resuelta acá.
- Motivo de la cancelación de un evento (texto libre): no existe en esta
  rebanada.
- Eventos recurrentes: no existen en esta rebanada; cada ocurrencia se
  carga como un evento individual.
- Edición de los campos del alta después de creado el evento: no existe.
- Integración de un evento puntual con `turnos` (por ejemplo, una
  actividad reservable dentro de un evento más grande): fuera de alcance,
  requiere una decisión de producto propia si aparece un caso real.
- Notificación al vecino de eventos nuevos o cancelados: mismo pendiente
  que arrastra el proyecto desde R6.
- Auditoría interna / Control de gestión y el framework de reportes/BI:
  siguen pendientes, sin candidata nueva de Fase 6 disponible después de
  esta rebanada.
