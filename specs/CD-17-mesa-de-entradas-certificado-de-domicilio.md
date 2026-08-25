# CD-17 · R9 — Un vecino inicia un trámite en Mesa de Entradas y el municipio lo tramita

Rama: `CD-17-mesa-de-entradas-workflow-minimo` (desde `develop`).

Requiere ADR nuevo:
[ADR 0015](../docs/arquitectura/decisiones/0015-motor-de-expediente-workflow-minimo.md)
— motor de expediente/workflow mínimo: circuito de estados fijo **por tipo
de trámite**, definido en código y catálogo de producto, no editable por
el municipio. El alta pública y anónima reutiliza tal cual el mecanismo de
[ADR 0014](../docs/arquitectura/decisiones/0014-reclamos-ciudadanos-alta-publica-anonima-y-estado-propio.md)
§1 (`rutasDeEscrituraPublica()`); listar y avanzar el estado siguen el
modelo de permisos del
[ADR 0011](../docs/arquitectura/decisiones/0011-autorizacion-por-roles-con-permisos-granulares.md)
sin extenderlo.

## Demo

Un vecino, **sin sesión**, entra al portal público de un municipio e
inicia un trámite de **certificado de domicilio**, indicando su nombre y
el domicilio a certificar. Un agente de Mesa de Entradas, **con sesión** y
el permiso `mesaentradas.gestionar`, ve el trámite entrar a la cola en
estado "Iniciado", lo pasa a "En revisión" y después a "Aprobado" (o
"Rechazado"), y cada paso queda registrado con quién lo hizo y cuándo. El
mismo trámite **no** aparece en el portal de otro municipio.

## Qué se construye

### Backend — Tarea única (bloqueante, un solo agente `backend`)

Paquete nuevo `ar.com.ciudaddigital.mesaentradas` (`mesaentradas.internal`
para todo lo que no sea el `package-info.java`), siguiendo el esqueleto de
`reclamos`/`boletin` pero con dos entidades en vez de una (motor mínimo,
ADR 0015).

**1. `package-info.java`**

Documentar, con el mismo estilo que `boletin`/`reclamos`, que este módulo
es el primer consumidor del motor de expediente/workflow mínimo
(ADR 0015), que reutiliza el mecanismo de alta pública anónima del
ADR 0014 §1, y que en esta rebanada solo implementa un tipo de trámite
(`CERTIFICADO_DOMICILIO`) de los 3-5 que el roadmap prevé para el subset
de Trámites a Distancia.

**2. `mesaentradas.internal.TipoDeTramite`**

Enum con un único valor por ahora: `CERTIFICADO_DOMICILIO`. Javadoc
explícito: agregar un tipo nuevo es agregar un valor acá, su
`CircuitoDeTramite` (ver más abajo) y sus campos propios en
`ExpedienteEntity` — no toca el motor (ADR 0015 §1).

**3. `mesaentradas.internal.EstadoDeExpediente`**

Enum: `INICIADO`, `EN_REVISION`, `APROBADO`, `RECHAZADO`.

**4. `mesaentradas.internal.CircuitoDeTramite`**

Clase (o record) inmutable con dos datos: el estado inicial y el mapa de
transiciones válidas (`Map<EstadoDeExpediente, Set<EstadoDeExpediente>>`,
mismo estilo `EnumMap`/`EnumSet` que `GestionDeReclamos.TRANSICIONES_VALIDAS`).
No conoce el `TipoDeTramite` al que pertenece: es una pieza reutilizable,
el registro que la asocia a un tipo va en la clase siguiente.

**5. `mesaentradas.internal.CircuitosDeTramite`**

Registro estático, **el motor propiamente dicho**: `Map<TipoDeTramite,
CircuitoDeTramite>` con una entrada hoy (`CERTIFICADO_DOMICILIO` →
`INICIADO → EN_REVISION → APROBADO/RECHAZADO`, sin vuelta atrás), y un
método `static CircuitoDeTramite de(TipoDeTramite tipo)` que
`GestionDeExpedientes` usa para validar cualquier transición sin conocer
el circuito concreto. Javadoc citando ADR 0015 §1: agregar un tipo de
trámite es agregar una entrada acá, nunca tocar la lógica de
`GestionDeExpedientes`.

**6. `mesaentradas.internal.ExpedienteEntity`**

Entidad JPA, tabla `expediente` (DDL abajo):
- `id`, `tipo` (enum), `estado` (enum, estado *actual*),
  `solicitanteNombre` (obligatorio: a diferencia de `reclamos`, un
  certificado se emite a nombre de alguien), `solicitanteContacto`
  (opcional, igual criterio que `reclamos.contacto`),
  `domicilioACertificar` (obligatorio — único dato propio de
  `CERTIFICADO_DOMICILIO` hoy, columna explícita, no JSON: ver ADR 0015
  §3), `creadoEn`, `actualizadoEn`.
- `@OneToMany(mappedBy = "expediente", cascade = CascadeType.ALL,
  orphanRemoval = true) @OrderBy("fecha asc")` hacia
  `MovimientoDeExpedienteEntity` (ver siguiente clase): el historial vive
  siempre junto al expediente (ADR 0015 §2), no hace falta un repositorio
  propio para los movimientos.
- Factory estático `nuevo(TipoDeTramite tipo, String solicitanteNombre,
  String solicitanteContacto, String domicilioACertificar)`: fija
  `estado = CircuitosDeTramite.de(tipo).estadoInicial()`, `creadoEn =
  actualizadoEn = Instant.now()`, y agrega el primer movimiento (ver
  `MovimientoDeExpedienteEntity.deAlta(...)` abajo) a la colección.
- Método `avanzar(EstadoDeExpediente nuevoEstado, String actorNombre,
  String actorEmail, String comentario)`: fija el nuevo estado,
  `actualizadoEn = Instant.now()`, y agrega un
  `MovimientoDeExpedienteEntity` nuevo a la colección con
  `estadoAnterior` = estado actual antes de pisarlo. **No valida** la
  transición (esa tabla vive en `GestionDeExpedientes`/`CircuitosDeTramite`,
  mismo criterio que `ReclamoEntity.cambiarEstado` — ADR 0014 §3).

**7. `mesaentradas.internal.MovimientoDeExpedienteEntity`**

Entidad JPA, tabla `movimiento_de_expediente` (DDL abajo):
- `id`, `expediente` (`@ManyToOne`, columna `expediente_id`),
  `estadoAnterior` (enum, **nullable**: `null` en el movimiento de alta),
  `estadoNuevo` (enum, obligatorio), `actorNombre`/`actorEmail`
  (**nullable**, copia del actor —mismo criterio "copia, no referencia"
  que `publicado_por_*`/`registrado_por_*` de R7/R8, ADR 0013—; `null` en
  el movimiento de alta porque es anónima), `comentario` (nullable,
  `text`), `fecha` (obligatorio).
- Dos factory estáticos package-private: `deAlta()` (estadoAnterior=null,
  actorNombre=null, actorEmail=null, comentario=null, fecha=ahora) y
  `deAvance(EstadoDeExpediente estadoAnterior, EstadoDeExpediente
  estadoNuevo, String actorNombre, String actorEmail, String comentario)`.
  Sin setter público de `expediente`: se fija desde
  `ExpedienteEntity` al agregar a la colección (mismo patrón bidireccional
  estándar de JPA, a criterio del agente `backend` para mantenerlo
  consistente en ambos lados).

**8. `mesaentradas.internal.ExpedienteRepository extends JpaRepository`**

`List<ExpedienteEntity> findAllByOrderByCreadoEnDesc()` — mismo patrón que
`ReclamoRepository`. No hace falta repositorio propio para
`MovimientoDeExpedienteEntity`: se persiste en cascada con el expediente.

**9. `mesaentradas.internal.GestionDeExpedientes` (`@Service`)**

- `iniciar(TipoDeTramite tipo, String solicitanteNombre, String
  solicitanteContacto, String domicilioACertificar)`,
  `@Transactional("tenantTransactionManager")`. Validaciones (mismo
  estilo defensivo que `GestionDeReclamos`, con límites de largo
  explícitos y mensajes en español):
  - `tipo` requerido (el controller ya lo resuelve desde el string del
    request, ver más abajo, así que acá solo se guarda contra un
    `null` defensivo).
  - `solicitanteNombre` requerido, máx. 200 caracteres.
  - `solicitanteContacto` opcional, máx. 200 caracteres.
  - `domicilioACertificar` requerido, máx. 300 caracteres.
- `listar()`: `List<ExpedienteEntity>` vía
  `findAllByOrderByCreadoEnDesc()`.
- `avanzar(Long id, EstadoDeExpediente nuevoEstado, String comentario,
  String actorNombre, String actorEmail)`,
  `@Transactional("tenantTransactionManager")`: busca el expediente (404
  lógico → `SolicitudInvalida` si no existe, mismo criterio que
  `GestionDeReclamos.cambiarEstado`), valida que `nuevoEstado` esté en
  `CircuitosDeTramite.de(expediente.getTipo()).transicionesValidas().get(estadoActual)`
  — si no, `SolicitudInvalida` con el mismo mensaje que usa
  `GestionDeReclamos` ("No se puede pasar de X a Y.") —, y llama
  `expediente.avanzar(nuevoEstado, actorNombre, actorEmail, comentario)`.

**10. `mesaentradas.internal.MesaDeEntradasController`**
(`@RequestMapping("/api/mesaentradas")`)

- `POST` sin ruta adicional, **sin** `@PreAuthorize`: es la ruta que
  `DescriptorDelModuloMesaDeEntradas` declara en
  `rutasDeEscrituraPublica()` (mismo Javadoc explicativo que
  `ReclamosController`, citando ADR 0014 §1 y ADR 0015 §4). Body
  `{tipo, solicitanteNombre, solicitanteContacto, domicilioACertificar}`;
  `tipo` se resuelve con el mismo patrón `tipoDe(String)` que
  `BoletinController`/`ReclamosController` (`SolicitudInvalida` si falta
  o no existe). Responde `201` con una respuesta **pública reducida**
  (`ExpedientePublicoResponse`: `id`, `tipo`, `estado`, `creadoEn`) —
  mismo criterio que `ReclamoPublicoResponse`: no hace falta devolver
  `solicitanteContacto`/`domicilioACertificar` a la confirmación.
- `GET` sin ruta adicional, `@PreAuthorize("hasAuthority('mesaentradas.ver')")`.
  Responde `List<ExpedienteResponse>` con los datos completos, incluido
  `movimientos: List<MovimientoResponse>` (cada uno con
  `estadoAnterior`, `estadoNuevo`, `actorNombre`, `actorEmail`,
  `comentario`, `fecha`).
- `PATCH /{id}/estado`,
  `@PreAuthorize("hasAuthority('mesaentradas.gestionar')")`. Toma el actor
  autenticado del `Authentication` igual que `BoletinController.publicar`
  (mismo bloque `ActorAutenticado`/`IllegalStateException` si no hay
  principal autenticado). Body `{estado, comentario}`; `estado` se resuelve
  con el mismo patrón `estadoDe(String)` que `ReclamosController`. Responde
  `200` con el `ExpedienteResponse` actualizado (mismos campos que en el
  listado, incluido el historial completo).
- Mismo `@ExceptionHandler(SolicitudInvalida.class)` → 400, mismo
  `ErrorResponse` record que el resto de los módulos.

**11. `mesaentradas.internal.DescriptorDelModuloMesaDeEntradas`**
implementa `DescriptorDeModulo`:
- `codigo()` = `"mesaentradas"`.
- `nombre()` = `"Mesa de Entradas"`.
- `descripcion()`: algo como "Mesa de Entradas digital: inicio y gestión
  de trámites del municipio, con circuito de estados propio por tipo de
  trámite (ADR 0015)."
- `prefijosDeApi()` = `List.of("/api/mesaentradas")`.
- `rutasDeEscrituraPublica()` = `List.of("/api/mesaentradas")` — solo el
  alta es pública.
- **Sin** `rutasDeLecturaPublica()`: a diferencia de `reclamos`/`boletin`/
  `cementerio`, en esta rebanada no hay ninguna lectura pública (el
  vecino no puede consultar su trámite después de iniciarlo — ADR 0015
  §4/Pendiente de definir). No declarar el método (usar el default
  vacío).

**12. `mesaentradas.internal.SolicitudInvalida`**

Excepción package-private propia, copiada de `reclamos`/`boletin`/
`cementerio` (no compartida entre módulos).

**13. Migración `V9__crear_mesa_de_entradas.sql`** en
`backend/src/main/resources/db/tenant/`:

```sql
-- Mesa de Entradas: motor de expediente/workflow mínimo (ADR 0015) y su
-- primer trámite, certificado de domicilio (backlog R9).
--
-- Sin columna de tenant: vive en la base del municipio, igual que
-- reclamo/norma/sepultura. El circuito de estados es fijo por tipo de
-- trámite, definido en código (TipoDeTramite/CircuitoDeTramite,
-- ADR 0015 §1), no editable por el municipio.
create table expediente (
    id                     bigint generated always as identity primary key,
    tipo                   varchar(40)  not null
        check (tipo in ('CERTIFICADO_DOMICILIO')),
    estado                 varchar(20)  not null
        check (estado in ('INICIADO', 'EN_REVISION', 'APROBADO', 'RECHAZADO')),
    solicitante_nombre     varchar(200) not null,
    solicitante_contacto   varchar(200),
    -- Único dato propio del tipo de trámite hoy: columna explícita, no
    -- JSON de datos variables (ADR 0015 §3 — se decide con un segundo
    -- tipo real delante, no antes).
    domicilio_a_certificar varchar(300) not null,
    creado_en              timestamptz  not null default now(),
    actualizado_en         timestamptz  not null default now()
);

create index expediente_creado_en_idx on expediente (creado_en desc);

comment on table expediente is
    'Trámites iniciados en Mesa de Entradas de este municipio (backlog R9, ADR 0015).';

-- Historial de cambios de estado: quién lo hizo y cuándo (ADR 0015 §2).
-- El primer movimiento (alta) tiene estado_anterior/actor_* en null: no
-- hay estado previo, y el alta es pública y anónima (ADR 0014 §1,
-- reutilizado acá), sin actor autenticado que la firme.
create table movimiento_de_expediente (
    id              bigint generated always as identity primary key,
    expediente_id   bigint      not null references expediente(id),
    estado_anterior varchar(20)
        check (estado_anterior in ('INICIADO', 'EN_REVISION', 'APROBADO', 'RECHAZADO')),
    estado_nuevo    varchar(20) not null
        check (estado_nuevo in ('INICIADO', 'EN_REVISION', 'APROBADO', 'RECHAZADO')),
    actor_nombre    varchar(150),
    actor_email     varchar(200),
    comentario      text,
    fecha           timestamptz not null default now()
);

create index movimiento_de_expediente_expediente_id_idx on movimiento_de_expediente (expediente_id);

comment on table movimiento_de_expediente is
    'Historial de cambios de estado de cada expediente: quién lo hizo y cuándo (ADR 0015 §2).';

-- Catálogo de permisos: área "Mesa de Entradas". Igual criterio que
-- reclamos.ver/reclamos.gestionar (V6): funcionalidad operativa real del
-- personal de Mesa de Entradas desde el día uno, se asigna a AMBOS roles
-- de sistema.
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('mesaentradas.ver',       'Mesa de Entradas', 'mesaentradas', 'ver',
     'Ver el listado de trámites iniciados en Mesa de Entradas.'),
    ('mesaentradas.gestionar', 'Mesa de Entradas', 'mesaentradas', 'gestionar',
     'Avanzar el estado de un trámite de Mesa de Entradas.');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo
from rol r, permiso p
where r.codigo in ('administrador', 'agente')
  and p.codigo in ('mesaentradas.ver', 'mesaentradas.gestionar');
```

Ajustar el DDL si al implementar aparece algo que no encaje con las
convenciones reales del proyecto, pero mantener las decisiones: dos
tablas (expediente + su historial), sin columna de tenant, `check` para
los enums, `domicilio_a_certificar` como columna explícita (no JSON),
permisos en ambos roles.

**14. Test de integración `MesaDeEntradasTest.java`**

En `backend/src/test/java/ar/com/ciudaddigital/mesaentradas/`, calcado de
`BoletinTest.java`/`ReclamosTest.java` (mismo `SoporteDeIntegracion`,
mismos municipios `tandil`/`olavarria`, mismo helper `fijarModulos`, mismo
helper `crearAgenteYLoguear`). Casos obligatorios:

1. Alta pública sin sesión, con el módulo contratado → 201, con
   `{id, tipo: "CERTIFICADO_DOMICILIO", estado: "INICIADO", creadoEn}`.
   Sin el módulo contratado → 403 `MODULO_NO_CONTRATADO`, aunque no haya
   sesión (mismo patrón que `ReclamosTest` para el alta pública).
2. Alta inválida (sin `solicitanteNombre`, sin `domicilioACertificar`, o
   con `tipo` inexistente/no soportado) → 400.
3. Listado protegido (`mesaentradas.ver`) devuelve el expediente completo,
   con **un** movimiento inicial: `estadoAnterior: null`,
   `estadoNuevo: "INICIADO"`, `actorNombre: null`, `actorEmail: null`.
   Sin el permiso → 403 sin código (mismo patrón que
   `publicacionSinElPermisoSeRechaza`).
4. Avanzar estado válido (`INICIADO` → `EN_REVISION`) con
   `mesaentradas.gestionar`, con un usuario `agente` (que sí tiene el
   permiso, a diferencia de `boletin.publicar`) → 200, y el listado
   siguiente muestra **dos** movimientos: el de alta (actor `null`) y el
   de avance (con `actorNombre`/`actorEmail` del agente que lo hizo).
5. Encadenar `EN_REVISION` → `APROBADO` → 200; luego intentar avanzar un
   expediente ya `APROBADO` (estado terminal) → 400.
6. Transición inválida directa (`INICIADO` → `APROBADO`, saltando
   `EN_REVISION`) → 400, con el mismo mensaje de "no se puede pasar de X
   a Y" que ya usa `GestionDeReclamos`.
7. **Aislamiento entre tenants**: un expediente iniciado en un municipio
   no aparece en el listado protegido del otro (con
   `solicitanteNombre` sufijado con `UUID.randomUUID()` para no chocar
   con filas de otros tests de la misma clase, mismo criterio que
   `BoletinTest.aislamientoEntreTenants`).

No hace falta test de gating adicional en `EntitlementDeModulosTest`: sigue
usando `ejemplo`/`reclamos` como sujetos, no se toca en esta rebanada
(mismo criterio que R7/R8).

### Frontend — Tarea única (después del backend, un solo agente `frontend`)

- `frontend/src/modulos/mesaentradas/PantallaDeMesaDeEntradas.tsx`, calcada
  estructuralmente de `PantallaDeReclamos.tsx` (mismo componente raíz que
  decide entre dos vistas según `usuario?.permisos.includes('mesaentradas.ver')`,
  mismo patrón `vigente`/`useRef`, mismo manejo de
  `ErrorDeApi`/`MODULO_NO_CONTRATADO`, mismo patrón de foco en el `h1` al
  montar y en el error/confirmación al enviar el formulario):

  - **`FormularioDeAlta`** (sin sesión, o con sesión pero sin
    `mesaentradas.ver`): campos `solicitanteNombre` (requerido),
    `solicitanteContacto` (opcional, mismo texto de ayuda que
    `reclamo-contacto-ayuda`: "Es para que el municipio pueda volver a
    contactarte..."), `domicilioACertificar` (requerido, `textarea` o
    `input`, con texto de ayuda indicando que es el domicilio a
    certificar). Al confirmar, `POST /api/mesaentradas` con
    `tipo: 'CERTIFICADO_DOMICILIO'` fijo (sin selector de tipo en esta
    rebanada: es el único disponible). Mensaje de confirmación mismo
    estilo que `ReclamosTest`/`PantallaDeReclamos`: "Tu trámite quedó
    registrado con el número {id}. Vas a ver el estado «Iniciado» hasta
    que Mesa de Entradas lo empiece a revisar: en esta rebanada todavía
    no hay una pantalla para volver a consultarlo más adelante, así que
    te conviene anotar el número." (mismo pendiente que `reclamos`,
    documentado en ADR 0015).

  - **`PanelDeGestion`** (con `mesaentradas.ver`): tabla con columnas
    Solicitante, Contacto, Domicilio a certificar, Estado, Historial,
    Creado, y (si `mesaentradas.gestionar`) Acción. La columna
    "Historial" lista cada movimiento como
    `fecha — estado (nombre del actor o "Alta pública")` en una lista
    compacta dentro de la celda (`<ul>` o líneas separadas por `<br />`,
    a criterio del agente `frontend`, manteniendo semántica de lista si
    usa `<ul>`). Mismo patrón de edición por fila que
    `PanelDeGestion` de `reclamos` (`abrirEdicion`/`cerrarEdicion`,
    `select` con las opciones válidas del estado actual + `textarea` de
    comentario opcional, botones Guardar/Cancelar, foco al abrir/cerrar/
    error). El mapa `TRANSICIONES_VALIDAS` del frontend se define
    **localmente para el único tipo de trámite que esta pantalla
    conoce** (`INICIADO: ['EN_REVISION']`, `EN_REVISION: ['APROBADO',
    'RECHAZADO']`, `APROBADO: []`, `RECHAZADO: []`) — el enforcement
    real sigue siendo del backend (`CircuitosDeTramite`, ADR 0015),
    igual comentario que ya usa `reclamos` sobre su propio mapa.

  - Cada campo con su `<label htmlFor>` explícito, `aria-invalid` y
    `aria-describedby` apuntando al mensaje de error cuando corresponda,
    mensajes de error con `role="alert"` y foco programático al aparecer,
    confirmación con `role="status"`, estado de carga con `role="status"`
    — mismo estándar de accesibilidad que `PantallaDeReclamos`/
    `PantallaDeBoletin`, sin componente ni patrón nuevo que inventar.

- Registrar el componente en `frontend/src/modulos/registro.ts`:
  `mesaentradas: PantallaDeMesaDeEntradas` (import + entrada en el
  record), sin tocar las entradas existentes.

- No hace falta tocar `Navegacion.tsx`/`CatalogoDeModulos.tsx`/`App.tsx`:
  confirmarlo al implementar, mismo criterio que R7/R8.

## Aislamiento entre tenants

Cubierto por el test 7 de la tarea de backend (arriba). No hay nada
adicional del lado del frontend: cada portal solo pega contra su propio
subdominio.

## Accesibilidad (WCAG)

Cubierta por seguir al pie de la letra los patrones ya validados de
`PantallaDeReclamos.tsx` (foco, labels, `aria-*`, roles de estado/alerta,
tabla con caption y encabezados de columna con `scope="col"`). La única
pieza de UI sin precedente exacto es la celda de "Historial" dentro de la
tabla: si se usa una lista (`<ul>`/`<li>`), mantiene semántica de lista
para lectores de pantalla; si se usa texto con saltos de línea, que cada
movimiento quede en su propio bloque de texto legible (no todo
concatenado sin separación).

## Fuera de alcance (explícitamente diferido)

- Los demás tipos de trámite del subset del roadmap: habilitación
  comercial simple, permiso de obra menor. Quedan para una rebanada
  siguiente que sume su propio `TipoDeTramite` + `CircuitoDeTramite` +
  campos propios, sin tocar el motor (ADR 0015).
- Circuitos configurables **por municipio** (ADR 0015, Pendiente de
  definir): todos los municipios que contraten `mesaentradas` usan el
  mismo circuito para `CERTIFICADO_DOMICILIO`.
- Seguimiento del trámite por el vecino anónimo con un código/token
  (mismo pendiente que ADR 0014 ya dejó abierto para `reclamos`; ADR 0015
  lo deja abierto también para `mesaentradas`, sugiriendo un mecanismo
  único cuando se resuelva).
- Giro entre áreas / derivación del expediente a otro sector,
  caratulación formal y numeración correlativa oficial (mismo criterio
  que R7 difirió para la numeración de normas: se expone el `id`, no un
  número oficial generado).
- Generación del documento del certificado (PDF u otro formato), firma
  electrónica/digital de actos administrativos.
- Notificación al vecino de cambios de estado de su trámite (integración
  con el motor de notificaciones transversal, ADR 0013) — mismo criterio
  que R6/R7/R8: se agrega cuando el módulo lo necesite de verdad.
- Cualquier otra integración con auditoría/notificaciones transversal más
  allá de lo que este propio módulo cubre.
- Rate limiting / anti-abuso sobre el alta pública — igual que
  `reclamos`, endurecimiento de seguridad diferido por CLAUDE.md.
- Paginado del listado — mismo criterio que `reclamos`/`boletin`/
  `cementerio`.
- Una vista de detalle por expediente separada del listado: todo vive en
  la fila de la tabla, igual que `reclamos`.

## Instrucción para los agentes implementadores

**No hagan commit, push, ni abran PR.** Dejen los cambios en el árbol de
trabajo sin commitear. El tech lead arma el commit, pushea la rama y abre
el PR contra `develop` una vez que backend, frontend y la auditoría estén
completos. Si el trabajo se corta por límite de sesión o cualquier otro
motivo, no reintenten commitear/pushear por su cuenta al retomar: avisen
el estado en el que quedó y esperen instrucción.
