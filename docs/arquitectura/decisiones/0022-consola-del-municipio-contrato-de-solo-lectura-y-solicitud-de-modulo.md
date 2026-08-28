# 0022 - Consola del municipio: contrato de solo lectura y solicitud de alta/baja de módulo en la base de control

- Estado: Aceptada
- Fecha: 2026-08-26

## Contexto

El [roadmap de Fase 3](../../producto/roadmap-fases.md#fase-3--compras-y-áreas-normativamente-pesadas)
pide, como último ítem no bloqueado por falta de piloto real, "Consola del
municipio: módulos activos, facturas, solicitud de alta/baja de módulos,
administración de usuarios". De esos cuatro puntos, tres ya existen o casi:

- **Administración de usuarios**: existe desde R3 (ADR 0010/0011). No se
  reconstruye.
- **Módulos activos**: `GET /api/modulos` ya expone, público, el catálogo
  completo con el flag `habilitado` por módulo para el tenant del request
  ([ADR 0012](0012-declaracion-de-modulos-y-gating-por-ruta.md) §7). No hace
  falta backend nuevo, solo una pantalla que lo muestre dentro de la
  administración.
- **Facturas / estado de facturación**: `tenant.tramo_poblacional` y
  `tenant.estado_facturacion` ya existen en la base de control desde R15
  ([ADR 0019](0019-consola-del-proveedor-ui-cross-tenant-y-contrato-minimo.md)),
  pero hoy solo los lee la consola del proveedor (`admin.localhost`),
  cross-tenant. El propio municipio nunca los vio.
- **Solicitud de alta/baja de módulos**: no existe ningún mecanismo. El
  municipio no puede prender/apagar sus propios módulos —eso sigue siendo
  operación de plataforma, [ADR 0012](0012-declaracion-de-modulos-y-gating-por-ruta.md)
  §8, sin cambios acá—, pero tampoco tiene ninguna forma de **pedirlo**
  dentro del sistema.

Esta ADR resuelve las dos preguntas de diseño reales que quedan:

1. ¿El municipio puede ver su propio tramo/estado de facturación? Si sí,
   ¿con qué alcance (incluida la nota interna de facturación o no)?
2. ¿Dónde vive una solicitud de alta/baja de módulo, y cómo la ve la
   plataforma sin romper el límite que [ADR 0019](0019-consola-del-proveedor-ui-cross-tenant-y-contrato-minimo.md)
   §5 fijó ("nunca datos operativos de un municipio" del lado de la
   consola del proveedor)?

Restricción estructural relevante: el backend tiene dos unidades de
persistencia separadas por paquete, no por request
(`ConfiguracionDePersistencia`, [ADR 0013](0013-persistencia-de-eventos-y-mecanismo-transversal-de-notificaciones-y-auditoria.md)):
las entidades del paquete `ar.com.ciudaddigital.tenants` van a la base de
control; todo lo demás (incluido `municipio`) va a la base del tenant en
curso, resuelta por `DataSourceDeTenants`. Cualquier dato que tenga que
convivir entre el request de un municipio y una vista cross-tenant de la
plataforma tiene que decidir explícitamente en qué unidad vive.

## Decisión

### 1. El municipio ve tramo y estado de facturación, nunca la nota interna

Se agrega una vista de solo lectura, protegida por el permiso nuevo
`municipio.verContrato` (administrador únicamente, ver punto 4), con
`tramoPoblacional` y `estadoFacturacion` del propio tenant. **No** se
expone `nota_facturacion`: es, por diseño y por el propio texto de ayuda ya
escrito en la consola del proveedor ("Tesorería avisó demora, contactado
el 20/08"), una nota de trabajo interna de la plataforma sobre la relación
comercial, no una comunicación pensada para el cliente. Mostrarla
verbatim en el portal del municipio filtraría comentarios internos de
seguimiento comercial a la persona sobre la que tratan.

Se expone mediante una interfaz pública nueva del módulo `tenants`,
`ContratoDelTenant`, con el mismo patrón que
[ADR 0012](0012-declaracion-de-modulos-y-gating-por-ruta.md) §2 ya usa para
`ModulosDelTenant`: interfaz pública + implementación interna que lee
`TenantContext.requerido()` y consulta `TenantRepository`. `TenantInfo` no
se extiende: su Javadoc ya excluye a propósito la configuración comercial,
y esta ADR no reabre eso.

### 2. La solicitud de alta/baja de módulo vive en la base de control, asociada al tenant

Es dato **contractual**, no operativo: mismo criterio que ya clasificó
`tramo_poblacional`/`estado_facturacion` como columnas de `tenant` en
ADR 0019, no como una tabla dentro de cada base de municipio. Vive en una
tabla nueva `solicitud_modulo` en `db/control`, con `tenant_id` como
clave foránea a `tenant`. Esto es lo que permite que la consola del
proveedor la liste sin violar
[ADR 0019](0019-consola-del-proveedor-ui-cross-tenant-y-contrato-minimo.md)
§5 ("nunca datos operativos de un municipio"): una solicitud de módulo no
es un reclamo, un usuario o una tasa de ese municipio, es un pedido sobre
el contrato, exactamente lo que esa consola ya administra.

La entidad (`SolicitudDeModuloEntity`) y su repositorio viven en
`ar.com.ciudaddigital.tenants.internal`, así el `EnableJpaRepositories` de
control (`ConfiguracionDePersistencia.RepositoriosDeControl`) los toma
automáticamente sin tocar código compartido.

Para que el municipio pueda crear y ver **sus propias** solicitudes desde
un request ya resuelto a su base de tenant, `tenants` expone una segunda
interfaz pública nueva, `SolicitudesDeModulo`, con `crear(...)` y
`delTenantActual()`, implementada en `tenants.internal`, y que toma el
`tenant_id` de `TenantContext.requerido().id()` — **nunca** de un campo
que mande el cliente. No hace falta nombrar
`@Transactional("controlTransactionManager")` explícito en esta
implementación: cada método hace una única llamada a un repositorio de
`tenants.internal` (`SolicitudDeModuloRepository`), y esos repositorios ya
resuelven solos al `controlTransactionManager` por su propia
configuración (`ConfiguracionDePersistencia.RepositoriosDeControl`) —
mismo motivo por el que `InformacionComercialDeMunicipios.actualizar`
tampoco lo necesita. El patrón de nombrar el gestor explícito
(`AutenticacionDePlataforma`, `SembradorDeUsuarioDePlataforma`, ADR 0013
§1) sigue haciendo falta solo cuando un método combina más de una
escritura de control en una misma transacción atómica, que no es el caso
acá. Un nuevo controller en `municipio.internal` (canon base, mismo
criterio que `ContactoController`) consume esa interfaz. `municipio`
depende de `tenants`; ese sentido de dependencia ya existe hoy en el
proyecto (`acceso` ya importa `TenantContext`/`TenantInfo`), así que no
introduce un ciclo nuevo.

Del lado de la plataforma, `AdministracionDeMunicipiosController` (ya
existente, `tenants.internal`) gana dos rutas más sobre el mismo
repositorio, sin pasar por la interfaz pública de arriba —está en el mismo
módulo que la entidad—: listar las solicitudes de un `{slug}` y marcarlas
`ATENDIDA`.

### 3. Estado de la solicitud: `PENDIENTE`/`ATENDIDA`, sin automatizar nada

Crear una solicitud no cambia el entitlement del municipio ni dispara
ninguna acción automática: sigue siendo la plataforma la que, por fuera
del sistema o por la vía ya existente
(`PUT /api/admin/municipios/{slug}/modulos`, ADR 0012 §8), decide prender o
apagar el módulo. Marcar una solicitud como `ATENDIDA` es solo un cambio de
estado de la solicitud misma —deja rastro de que alguien la vio y actuó—,
nunca una operación que module el entitlement. Esto es intencional y
mantiene vigente la restricción de ADR 0012 §8: prender/apagar sigue
siendo, exclusivamente, la API de administración cross-tenant.

No se valida que el `tipo` (`ALTA`/`BAJA`) sea coherente con si el módulo
está hoy habilitado o no: agrega una verificación cruzada con el
entitlement actual sin que ningún caso real la haya pedido todavía.
Tampoco hay límite de una solicitud pendiente por módulo: un municipio
puede mandar duplicadas: es la plataforma, al revisar, quien decide qué
hacer con eso.

### 4. Permisos nuevos, ambos reservados a `administrador`

- `municipio.verContrato`: ver el tramo poblacional, el estado de
  facturación y el historial de solicitudes propias (no solo crear una
  nueva: ver qué se pidió y en qué quedó).
- `municipio.solicitarModulo`: crear una solicitud nueva.

Se reservan a `administrador` (no `agente`), mismo criterio que
`boletin.publicar`/`tasas.publicar` y las mismas migraciones ya usan para
la administración de usuarios y roles: es información contractual/
comercial del municipio, no trabajo operativo cotidiano. Se separan en dos
permisos (ver vs. actuar) en vez de uno solo, mismo patrón que
`usuarios.ver`/`usuarios.administrar`, aunque hoy ambos terminen asignados
al mismo rol: es el criterio ya establecido en el catálogo de permisos, no
una decisión nueva.

### 5. No se integra con la auditoría/notificaciones transversales de R5

[ADR 0013](0013-persistencia-de-eventos-y-mecanismo-transversal-de-notificaciones-y-auditoria.md)
ya dejó pendiente, sin resolver, "auditoría de la API de administración de
plataforma (cross-tenant)" —exactamente la misma clase de problema que
tiene escribir en `event_publication`/`registro_auditoria`, que viven en
la base de **tenant**, desde una acción cuyo dato de negocio vive en
**control**—. Crear una solicitud de módulo no publica ningún evento de
dominio ni pasa por el bus de Spring Modulith: se guarda con copia directa
del actor (`solicitada_por_nombre`/`email`, mismo criterio "copia, no
referencia" que ya usa `registro_auditoria` y `multa.labrada_por_*`), sin
reabrir el pendiente de ADR 0013.

## Alternativas consideradas

- **Solicitud de módulo en la base de tenant**, como una tabla más del
  municipio: mantiene todo el dato operativo de un municipio en su propia
  base, coherente con el aislamiento de ADR 0001, pero la consola del
  proveedor no tendría ninguna vía legítima de leerla sin contradecir
  ADR 0019 §5. Se descarta porque el propio pedido de la rebanada
  (que la plataforma la vea) exige que viva donde la plataforma ya mira.
- **Exponer `nota_facturacion` también al municipio**: es el dato que ya
  existe y ya se pidió reusar, pero su propio texto de ayuda en la consola
  del proveedor muestra que se escribe pensando en un lector interno de
  la plataforma, no en el municipio. Descartado por riesgo de fuga de
  contexto comercial interno, no por costo técnico.
- **Automatizar el alta/baja al crear la solicitud** (o al marcarla
  "atendida"): contradice ADR 0012 §8, que reserva esa operación a la
  plataforma explícitamente. Descartado sin ambigüedad.
- **Una interfaz genérica de "eventos cross-base"** para no tener que
  decidir, para cada dato nuevo, en qué unidad de persistencia vive:
  deseable a largo plazo, pero es la misma generalización prematura que
  ADR 0013 ya evita para el caso de auditoría/notificaciones. Se decide
  caso por caso, igual que el resto del proyecto.

## Consecuencias

- Nueva migración en `db/control` (`solicitud_modulo`, con `tenant_id`
  como FK) y nueva migración en `db/tenant` (catálogo de los dos permisos
  nuevos, sembrados solo para `administrador`).
- `municipio` (módulo canon base) pasa a depender de `tenants` para dos
  cosas nuevas (`ContratoDelTenant`, `SolicitudesDeModulo`), sumándose al
  mismo sentido de dependencia que ya usa `acceso`.
- `AdministracionDeMunicipiosController` crece con dos rutas más sobre
  `solicitud_modulo`; se agrega un contador
  (`cantidadDeSolicitudesPendientes`) a `MunicipioResponse` para que la
  lista de municipios de la consola del proveedor muestre de un vistazo
  qué municipios tienen pedidos sin atender, mismo criterio que
  `cantidadDeModulosContratados`.
- Una solicitud de módulo no queda registrada en la auditoría transversal
  ni dispara ninguna notificación automática a la plataforma: hoy hay que
  entrar a la consola del proveedor a mirar si hay algo pendiente, igual
  que ya pasa con `estado_facturacion = ATRASADO` (ADR 0019, "Pendiente de
  definir": alertas proactivas).

## Pendiente de definir

- Notificar a la plataforma (email) cuando entra una solicitud nueva, en
  vez de que dependa de que alguien entre a mirar la consola. Mismo tipo
  de pendiente que "alertas proactivas sobre municipios `ATRASADO`" de
  ADR 0019.
- Auditoría de acciones cross-tenant/cruzando bases (crear una solicitud,
  marcarla atendida, y en general toda la API de administración de
  plataforma): sigue siendo el mismo pendiente que dejó ADR 0013.
- Validación cruzada entre el `tipo` de la solicitud y el estado actual
  del módulo en el entitlement.
- Retiro o edición de una solicitud ya creada por el propio municipio.
- Un tercer estado intermedio (por ejemplo "en análisis") si alguna vez
  hace falta distinguirlo de "pendiente sin mirar".
