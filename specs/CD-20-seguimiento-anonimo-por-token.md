# CD-20 · R12 — Un vecino sin sesión consulta el estado de su reclamo o trámite con el token que recibió al cargarlo

Rama: `CD-20-consulta-publica-por-token` (desde `develop`).

Requiere [ADR 0017](../docs/arquitectura/decisiones/0017-seguimiento-anonimo-por-token-en-reclamos-y-mesa-de-entradas.md)
(léanlo antes de tocar código: decide generación del token, por qué se
guarda hasheado, por qué el mecanismo es compartido entre `reclamos` y
`mesaentradas`, y qué devuelve exactamente cada consulta pública). Esta
spec no repite ese razonamiento, solo lo traduce a tareas concretas.

## Demo

Un vecino, sin sesión, carga un reclamo (o inicia un trámite de Mesa de
Entradas). La confirmación del alta le muestra, además de lo que ya
mostraba, un código largo (`tokenDeSeguimiento`) con la instrucción
explícita de guardarlo porque es la única forma de volver a consultar. En
una pantalla pública nueva, pega ese código y ve el estado actual (y, en
Mesa de Entradas, el historial de movimientos). Un código inventado, o el
de un municipio probado contra otro, no encuentra nada.

## Qué se construye

### Backend — Tarea 1 (bloqueante, un solo agente `backend`): módulo `seguimientoanonimo`

Paquete nuevo `ar.com.ciudaddigital.seguimientoanonimo` (sin
`.internal`: la única clase es API pública del módulo, mismo criterio que
`ActorAutenticado`/`DescriptorDeModulo`).

- `package-info.java`: explicar que es módulo canon base (no
  contratable, sin `DescriptorDeModulo`), sin persistencia propia, usado
  por `reclamos` y `mesaentradas` para generar y verificar el token de
  seguimiento anónimo (ADR 0017).

- `TokenDeSeguimiento`: clase pública, final, constructor privado (mismo
  patrón que `RespuestasJson` en `acceso.internal`), dos métodos
  estáticos:
  - `String generar()`: 32 bytes de `java.security.SecureRandom` (una
    instancia `static final`, no crear una por llamada), codificados con
    `Base64.getUrlEncoder().withoutPadding().encodeToString(...)`.
  - `String hash(String tokenEnClaro)`: SHA-256
    (`MessageDigest.getInstance("SHA-256")`) sobre los bytes UTF-8 del
    token, resultado en hexadecimal minúscula (64 caracteres). Si
    `tokenEnClaro` es `null` o vacío, devolver algo que nunca matchee un
    hash real (por ejemplo lanzar `IllegalArgumentException`: nadie
    debería llamar a `hash` con un valor vacío, ni siquiera un llamador
    interno del propio módulo `reclamos`/`mesaentradas`) — decisión del
    agente `backend` sobre la forma exacta, pero que quede explícito y
    documentado en el Javadoc por qué.

No hace falta test unitario dedicado a esta clase por fuera de los tests
de integración de `reclamos`/`mesaentradas` (Tareas 2 y 3): no tiene
lógica de negocio propia que amerite un test aislado, y su comportamiento
ya queda ejercitado por los tests de alta/consulta de ambos módulos.

### Backend — Tarea 2 (después de la Tarea 1, mismo agente `backend`): `reclamos`

**Migración** `V12__agregar_token_de_seguimiento_a_reclamo.sql` en
`backend/src/main/resources/db/tenant/`:

```sql
-- Token de seguimiento anónimo (ADR 0017): el vecino que cargó un
-- reclamo sin sesión recibe, una única vez, un token en claro para
-- volver a consultar el estado más adelante. Acá solo se guarda su hash
-- SHA-256, nunca el token en claro.
alter table reclamo add column token_hash varchar(64) not null;

create unique index reclamo_token_hash_idx on reclamo (token_hash);

comment on column reclamo.token_hash is
    'Hash SHA-256 del token de seguimiento anónimo (ADR 0017). El token en claro no se guarda en ningún lado.';
```

Si en algún ambiente de desarrollo ya hay filas de `reclamo` cargadas a
mano (no debería pasar en test, que arranca la base desde V1 en cada
corrida), la migración va a fallar por la restricción `not null` sin
default: resetear los volúmenes de la base de desarrollo (`docker compose
down -v` + recrear), no agregar un `default` artificial a la columna —
un token por defecto compartido rompería la garantía de unicidad que este
mecanismo existe para dar.

**Cambios en `ar.com.ciudaddigital.reclamos.internal`**:

- `ReclamoEntity`: agregar campo `tokenHash` (`@Column(name =
  "token_hash", nullable = false, length = 64)`), fijado en
  `nuevo(...)` — el factory method gana un parámetro más,
  `tokenHash`, que recibe ya calculado (no genera el token él mismo: la
  entidad no depende de `seguimientoanonimo`, es `GestionDeReclamos`
  quien orquesta). Sin getter público del token en claro porque nunca se
  guarda en claro; sí puede tener (o no, a criterio del agente) un getter
  de `tokenHash` si hace falta para el repositorio — Spring Data puede
  derivar la consulta sin él, así que solo agregarlo si realmente se
  necesita.

- `ReclamoRepository`: agregar `Optional<ReclamoEntity>
  findByTokenHash(String tokenHash)`.

- `GestionDeReclamos`:
  - `cargar(...)` pasa a generar `TokenDeSeguimiento.generar()`, calcular
    `TokenDeSeguimiento.hash(...)` y pasar el hash a `ReclamoEntity.nuevo(...)`.
    Cambia lo que devuelve: hoy devuelve `ReclamoEntity`, tiene que
    devolver también el token en claro para que el controller lo mande
    en la respuesta **una sola vez**. Resolver esto con un record nuevo
    package-private, por ejemplo `record ReclamoCreado(ReclamoEntity
    reclamo, String tokenDeSeguimiento) {}`, en vez de forzar el
    controller a volver a tocar el servicio.
  - Nuevo método `ReclamoEntity consultarPorToken(String token)`: valida
    que `token` no sea `null`/vacío (si lo es, tratarlo igual que "no
    encontrado", nunca como `SolicitudInvalida` 400 — ver Decisión 4 del
    ADR sobre no distinguir formato inválido de no encontrado), calcula
    `TokenDeSeguimiento.hash(token)`, busca con
    `findByTokenHash(...)`, y si no aparece nada lanza una excepción
    nueva `TokenNoEncontrado` (package-private, `extends
    RuntimeException`, mismo paquete que `SolicitudInvalida`).

- `ReclamosController`:
  - `cargar(...)` ahora usa `gestion.cargar(...)` (el nuevo
    `ReclamoCreado`) y agrega `tokenDeSeguimiento` a
    `ReclamoPublicoResponse` (el único momento en el que ese campo tiene
    un valor no nulo en toda la vida del reclamo).
  - Nuevo endpoint `@GetMapping("/seguimiento/{token}")` — **sin**
    `@PreAuthorize`, ruta pública: llama a
    `gestion.consultarPorToken(token)` y devuelve un DTO nuevo,
    `SeguimientoDeReclamoResponse(Long id, String categoria, String
    estado, String comentarioGestion, Instant creadoEn, Instant
    actualizadoEn)` — exactamente los campos que decide el ADR 0017 §5,
    ni uno más (nada de `descripcion`/`direccion`/`nombreContacto`/
    `contacto`).
  - Nuevo `@ExceptionHandler(TokenNoEncontrado.class)` →
    `ResponseEntity.status(HttpStatus.NOT_FOUND).body(new
    ErrorResponse(...))`, mensaje genérico (algo como "No encontramos un
    reclamo con ese código.") que no distinga por qué falló.

- `DescriptorDelModuloReclamos`: `rutasDeLecturaPublica()` pasa de
  `List.of()` a `List.of("/api/reclamos/seguimiento/{token}")`. Verificar
  al implementar que `HttpSecurity#requestMatchers(HttpMethod, String)`
  matchea la variable de path tal cual (debería, es un
  `PathPatternRequestMatcher` estándar de Spring Security 6); si por
  algún motivo no matchea así, avisar antes de improvisar un patrón
  distinto (`/api/reclamos/seguimiento/**`), porque cambia qué tan
  específica es la regla de seguridad.

**Test**: extender `ReclamosTest.java` (no crear una clase nueva) con:

1. El alta pública (`altaAnonimaSoloConElModuloContratado`, ya existente)
   gana una aserción de que la respuesta trae `tokenDeSeguimiento` no
   vacío.
2. Nuevo test: cargar un reclamo, tomar el `tokenDeSeguimiento` de la
   respuesta, `GET /api/reclamos/seguimiento/{token}` sin sesión →
   200, con `id`/`categoria`/`estado` correctos y **sin** los campos
   `descripcion`/`direccion`/`nombreContacto`/`contacto` en el JSON
   (`jsonPath("$.descripcion").doesNotExist()`, etc.). Cambiar el estado
   con sesión de administrador y volver a consultar por token: el nuevo
   `estado`/`comentarioGestion` se reflejan.
3. Nuevo test: `GET /api/reclamos/seguimiento/{token}` con un token que
   no existe (por ejemplo `"token-inventado"`) → 404. Sin el módulo
   contratado, la misma consulta con un token válido → 403
   `MODULO_NO_CONTRATADO` (el gating por entitlement sigue corriendo
   antes que la regla de ruta pública).
4. **Aislamiento entre tenants**: el `tokenDeSeguimiento` de un reclamo
   cargado en el municipio A, consultado contra el subdominio de B (`GET
   portalDe(B, "/api/reclamos/seguimiento/" + token)`, con B con el
   módulo contratado), devuelve 404 — no porque el token esté "mal", sino
   porque la consulta corre contra la base de B, que no tiene esa fila
   (la garantía real de aislamiento es que la query usa el datasource
   ruteado por tenant, mismo mecanismo que ya prueba
   `aislamientoEntreTenants` para el listado).

### Backend — Tarea 3 (puede ir en paralelo con la Tarea 2, mismo agente `backend`): `mesaentradas`

Mismo patrón que la Tarea 2, aplicado a `expediente`/`ExpedienteEntity`/
`ExpedienteRepository`/`GestionDeExpedientes`/`MesaDeEntradasController`/
`DescriptorDelModuloMesaDeEntradas`. Puntualmente:

**Migración** `V13__agregar_token_de_seguimiento_a_expediente.sql`
(número siguiente al que exista en `db/tenant/` al momento de
implementar — confirmar que no choca con ningún otro V12 si la Tarea 2 y
esta se implementan en un orden distinto al de esta spec; son
migraciones de tablas distintas así que no importa cuál va primero,
importa que los números no se repitan):

```sql
-- Token de seguimiento anónimo (ADR 0017): mismo mecanismo que reclamo
-- (ver esa migración para el razonamiento completo).
alter table expediente add column token_hash varchar(64) not null;

create unique index expediente_token_hash_idx on expediente (token_hash);

comment on column expediente.token_hash is
    'Hash SHA-256 del token de seguimiento anónimo (ADR 0017). El token en claro no se guarda en ningún lado.';
```

**Cambios en `ar.com.ciudaddigital.mesaentradas.internal`**:

- `ExpedienteEntity`: mismo agregado que `ReclamoEntity` (`tokenHash`,
  parámetro de `nuevo(...)`).
- `ExpedienteRepository`: `Optional<ExpedienteEntity>
  findByTokenHash(String tokenHash)`.
- `GestionDeExpedientes`:
  - `iniciar(...)` genera y hashea el token igual que
    `GestionDeReclamos.cargar(...)`; devuelve un record nuevo
    `ExpedienteCreado(ExpedienteEntity expediente, String
    tokenDeSeguimiento)`.
  - Nuevo `ExpedienteEntity consultarPorToken(String token)`, mismo
    criterio que en `reclamos` (token vacío/no encontrado → misma
    excepción `TokenNoEncontrado`, package-private en este paquete —
    **no** compartir la clase de excepción entre módulos, mismo criterio
    que ya usan las dos clases `SolicitudInvalida` package-private
    homónimas de `reclamos` y `mesaentradas`, cada módulo tiene la suya).
- `MesaDeEntradasController`:
  - `iniciar(...)` agrega `tokenDeSeguimiento` a
    `ExpedientePublicoResponse`.
  - Nuevo `@GetMapping("/seguimiento/{token}")` público →
    `SeguimientoDeExpedienteResponse`: mismos campos que
    `ExpedienteResponse` **menos** `solicitanteContacto`, y con
    `movimientos` mapeado a un `MovimientoSeguimientoResponse` sin
    `actorNombre`/`actorEmail` (`estadoAnterior`, `estadoNuevo`,
    `comentario`, `fecha`) — ver ADR 0017 §5. Los campos propios del tipo
    de trámite (`domicilioACertificar`, etc.) sí se incluyen tal cual:
    son datos que el propio vecino ya cargó.
  - Mismo `@ExceptionHandler(TokenNoEncontrado.class)` → 404.
- `DescriptorDelModuloMesaDeEntradas`: agregar
  `/api/mesaentradas/seguimiento/{token}` a `rutasDeLecturaPublica()`.

**Test**: extender `MesaDeEntradasTest.java` con los mismos cuatro casos
de la Tarea 2, adaptados: token en la respuesta del alta; consulta pública
devuelve estado + historial de movimientos sin actor y sin
`solicitanteContacto`; token inexistente → 404; token de un municipio
consultado contra el otro → 404 (aislamiento).

### Frontend — Tarea 4 (después de que el backend de ambos módulos esté completo, un solo agente `frontend`)

**`PantallaDeReclamos.tsx`**:

- Tipo `RespuestaAlta` gana `tokenDeSeguimiento: string`.
- En `FormularioDeAlta`, el mensaje de confirmación (el que hoy dice
  "en esta rebanada todavía no hay una pantalla para volver a
  consultarlo más adelante, así que te conviene anotar el número") se
  reemplaza por algo que:
  - Muestre el código completo (`confirmacion.tokenDeSeguimiento`) de
    forma que se pueda seleccionar y copiar fácil (por ejemplo un
    `<code>` dentro del párrafo, o un campo de texto de solo lectura con
    un botón "Copiar" — a criterio del agente, pero legible y no
    truncado).
  - Deje explícito, en texto (no solo implícito por el diseño), que **es
    la única forma de volver a consultar el estado** y que hay que
    guardarlo.
  - Siga siendo un `role="status"` enfocable igual que hoy (no cambiar
    el mecanismo de foco/anuncio a lectores de pantalla, solo el
    contenido).
- Nueva sub-vista de consulta pública, alcanzable con un botón/enlace
  visible tanto en el estado inicial del formulario como después de
  cargar un reclamo (por ejemplo "¿Ya cargaste un reclamo? Consultá su
  estado"), que cambia un estado local del componente (no hay router de
  URLs en este frontend — confirmado en `registro.ts`/`Navegacion.tsx`,
  no inventar uno acá) a una pantalla `ConsultaDeSeguimiento`:
  - Un `<form>` con un único campo (`<label>` "Código de seguimiento") y
    botón "Consultar".
  - Al enviar, `pedir<SeguimientoDeReclamo>('/api/reclamos/seguimiento/'
    + encodeURIComponent(codigo), 'No pudimos encontrar un reclamo con
    ese código.')`.
  - Resultado: mostrar categoría, estado (con la misma
    `ETIQUETA_ESTADO` ya definida en el archivo), comentario de gestión
    si existe, fecha de creación y de última actualización. Mismo
    patrón de `role="status"`/foco que el resto de la pantalla.
  - Error (404 u otro): `role="alert"`, mismo patrón de foco que
    `errorRef` en `FormularioDeAlta`.
  - Botón para volver al formulario de alta / a la pantalla anterior.
  - Mismo `h1`/foco al montar que el resto de las vistas de esta
    pantalla.

**`PantallaDeMesaDeEntradas.tsx`**: mismo patrón, adaptado:

- `RespuestaAlta` (o el tipo equivalente que use ese archivo) gana
  `tokenDeSeguimiento`.
- Mismo reemplazo del mensaje "todavía no hay pantalla para consultar".
- Misma sub-vista de consulta, pero mostrando además el historial de
  movimientos (tabla o lista con estado anterior → nuevo, comentario y
  fecha de cada paso, sin nombre de quien lo gestionó) y los campos
  propios del tipo de trámite que corresponda.

No hace falta tocar `registro.ts`, `Navegacion.tsx` ni
`CatalogoDeModulos.tsx`: la consulta por token es una vista interna de
cada `Pantalla*` existente, no un módulo nuevo ni una entrada de
navegación nueva.

## Aislamiento entre tenants

Cubierto por los tests 4 de las Tareas 2 y 3: la consulta por token
corre, igual que el resto de cada módulo, contra el datasource ruteado
por el `Host` del request (ADR 0001) — no hay lógica nueva que pueda
"cruzar" tenants, la única superficie nueva es una columna y una consulta
`findByTokenHash` dentro del mismo repositorio ya scopeado por tenant. El
test tiene que demostrarlo con un token real de un municipio contra el
subdominio del otro, no solo con un token inventado.

## Accesibilidad (WCAG)

La pantalla de consulta por token es pantalla nueva (aunque viva como
sub-vista de un componente existente): mismo estándar ya validado en el
resto del frontend (`PantallaDeReclamos`/`PantallaDeMesaDeEntradas`
mismas) — `<label htmlFor>` explícito en el campo de código, foco
programático en el `h1` al entrar a la vista, foco en el mensaje de error
al fallar la consulta (`role="alert"`), resultado exitoso anunciado con
`role="status"` y foco, sin depender únicamente de color para distinguir
estados. Atención particular a que el código de seguimiento mostrado en
la confirmación del alta sea seleccionable con teclado (no una imagen ni
un elemento no enfocable) y que el texto de instrucción ("guardá este
código") no dependa de un ícono sin texto alternativo.

## Fuera de alcance (explícitamente diferido, ver ADR 0017)

- Rate limiting sobre `GET /api/{modulo}/seguimiento/{token}` y sobre las
  altas públicas.
- Reenvío del token por email/SMS.
- Expiración del token.
- Cualquier acción sobre el reclamo/trámite desde la consulta pública
  (es de solo lectura: no se puede comentar, adjuntar ni cancelar desde
  ahí).
- Extender el mecanismo a otros módulos: no hay un tercer consumidor en
  esta rebanada.
- Cualquier integración con auditoría/notificaciones transversal (ADR
  0013) más allá de lo que cada módulo ya cubre.

## Instrucción para los agentes implementadores

**No hagan commit, push, ni abran PR.** Dejen los cambios en el árbol de
trabajo sin commitear. El tech lead arma el commit, pushea la rama y abre
el PR contra `develop` una vez que backend, frontend y la auditoría estén
completos. Si el trabajo se corta por límite de sesión o cualquier otro
motivo, no reintenten commitear/pushear por su cuenta al retomar: avisen
el estado en el que quedó y esperen instrucción.
