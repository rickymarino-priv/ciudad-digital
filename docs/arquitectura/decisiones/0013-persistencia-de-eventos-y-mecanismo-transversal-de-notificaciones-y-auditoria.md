# 0013 - Persistencia de eventos de dominio y mecanismo transversal de notificaciones y auditoría

- Estado: Aceptada
- Fecha: 2026-08-24

## Contexto

R5 (CD-13) es la primera rebanada con eventos de dominio reales entre
módulos: el [ADR 0003](0003-spring-modulith-para-el-backend.md) previó la
comunicación entre módulos vía eventos desde el inicio, pero hasta ahora
ningún módulo publicaba ni consumía uno. `spring-modulith-starter-jpa`
—el registro persistente de publicaciones, que da entrega al menos una vez
aunque el listener falle o la app se reinicie a mitad de proceso— quedó
fuera de R1 explícitamente por no tener consumidor, con una nota en el
`pom.xml` que difería a esta rebanada la decisión de en qué base vive la
tabla `event_publication`.

El backend tiene dos unidades de persistencia manuales
([ADR 0007](0007-modelo-de-datos-del-tenant.md),
`ConfiguracionDePersistencia`): un `EntityManagerFactory` para la base de
control (paquete `tenants`) y otro para la base del tenant en curso
(paquetes `municipio` y `acceso`), enrutado en runtime por
`DataSourceDeTenants` a partir de `TenantContext` (ADR 0001, ADR 0004).
`TenantContext.actual()` se apoya en `TenantHolder`, un `ThreadLocal` que
el filtro de resolución de tenant llena al empezar el request y limpia al
terminar; su propio Javadoc ya anticipaba el límite: queda vacío "fuera de
un request con tenant resuelto (por ejemplo, en tareas de fondo)".

La acción de ejemplo de esta rebanada —crear un usuario desde el ABM de
R3 ([ADR 0011](0011-autorizacion-por-roles-con-permisos-granulares.md))—
dispara dos reacciones: mandar un email de bienvenida y dejar un registro
de auditoría. Ambas necesitan escribir en la base del **tenant** del
request que originó la acción, no en la de control: son datos operativos
de ese municipio (quién de ese municipio hizo qué), del mismo modo que la
tabla `usuario` que consultan ya vive ahí.

`@ApplicationModuleListener` —la anotación que Spring Modulith recomienda
para integrar módulos por eventos— compone `@Async` con
`@TransactionalEventListener` y una transacción propia. `@Async` ejecuta
el listener en un hilo del pool de tareas, no en el hilo del request. Como
`TenantHolder` es un `ThreadLocal` sin propagación a hilos hijos, un
listener así se encuentra, en el hilo nuevo, sin tenant resuelto:
`DataSourceDeTenants` no tiene una base a la que conectarse y falla
—correctamente, en el sentido de que no hay riesgo de escribir en la base
equivocada, pero el mecanismo completo deja de funcionar—.

## Decisión

### 1. `event_publication` vive en la base de cada tenant, no en la de control

Las bases de tenant ganan un tercer `EntityManagerFactory`... no: se
agrega el paquete de la entidad de Spring Modulith
(`org.springframework.modulith.events.jpa`) al *scanning* del
`EntityManagerFactory` de tenant ya existente, y se expone un bean
`EntityManager` propio para esa unidad (con
`SharedEntityManagerCreator.createSharedEntityManager(...)` sobre el EMF
de tenant) para que `JpaEventPublicationConfiguration` —que pide un
`EntityManager` por autowiring de tipo— resuelva sin ambigüedad el de
tenant y no el de control.

Motivo: todo evento que hoy se publica nace de una acción dentro del
portal de un municipio, y sus listeners escriben en la base de ese mismo
municipio (auditoría, notificaciones). Si `event_publication` viviera en
la base de control, cada fila mezclaría en una tabla compartida la
serialización de eventos —potencialmente datos personales, como el email
del usuario creado— de todos los municipios, exactamente el tipo de
convivencia de datos entre tenants que el [ADR 0001](0001-multi-tenant-con-bd-por-tenant.md)
existe para evitar. La base de control no tiene, hoy, ningún evento propio
que publicar: el alta de municipio (R2) no pasa por el bus de eventos.

Consecuencia directa: la tabla `event_publication` de cada municipio se
crea por Flyway en su propia migración de tenant (`db/tenant`), con el
mismo mecanismo de aprovisionamiento que el resto del esquema
([ADR 0005](0005-aprovisionamiento-de-tenant.md)) — no por el
autoarranque de esquema de Spring Modulith, que este proyecto no usa en
ninguna tabla.

El bean nuevo que hace falta es un `EntityManager` sobre el EMF de tenant
(`SharedEntityManagerCreator.createSharedEntityManager(...)`), para que
`JpaEventPublicationConfiguration` —que pide uno por autowiring de tipo—
tenga a qué engancharse.

Esto resultó menos simple de lo que parece, por dos ambigüedades reales que
solo aparecieron al implementar y verificar contra la suite de tests (no
alcanzaba con razonarlas de antemano):

- **`EntityManager`**: `LocalContainerEntityManagerFactoryBean` implementa
  `SmartFactoryBean<EntityManagerFactory>`, que también sabe producir un
  `EntityManager` compartido si se le pide ese tipo. Consecuencia: los
  beans `controlEntityManagerFactory` y `tenantEntityManagerFactory` son,
  ellos mismos, candidatos implícitos de tipo `EntityManager`, además del
  bean explícito de arriba — y `@Primary` en la *bean definition* del EMF
  "sangra" hacia esa vista implícita. Por eso `controlEntityManagerFactory`
  **no** lleva `@Primary` (nada en este código autowirea
  `EntityManagerFactory` sin nombrarlo, así que no hace falta), y el
  `EntityManager` de tenant sí, para ser el único candidato primario de ese
  tipo en todo el contexto.
- **`PlatformTransactionManager`**: `JpaEventPublicationRepository` —la
  clase de Spring Modulith que hace el trabajo, no anotable por
  nosotros— lleva `@Transactional` a nivel de clase **sin nombrar
  gestor**, y sus escrituras (`markProcessing`, `markCompleted`) corren por
  fuera de la transacción propia del listener (el advisor de Modulith que
  las envuelve tiene prioridad más alta). Esa transacción sin nombre
  necesita, igual que el `EntityManager` de arriba, resolver al gestor de
  **tenant** — si resuelve al de control, Hibernate rechaza el
  `executeUpdate()` contra el `EntityManager` de tenant con
  `TransactionRequiredException: No active transaction`. Por eso el
  default de `PlatformTransactionManager` de toda la aplicación **sí**
  cambia en R5: pasa a ser `tenantTransactionManager`.

  El único código de este proyecto que necesitaba el default anterior
  (control) son dos clases de `tenants.internal` —`AutenticacionDePlataforma`
  y `SembradorDeUsuarioDePlataforma`—, que ahora nombran
  `@Transactional("controlTransactionManager")` explícitamente en vez de
  apoyarse en el default. Se verificó por búsqueda en todo el código que no
  hay ningún otro `@Transactional` sin nombrar gestor que dependiera del
  default anterior.

En síntesis: el default de `EntityManagerFactory` no cambia (nada lo
necesitaba); el default de `PlatformTransactionManager` sí, de control a
tenant, porque el código de terceros de Spring Modulith lo exige y no se
puede anotar. Es una asimetría real entre los dos tipos, no un descuido —
está documentada en el Javadoc de `ConfiguracionDePersistencia`.

### 2. Los listeners de integración corren síncronos, en el hilo del request, sin `@Async`

Para no perder el tenant al saltar de hilo, `auditoria` y `notificaciones`
**no** usan `@ApplicationModuleListener`. Usan la mitad no asíncrona de lo
mismo: `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`
más `@Transactional("tenantTransactionManager")` explícito en el método
del listener. Así:

- El listener corre en el mismo hilo que hizo el commit de la transacción
  original, todavía dentro del mismo request: `TenantHolder` sigue
  resuelto y `DataSourceDeTenants` conecta a la base correcta sin ningún
  mecanismo nuevo de propagación.
- Corre **después** de que la transacción que creó el usuario haya
  confirmado: no hay auditoría de una fila que terminó no existiendo por
  un rollback.
- Sigue quedando registrado en `event_publication`: el registro persistente
  de Spring Modulith no depende de que el listener sea asíncrono, registra
  una fila por cada (evento, listener) y la marca completa cuando el
  listener termina sin error, sea cual sea su hilo.

El costo es que la respuesta HTTP de "crear usuario" espera a que se
escriba el registro de auditoría y se intente el envío del email. Para el
volumen y la frecuencia de esta acción (alta administrativa, no un flujo
masivo) es un costo aceptable; no lo es todavía justificar construir el
mecanismo de propagación de tenant entre hilos que requeriría volver a
`@ApplicationModuleListener` (ver Pendiente de definir).

Una falla del listener de email (por ejemplo, el servidor SMTP caído) no
tira abajo el request original —Spring no repropaga una excepción de un
callback `afterCommit()`—, y esa fila de `event_publication` queda sin
completar. Esa fila incompleta es, concretamente, la razón de tener el
registro persistente desde esta rebanada: sin él, esa notificación fallida
se pierde sin dejar rastro.

**No** se habilita en R5 `spring.modulith.events.republish-outstanding-events-on-restart=true`.
La reincorporación automática que promete esa propiedad asume un único
almacén de eventos alcanzable al arrancar el proceso; acá hay N —uno por
cada base de tenant— y ninguno es alcanzable sin resolver primero un
tenant, que requiere un request con `Host`. Forzar esa propiedad exigiría
además un cambio bastante más grande de lo que sugiere: como el mecanismo
de reintento de Spring Modulith usa siempre el `EntityManager`/
`PlatformTransactionManager` **por defecto** de la aplicación sin
nombrarlo, y corre antes de que exista ningún tenant resuelto, terminaría
necesitando volver `@Primary` el `EntityManagerFactory` de tenant —con el
riesgo de que algún código de `tenants.internal` que hoy confía en el
default (control) sin nombrarlo empiece a escribir, sin darse cuenta,
contra la base equivocada— más algún mecanismo que tolere ejecutarse sin
tenant resuelto al arrancar. Es una pieza de infraestructura nueva,
riesgosa para la propiedad más sensible del producto (aislamiento entre
tenants), a cambio de una propiedad que, aun resuelta, en esta arquitectura
solo podría reintentar contra la base de un tenant a la vez —nunca "todos
los pendientes de todos los municipios" que su nombre sugiere— sin además
iterar explícitamente sobre la lista de tenants. Ver Pendiente de definir.

### 3. Patrón concreto por evento, no una interfaz genérica de "hecho auditable"

`acceso` publica un evento propio y concreto, `UsuarioCreado` (en la raíz
del paquete del módulo, no en `.internal`: es su primera API pública),
con los datos que auditoría y notificaciones necesitan, incluido el actor
—leído de `UsuarioAutenticado` vía `SecurityContextHolder`, el mismo
mecanismo que ya usa el resto del backend para saber quién hace el
request—.

`auditoria` y `notificaciones` dependen de `acceso` y declaran cada una su
propio listener concreto para `UsuarioCreado`. No hay una interfaz común
tipo `EventoAuditable`/`EventoNotificable` que los módulos futuros deban
implementar: con un solo evento real en todo el sistema, generalizar el
contrato es diseñar a ciegas sobre una forma que ningún segundo caso
todavía obligó a tener. El costo declarado es que el próximo módulo que
quiera auditoría o notificación agrega su propio listener en cada uno de
`auditoria` y `notificaciones`, en vez de que el mecanismo lo capture
solo — mismo criterio que el [ADR 0011](0011-autorizacion-por-roles-con-permisos-granulares.md)
usa para diferir ACL y el [ADR 0009](0009-modelo-comercial-y-entitlement.md)
para diferir la granularidad del contrato.

### 4. `auditoria` y `notificaciones` son canon base, no módulos contratables

Ninguna de las dos entra al catálogo de `entitlement`
([ADR 0012](0012-declaracion-de-modulos-y-gating-por-ruta.md)): no
publican `DescriptorDeModulo` ni tienen prefijo de API gateado. El
[catálogo funcional](../../producto/catalogo-funcional.md) ya las ubica
en "Plataforma transversal", junto con identidad y accesos — no son
"módulos de área" que un municipio contrata o no. La pantalla de
auditoría vive junto a usuarios y roles en la administración del
municipio, protegida por permiso (`auditoria.ver`), no por entitlement.

## Alternativas consideradas

- **`event_publication` en la base de control**: un único lugar para
  operar (una tabla, no N), pero mezcla datos operativos de todos los
  municipios en una base que hoy no tiene ningún evento propio, y
  contradice el aislamiento del ADR 0001 apenas el evento serializado
  lleve un dato personal, que es exactamente el caso de `UsuarioCreado`.
  Descartada.
- **`@ApplicationModuleListener` con un `TaskDecorator` que propague
  `TenantHolder` al hilo asíncrono**: es la forma "correcta" a largo plazo
  —desacopla el listener del request que lo disparó, como Spring Modulith
  recomienda— pero exige que `tenants` (el único módulo con acceso a
  `TenantHolder.internal`) publique un decorator para el executor
  asíncrono de toda la aplicación, y probar explícitamente que sobrevive
  el salto de hilo. Es una pieza de infraestructura nueva y no trivial
  para un solo consumidor real. Se difiere hasta que el volumen de la
  acción lo justifique o aparezca un segundo caso que la necesite.
- **Interfaz genérica `EventoAuditable`**: ver punto 3. Descartada por
  ahora, no definitivamente.
- **Leer el actor desde el `UsuarioEntity` en vez de `SecurityContext`**:
  el actor de la acción no es necesariamente el usuario sobre el que se
  actúa (creo a otro usuario, no me creo a mí mismo); solo
  `SecurityContext` tiene "quién hace el request". Descartada por no
  responder la pregunta que hace falta.
- **Habilitar `republish-outstanding-events-on-restart` haciendo `@Primary`
  el `EntityManagerFactory`/`PlatformTransactionManager` de tenant, con un
  envoltorio que tolere ejecutarse sin tenant resuelto al arrancar**: se
  probó durante la implementación. Funciona, pero cambia el default de
  persistencia de toda la aplicación (antes control, ahora tenant) para
  una propiedad que, en esta arquitectura, no puede cumplir lo que promete
  —no hay forma de que una consulta sin tenant resuelto encuentre algo que
  reintentar en ninguna base—, así que el resultado observable es una
  pieza de infraestructura nueva, con más superficie para un bug de
  aislamiento, que en la práctica nunca reintenta nada. Descartada por
  desproporcionada frente al beneficio real.

## Consecuencias

- Cada base de tenant nueva —desde el alta de municipio en adelante,
  ADR 0005— trae `event_publication` de fábrica, migrada como el resto del
  esquema.
- Un evento publicado fuera de un request con tenant resuelto (una tarea
  de fondo futura, por ejemplo) no tiene dónde persistir su publicación:
  esta decisión asume que, mientras el único origen de eventos sea una
  acción dentro del portal de un municipio, siempre hay un tenant
  resuelto en el hilo que publica.
- El próximo módulo que necesite auditoría o notificación repite el mismo
  patrón (evento propio y concreto, listener síncrono explícito con
  `@Transactional("tenantTransactionManager")`) hasta que este ADR se
  reemplace por uno que generalice el mecanismo.
- La cadena de creación de usuario ahora incluye, de forma síncrona, una
  escritura a `registro_auditoria` y un intento de envío de email: un SMTP
  lento alarga la respuesta de `POST /api/usuarios`. No hay timeout propio
  configurado en R5; queda como parte del hardening diferido
  ([CLAUDE.md](../../../CLAUDE.md) — endurecimiento de seguridad y
  performance sin problema medido).
- La auditoría de la API de administración de plataforma (alta de
  municipios, prender/apagar módulos —pendiente que el propio ADR 0012
  señala como dependiente de esta rebanada—) queda **fuera** de R5: esas
  acciones son cross-tenant y no tienen una base de tenant donde escribir
  el registro. Resolverlo es una decisión de datos distinta (¿tabla en la
  base de control? ¿misma tabla `registro_auditoria` con una fila
  "plataforma"?) que este ADR no toma.

## Pendiente de definir

- Reintento automático de publicaciones incompletas al reiniciar la
  aplicación. Con `event_publication` por tenant, esto no es un booleano:
  requiere una tarea que enumere los tenants activos (base de control),
  resuelva `TenantHolder` para cada uno por turno, y dispare la
  resubmisión de pendientes de esa base puntual —
  `IncompleteEventPublications` de Spring Modulith expone lo necesario
  para eso—. Es una pieza real de infraestructura, con su propio costo y
  su propio test, que no bloquea la demo de esta rebanada: hoy, un email
  que falla queda con su fila `event_publication` incompleta y visible
  para diagnóstico manual, pero no se reintenta solo.
- Migrar los listeners a `@ApplicationModuleListener` asíncrono con
  propagación de `TenantHolder` vía `TaskDecorator`, cuando el volumen de
  la acción o la latencia agregada lo justifiquen.
- Interfaz genérica de evento auditable/notificable, cuando exista un
  segundo y un tercer caso real con los que generalizar sin adivinar.
- Retención y purga de `registro_auditoria` y de `event_publication`
  (hoy crecen sin límite; ninguna de las dos tiene un mecanismo de
  archivado o borrado).
- Auditoría de la API de administración de plataforma (cross-tenant), que
  este ADR deja explícitamente fuera.
- Motor de notificaciones multicanal más allá de email: SMS, WhatsApp
  Business API, push (catálogo funcional, "Plataforma transversal") — R5
  construye la forma (`CanalDeNotificacion`) pero implementa un único
  canal.
