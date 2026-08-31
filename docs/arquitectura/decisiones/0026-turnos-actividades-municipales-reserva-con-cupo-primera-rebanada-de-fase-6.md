# 0026 - Turnos para actividades municipales: reserva pública con cupo, primera rebanada de Fase 6 (cierra Fase 5 en una rebanada)

- Estado: Aceptada
- Fecha: 2026-08-31

## Contexto

[ADR 0025](0025-desarrollo-social-inscripcion-a-programa-social-con-minimizacion-de-datos-sensibles.md)
abrió Fase 5 — Áreas sociales con Desarrollo Social (R21, CD-30) y dejó el
resto de la fase así: Salud municipal diferida como fase completa (un
historial clínico no se puede minimizar sin vaciarse de sentido);
Discapacidad diferida para esa rebanada en particular (un turno de Junta
Evaluadora de CUD es en sí mismo un dato de salud identificable); Educación
municipal viable sin dato sensible, pero señalada explícitamente como
candidata "sin aportar una dimensión de dominio nueva" frente a los
padrones públicos que ya construyeron Obras (R19, ADR 0023) y Arbolado
(R20, ADR 0024).

Toca decidir R22 con el mismo criterio de descarte razonado que vienen
aplicando ADR 0021/0023/0024/0025 al abrir cada fase nueva, más el criterio
que agregó ADR 0025: minimización de datos sensibles cuando la rebanada
toca salud, discapacidad o situación socioeconómica de una persona
identificable.

Candidatas evaluadas:

- **Educación municipal** (segunda rebanada de Fase 5): un registro de
  instituciones educativas municipales sería viable sin dato personal
  (nombre, ubicación, tipo, estado), pero es, en forma, el mismo catálogo
  público con estado propio que `obras`/`arbolado`/el catálogo de
  `desarrollosocial.ProgramaSocialEntity` ya demostraron tres veces. No
  hay tensión de minimización (Educación no toca dato personal), así que
  sería una elección válida si no hubiera nada mejor — pero repetir el
  patrón una cuarta vez no suma una dimensión de dominio nueva al
  producto, exactamente lo que ADR 0025 ya señaló al descartarla para R21.
- **Auditoría interna / Control de gestión** (Fase 6): un tablero de
  indicadores agregados (cantidad de reclamos por estado, de multas por
  estado, de inscripciones sociales por estado, etc.) parece atractivo
  porque el producto ya tiene siete módulos funcionales con datos reales
  para agregar. Se descarta para esta rebanada: Spring Modulith prohíbe
  que un módulo nuevo lea las entidades internas de otro
  ([ADR 0003](0003-spring-modulith-para-el-backend.md)), así que un
  tablero cruzado necesitaría que **cada módulo existente** exponga un
  endpoint de conteo propio (tocar siete módulos ya construidos) o que se
  diseñe recién ahora un mecanismo de eventos/proyección agregada sobre
  `registro_auditoria` ([ADR 0013](0013-persistencia-de-eventos-y-mecanismo-transversal-de-notificaciones-y-auditoria.md))
  que hoy no discrimina "cantidad de X en estado Y" por diseño (guarda
  quién hizo qué, no un modelo de agregación). Cualquiera de las dos
  vías es más una pieza de infraestructura de reportes que una rebanada
  vertical de una semana con algo nuevo para mostrar; queda como
  candidata fuerte para cuando el framework de reportes/BI (pendiente
  desde Fase 0, ver
  [backlog inicial](../../producto/backlog-inicial.md#movido-a-fase-1))
  tenga un consumidor real que lo justifique construir bien, no a las
  apuradas sobre esta rebanada.
- **Turnos para actividades municipales** (Fase 6, área "Cultura, Turismo
  y Deportes"): el [catálogo funcional](../../producto/catalogo-funcional.md)
  lista "turnos deportivos" bajo esa área y, en la sección de plataforma
  para el ciudadano, "Turnos online: atención presencial, salud municipal,
  tránsito — complejidad baja" como una capacidad todavía no construida.
  Acotado a actividades recreativas (deporte/cultura/turismo, nunca salud
  ni un trámite administrativo — ver Decisión 1), no toca dato sensible:
  el dato personal que pide (nombre, DNI, contacto) es el mismo nivel que
  ya maneja Mesa de Entradas o Reclamos, no una categoría del Art. 2 de la
  Ley 25.326. Y a diferencia de Educación, sí aporta una dimensión de
  dominio genuinamente nueva: **un recurso con cupo limitado que se agota
  con la concurrencia de altas públicas anónimas**. Ningún módulo
  construido hasta ahora tiene esta propiedad — Obras/Arbolado son
  catálogos sin límite de "cuántas veces se puede leer o mutar", y Mesa de
  Entradas/Reclamos/Multas/Desarrollo Social aceptan cualquier alta
  pública sin un techo compartido entre solicitantes. Una reserva de turno
  sí tiene ese techo (el cupo de la franja), y dos vecinos pueden
  competir por el mismo lugar al mismo tiempo sin coordinarse — un
  problema de corrección bajo concurrencia que el producto no había
  necesitado resolver todavía. Elegida.

Elegir Turnos implica cerrar Fase 5 en una sola rebanada (R21) y abrir
**Fase 6 — Áreas de imagen y control de gestión**, mismo patrón que Fase 3
cerró en una rebanada (R17, ADR 0021) antes de que Fase 4 abriera con dos
(R19/R20). Fase 5 no se cierra por agotada: Educación sigue disponible
como candidata futura de una rebanada chica sin dato personal (mismo
estatus que "Espacios verdes" quedó para Fase 4 en ADR 0024), y Salud
municipal/Discapacidad siguen diferidas por los motivos que ya dio
ADR 0025 — ninguno de los dos motivos cambió acá.

## Decisión

### 1. Módulo nuevo `turnos`, contratable, acotado a actividades recreativas — nunca salud, nunca un trámite administrativo

`turnos` es un módulo funcional propio
([ADR 0009](0009-modelo-comercial-y-entitlement.md)), con su propio
`DescriptorDeModulo` y prefijo `/api/turnos`. No depende de ningún otro
módulo funcional. El catálogo funcional menciona "Turnos online" también
para salud municipal y para atención en tránsito — esta rebanada
**no** los cubre: el dominio de esta rebanada es exclusivamente actividades
de deporte, cultura y turismo (polideportivos, talleres, visitas guiadas),
elegido explícitamente por ser el único de los tres sin tensión con
ADR 0025 (turnos de salud son, en sí mismos, un dato de salud) ni con el
alcance ya cubierto por otro módulo (Multas/Juzgado de Faltas ya cubre lo
que tránsito necesitaría). Si en el futuro aparece un caso real de turnos
de atención administrativa general, se evalúa entonces si conviene
generalizar el mecanismo de cupo de este módulo o construir uno nuevo —no
se anticipa acá, mismo criterio que ADR 0024 §7 aplicó para no extraer una
abstracción sin un segundo caso real.

Tres entidades en `turnos.internal`, todas en la base del tenant, sin
columna de tenant explícita (mismo criterio que todos los módulos
anteriores, [ADR 0001](0001-multi-tenant-con-bd-por-tenant.md)):

- `ActividadEntity` (tabla `actividad`): el catálogo de actividades que el
  municipio ofrece — no personal, público, mismo perfil de riesgo que
  `ObraPublicaEntity`/`ArbolUrbanoEntity`/`ProgramaSocialEntity`.
- `FranjaHorariaEntity` (tabla `franja_horaria`): una franja horaria
  puntual de una actividad, con cupo — no personal, público.
- `TurnoEntity` (tabla `turno`): una reserva de un vecino sobre una
  franja — datos personales del mismo nivel que Mesa de Entradas/Reclamos,
  sin lectura pública (Decisión 5).

### 2. Catálogo de actividades: alta protegida / lectura pública, mismo mecanismo que Obras/Arbolado/Desarrollo Social

`POST /api/turnos/actividades` requiere sesión y el permiso
`turnos.gestionar` (Decisión 6). `GET /api/turnos/actividades` es lectura
pública (`rutasDeLecturaPublica()`, [ADR 0012](0012-declaracion-de-modulos-y-gating-por-ruta.md)
§1), con filtro opcional por `tipo`, por `estado` y por texto (`q`) sobre
`nombre`/`descripcion`, mismo patrón `ILIKE` que el resto del proyecto.

Campos: `nombre`, `tipo` (enum `DEPORTE`, `CULTURA`, `TURISMO` — cerrado,
alcanza para separar las tres áreas del catálogo funcional sin inventar un
nomenclador más fino), `descripcion` (texto libre), `ubicacion` (texto
libre, ej. "Polideportivo Municipal" — mismo criterio de texto libre sin
catálogo fijo que `ubicacion` en Obras/Arbolado, ADR 0023 §6/ADR 0024 §3:
un catálogo cerrado de sedes municipales sería inventar infraestructura
real de un municipio que no existe), `estado` (enum `ACTIVA`/`INACTIVA`,
transición libre en ambos sentidos, mismo criterio que
`EstadoDePrograma` en Desarrollo Social, ADR 0025 §3). Una actividad
`INACTIVA` sigue visible públicamente pero no acepta nuevas reservas en
ninguna de sus franjas (Decisión 4).

`PATCH /api/turnos/actividades/{id}/estado`, mismo permiso.

### 3. Franjas horarias: alta protegida bajo una actividad, lectura pública con el cupo disponible, sin edición del cupo total una vez creada

`POST /api/turnos/actividades/{id}/franjas` requiere sesión y
`turnos.gestionar`. Campos: `fecha` (fecha), `horaInicio`/`horaFin`
(hora), `cupoTotal` (entero positivo). Al crearse, `cupoDisponible` se
inicializa en `cupoTotal` — es la única vez que se escribe directo; de ahí
en más `cupoDisponible` solo se modifica por el mecanismo atómico de
reserva (Decisión 4). Esta rebanada no permite editar `cupoTotal` de una
franja ya creada ni cancelarla: si el municipio se equivoca, no publica
esa franja o crea una franja nueva. Editar/cancelar una franja con
reservas ya tomadas es un problema de "qué pasa con esas reservas" que
esta rebanada no resuelve (ver Pendiente de definir).

`GET /api/turnos/franjas?actividadId={id}` es lectura pública, sin
filtro de fecha obligatorio en esta rebanada (el volumen esperado por
actividad es chico); devuelve `cupoDisponible`, nunca la lista de quién
reservó (eso es `TurnoEntity`, sin lectura pública — Decisión 5).

### 4. Reserva pública anónima con decremento atómico del cupo — la decisión central de esta ADR

`POST /api/turnos/reservas` no requiere sesión
(`rutasDeEscrituraPublica()`, mismo mecanismo que Reclamos/Mesa de
Entradas/Multas-pago/Desarrollo Social — el producto no tiene identidad
ciudadana todavía). Recibe `franjaId`, `nombreSolicitante`,
`dniSolicitante`, `contacto` (obligatorio, mismo criterio que
`contacto` en Desarrollo Social, ADR 0025 §4: el municipio necesita poder
avisar si la actividad se reprograma o cancela).

Validaciones, en este orden:

1. La franja tiene que existir (si no, `FranjaNoEncontrada`, 404) y su
   actividad tiene que estar `ACTIVA` (si no, `SolicitudInvalida`, 400 —
   mismo criterio que "el programa tiene que existir y estar `ABIERTO`",
   ADR 0025 §5).
2. Un DNI no puede reservar dos veces la misma franja: `unique (franja_id,
   dni_solicitante)` en la base. Una violación de esa restricción se
   traduce a `ReservaDuplicada` (409 Conflict — primer uso de 409 en el
   proyecto, ver más abajo).
3. El cupo tiene que alcanzar. **Este es el problema nuevo**: dos
   solicitudes públicas anónimas pueden llegar concurrentemente pidiendo
   el último lugar de una franja, sin coordinarse entre sí. Un patrón
   "leer `cupoDisponible`, verificar que sea mayor a cero, restar uno,
   guardar" a nivel de aplicación tiene una ventana de carrera real: dos
   requests pueden leer el mismo valor antes de que cualquiera de las dos
   escriba, y las dos concluir que hay cupo. La solución es un **update
   condicional atómico a nivel de base de datos**, ejecutado como una
   única sentencia:

   ```sql
   update franja_horaria
      set cupo_disponible = cupo_disponible - 1
    where id = :franjaId
      and cupo_disponible > 0
   ```

   Si la sentencia afecta 0 filas, el cupo ya estaba en cero: se lanza
   `CupoAgotado` (409 Conflict). Si afecta 1 fila, la reserva sigue
   adelante y se guarda el `TurnoEntity` en la misma transacción. No hay
   lectura previa de `cupoDisponible` de la que dependa la decisión de
   escribir: la condición y la escritura son la misma sentencia, así que
   no hay ventana de carrera entre leer y escribir sin importar cuántas
   solicitudes concurrentes lleguen. Se implementa como un método
   `@Modifying` de Spring Data JPA/JPQL (o `@Query` nativa si JPQL no
   soporta bien la cláusula `where` con la propia columna en la
   expresión) sobre `FranjaHorariaRepository`, dentro de la misma
   `@Transactional("tenantTransactionManager")` que guarda el turno.

   Además, columna `check (cupo_disponible >= 0)` en la migración: defensa
   en profundidad, no el mecanismo principal — si algún código futuro
   escribiera `cupoDisponible` sin pasar por el update condicional, la
   base rechaza dejarlo negativo en vez de permitir sobreventa
   silenciosa.

   Se descarta bloqueo optimista (`@Version` + reintento) por agregar una
   complejidad de reintento acotado que el update condicional no necesita
   — con una sola sentencia atómica no hay nada que reintentar: la
   solicitud que pierde la carrera simplemente recibe `CupoAgotado` en el
   mismo request, sin reintento automático del backend.

### 5. Sin lectura pública de turnos — mismo criterio de minimización que Desarrollo Social, aplicado a un dato personal no sensible

`TurnoEntity` no tiene ningún endpoint de lectura pública, ni listado ni
búsqueda por identificador: mismo criterio que ADR 0025 §6 aplicó a
`InscripcionSocialEntity`, aunque acá el dato (nombre, DNI, contacto para
una actividad recreativa) no es sensible en el sentido del Art. 2 de la
Ley 25.326 — es del mismo nivel que Cementerio (titular/contacto de una
concesión, oculto en la búsqueda pública, ADR de R8) o Mesa de
Entradas/Reclamos (datos del vecino, visibles solo a quien gestiona). No
hace falta que el dato sea "sensible" para que este proyecto lo minimice
por default: la política ya establecida es no exponer datos personales de
terceros en una respuesta pública sin necesidad real de que estén ahí.
`GET /api/turnos/reservas?franjaId={id}` es la única vía de lectura,
protegida por sesión y `turnos.gestionar` (Decisión 6).

A diferencia de Desarrollo Social, esta rebanada **no** agrega seguimiento
por token para que el propio vecino consulte su reserva más tarde: la
respuesta de `POST /api/turnos/reservas` ya devuelve la confirmación
completa (actividad, franja, cupo restante) en el momento, que es lo que
hace falta para demostrar la rebanada. El mecanismo de
`seguimientoanonimo` (ADR 0017) queda disponible para sumarlo después sin
cambios si un caso real lo pide (ver Pendiente de definir).

### 6. Permiso único `turnos.gestionar`, asignado a `administrador` y `agente`

Cubre alta de actividades, alta de franjas, cambio de estado de actividad
y listado de reservas. No se separa en dos permisos como sí hizo
Desarrollo Social (ADR 0025 §7): ahí la separación existía porque el dato
de inscripciones era sensible (situación socioeconómica); acá el dato de
`TurnoEntity` (nombre, DNI, contacto para una actividad recreativa) es del
mismo nivel de sensibilidad que ya maneja `mesaentradas.gestionar`/
`reclamos.gestionar`/`cementerio.registrar`, todos asignados a ambos
roles de sistema — no hay una diferencia real de sensibilidad entre
"publicar una actividad" y "ver quién se anotó a jugar al fútbol" que
amerite un permiso aparte.

### 7. Primer uso de 409 Conflict en el proyecto

Hasta esta rebanada, el proyecto solo usó 400 (`SolicitudInvalida`) y 404
(`...NoEncontrado`/`...NoEncontrada`) como códigos de error de negocio.
`CupoAgotado` y `ReservaDuplicada` son, semánticamente, un conflicto con
el estado actual del recurso (la franja ya no tiene lugar; ese DNI ya
tiene un lugar en esa franja) — no una solicitud malformada (400) ni un
recurso inexistente (404). Se decide usar 409 para ambos, con su propio
`@ExceptionHandler` en `TurnosController`, en vez de forzarlos a 400 solo
por consistencia superficial con el resto del proyecto. El frontend
distingue el mensaje de "cupo agotado" (ofrece elegir otra franja) del de
"ya te anotaste" (informa, no hace falta elegir otra franja) usando el
cuerpo del error, no el código HTTP, así que ambos casos comparten 409 sin
perder claridad para quien reserva.

### 8. Sin geolocalización, sin adjuntos, sin pagos, sin notificaciones, sin cancelación

Mismos motivos que todos los módulos anteriores con estado propio (ADR
0023 §6/§7/§8, ADR 0024 §6, ADR 0025 §9): sin GIS, sin fotos/documentos.
Sin integración con `pagos` (ADR 0018) aunque algunas actividades
municipales reales cobran arancel — esta rebanada modela solo turnos
gratuitos; cobrar es un problema aparte que no hace falta resolver para
demostrar el mecanismo de cupo. Sin notificación al vecino de cambios
(mismo pendiente que todo el proyecto arrastra desde R6). Sin cancelación
de una reserva ya hecha, ni por el vecino ni por el municipio: una vez
reservado el lugar, esta rebanada no libera cupo — es la simplificación
deliberada que mantiene el alcance de una semana, a costa de un cupo que
no se puede recuperar si alguien no asiste (ver Pendiente de definir).

## Alternativas consideradas

- **Elegir Educación municipal (segunda rebanada de Fase 5)**: ver
  Contexto — no aporta dimensión de dominio nueva.
- **Elegir Auditoría interna / Control de gestión**: ver Contexto —
  necesitaría tocar los siete módulos funcionales existentes o diseñar a
  las apuradas el framework de reportes/BI pendiente desde Fase 0.
- **Cubrir también turnos de salud o de atención administrativa en esta
  rebanada**: descartada — ver Decisión 1. Turnos de salud son, en sí
  mismos, un dato de salud (mismo argumento que ADR 0025 aplicó a
  Discapacidad).
- **Bloqueo optimista (`@Version`) con reintento en vez de update
  condicional atómico**: descartada — ver Decisión 4. Más complejidad
  (lógica de reintento acotado) sin necesidad: una sola sentencia atómica
  ya resuelve la carrera sin reintentar nada.
- **Verificar cupo con `SELECT ... FOR UPDATE` explícito en vez de un
  `UPDATE` condicional**: funcionalmente equivalente pero más verboso (dos
  sentencias en vez de una) y depende de que la transacción mantenga el
  lock hasta el commit; el `UPDATE` condicional es una sola sentencia
  atómica por diseño del motor, sin depender de aislamiento de
  transacción explícito. Descartada por simplicidad, no por incorrección.
- **Reservar más de un lugar por solicitud (para un grupo familiar)**:
  descartada por ahora — cada integrante reserva su propio turno con su
  propio DNI, mismo criterio de no pedir datos de terceros sin
  consentimiento que ya aplicó ADR 0025 §4 a la composición del grupo
  familiar.
- **Separar `turnos.gestionarAgenda` de `turnos.verReservas`, mismo
  patrón que Desarrollo Social**: descartada — ver Decisión 6. El dato de
  `TurnoEntity` no tiene la sensibilidad que justificó esa separación en
  ADR 0025.
- **Forzar 400 para `CupoAgotado`/`ReservaDuplicada` en vez de introducir
  409**: descartada — ver Decisión 7. Ninguno de los dos es una solicitud
  malformada.

## Consecuencias

- `turnos` no depende de ningún otro módulo funcional; el test de
  modularidad de Spring Modulith lo verifica en el build.
- Cierra Fase 5 en una sola rebanada (R21) y abre Fase 6 — Áreas de imagen
  y control de gestión, con Turnos como primera rebanada.
- Primer módulo del proyecto con una propiedad de corrección bajo
  concurrencia como parte de su modelo de datos (cupo compartido entre
  solicitantes anónimos), y primer uso de 409 Conflict como código de
  error de negocio.
- Un cupo reservado no se libera nunca en esta rebanada, ni por
  cancelación ni por inasistencia — una franja puede quedar con cupo
  agotado por reservas que después no se usan (ver Pendiente de definir).
- Educación municipal sigue disponible como candidata futura de Fase 5;
  Salud municipal y Discapacidad siguen diferidas por los motivos ya
  dados en ADR 0025 (sin cambios acá). Auditoría interna / Control de
  gestión sigue disponible como candidata de una rebanada futura de Fase
  6, mejor abordada cuando el framework de reportes/BI tenga diseño
  propio.

## Pendiente de definir

- Cancelación de una reserva (por el vecino o por el municipio) con
  liberación de cupo: no existe en esta rebanada.
- Edición o cancelación de una franja ya publicada, y qué pasa con las
  reservas ya tomadas sobre ella: no existe en esta rebanada.
- Seguimiento por token para que el vecino consulte su propia reserva más
  tarde (mecanismo `seguimientoanonimo`, ADR 0017, listo para reutilizar
  sin cambios si un caso real lo pide).
- Cobro de arancel para actividades pagas (integración con `pagos`, ADR
  0018): fuera de alcance, esta rebanada modela solo turnos gratuitos.
- Notificación al vecino (confirmación por email/SMS, aviso de
  reprogramación/cancelación): mismo pendiente que arrastra todo el
  proyecto desde R6, sin resolver acá tampoco.
- Turnos de salud municipal o de atención administrativa/tránsito: fuera
  de alcance de este módulo (Decisión 1); requieren su propia decisión
  de producto si aparecen como caso real.
- Auditoría interna / Control de gestión y el framework de reportes/BI
  que necesitaría: siguen pendientes, candidatos de una rebanada futura
  de Fase 6 (Contexto).
- El frontend distingue `CupoAgotado` de `ReservaDuplicada` (Decisión 7)
  comparando el texto exacto del mensaje de error, porque
  `ErrorResponse` no lleva un `codigo` machine-readable para ninguno de
  los dos (el proyecto solo tiene ese campo para `MODULO_NO_CONTRATADO`,
  ADR 0012). Es una fragilidad real y consciente: si el texto de
  `TurnosController` cambia, el frontend deja de distinguir los casos sin
  que ningún test lo detecte. Extender `codigo`/`modulo` a estos dos
  errores de negocio es candidato de una rebanada futura que también
  revise si conviene generalizarlo a otros módulos con más de un 4xx de
  negocio, no solo a `turnos`.
