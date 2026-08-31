# CD-31 · R22 — Turnos para actividades municipales: reserva pública con cupo, primera rebanada de Fase 6

Ver [ADR 0026](../docs/arquitectura/decisiones/0026-turnos-actividades-municipales-reserva-con-cupo-primera-rebanada-de-fase-6.md)
para el porqué de cada decisión de esta spec. Esta spec no reabre nada del
ADR: lo traduce a tareas concretas. Leé el ADR completo antes de
implementar — en particular Decisión 4, que es la que más se aparta del
patrón de módulos anteriores (Obras/Arbolado/Desarrollo Social): acá hay
un recurso con cupo compartido entre solicitantes anónimos concurrentes,
y la forma de decrementarlo sin sobrevender es la pieza central de esta
rebanada. No la resuelvas con tu propio criterio si algo no queda claro
acá — preguntá antes de improvisar un mecanismo distinto al que describe
la Tarea 3.

## Demo objetivo

Un agente municipal (con sesión y `turnos.gestionar`) publica una
actividad, "Cancha de Fútbol 5 — Polideportivo Municipal" (tipo
`DEPORTE`), estado `ACTIVA`, y le agrega una franja horaria: sábado
10:00–11:00, cupo 2. Un vecino, sin sesión, ve la actividad y esa franja
en el catálogo público con "2 lugares disponibles", y reserva un turno
con nombre, DNI y contacto. El cupo baja a 1. Un segundo vecino reserva
el último lugar: el cupo baja a 0. Un tercer vecino que lo intenta
recibe un error claro de "no queda cupo disponible" (409), no una
reserva fantasma ni un cupo negativo. El mismo agente entra a la agenda
de esa franja y ve las dos reservas con nombre, DNI y contacto completos;
no existe ningún listado público de quién se anotó. Las actividades,
franjas y reservas de un municipio no aparecen en el portal de otro.

## Tarea 1 (backend) — módulo `turnos`: catálogo de actividades

**Comportamiento observable**: con sesión y `turnos.gestionar`, `POST
/api/turnos/actividades` da de alta una actividad en estado `ACTIVA` y
devuelve sus datos (201). Sin sesión, `GET /api/turnos/actividades`
devuelve el listado de actividades del municipio en curso, con filtros
opcionales combinables `tipo`, `estado` y `q` (`ILIKE` en `nombre` o
`descripcion`), ordenado por `creadoEn` descendente. Con sesión y el
mismo permiso, `PATCH /api/turnos/actividades/{id}/estado` cambia el
estado (`ACTIVA ↔ INACTIVA`, en ambos sentidos). Un municipio sin el
módulo `turnos` contratado rechaza las tres rutas con 403
`MODULO_NO_CONTRATADO`, con o sin sesión. Sin sesión o sin el permiso,
alta y cambio de estado dan 401/403 (no están en
`rutasDeEscrituraPublica()`).

**Modelo** (`turnos.internal`, módulo nuevo, prefijo `/api/turnos`):

- `TipoDeActividad`: enum `DEPORTE, CULTURA, TURISMO`.
- `EstadoDeActividad`: enum `ACTIVA, INACTIVA`.
- `ActividadEntity` (tabla `actividad`), sin columna de tenant (mismo
  criterio que `ObraPublicaEntity`/`ArbolUrbanoEntity`/
  `ProgramaSocialEntity`):
  - `id`, `nombre` (`varchar(150)`, not null), `tipo` (`varchar(20)`, not
    null, `check` de valores del enum), `descripcion` (`text`,
    nullable), `ubicacion` (`varchar(300)`, not null — texto libre, sin
    catálogo fijo de sedes, ADR 0026 §2).
  - `estado` (`varchar(15)`, not null, default `'ACTIVA'`, `check` de
    valores válidos).
  - Copia del actor que publica (ADR 0013, mismo criterio que
    `publicadoPorNombre`/`publicadoPorEmail` en `ObraPublicaEntity`):
    `publicadoPorNombre` (`varchar(150)`, not null), `publicadoPorEmail`
    (`varchar(200)`, not null).
  - `creadoEn`, `actualizadoEn` (`timestamptz`, not null, default
    `now()`).
  - Sin más columnas: nada de arancel, foto ni geolocalización — no las
    agregues, están fuera de alcance a propósito (ADR 0026 §8).
  - Índices: `actividad_creado_en_idx on actividad (creado_en desc)`,
    `actividad_estado_idx on actividad (estado)`,
    `actividad_tipo_idx on actividad (tipo)`.

- `GestionDeAgenda` (`@Service`), con
  `@Transactional("tenantTransactionManager")` en los métodos de
  escritura:
  - `publicarActividad(nombre, tipo, descripcion, ubicacion, publicadoPorNombre, publicadoPorEmail)`:
    valida `nombre`/`ubicacion` no-blank y largos máximos (mismos límites
    de columna); `tipo` no-null (400 `SolicitudInvalida` si falta o no
    matchea el enum); `descripcion` opcional. Guarda con `estado =
    ACTIVA`; no es un parámetro que reciba el cliente.
  - `buscarActividades(tipo, estado, q)`: los tres parámetros opcionales
    y combinables (AND entre los que vengan). `tipo`/`estado` inválidos →
    400 `SolicitudInvalida`. `q` es `ILIKE '%valor%'` contra `nombre` **o**
    `descripcion`. Ordena por `creadoEn` descendente, sin paginado.
  - `cambiarEstadoDeActividad(Long id, EstadoDeActividad estadoNuevo)`:
    busca la actividad (o `ActividadNoEncontrada`, 404) y aplica el
    cambio (`ACTIVA ↔ INACTIVA`, cualquiera de los dos sentidos es
    válido — no hay tabla de transiciones, mismo criterio que
    `EstadoDePrograma` en Desarrollo Social, ADR 0025 §3).

  Este mismo servicio gestiona también las franjas horarias (Tarea 2):
  no crees un segundo `@Service` para eso, es la misma unidad de trabajo
  administrativo (publicar la agenda de una actividad).

**`DescriptorDelModuloTurnos`**:
- `codigo() = "turnos"`, `prefijosDeApi() = List.of("/api/turnos")`.
- `rutasDeLecturaPublica()` por ahora `List.of("/api/turnos/actividades")`
  (se extiende en la Tarea 2).

**Controller**: `TurnosController`, mismo patrón exacto que
`ArboladoController`/`ObrasController` (alta y cambio de estado con
`@PreAuthorize("hasAuthority('turnos.gestionar')")`, listado sin
`@PreAuthorize`, `@ExceptionHandler` local para `SolicitudInvalida` (400)
y `ActividadNoEncontrada` (404, mensaje genérico "No encontramos esa
actividad.")).

**Fuera de alcance de esta tarea**: franjas horarias y reservas (Tareas
2 a 4).

## Tarea 2 (backend) — franjas horarias con cupo

**Comportamiento observable**: con sesión y `turnos.gestionar`, `POST
/api/turnos/actividades/{id}/franjas` crea una franja para esa
actividad con `cupoDisponible` inicializado en `cupoTotal`, y la
devuelve (201). `id` de actividad inexistente → 404
`ActividadNoEncontrada`. Sin sesión, `GET
/api/turnos/franjas?actividadId={id}` devuelve las franjas de esa
actividad (parámetro obligatorio — sin listado global de todas las
franjas de todas las actividades), con `cupoDisponible`, ordenadas por
`fecha`/`horaInicio` ascendente. Un municipio sin `turnos` contratado
rechaza ambas rutas con 403 `MODULO_NO_CONTRATADO`.

**Modelo**:

- `FranjaHorariaEntity` (tabla `franja_horaria`), sin columna de tenant:
  - `id`, `actividadId` (`bigint`, not null, FK a `actividad.id`).
  - `fecha` (`date`, not null), `horaInicio` (`time`, not null),
    `horaFin` (`time`, not null, `check (hora_fin > hora_inicio)`).
  - `cupoTotal` (`integer`, not null, `check (cupo_total > 0)`).
  - `cupoDisponible` (`integer`, not null,
    `check (cupo_disponible >= 0)` — defensa en profundidad, ver Tarea
    3; nunca se escribe directo salvo al crear la franja, donde arranca
    igual a `cupoTotal`).
  - `creadoEn` (`timestamptz`, not null, default `now()`).
  - Sin `actualizadoEn`: esta rebanada no edita una franja ya creada
    (ADR 0026 §3) — el único campo que cambia después de creada
    (`cupoDisponible`) lo hace el mecanismo de reserva, no una edición
    administrativa.
  - Índices: `franja_horaria_actividad_id_idx on franja_horaria (actividad_id)`,
    `franja_horaria_fecha_idx on franja_horaria (fecha, hora_inicio)`.

- En `GestionDeAgenda` (mismo service de la Tarea 1):
  - `crearFranja(Long actividadId, LocalDate fecha, LocalTime horaInicio, LocalTime horaFin, Integer cupoTotal)`:
    busca la actividad (o `ActividadNoEncontrada`); valida `fecha`/
    `horaInicio`/`horaFin` no-null, `horaFin` posterior a `horaInicio`
    (400 si no), `cupoTotal` no-null y `> 0` (400 si no). Guarda con
    `cupoDisponible = cupoTotal`. No valida que la actividad esté
    `ACTIVA` para crear la franja — eso se valida recién al reservar
    (Tarea 3), no al publicarla (ADR 0026 §3).
  - `buscarFranjas(Long actividadId)`: `actividadId` obligatorio (400
    `SolicitudInvalida` si falta); ordena por `fecha` y `horaInicio`
    ascendente, sin paginado.

**`FranjaHorariaRepository`**: además de los métodos de consulta
normales, declará el método de la Tarea 3 acá (`reservarUnLugar`),
aunque lo use el servicio de la Tarea 3 — es el mismo repositorio.

**`DescriptorDelModuloTurnos`** (edición de la Tarea 1):
`rutasDeLecturaPublica()` pasa a
`List.of("/api/turnos/actividades", "/api/turnos/franjas")`.

**Controller**: `POST /api/turnos/actividades/{id}/franjas` con
`@PreAuthorize("hasAuthority('turnos.gestionar')")`. `GET
/api/turnos/franjas` sin `@PreAuthorize`, con `@RequestParam(required =
true) Long actividadId`.

## Tarea 3 (backend) — reserva pública con decremento atómico de cupo (decisión central de la ADR)

**Comportamiento observable**: sin sesión, `POST /api/turnos/reservas`
da de alta una reserva contra una franja existente cuya actividad esté
`ACTIVA` y con cupo disponible, decrementa `cupoDisponible` en 1, y
devuelve (201) la confirmación con el nombre de la actividad, fecha,
horario y `cupoDisponible` resultante — sin reexponer el DNI/contacto
que el propio vecino acaba de escribir (alcanza con que él ya lo tiene).
Con cupo en 0, la misma solicitud da 409 con un error de "no queda cupo
disponible" (`CupoAgotado`), y `cupoDisponible` queda exactamente en 0,
nunca negativo. Un mismo DNI que intenta reservar dos veces la misma
franja da 409 con un error distinto de "ya existe una reserva con ese
DNI para esta franja" (`ReservaDuplicada`), sin haber consumido cupo en
el segundo intento. `franjaId` inexistente → 404 `FranjaNoEncontrada`.
Actividad `INACTIVA` → 400 `SolicitudInvalida`.

**Por qué esto no es un `SELECT` seguido de un `UPDATE`**: dos
solicitudes públicas anónimas pueden llegar casi al mismo tiempo pidiendo
el último lugar de una franja, sin coordinarse entre sí. Si el código
lee `cupoDisponible`, decide en Java si es mayor a cero, y recién después
escribe el nuevo valor, hay una ventana entre la lectura y la escritura
en la que dos solicitudes pueden leer el mismo valor y las dos concluir
que hay cupo — eso sobrevende el lugar. La solución que exige esta
rebanada es que la condición y la escritura sean **la misma sentencia
SQL**, ejecutada como una única operación atómica.

**Implementación exacta**:

1. En `FranjaHorariaRepository` (interfaz Spring Data JPA):

   ```java
   @Modifying
   @Query("update FranjaHorariaEntity f set f.cupoDisponible = f.cupoDisponible - 1 "
           + "where f.id = :id and f.cupoDisponible > 0")
   int reservarUnLugar(@Param("id") Long id);
   ```

   Devuelve la cantidad de filas afectadas: `1` si había cupo y se
   decrementó, `0` si no había cupo (la fila existe pero no matcheó la
   condición). No uses `@Version`/bloqueo optimista con reintento — la
   sentencia atómica ya resuelve la carrera sin necesidad de reintentar
   nada (ADR 0026 §4). Bajo el nivel de aislamiento por defecto de
   Postgres (`READ COMMITTED`), dos `UPDATE` concurrentes sobre la misma
   fila se serializan a nivel de fila: el segundo espera a que el primero
   confirme o revierta, y vuelve a evaluar el `where` contra el valor ya
   confirmado — así que no hace falta subir el nivel de aislamiento ni
   agregar locking explícito.

2. En un nuevo `@Service GestionDeReservas` (servicio propio, separado de
   `GestionDeAgenda`: es el que atiende escritura pública anónima, no
   gestión administrativa), inyectando `FranjaHorariaRepository`,
   `ActividadRepository` y `TurnoRepository`:

   ```java
   @Transactional("tenantTransactionManager")
   TurnoEntity reservar(Long franjaId, String nombreSolicitante, String dniSolicitante, String contacto) {
       // 1. Validar campos no-blank y largos máximos (mismos límites de columna).
       // 2. Buscar la franja (o FranjaNoEncontrada, 404).
       // 3. Buscar la actividad de la franja y validar que esté ACTIVA
       //    (si no, SolicitudInvalida, 400).
       // 4. Chequeo temprano de duplicado: turnos.existsByFranjaIdAndDniSolicitante(franjaId, dniSolicitante)
       //    -> si ya existe, ReservaDuplicada (409) ACÁ, antes de tocar el cupo.
       //    Esto no es la barrera definitiva contra la duplicación bajo
       //    concurrencia (ver el punto 6, más abajo) — es una salida
       //    rápida para el caso común (secuencial, sin carrera).
       // 5. franjas.reservarUnLugar(franjaId): si devuelve 0, CupoAgotado (409).
       //    Recién en este punto se consumió un lugar.
       // 6. turnos.save(nuevo TurnoEntity): si tira DataIntegrityViolationException
       //    (dos solicitudes con el mismo DNI ganaron la carrera del paso 4
       //    casi al mismo tiempo, y la restricción unique de la base es la
       //    que realmente lo evita), atrapala y relanzá ReservaDuplicada (409).
       //    NO atrapes la excepción y devuelvas silenciosamente: tiene que
       //    seguir siendo una excepción sin atrapar (RuntimeException) que
       //    llegue hasta el proxy transaccional, para que TODA la
       //    transacción (incluido el decremento del cupo del paso 5) haga
       //    rollback. Si el rollback no ocurriera, quedaría un cupo
       //    consumido sin ningún turno guardado — un cupo "fantasma"
       //    perdido. Verificá esto con un test (Tarea 5).
   }
   ```

   `existsByFranjaIdAndDniSolicitante(Long franjaId, String dniSolicitante)`:
   agregalo a `TurnoRepository`.

**Modelo — `TurnoEntity`** (tabla `turno`), sin columna de tenant:
- `id`, `franjaId` (`bigint`, not null, FK a `franja_horaria.id`).
- `nombreSolicitante` (`varchar(150)`, not null), `dniSolicitante`
  (`varchar(20)`, not null), `contacto` (`varchar(200)`, not null —
  obligatorio, mismo criterio que `contacto` en Desarrollo Social, ADR
  0026 §4).
- `creadoEn` (`timestamptz`, not null, default `now()`).
- Sin más columnas: sin estado, sin cancelación (ADR 0026 §8) — no las
  agregues.
- Restricción `unique (franja_id, dni_solicitante)`, nombrala
  `turno_franja_id_dni_solicitante_unq`.
- Índice: `turno_franja_id_idx on turno (franja_id)`.

**`DescriptorDelModuloTurnos`** (edición de las Tareas 1/2):
`rutasDeEscrituraPublica() = List.of("/api/turnos/reservas")`.

**Controller**: `POST /api/turnos/reservas` sin `@PreAuthorize` (ruta de
escritura pública). Nuevos `@ExceptionHandler` en `TurnosController`:
`CupoAgotado` → 409 con mensaje "No queda cupo disponible para esta
franja."; `ReservaDuplicada` → 409 con mensaje "Ya existe una reserva
con ese DNI para esta franja."; `FranjaNoEncontrada` → 404 con mensaje
genérico "No encontramos esa franja.". Response
`ReservaPublicaResponse(Long id, String nombreActividad, LocalDate
fecha, LocalTime horaInicio, LocalTime horaFin, Integer
cupoDisponibleRestante)` — armalo en el servicio (join contra
`ActividadRepository`/`FranjaHorariaRepository`, no en el controller),
sin `nombreSolicitante`/`dniSolicitante`/`contacto`.

**Fuera de alcance de esta tarea**: listado protegido de reservas
(Tarea 4).

## Tarea 4 (backend) — agenda de gestión de reservas

**Comportamiento observable**: con sesión y `turnos.gestionar`, `GET
/api/turnos/reservas?franjaId={id}` devuelve el listado completo de
reservas de esa franja (nombre, DNI, contacto, `creadoEn`), ordenado por
`creadoEn` ascendente. `franjaId` es obligatorio (400 si falta). Sin
sesión, o con sesión pero sin `turnos.gestionar`, da 401/403 — no hay
lectura pública de reservas (ADR 0026 §5).

**Implementación**: `GestionDeReservas.listarParaGestion(Long
franjaId)` (mismo servicio de la Tarea 3, agregale este método de solo
lectura). Controller: `@PreAuthorize("hasAuthority('turnos.gestionar')")`,
response `TurnoResponse` con todos los campos.

## Tarea 5 (backend) — test de aislamiento entre tenants y test de concurrencia de cupo

**Obligatorio, no diferible (CLAUDE.md).** Crear
`backend/src/test/java/ar/com/ciudaddigital/turnos/TurnosTest.java`
(extiende `SoporteDeIntegracion`, mismo patrón que `ObrasTest`/
`ArboladoTest`/`DesarrolloSocialTest`).

**Test de aislamiento**, `@DisplayName("aislamiento: una actividad, una
franja o una reserva de un municipio no son visibles ni gestionables
desde otro")`: publica una actividad en el tenant A, le agrega una
franja, reserva un turno sobre esa franja (tenant A), y verifica que:
- `GET /api/turnos/actividades` desde el tenant B no incluye la
  actividad del tenant A.
- `GET /api/turnos/franjas?actividadId={id}` contra el tenant B con el
  `id` de la actividad del tenant A no devuelve la franja (lista vacía,
  no error — el `id` simplemente no matchea ninguna fila del tenant B).
- `PATCH /api/turnos/actividades/{id}/estado` contra el tenant B con el
  `id` de la actividad del tenant A da 404.
- `POST /api/turnos/actividades/{id}/franjas` contra el tenant B con el
  `id` de la actividad del tenant A da 404.
- `GET /api/turnos/reservas?franjaId={id}` (con `turnos.gestionar` en el
  tenant B) con el `id` de la franja del tenant A no devuelve la reserva.
- `POST /api/turnos/reservas` contra el tenant B con el `franjaId` de la
  franja del tenant A da 404 (`FranjaNoEncontrada`) — y de paso confirma
  que no decrementa el cupo de la franja del tenant A (verificalo
  consultando esa franja desde el tenant A después).

**Test de concurrencia de cupo**, `@DisplayName("concurrencia: N
reservas simultáneas sobre una franja con cupo M dejan exactamente M
reservas exitosas")`: crea una actividad `ACTIVA` y una franja con
`cupoTotal = 2`. Con un `ExecutorService` de al menos 5 hilos, lanzá 5
`POST /api/turnos/reservas` **simultáneos** (mismo `franjaId`, un DNI
distinto cada uno — usá `ExecutorService.invokeAll(...)` con las 5
tareas ya armadas antes de lanzarlas, no un `for` secuencial) contra esa
franja, esperá a que las 5 terminen, y verificá:
- Exactamente 2 respuestas con status 201.
- Exactamente 3 respuestas con status 409 (`CupoAgotado`).
- `GET /api/turnos/franjas?actividadId=...` muestra `cupoDisponible = 0`
  para esa franja (nunca negativo).
- `GET /api/turnos/reservas?franjaId=...` (con permiso) devuelve
  exactamente 2 filas.

**Test de reserva duplicada**: reservar con un DNI sobre una franja con
cupo, y reservar de nuevo con el mismo DNI sobre la misma franja → 409
`ReservaDuplicada`, y el cupo de la franja **no** bajó en el segundo
intento (verificá `cupoDisponible` antes y después del segundo intento:
tiene que ser el mismo valor) — este es el test que prueba que el
rollback de la Tarea 3, punto 6, funciona de verdad.

Cubrí además, en tests normales (no de aislamiento/concurrencia):
- Alta de actividad con `turnos.gestionar` (queda `ACTIVA`); alta sin el
  permiso (403); listado público con cada filtro (`tipo`/`estado`/`q`)
  por separado y combinados; `PATCH` de estado en ambos sentidos.
- Alta de franja con datos válidos; `horaFin` anterior o igual a
  `horaInicio` → 400; `cupoTotal` cero o negativo → 400; `actividadId`
  inexistente → 404.
- Reserva pública contra una franja con cupo (201, cupo baja en 1);
  contra una actividad `INACTIVA` → 400; contra un `franjaId`
  inexistente → 404.
- `GET /api/turnos/reservas` sin sesión → 401; con sesión pero sin
  `turnos.gestionar` → 403.
- `MODULO_NO_CONTRATADO` en todas las rutas cuando el tenant no tiene
  `turnos` contratado.

## Tarea 6 (frontend) — pantalla del módulo `turnos`

**Comportamiento observable**: pantalla nueva `PantallaDeTurnos.tsx` en
`frontend/src/modulos/turnos/`, registrada en
`frontend/src/modulos/registro.ts` (clave `turnos`).

Misma necesidad que `PantallaDeDesarrolloSocial` de combinar audiencias:
vecino anónimo, y quien gestiona con `turnos.gestionar`. Usá navegación
por estado local (sin router, ADR 0008):

```
const puedeGestionar = usuario?.permisos.includes('turnos.gestionar') ?? false
const [vista, setVista] = useState<'catalogo' | 'reserva' | 'agenda'>('catalogo')
```

1. **`catalogo`** (default, visible para todos, pública): listado de
   actividades — mismo patrón que la sección de búsqueda de
   `PantallaDeObras`/`PantallaDeArbolado` (filtros
   `tipo`/`estado`/`q`, tabla con `<caption>`, `scope="col"`/
   `scope="row"`). Columnas: Nombre, Tipo (etiquetas legibles: Deporte,
   Cultura, Turismo), Ubicación, Estado. Cada fila de actividad `ACTIVA`,
   al expandirse (botón "Ver franjas"), carga y muestra sus franjas
   (`GET /api/turnos/franjas?actividadId=...`): fecha, horario, "X
   lugares disponibles" (o "Sin cupo" en rojo si `cupoDisponible === 0`,
   con `aria-label` explícito, no solo color — criterio de accesibilidad
   de no depender únicamente del color). Cada franja con cupo disponible
   tiene un botón "Reservar" que navega a `reserva` con la franja
   seleccionada guardada en estado local. Si `puedeGestionar`: sección
   adicional "Publicar una actividad" (formulario: Nombre obligatorio,
   Tipo `<select>` obligatorio, Ubicación obligatoria, Descripción
   opcional `textarea`), por fila de actividad un botón "Cambiar estado"
   (`<select>` con la opción contraria a la actual, mismo patrón que
   `PantallaDeDesarrolloSocial`), y dentro de cada actividad expandida un
   formulario "Agregar franja" (Fecha, Hora inicio, Hora fin, Cupo total
   — todos obligatorios) más, por franja, un botón "Ver reservas" (→
   `agenda`, con esa franja seleccionada).

2. **`reserva`**: formulario público de alta contra la franja
   seleccionada (mismo patrón que `FormularioDeAlta` de
   `PantallaDeReclamos`), mostrando primero un resumen de la franja
   (actividad, fecha, horario, cupo disponible). Campos: Nombre y
   apellido (obligatorio), DNI (obligatorio), Contacto — teléfono o
   email (obligatorio). Al confirmar (`POST /api/turnos/reservas`),
   mostrá la confirmación devuelta por la API (actividad, fecha,
   horario, cupo restante) con `role="status"`. Si la API devuelve 409
   `CupoAgotado`, mostrá con `role="alert"` un mensaje claro ("Se agotó
   el cupo de esta franja justo ahora — elegí otra.") y un botón "Volver
   al catálogo" en vez de dejar el formulario reintentable sin contexto.
   Si devuelve 409 `ReservaDuplicada`, mostrá el mensaje de la API tal
   cual (`role="alert"`). Botón "Volver al catálogo" siempre visible.

3. **`agenda`** (solo alcanzable si `puedeGestionar`; si alguien sin el
   permiso llega a este estado por cualquier motivo, redirigí a
   `catalogo` en vez de renderizarla — la protección real es el backend):
   `GET /api/turnos/reservas?franjaId=...` para la franja seleccionada.
   Tabla con columnas Nombre, DNI, Contacto, Reservado el (fecha/hora).
   Botón "Volver al catálogo".

**Accesibilidad (obligatorio, no diferible, CLAUDE.md)**: replicá al pie
de la letra los patrones ya usados en `PantallaDeArbolado.tsx` y
`PantallaDeDesarrolloSocial.tsx` — foco gestionado por
`useRef`+`tabIndex={-1}` al montar/cambiar de vista, anuncios con
`role="status"`/`role="alert"`, `aria-invalid`/`aria-describedby` en
campos con error, `aria-busy` en botones de acción en curso, `<label
htmlFor>` en todo input/select/textarea, tabla con `<caption>` y
`scope="col"`/`scope="row"`, y el aviso de "cupo agotado" con
`aria-label` explícito además de color (no dependas solo del color para
comunicar "sin cupo"). No inventes un patrón nuevo de accesibilidad.

**Fuera de alcance**: routing de URLs, edición de una franja/actividad ya
creada, cancelación de una reserva, mapa/geolocalización, paginado,
exportación de la agenda, indicador de "actualizando en vivo" del cupo
(el cupo que ve el vecino es el de la última carga del catálogo, no se
refresca solo — si dos vecinos miran la misma franja al mismo tiempo,
ambos pueden ver "1 lugar disponible" y solo uno gana la reserva al
confirmar; el manejo de ese caso ya está cubierto por el mensaje de 409
de arriba, no hace falta un mecanismo de tiempo real).

## Qué NO tocar

- Los módulos `obras`, `arbolado`, `reclamos`, `mesaentradas`,
  `desarrollosocial`: código, tablas, permisos. `turnos` no depende de
  ninguno de ellos (ADR 0026 §1).
- `seguimientoanonimo`, `pagos`: no se usan en esta rebanada (ADR 0026
  §5/§8) — no los enganches "por si acaso".
- `modulosHabilitados` de los tenants de prueba `sanmartin`/`moron`
  (`db/control/V2__sembrar_municipios_de_prueba.sql`): si hace falta
  `turnos` contratado para una demo manual, sembralo con el mecanismo ya
  existente. Los tests de integración contratan el módulo directamente
  contra la base de control de test, mismo patrón que `ObrasTest`/
  `ArboladoTest`/`DesarrolloSocialTest`.

## Instrucciones para los agentes implementadores

No hagas commit, push ni abras PR por tu cuenta: dejá los cambios en el
working tree. El tech lead revisa, commitea y coordina el PR.
