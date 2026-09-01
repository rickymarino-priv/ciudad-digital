# CD-35 · R26 — Agenda de eventos de Cultura, Turismo y Deportes, tercera rebanada de Fase 6

Ver [ADR 0030](../docs/arquitectura/decisiones/0030-agenda-de-eventos-cultura-turismo-y-deporte-tercera-rebanada-de-fase-6.md)
para el porqué de cada decisión de esta spec, en particular por qué esto
**no** es lo mismo que `turnos` (ADR 0026) y por qué el módulo se llama
`eventos` y no `agenda`. Esta spec no reabre nada del ADR: lo traduce a
tareas concretas.

## Demo objetivo

Un agente municipal (con sesión y permiso `eventos.gestionar`) publica un
evento nuevo: "Maratón Municipal" (categoría Deporte), ubicación
"Costanera", del 15 al 15 de octubre (un solo día), 9:00hs. Queda creado
en estado "Programado". Un vecino, sin sesión, entra al portal público,
ve la agenda ordenada por fecha (los eventos que vienen primero, arriba),
filtra por categoría "Deporte" y busca por texto, y encuentra ese evento.
El agente cancela el evento (por ejemplo, por mal tiempo); al volver a
consultar, el vecino lo ve como "Cancelado", sin que desaparezca de la
agenda. El mismo evento no aparece en el portal de otro municipio.

## Tarea 1 (backend) — módulo `eventos`: modelo, alta protegida, lectura pública, migración, permisos

**Comportamiento observable**: con sesión y `eventos.gestionar`, `POST
/api/eventos` da de alta un evento en estado `PROGRAMADO` y devuelve sus
datos (201). Sin sesión, `GET /api/eventos` devuelve la agenda pública de
eventos del municipio en curso, con filtros opcionales combinables
`categoria`, `estado` y `q` (coincidencia `ILIKE` en `nombre` u
`ubicacion`), **ordenada por `fechaInicio` ascendente y, a igual fecha,
por `nombre` ascendente** (ADR 0030 §4 — no por `creadoEn` descendente
como el resto del patrón del proyecto; es una desviación deliberada, no
un error). Un municipio sin el módulo `eventos` contratado rechaza ambas
rutas con 403 `MODULO_NO_CONTRATADO`, con o sin sesión. Sin sesión, `POST
/api/eventos` da 401/403; con sesión pero sin `eventos.gestionar`, 403.

**Modelo** (`eventos.internal`, módulo nuevo, prefijo `/api/eventos`):

- `CategoriaDeEvento`: enum `CULTURA, TURISMO, DEPORTE, OTRA`. **No
  reutilices** `turnos.internal.TipoDeActividad` — este módulo no depende
  de `turnos`, se define desde cero (ADR 0030 §2/§1).
- `EstadoDeEvento`: enum `PROGRAMADO, CANCELADO`.
- `EventoEntity` (tabla `evento`), sin columna de tenant (mismo criterio
  que `ObraPublicaEntity`/`EspacioVerdeEntity`: vive en la base del
  municipio, aislada por base física, no por columna):
  - `id`, `nombre` (`varchar(200)`, not null), `categoria` (`varchar(20)`,
    not null, `check` de valores válidos), `ubicacion` (`varchar(300)`,
    not null, texto libre), `descripcion` (`text`, nullable).
  - `fechaInicio` (`date`, not null), `fechaFin` (`date`, nullable),
    `horaInicio` (`time`, nullable, tipo Java `LocalTime`). Sin
    `horaFin`: no es una franja reservable (ADR 0030 §2).
  - `estado` (`varchar(20)`, not null, default `'PROGRAMADO'`, `check` de
    valores válidos).
  - Copia del actor que registra (mismo criterio que
    `publicadoPorNombre`/`publicadoPorEmail` en `ObraPublicaEntity`/
    `EspacioVerdeEntity`, ADR 0013): `publicadoPorNombre` (`varchar(150)`,
    not null), `publicadoPorEmail` (`varchar(200)`, not null).
  - `creadoEn`, `actualizadoEn` (`timestamptz`, not null, default
    `now()`).
  - Sin más columnas: nada de motivo de cancelación, recurrencia,
    adjuntos ni geolocalización estructurada (ADR 0030 §8, Pendiente de
    definir) — no las agregues aunque te parezcan naturales, están fuera
    de alcance a propósito.
  - Índices: `evento_fecha_inicio_idx on evento (fecha_inicio)` (orden y
    filtro principal de la agenda pública — a diferencia del resto del
    patrón, acá el índice de orden **no** es sobre `creado_en`, ver ADR
    0030 §4), `evento_estado_idx on evento (estado)` (filtro de la
    agenda pública).

- `GestionDeEventos` (`@Service`), con
  `@Transactional("tenantTransactionManager")` en los métodos de
  escritura:
  - `publicar(nombre, categoria, ubicacion, descripcion, fechaInicio,
    fechaFin, horaInicio, publicadoPorNombre, publicadoPorEmail)`: valida
    `nombre`/`ubicacion` no-blank y largos máximos (mismos límites de
    columna), `categoria` no nula, `fechaInicio` no nula. Si `fechaFin`
    viene, tiene que ser `>= fechaInicio` (`LocalDate.isBefore`); si no,
    400 `SolicitudInvalida` con mensaje claro ("La fecha de fin no puede
    ser anterior a la fecha de inicio."). `descripcion` y `horaInicio` son
    opcionales sin más validación que el tipo. Guarda con `estado =
    PROGRAMADO`; el estado inicial **no** es un parámetro que reciba el
    cliente, es siempre `PROGRAMADO`.
  - `buscar(categoria, estado, q)`: los tres parámetros opcionales y
    combinables (AND entre los que vengan). `categoria`/`estado`
    inválidos (que no matcheen ningún valor del enum) → 400
    `SolicitudInvalida`, no se tratan como "sin filtro". `q` es `ILIKE
    '%valor%'` contra `nombre` **o** `ubicacion`. Ordena por
    `fechaInicio` ascendente, luego `nombre` ascendente, sin paginado
    (fuera de alcance, mismo criterio que el resto del patrón).

Creá también, desde cero en `eventos.internal` (son clases
package-private, no se pueden reutilizar entre módulos):
`SolicitudInvalida` (mismo texto/patrón que
`espaciosverdes.internal.SolicitudInvalida`), `EventoNoEncontrado` (mismo
patrón que `EspacioVerdeNoEncontrado`/`ObraNoEncontrada`), y
`package-info.java` del paquete `eventos` con el resumen del módulo (mismo
estilo que `ar.com.ciudaddigital.espaciosverdes.package-info`, citando ADR
0030).

**Registro de persistencia (obligatorio, se suele olvidar)**: agregá
`PAQUETE_EVENTOS = "ar.com.ciudaddigital.eventos"` a
`backend/src/main/java/ar/com/ciudaddigital/persistencia/ConfiguracionDePersistencia.java`
y sumalo tanto a `basePackages` de `RepositoriosDeTenant` como a
`setPackagesToScan(...)`/la lista de `tenantEntityManagerFactory` (mismo
patrón que `PAQUETE_ESPACIOSVERDES`, agregado en R25). Sin este paso, las
entidades de `eventos` no se mapean contra la base del municipio y todo
falla en runtime con un error de metamodelo, no en compilación.

**Fuera de alcance de esta tarea**: cambio de estado/cancelación (Tarea
2), permisos y `DescriptorDeModulo` (van también en esta tarea, ver abajo
— no hay una Tarea separada solo para eso, mismo criterio que el resto del
patrón).

**Migración** (`V26__crear_eventos.sql`, tenant): tabla `evento` completa
(todas las columnas de arriba), catálogo de permisos:

```sql
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('eventos.gestionar', 'Cultura, Turismo y Deportes', 'eventos', 'gestionar',
     'Publicar un evento en la agenda cultural, turística o deportiva y cancelarlo.');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo from rol r, permiso p
where r.codigo in ('administrador', 'agente') and p.codigo = 'eventos.gestionar';
```

(Ver ADR 0030 §6 para el porqué de un único permiso y de asignarlo a
ambos roles de sistema — no lo reabras.)

**`DescriptorDelModuloEventos`** (`eventos.internal`, `@Component`):
- `codigo() = "eventos"`, `nombre() = "Cultura, Turismo y Deportes"`,
  `descripcion()` breve (agenda pública de eventos culturales, turísticos
  y deportivos), prefijo `/api/eventos`.
- `rutasDeLecturaPublica() = List.of("/api/eventos")` (la agenda con
  filtros).
- `rutasDeEscrituraPublica()`: **no la sobrescribas** (default vacío) —
  este módulo no tiene ninguna escritura pública/anónima, mismo criterio
  que Obras/Arbolado/Educación/Espacios verdes (ADR 0030 §5).

**Controller** (`EventosController`, `/api/eventos`): seguí el estilo de
`EspaciosVerdesController`/`ObrasController` (mismos nombres de patrón:
`ErrorResponse`, `@ExceptionHandler` por tipo de excepción, records para
request/response, helpers `categoriaDe(String)`/`estadoDe(String)` que
parsean o tiran `SolicitudInvalida`). `POST /api/eventos` con
`@PreAuthorize("hasAuthority('eventos.gestionar')")`, usa
`ActorAutenticado` (mismo mecanismo que `ObrasController.actorDe`) para
`publicadoPorNombre`/`publicadoPorEmail`. `GET /api/eventos` sin
`@PreAuthorize` (pública). Response único `EventoResponse` con todos los
campos (`fechaFin`/`horaInicio` como `null` si no se cargaron).

## Tarea 2 (backend) — cancelación del evento

**Comportamiento observable**: con sesión y `eventos.gestionar`, `PATCH
/api/eventos/{id}/estado` con body `{estadoNuevo: "CANCELADO"}` cancela un
evento `PROGRAMADO` y actualiza `actualizadoEn`. Cualquier otro valor de
`estadoNuevo` (incluido `"PROGRAMADO"`, y cualquier intento sobre un
evento ya `CANCELADO`) → 400 `SolicitudInvalida` con mensaje claro
indicando el estado actual y el pedido — la única transición válida es
`PROGRAMADO → CANCELADO` (ADR 0030 §3, sin retorno). `id` inexistente →
404 (`EventoNoEncontrado`). Sin sesión o sin `eventos.gestionar` →
401/403, no está en `rutasDeEscrituraPublica()`.

**Implementación**:
- `GestionDeEventos.cancelar(Long id)`: busca el evento (o
  `EventoNoEncontrado`), valida que su estado actual sea `PROGRAMADO` (si
  no, `SolicitudInvalida`), aplica `CANCELADO` y `actualizadoEn =
  Instant.now()`. Dado que hay una única transición posible, no hace
  falta un `Map<EstadoDeEvento, Set<EstadoDeEvento>>` como en
  Obras/Arbolado/Espacios verdes: alcanza con el chequeo directo `if
  (estadoActual != PROGRAMADO) throw ...` — no construyas una tabla de
  transiciones genérica para un solo caso, sería una abstracción sin uso
  real (ADR 0030 §7). El endpoint sigue aceptando `estadoNuevo` en el body
  (no un endpoint `POST .../cancelar` sin body) por consistencia con el
  resto del patrón (`PATCH .../estado`), aunque el único valor válido hoy
  sea `CANCELADO`.
- Podés poner el chequeo en `EventoEntity.cancelar()` (sin parámetro de
  estado nuevo, ya que solo hay un destino posible) como segunda barrera,
  a tu criterio de dónde queda más claro — si lo hacés así, el controller/
  servicio igual valida que el `estadoNuevo` del body sea exactamente
  `CANCELADO` antes de llamarlo, para no aceptar silenciosamente un valor
  no soportado.
- No se agrega ninguna columna nueva para esta tarea: no hay campo de
  motivo de cancelación (ADR 0030, Pendiente de definir) — no lo agregues
  por iniciativa propia.

**Fuera de alcance**: edición de `nombre`/`categoria`/`ubicacion`/
`descripcion`/fechas/`horaInicio` después de creado el evento, y cualquier
estado más allá de `PROGRAMADO`/`CANCELADO` (por ejemplo `FINALIZADO`) —
no lo construyas ni lo bloquees con validación extra, simplemente no
existe.

## Tarea 3 (backend) — test de aislamiento entre tenants

**Obligatorio, no diferible (CLAUDE.md).** Crear
`backend/src/test/java/ar/com/ciudaddigital/eventos/EventosTest.java`
(extiende `SoporteDeIntegracion`, mismo patrón que
`EspaciosVerdesTest`/`ObrasTest`), con un test `@DisplayName("aislamiento:
un evento publicado en un municipio no es visible ni cancelable desde
otro")` que: publica un evento en el tenant A, verifica que `GET
/api/eventos` (con y sin filtros) desde el tenant B no lo incluye, y que
`PATCH /api/eventos/{id}/estado` ejecutado contra el tenant B con el `id`
del evento del tenant A da 404 (no "lo encuentra y lo cancela").

Cubrí además, en tests normales (no de aislamiento): alta con
`eventos.gestionar` (queda `PROGRAMADO`), alta sin el permiso (403), alta
con `fechaFin` anterior a `fechaInicio` → 400, alta sin `fechaFin` ni
`horaInicio` (ambos opcionales, quedan `null`), listado público sin
sesión con cada filtro (`categoria`, `estado`, `q`) por separado y
combinados, **orden del listado por `fechaInicio` ascendente** (crear
eventos con fechas fuera de orden de creación y verificar que el listado
los devuelve ordenados por fecha, no por orden de alta), cancelación
exitosa (`PROGRAMADO → CANCELADO`), cancelación de un evento ya
`CANCELADO` → 400, intento de `estadoNuevo: "PROGRAMADO"` → 400, y
`MODULO_NO_CONTRATADO` en `POST`/`GET`/`PATCH` cuando el tenant no tiene
`eventos` contratado.

## Tarea 4 (frontend) — pantalla del módulo `eventos`

**Comportamiento observable**: pantalla nueva `PantallaDeEventos.tsx` en
`frontend/src/modulos/eventos/`, registrada en
`frontend/src/modulos/registro.ts` igual que el resto (agregando la
entrada `eventos: PantallaDeEventos`).

Es una única vista para todos (sin router, mismo patrón por estado local
que `PantallaDeEspaciosVerdes`/`PantallaDeObras`): agenda pública siempre
visible, con la acción de "Publicar evento" y la acción de cancelar
apareciendo condicionadas al permiso, mismo patrón exacto que
`PantallaDeEspaciosVerdes`/`PantallaDeObras` (no repliques el patrón de
`PantallaDeReclamos`, que muestra vistas *alternativas* según permiso).

1. **Agenda pública** (default, pública, sin sesión): filtros combinables
   — `<select>` de categoría (con opción "Todas"), `<select>` de estado
   (con opción "Todos"), campo de texto para `q` ("Buscar en nombre o
   ubicación"). Tabla con columnas Nombre, Categoría, Fecha (mostrar
   `fechaInicio` formateada con `Intl.DateTimeFormat`, mismo criterio que
   fechas en `PantallaDeObras`/`PantallaDeEspaciosVerdes`; si hay
   `fechaFin` distinta de `fechaInicio`, mostrar el rango "15 oct al 17
   oct", si no, mostrar solo la fecha; agregar la hora si `horaInicio` no
   es null, ej. "15 oct, 9:00hs"), Ubicación, Estado. **La tabla ya viene
   ordenada por el backend por fecha ascendente — no reordenes en el
   frontend.** Etiquetas legibles en español para `categoria` (Cultura,
   Turismo, Deporte, Otra) y `estado` (Programado, Cancelado), mismo
   patrón `ETIQUETA_ESTADO`/`ETIQUETA_CATEGORIA` que
   `PantallaDeEspaciosVerdes`.

2. **Publicar evento** (visible solo con
   `usuario?.permisos.includes('eventos.gestionar')`, mismo patrón que la
   sección "Registrar espacio verde" de `PantallaDeEspaciosVerdes`):
   formulario con Nombre (`input`, obligatorio), Categoría (`<select>`,
   obligatorio, sin opción "Todas" acá), Ubicación (`input`, obligatorio),
   Fecha de inicio (`input type="date"`, obligatorio), Fecha de fin
   (`input type="date"`, opcional — si se completa, el frontend valida
   que no sea anterior a la fecha de inicio antes de enviar, mismo
   criterio de validación duplicada cliente+servidor que el resto del
   proyecto), Hora de inicio (`input type="time"`, opcional), Descripción
   (opcional, `textarea`). Al confirmar, recarga la agenda con los
   filtros aplicados y cierra el formulario (mismo flujo que
   `registrarEspacioVerde` en `PantallaDeEspaciosVerdes`).

3. **Cancelar evento** (visible por fila, solo con `eventos.gestionar`, y
   solo si el evento está `PROGRAMADO` — un evento `CANCELADO` no muestra
   ninguna acción, no hay a dónde transicionar): un botón "Cancelar
   evento" por fila (sin `<select>` de destino, a diferencia de
   `PantallaDeEspaciosVerdes`/`PantallaDeObras`: acá hay un solo destino
   posible, así que el botón dispara directo `PATCH
   /api/eventos/{id}/estado` con `{estadoNuevo: "CANCELADO"}", sin
   selector). Pedí confirmación antes de disparar (`window.confirm` o un
   diálogo accesible propio, mismo criterio mínimo que ya usa el proyecto
   para acciones destructivas — revisá si `PantallaDeObras` o similar ya
   tiene un patrón de confirmación antes de inventar uno nuevo; si no hay
   ninguno, un `window.confirm` con el nombre del evento alcanza para esta
   rebanada). Al confirmar, recargá la agenda completa con los filtros
   aplicados.

**Accesibilidad (obligatorio, no diferible, CLAUDE.md)**: seguí al pie de
la letra los patrones ya usados en `PantallaDeEspaciosVerdes.tsx` — foco
gestionado por `useRef`+`tabIndex={-1}` al montar/cambiar de estado,
anuncios con `role="status"`/`role="alert"`, `aria-invalid`/
`aria-describedby` en campos con error, `aria-busy` en botones de acción
en curso, `<label htmlFor>` en todo input/select, tabla con `<caption>` y
`scope="col"`/`scope="row"`. No inventes un patrón nuevo de
accesibilidad: replicá el que ya existe.

**Fuera de alcance**: routing de URLs (no existe en este frontend),
edición de los campos del alta, mapa/geolocalización, adjuntos/fotos,
paginado, eventos recurrentes, motivo de cancelación.

## Qué NO tocar

- El módulo `turnos` (código, tablas, permisos, `TipoDeActividad`): no se
  reutiliza ni se extiende (ADR 0030, "Por qué esto no es lo mismo que
  `turnos`" / §1/§2).
- Los módulos `obras`, `arbolado`, `educacion`, `espaciosverdes` (código,
  tablas, permisos): `eventos` no depende de ninguno ni reutiliza su
  código (ADR 0030 §1/§7).
- Los permisos de `boletin`, `reclamos`, `transparencia`, `multas`,
  `obras`, `arbolado`, `educacion`, `espaciosverdes`, `turnos`, `prensa` u
  otro módulo existente.
- `modulosHabilitados` de los tenants de prueba `sanmartin`/`moron`
  (`db/control/V2__sembrar_municipios_de_prueba.sql`): si hace falta que
  `eventos` esté contratado para demostrarlo manualmente, hacelo
  sembrando la contratación con el mecanismo ya existente (mismo criterio
  que la spec de CD-34 dejó para `espaciosverdes`). Los tests de
  integración contratan el módulo directamente contra la base de control
  de test, mismo patrón que `EspaciosVerdesTest`/`ObrasTest`.

## Instrucciones para los agentes implementadores

No hagas commit, push ni abras PR por tu cuenta: dejá los cambios en el
working tree. El tech lead revisa, commitea y coordina el PR.
