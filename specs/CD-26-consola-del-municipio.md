# CD-26 · R18 — Consola del municipio: contrato, módulos y solicitud de alta/baja

Ver [ADR 0022](../docs/arquitectura/decisiones/0022-consola-del-municipio-contrato-de-solo-lectura-y-solicitud-de-modulo.md)
para el porqué de cada decisión de esta spec. Esta spec no reabre nada del
ADR: lo traduce a tareas concretas. Si algo acá contradice al ADR, gana el
ADR y hay que avisar al tech lead, no improvisar.

## Demo objetivo

Un administrador del municipio (sesión con `municipio.verContrato` y
`municipio.solicitarModulo`) entra a Administración y ve una sección "Mi
municipio": los módulos que tiene contratados, su tramo poblacional y
estado de facturación (sin la nota interna de la plataforma), y puede
pedir el alta de un módulo que no tiene, con una justificación de texto
libre. Ve el pedido en su propio historial como "Pendiente". Un usuario de
plataforma, en la consola del proveedor (`admin.localhost`), entra al
detalle de ese municipio, ve el mismo pedido y lo marca "Atendida" después
de resolverlo por fuera (prendiendo el módulo con el mecanismo ya
existente, sin que esta rebanada automatice nada). El municipio, al volver
a consultar, ve su pedido como "Atendida".

## Qué NO tocar

- El entitlement en sí (`AdministracionDeModulos`, `PUT
  /api/admin/municipios/{slug}/modulos`, `GatingDeModulosFilter`): crear o
  atender una solicitud **nunca** prende ni apaga un módulo. Es
  intencional (ADR 0012 §8, ADR 0022 §3).
- `GET /api/modulos` (ya existe, ADR 0012 §7): la pantalla nueva lo
  reusa tal cual para "módulos contratados", no se le agrega nada.
- `tenant.nota_facturacion`: no se expone al municipio bajo ninguna ruta
  nueva de esta rebanada (ADR 0022 §1).
- La administración de usuarios/roles (`UsuariosController`,
  `RolesController`, `PanelDeUsuarios`, `PanelDeRoles`): no se reconstruye
  ni se le cambia comportamiento, solo conviven en la misma pantalla de
  administración.

## Tarea 1 (backend) — solicitud de módulo en la base de control, e interfaces públicas de `tenants`

**Comportamiento observable**: no hay endpoint todavía en esta tarea; es
la base que consumen las tareas 2 y 3. Al terminar, tiene que compilar y
pasar `mvn -q -pl backend test -Dtest=ModularityTests` (o el módulo
correspondiente) sin romper el test de modularidad de Spring Modulith.

**Migración `db/control/V4__crear_solicitud_de_modulo.sql`**:

```sql
create table solicitud_modulo (
    id                     bigint generated always as identity primary key,
    tenant_id              uuid          not null references tenant (id),
    modulo_codigo          varchar(60)   not null,
    tipo                   varchar(10)   not null check (tipo in ('ALTA', 'BAJA')),
    justificacion          varchar(1000) not null,
    estado                 varchar(20)   not null default 'PENDIENTE'
        check (estado in ('PENDIENTE', 'ATENDIDA')),
    solicitada_por_nombre  varchar(150)  not null,
    solicitada_por_email   varchar(200)  not null,
    creada_en              timestamptz   not null default now(),
    atendida_en            timestamptz
);

create index solicitud_modulo_tenant_idx on solicitud_modulo (tenant_id);

comment on table solicitud_modulo is
    'Pedido de un municipio de alta o baja de un módulo (R18, ADR 0022). '
    'No cambia el entitlement por sí sola: la plataforma sigue prendiendo '
    'o apagando módulos por separado (ADR 0012 §8).';
```

Después de agregar esta migración corré `mvn clean` antes de compilar
(regla ya conocida del proyecto para migraciones Flyway nuevas).

**Entidad y repositorio** (`ar.com.ciudaddigital.tenants.internal` — el
paquete importa: es lo que hace que Spring caiga en el
`EnableJpaRepositories`/EMF de **control**, no el de tenant, ver
`ConfiguracionDePersistencia.PAQUETE_CONTROL`):

- `TipoDeSolicitudDeModulo`: enum `ALTA, BAJA`.
- `EstadoDeSolicitudDeModulo`: enum `PENDIENTE, ATENDIDA`.
- `SolicitudDeModuloEntity` (tabla `solicitud_modulo`):
  - Constructor de fábrica `nueva(UUID tenantId, String moduloCodigo,
    TipoDeSolicitudDeModulo tipo, String justificacion, String
    solicitadaPorNombre, String solicitadaPorEmail)`: arranca en
    `PENDIENTE`, `creadaEn = OffsetDateTime.now()` (o `Instant`, seguí el
    tipo que ya usa `TenantEntity.fechaAlta` para consistencia dentro del
    mismo paquete).
  - `marcarAtendida()`: exige estado `PENDIENTE` (si no, lanzar
    `IllegalStateException` — no hace falta una excepción de negocio
    nueva para un caso que ni siquiera el frontend va a poder disparar, ya
    que la pantalla no ofrece "atender" dos veces).
  - Getters de todos los campos.
- `SolicitudDeModuloRepository extends JpaRepository<SolicitudDeModuloEntity, Long>`:
  - `List<SolicitudDeModuloEntity> findByTenantIdOrderByCreadaEnDesc(UUID tenantId)`
  - `long countByTenantIdAndEstado(UUID tenantId, EstadoDeSolicitudDeModulo estado)`
  - `Optional<SolicitudDeModuloEntity> findByIdAndTenantId(Long id, UUID tenantId)`

**Excepción pública nueva** `ar.com.ciudaddigital.tenants.SolicitudDeModuloInvalida
extends RuntimeException` (paquete público, no `.internal`: la va a
atrapar un `@ExceptionHandler` en el módulo `municipio`, que no tiene
visibilidad de `tenants.internal`).

**Interfaces públicas nuevas** (`ar.com.ciudaddigital.tenants`, mismo
patrón que `ModulosDelTenant`/`ModulosDelTenantEnTenants`, ADR 0012 §2):

```java
package ar.com.ciudaddigital.tenants;

import java.util.Optional;

public interface ContratoDelTenant {
    Optional<Contrato> actual();

    record Contrato(String tramoPoblacional, String estadoFacturacion) {}
}
```

```java
package ar.com.ciudaddigital.tenants;

import java.time.Instant; // o el tipo temporal que uses en la entidad
import java.util.List;

public interface SolicitudesDeModulo {

    SolicitudDeModuloInfo crear(String moduloCodigo, String tipo, String justificacion,
            String nombreSolicitante, String emailSolicitante);

    List<SolicitudDeModuloInfo> delTenantActual();

    record SolicitudDeModuloInfo(
            Long id, String moduloCodigo, String tipo, String justificacion, String estado,
            Instant creadaEn, Instant atendidaEn) {}
}
```

**Implementaciones** (`tenants.internal`, `@Component`), ambas leyendo
`TenantContext.requerido()` para saber de qué tenant se trata — **nunca**
reciben el tenant como parámetro desde quien las llama, para que sea
estructuralmente imposible que `municipio` pida o cree algo de otro
tenant:

- `ContratoDelTenantEnTenants implements ContratoDelTenant`: busca
  `TenantRepository.findById(TenantContext.requerido().id())` y mapea a
  `Contrato(tenant.getTramoPoblacional().name(), tenant.getEstadoFacturacion().name())`.
- `SolicitudesDeModuloEnTenants implements SolicitudesDeModulo`, inyecta
  `SolicitudDeModuloRepository` y `CatalogoDeModulos` (de `entitlement` —
  `tenants` ya depende de `entitlement` hoy, vía
  `ModulosDelTenantEnTenants`, así que esto no abre una dependencia
  nueva):
  - `crear(...)`: valida `moduloCodigo` no vacío y que exista en
    `catalogo.catalogo()` (por código); valida `tipo` es `"ALTA"` o
    `"BAJA"` (parseá con `TipoDeSolicitudDeModulo.valueOf`, capturando
    `IllegalArgumentException`); valida `justificacion` no vacía (trim) y
    ≤ 1000 caracteres. Cualquier fallo de validación:
    `SolicitudDeModuloInvalida` con mensaje claro. Si todo es válido,
    arma la entidad con `TenantContext.requerido().id()`, la guarda, y
    devuelve el `SolicitudDeModuloInfo`.
  - `delTenantActual()`: `findByTenantIdOrderByCreadaEnDesc(TenantContext.requerido().id())`,
    mapeado a `SolicitudDeModuloInfo`.
  - No hace falta `@Transactional("controlTransactionManager")` explícito
    en ninguno de los dos métodos: cada uno hace una sola llamada a un
    repositorio de `tenants.internal` (paquete de control), y esos
    repositorios ya resuelven solos al `controlTransactionManager` por su
    propia configuración (`RepositoriosDeControl`) — mismo motivo por el
    que `InformacionComercialDeMunicipios.actualizar` tampoco lo necesita.
    Si en algún momento necesitás combinar más de una escritura en una
    sola transacción de control, ahí sí nombralo explícito (mismo patrón
    que `AutenticacionDePlataforma`).

## Tarea 2 (backend) — controller intra-tenant en `municipio`, permisos nuevos

**Comportamiento observable**:

- Con sesión y `municipio.verContrato`, `GET /api/municipio/contrato`
  devuelve `{"tramoPoblacional": "...", "estadoFacturacion": "..."}` (sin
  `notaFacturacion`, ese campo no existe en esta respuesta). Sin el
  permiso, 403. Sin sesión, 401 (mismo comportamiento que el resto de
  rutas protegidas del portal).
- Con sesión y `municipio.verContrato`, `GET
  /api/municipio/solicitudes-de-modulo` devuelve la lista de solicitudes
  del propio municipio (puede ser vacía), más reciente primero.
- Con sesión y `municipio.solicitarModulo`, `POST
  /api/municipio/solicitudes-de-modulo` con body `{"moduloCodigo": "...",
  "tipo": "ALTA"|"BAJA", "justificacion": "..."}` crea la solicitud
  `PENDIENTE`, con el actor autenticado copiado en
  `solicitadaPorNombre`/`solicitadaPorEmail` (mismo patrón que
  `MultasController.actorDe`/`ActorAutenticado`), y devuelve la solicitud
  creada. Código de módulo inexistente, tipo inválido o justificación
  vacía → 400.
- Un usuario con `municipio.verContrato` pero sin `municipio.solicitarModulo`
  puede ver el contrato y el historial, pero el `POST` le da 403.

**Implementación** (`ar.com.ciudaddigital.municipio.internal`, nuevo
archivo `ConsolaDelMunicipioController.java`, `@RequestMapping("/api/municipio")`,
al lado de `ContactoController`):

```java
@GetMapping("/contrato")
@PreAuthorize("hasAuthority('municipio.verContrato')")
ResponseEntity<ContratoResponse> contrato() { ... }

@GetMapping("/solicitudes-de-modulo")
@PreAuthorize("hasAuthority('municipio.verContrato')")
List<SolicitudDeModuloResponse> solicitudes() { ... }

@PostMapping("/solicitudes-de-modulo")
@PreAuthorize("hasAuthority('municipio.solicitarModulo')")
ResponseEntity<SolicitudDeModuloResponse> solicitar(
        @RequestBody SolicitarModuloRequest request, Authentication autenticacion) { ... }
```

- Inyectá `ContratoDelTenant` y `SolicitudesDeModulo` (las interfaces de
  la Tarea 1) — el controller no toca `TenantRepository` ni ninguna clase
  de `tenants.internal` directamente, solo esas dos interfaces públicas.
- `contrato()`: `contratoDelTenant.actual().map(...).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build())`,
  mismo patrón exacto que `ContactoController.contacto()`.
- `solicitar(...)`: extraé el actor con el mismo helper/patrón que
  `MultasController.actorDe(Authentication)` (podés duplicar el método
  privado, es package-private de otro módulo). Capturá
  `SolicitudDeModuloInvalida` con un `@ExceptionHandler` que devuelva 400
  con `{"error": mensaje}` (mismo shape que el resto de la API).
- Records: `ContratoResponse(String tramoPoblacional, String
  estadoFacturacion)`, `SolicitudDeModuloResponse(Long id, String
  moduloCodigo, String tipo, String justificacion, String estado, Instant
  creadaEn, Instant atendidaEn)`, `SolicitarModuloRequest(String
  moduloCodigo, String tipo, String justificacion)`.

**Migración `db/tenant/V18__agregar_permisos_de_consola_del_municipio.sql`**:

```sql
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('municipio.verContrato', 'Administración', 'municipio', 'verContrato',
     'Ver los módulos contratados, el tramo poblacional, el estado de facturación '
     'y las solicitudes de alta/baja de módulo del municipio.'),
    ('municipio.solicitarModulo', 'Administración', 'municipio', 'solicitarModulo',
     'Solicitar el alta o la baja de un módulo contratado.');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo from rol r, permiso p
where r.codigo = 'administrador'
  and p.codigo in ('municipio.verContrato', 'municipio.solicitarModulo');
```

Solo `administrador` (ADR 0022 §4) — **no** agregues estos permisos a
`agente`.

## Tarea 3 (backend) — extender la consola del proveedor (cross-tenant)

**Comportamiento observable**:

- Con sesión de usuario de plataforma, `GET
  /api/admin/municipios/{slug}/solicitudes-de-modulo` devuelve todas las
  solicitudes de ese municipio (más reciente primero).
- Con sesión de plataforma, `PATCH
  /api/admin/municipios/{slug}/solicitudes-de-modulo/{id}/atender` marca
  la solicitud `ATENDIDA` (con `atendidaEn = now()`) y devuelve la
  solicitud actualizada. Un `id` que no existe, o que existe pero
  pertenece a otro municipio, da 404. Atender una solicitud ya `ATENDIDA`
  da 400 (reusá la semántica de `IllegalStateException` de
  `SolicitudDeModuloEntity.marcarAtendida`, atrapándola donde ya se
  atrapan las demás en este controller, o agregá un
  `@ExceptionHandler` puntual si no hay uno genérico).
- Sin sesión, o con sesión de un usuario de municipio (no de plataforma),
  ambas rutas dan 401 — mismo criterio que el resto de
  `/api/admin/municipios/**`.
- `GET /api/admin/municipios` (ya existente) suma el campo
  `cantidadDeSolicitudesPendientes` a cada fila de `MunicipioResponse`.

**Implementación** (`AdministracionDeMunicipiosController`, mismo
archivo): inyectá `SolicitudDeModuloRepository` directamente (está en el
mismo módulo, `tenants.internal`, no hace falta pasar por ninguna
interfaz pública). Reusá `administracionDeModulos.municipio(slug)` (ya
existe, lanza lo que ya hace lanzar un slug inexistente) para resolver el
`TenantEntity` antes de consultar por `tenant.getId()`.

- `describir(TenantEntity tenant)`: sumale
  `cantidadDeSolicitudesPendientes = solicitudDeModuloRepository.countByTenantIdAndEstado(tenant.getId(), EstadoDeSolicitudDeModulo.PENDIENTE)`
  al `MunicipioResponse` (que gana ese campo `int` al final del record).
- `GET /{slug}/solicitudes-de-modulo`: `findByTenantIdOrderByCreadaEnDesc`.
- `PATCH /{slug}/solicitudes-de-modulo/{id}/atender`:
  `findByIdAndTenantId(id, tenant.getId())`, si vacío 404 (mismo patrón
  de excepción que ya usa este controller para "no existe", revisá cómo
  resuelve hoy un slug inexistente para no inventar un mecanismo nuevo de
  404 — si hoy todo lo inexistente es 400 en vez de 404, seguí ese mismo
  criterio en vez del que dice esta spec; priorizá consistencia con lo
  que ya hay en el archivo por sobre el código HTTP exacto que sugiero
  acá).
- Response nuevo, propio de este controller (no reutilices el de la Tarea
  2, son módulos distintos): `SolicitudDeModuloAdminResponse(Long id,
  String moduloCodigo, String tipo, String justificacion, String estado,
  Instant creadaEn, Instant atendidaEn)`.

## Tarea 4 (backend) — tests de aislamiento y de permisos (obligatorio, no diferible)

**Archivo nuevo** `backend/src/test/java/ar/com/ciudaddigital/municipio/ConsolaDelMunicipioTest.java`
(paquete `ar.com.ciudaddigital.municipio`, extiende `SoporteDeIntegracion`):

- Circuito feliz: administrador de un municipio con `municipio.verContrato`
  + `municipio.solicitarModulo` (son de sistema, `administrador` ya los
  tiene por la migración de la Tarea 2) crea una solicitud, aparece en
  `GET /api/municipio/solicitudes-de-modulo` como `PENDIENTE`.
- `GET /api/municipio/contrato` devuelve el tramo/estado por defecto de un
  municipio recién dado de alta (`MEDIANO`/`AL_DIA`, mismos valores que
  usa `ConsolaDelProveedorTest.unMunicipioNuevoArrancaConLosValoresPorDefecto`)
  y **no** incluye `notaFacturacion` en el JSON
  (`jsonPath("$.notaFacturacion").doesNotExist()`).
- Un código de módulo inexistente, un tipo distinto de `ALTA`/`BAJA`, y
  una justificación vacía dan 400 cada uno (tres tests o uno
  parametrizado, a tu criterio).
- **Test de aislamiento (obligatorio, CLAUDE.md)**:
  `aislamientoEntreTenants`: crear una solicitud desde el municipio A;
  verificar que `GET /api/municipio/solicitudes-de-modulo` desde una
  sesión de administrador del municipio B (mismo patrón
  `iniciarSesionDeAdministrador(B)` + `portalDe(B, ...)` que
  `MultasTest.aislamientoEntreTenants`) devuelve lista vacía (no la
  solicitud de A). Esto es más importante acá que en cualquier otro
  módulo: `solicitud_modulo` vive en la base de **control**, compartida
  entre todos los tenants, así que el aislamiento depende enteramente de
  filtrar por `tenant_id` en cada consulta — no hay separación física de
  base que lo garantice sola. Verificá también que `GET
  /api/municipio/contrato` desde B nunca puede reflejar el tramo/estado
  que se le haya cambiado a A (si tocás la información comercial de A vía
  `iniciarSesionDePlataforma` + `PATCH .../comercial` dentro del mismo
  test, es un buen agregado, no obligatorio si ya lo cubre
  `ConsolaDelProveedorTest`).
- Test de permisos: un usuario con `municipio.verContrato` pero sin
  `municipio.solicitarModulo` recibe 403 al hacer `POST
  /api/municipio/solicitudes-de-modulo` (podés crear ese usuario de
  prueba con un rol ad-hoc, mismo mecanismo que ya usan otros tests de
  este proyecto para probar combinaciones de permisos que ningún rol de
  sistema tiene solo).

**Extender** `ConsolaDelProveedorTest.java` (no crear un archivo nuevo:
es la misma superficie cross-tenant que ya cubre) con:

- Circuito feliz: crear una solicitud desde el municipio (sesión de
  administrador de `SLUG`), verla en `GET
  /api/admin/municipios/{SLUG}/solicitudes-de-modulo` con sesión de
  plataforma, marcarla `ATENDIDA` con el `PATCH`, verificar que queda
  `ATENDIDA` con `atendidaEn` no nulo tanto en esa respuesta como en el
  `GET /api/municipio/solicitudes-de-modulo` del propio municipio.
- `cantidadDeSolicitudesPendientes` en `GET /api/admin/municipios` sube al
  crear una solicitud y baja al atenderla (mismo patrón que
  `laCantidadDeModulosContratadosReflejaLaConfiguracionReal`).
- **Test de "quién puede llegar"** (mismo criterio que
  `soloUnaSesionDePlataformaPuedeOperarElContrato`): ni una sesión
  anónima ni una sesión de administrador de municipio pueden hacer `GET`
  ni `PATCH` sobre `/api/admin/municipios/{slug}/solicitudes-de-modulo**`.

## Tarea 5 (frontend) — sección "Mi municipio" dentro de la administración

**Comportamiento observable**: dentro de `PanelDeAdministracion`, un
administrador ve una sección nueva "Mi municipio" (además de Usuarios,
Roles y Auditoría, que no cambian). Un agente sin `municipio.verContrato`
no la ve, igual que hoy no ve Usuarios/Roles si no tiene esos permisos.

**Archivo nuevo** `frontend/src/acceso/PanelDeMiMunicipio.tsx` (mismo
directorio que `PanelDeUsuarios.tsx`/`PanelDeAuditoria.tsx`, aunque el
backend que consume viva en el módulo `municipio`: el frontend organiza
por dónde se renderiza, no por módulo de backend — mismo criterio que ya
aplica `PanelDeAuditoria` hoy).

Props: `{ puedeSolicitar: boolean }` (igual patrón que
`puedeAdministrar` en `PanelDeUsuarios`).

Contenido, en este orden:

1. **Módulos contratados** (siempre visible con `municipio.verContrato`):
   lista de solo lectura de los módulos con `habilitado: true` del
   catálogo (`GET /api/modulos`, podés reusar el hook `useModulos` de
   `frontend/src/modulos/useModulos.ts` tal cual, sin tocarlo). Mostralos
   en una lista o tabla simple (nombre + descripción), sin controles de
   edición — esto es explícitamente de solo lectura, prender/apagar sigue
   siendo tarea de la plataforma.
2. **Mi contrato** (siempre visible con `municipio.verContrato`):
   `GET /api/municipio/contrato` → tramo poblacional y estado de
   facturación, mostrados con el mismo texto que ya usa la consola del
   proveedor (reusá o replicá `TEXTO_TRAMO_POBLACIONAL`/
   `TEXTO_ESTADO_DE_FACTURACION` de `frontend/src/plataforma/tipos.ts` —
   **no** los importes desde `plataforma/`, ese directorio es de la otra
   consola con otro ciclo de vida; duplicá las dos constantes acá, son
   dos objetos chicos). Sin nota de facturación: el backend no la manda.
3. **Solicitar alta o baja de un módulo** (visible solo si
   `puedeSolicitar`): formulario con selector de módulo (poblalo con el
   catálogo completo de `GET /api/modulos`, mostrando código + nombre,
   tanto habilitados como no —un municipio puede pedir la baja de uno que
   tiene, o el alta de uno que no tiene—), selector Alta/Baja, y textarea
   de justificación (obligatoria). Al confirmar, `POST
   /api/municipio/solicitudes-de-modulo`, mostrar confirmación accesible
   (`role="status"`, foco), y refrescar el historial de abajo sin recargar
   la página completa.
4. **Historial de solicitudes** (siempre visible con
   `municipio.verContrato`): tabla con columnas Módulo, Tipo,
   Justificación, Estado, Fecha (usá `GET
   /api/municipio/solicitudes-de-modulo`). Estado `PENDIENTE` en texto
   simple (no hace falta color especial, ya es una tabla de solo
   lectura del lado del municipio: no hay ninguna acción que tomar acá,
   la resuelve la plataforma).

**Cablear en `PanelDeAdministracion.tsx`**:
- `veMiMunicipio = puede('municipio.verContrato')`.
- Renderizar `{veMiMunicipio && <PanelDeMiMunicipio puedeSolicitar={puede('municipio.solicitarModulo')} />}`
  después de `PanelDeAuditoria` (o en el orden que te parezca más natural
  de lectura; no es una decisión que afecte nada funcional).
- Sumar `!veMiMunicipio` a la condición del mensaje "No tenés permisos
  para..." (ajustá el texto para que mencione también "ni ver el
  contrato del municipio", o similar).

**Accesibilidad (obligatorio, no diferible, CLAUDE.md)**: mismos patrones
ya usados en `PanelDeUsuarios.tsx`/`DetalleDeMunicipio.tsx` — foco por
`useRef` + `tabIndex={-1}` en confirmaciones/errores, `role="status"`/
`role="alert"`, `<label htmlFor>` en todo campo, `<fieldset>`+`<legend>`
si agrupás controles relacionados, tabla con `<caption>` y
`scope="col"`/`scope="row"`, `aria-busy` en botones mientras se envía el
formulario. No inventes un patrón nuevo.

**Fuera de alcance**: edición o cancelación de una solicitud ya creada,
cualquier indicación visual de "cuántas solicitudes tenés pendientes" en
la navegación general (fuera de la propia sección).

## Tarea 6 (frontend) — extender la consola del proveedor con las solicitudes recibidas

**Comportamiento observable**: en `DetalleDeMunicipio.tsx` (consola del
proveedor, `admin.localhost`), después de la sección "Información
comercial", una sección nueva "Solicitudes de alta/baja de módulo" con la
lista de solicitudes de ese municipio (`GET
/api/admin/municipios/{slug}/solicitudes-de-modulo`) y, para cada una en
estado `PENDIENTE`, un botón "Marcar atendida" que llama al `PATCH
.../solicitudes-de-modulo/{id}/atender` y refresca la fila sin recargar
toda la página (mismo patrón que `guardarModulos`/`guardarComercial` en
el mismo archivo: estado local, `vigente.current`, manejo de error con
`role="alert"` + foco).

En `ListaDeMunicipios.tsx`, sumá una columna o indicador con
`cantidadDeSolicitudesPendientes` (ya viene en `MunicipioResponse` una vez
que Tarea 3 lo agregue), mismo criterio visual que
`cantidadDeModulosContratados` si ya se muestra ahí, o agregalo si no
—revisá el archivo actual antes de decidir dónde entra mejor—.

Actualizá `frontend/src/plataforma/tipos.ts`: sumá
`cantidadDeSolicitudesPendientes: number` a `MunicipioResponse`, y agregá
los tipos `SolicitudDeModuloResponse` (mismos campos que el backend de la
Tarea 3) y las constantes de texto que necesites para `tipo`/`estado`
(`ALTA`/`BAJA`, `PENDIENTE`/`ATENDIDA`) si preferís mostrarlos traducidos
en vez de tal cual — a tu criterio, no es un requisito duro.

**Accesibilidad**: mismos patrones que el resto de `DetalleDeMunicipio.tsx`
(ya lo leíste si llegaste hasta acá: foco, `role="status"`/`role="alert"`,
`aria-busy`).

## Instrucciones para los agentes implementadores

No hagas commit, push ni abras PR por tu cuenta: dejá los cambios en el
working tree. El tech lead revisa, commitea y coordina el PR.
