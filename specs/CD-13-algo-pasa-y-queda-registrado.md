# CD-13 · R5 — Algo pasa y queda registrado

Rama: `CD-13-algo-pasa-y-queda-registrado` (desde `develop`).
ADR de referencia: [0013](../docs/arquitectura/decisiones/0013-persistencia-de-eventos-y-mecanismo-transversal-de-notificaciones-y-auditoria.md)
(léelo antes de implementar: fija las decisiones de diseño que esta spec
da por tomadas — dónde vive `event_publication`, por qué los listeners son
síncronos, por qué no hay una interfaz genérica de evento auditable).

## Demo

Un administrador del municipio da de alta un usuario nuevo desde el ABM
existente (R3). Sin ningún paso adicional:

1. Al usuario nuevo le llega un email de bienvenida (visible en la bandeja
   web de Mailpit en desarrollo).
2. En la pantalla de Administración del municipio aparece una sección
   nueva, "Registro de auditoría", con una fila que dice quién (el
   administrador que hizo la acción), cuándo, y qué pasó ("Creó al usuario
   Juan Pérez (juan@sanmartin.gob.ar)").

Se demuestra una sola acción (alta de usuario). Editar un usuario, iniciar
sesión, o prender/apagar un módulo **no** generan notificación ni
auditoría en esta rebanada — ver "Fuera de alcance".

## Qué se construye, en orden

### Backend — Tarea 1: mecanismo transversal + auditoría (bloqueante para el resto)

**1a. `spring-modulith-starter-jpa` y la unidad de persistencia de tenant**

- Agregar la dependencia (ya reservada con comentario en `pom.xml`).
- La entidad de Spring Modulith (paquete
  `org.springframework.modulith.events.jpa`, tabla física
  `event_publication`) se mapea contra el `EntityManagerFactory` de
  **tenant**, no el de control (ADR 0013 §1): sumarla al
  `packagesToScan` del `tenantEntityManagerFactory` en
  `ConfiguracionDePersistencia`.
- `JpaEventPublicationConfiguration` (de Spring Modulith) pide un
  `EntityManager` por tipo. Como hoy no existe ningún bean de ese tipo en
  el contexto, agregar uno que envuelva el EMF de tenant —
  `SharedEntityManagerCreator.createSharedEntityManager(emf)` sobre el
  `tenantEntityManagerFactory`— para que resuelva sin ambigüedad al de
  tenant. Ubicarlo en `persistencia`, junto al resto del cableado de EMFs.
- Migración Flyway nueva en `db/tenant` que crea `event_publication`. El
  `starter-jpa` no trae el DDL empaquetado: para no adivinar tipos de
  columna, generar el DDL real con la utilidad de esquema de Jakarta
  Persistence apuntada al dialecto de PostgreSQL
  (`jakarta.persistence.schema-generation.scripts.action=create` con
  `scripts.create-target` a un archivo; no necesita una base viva) contra
  la entidad `DefaultJpaEventPublication` de esta versión de Modulith
  (2.1.0), y versionar ese resultado como migración, ajustando lo que
  haga falta (por ejemplo, texto sin límite para el evento serializado).
- **No** habilitar `spring.modulith.events.republish-outstanding-events-on-restart`
  (revisión del ADR 0013 tras implementar el punto anterior: esa propiedad
  asume un único almacén de eventos alcanzable al arrancar el proceso, y acá
  hay uno por tenant, ninguno alcanzable sin resolver primero un tenant —
  ver ADR 0013 §2 y su "Pendiente de definir"). Una notificación fallida
  queda con su fila de `event_publication` incompleta, visible para
  diagnóstico manual, pero no se reintenta sola en esta rebanada.
- Criterio de aceptación de este paso: la app arranca, `ModularityTests`
  sigue en verde, y la fila de `event_publication` correspondiente a un
  listener que corrió sin error queda marcada como completa (se termina de
  verificar con 1c, no hace falta un test aislado solo para esto).

**1b. `acceso` publica `UsuarioCreado`**

- Nuevo tipo público (raíz de `ar.com.ciudaddigital.acceso`, no
  `.internal`): `UsuarioCreado`, record con lo que necesitan sus
  consumidores: id/nombre/email del usuario creado, y
  id/nombre/email del **actor** (quien hizo el alta, no quien se creó).
  El actor se obtiene de `SecurityContextHolder` →
  `UsuarioAutenticado` (mismo mecanismo que ya usa el resto del backend
  para saber quién hace el request).
- `AdministracionDeUsuarios.crear(...)` publica el evento (vía
  `ApplicationEventPublisher`) después de `usuarios.save(usuario)`,
  todavía dentro de su método `@Transactional("tenantTransactionManager")`
  — el evento tiene que nacer dentro de la transacción para que
  `@TransactionalEventListener(phase = AFTER_COMMIT)` lo vea (por defecto
  descarta eventos publicados fuera de una transacción en curso).
- `editar(...)` **no** publica evento en esta rebanada (fuera de alcance).

**1c. Módulo nuevo `auditoria`**

- Paquete `ar.com.ciudaddigital.auditoria`, con `.internal` para lo que no
  se expone (mismo patrón que `acceso`/`tenants`/`entitlement`). No
  publica `DescriptorDeModulo`: es canon base (ADR 0013 §4), no se gatea
  por entitlement.
- Depende de `acceso` (consume `UsuarioCreado`). No necesita depender de
  `tenants`: el aislamiento por tenant lo da el enrutamiento del
  datasource, no una consulta explícita al `TenantContext`.
- Migración de tenant nueva: tabla `registro_auditoria` —
  `id` (identity), `ocurrido_en` (timestamptz not null),
  `actor_id` (bigint not null, sin FK: es un dato informativo, no
  referencial — el registro tiene que sobrevivir aunque el actor se
  desactive), `actor_nombre` y `actor_email` (snapshot al momento del
  hecho, **no** un join contra `usuario`: si el actor cambia de nombre
  después, el registro histórico no debe cambiar con él), `accion`
  (varchar, código tipo `usuario.creado`), `entidad_tipo` (varchar,
  `"usuario"`), `entidad_id` (varchar — texto a propósito, para no atar la
  tabla al tipo de id de la primera entidad auditada), `detalle` (text,
  descripción legible). Índice por `ocurrido_en` descendente.
- Mismo migración o la siguiente: agregar el permiso `auditoria.ver`
  (área "Administración", igual que `usuarios.ver`/`roles.ver`) al
  catálogo, asignado **solo** al rol de sistema `administrador` (el rol
  `agente` no lo recibe por defecto — es información sensible del
  municipio).
- Listener: `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`
  más `@Transactional("tenantTransactionManager")` explícito (no
  `@ApplicationModuleListener` — ADR 0013 §2) sobre `UsuarioCreado`, que
  arma el registro y lo guarda.
- Controller: `GET /api/auditoria`, protegido con
  `@PreAuthorize("hasAuthority('auditoria.ver')")`, devuelve la lista
  completa ordenada por `ocurridoEn` descendente (sin paginado — fuera de
  alcance de esta rebanada). Forma de cada elemento: `id`, `ocurridoEn`,
  `actorNombre`, `actorEmail`, `accion`, `entidadTipo`, `entidadId`,
  `detalle`.

**Tests de esta tarea (obligatorios, no delegables a después):**

- Crear un usuario y verificar que `GET /api/auditoria` devuelve una fila
  coherente (actor = quien hizo el alta, no el usuario creado; acción,
  entidad y detalle correctos).
- Verificar que la fila de `event_publication` correspondiente al listener
  de auditoría queda marcada como completa — es la prueba de que el
  registro persistente decidido en el ADR 0013 realmente engancha con el
  listener síncrono, no solo con el async que no se usa.
- **Test de aislamiento (obligatorio, criterio de completitud de la
  rebanada, no un ticket aparte):** dos municipios de prueba, alta de
  usuario en cada uno, y verificar que `GET /api/auditoria` con sesión del
  municipio A no incluye ninguna fila del municipio B (ni al revés). Seguir
  el patrón ya establecido en `SoporteDeIntegracion`
  (`asegurarMunicipio`, `iniciarSesionDeAdministrador`).
- Un usuario sin `auditoria.ver` recibe 403 en `GET /api/auditoria`.

### Backend — Tarea 2: módulo `notificaciones` (depende de que exista `UsuarioCreado`, no de `auditoria`)

- Paquete `ar.com.ciudaddigital.notificaciones`. Depende de `acceso`
  (consume `UsuarioCreado`) y de `tenants` (`TenantContext.requerido()`
  para el nombre del municipio en el cuerpo del email — válido acá porque
  el listener corre síncrono, en el mismo hilo del request, con el tenant
  todavía resuelto).
- Forma mínima de "motor multicanal" (ADR 0013 §3 y su Pendiente de
  definir): una interfaz `CanalDeNotificacion` con un método
  `enviar(Notificacion)`, donde `Notificacion` es
  `(String destinatario, String asunto, String cuerpo)`. Una única
  implementación en R5: `CanalDeEmailNotificacion`, sobre
  `spring-boot-starter-mail` / `JavaMailSender`. No implementar SMS,
  WhatsApp ni push: son solo la forma prevista para más adelante, no
  código.
- Listener: mismo patrón que en `auditoria` —
  `@TransactionalEventListener(phase = AFTER_COMMIT)`, sin `@Async`— que
  arma un email de bienvenida al usuario creado (destinatario: su email;
  cuerpo: incluye el nombre del municipio) y lo manda por el canal de
  email.
- Config de desarrollo: agregar un servicio `mailpit` a
  `docker-compose.yml` (imagen `axllent/mailpit`, puerto `1025` SMTP y
  `8025` UI web), y `spring.mail.host`/`spring.mail.port` en
  `application.properties` apuntando a `localhost:1025` sin autenticación
  — mismo criterio que ya usa el proyecto para Postgres en desarrollo.
- **Los tests no deben depender de un servidor SMTP real en la red.**
  Usar un servidor SMTP falso embebido para los tests de integración (por
  ejemplo GreenMail, arrancado igual que el contenedor de Postgres en
  `SoporteDeIntegracion`: una instancia por suite) y verificar destinatario
  y asunto del email recibido.
- Test de resiliencia (valida la consecuencia descripta en el ADR 0013):
  si el envío de email falla (por ejemplo, apagando el servidor SMTP falso
  para ese caso puntual), la creación del usuario sigue respondiendo
  200/201 igual — el fallo del listener no debe tirar abajo el request
  original.

### Frontend — Pantalla de auditoría (puede construirse en paralelo a la Tarea 1 de backend: el contrato de `GET /api/auditoria` ya está fijado arriba)

- Nuevo componente `PanelDeAuditoria`, en `frontend/src/acceso/` (junto a
  `PanelDeUsuarios.tsx`/`PanelDeRoles.tsx`: es administración del
  municipio, no un módulo contratable — no se registra en
  `modulos/registro.ts`).
- Se monta dentro de `PanelDeAdministracion.tsx`, visible solo si
  `usuario.permisos.includes('auditoria.ver')` (comodidad de UI: el
  backend ya lo hace cumplir con `@PreAuthorize`, ADR 0011).
- Solo lectura: sin formularios de alta/edición/borrado — el registro lo
  genera el sistema, no se edita a mano.
- Tabla accesible con la misma convención que `PanelDeUsuarios.tsx`:
  `<section aria-labelledby>` con su `<h2>`, `<table>` con `<caption>`
  descriptivo, encabezados con `scope="col"`, primera columna con
  `scope="row"`, `role="status"` mientras carga, `role="alert"` si falla
  la carga. Columnas: Cuándo (formateada con el mismo `Intl.DateTimeFormat`
  que ya usa `PanelDeUsuarios`), Quién (nombre y email del actor), Acción,
  Sobre qué (tipo y id de entidad), Detalle.
- Reutilizar `pedir` de `./api` para `GET /api/auditoria` y las clases CSS
  `.tabla`/`.tabla-contenedor`/`.contenido` ya existentes — no se necesita
  CSS nuevo salvo que algo no alcance.
- No hay paginado ni filtros: se muestra la lista completa que devuelve el
  backend (fuera de alcance, ver más abajo).

## Fuera de alcance de esta rebanada

- Auditoría o notificación de: editar un usuario, iniciar sesión, y
  prender/apagar un módulo (API de administración de plataforma). Esta
  última es, además, cross-tenant y no tiene una base de tenant donde
  escribir — el ADR 0013 lo deja explícitamente pendiente.
- SMS, WhatsApp Business API o push como canal de notificación: solo la
  interfaz `CanalDeNotificacion` y el canal de email.
- Paginado, filtros o búsqueda en la pantalla de auditoría.
- Reintento manual desde la UI de una notificación fallida (el reintento
  que existe es el automático de Spring Modulith al reiniciar la app).
- `@ApplicationModuleListener` asíncrono y la propagación de tenant entre
  hilos que requeriría — ADR 0013 lo difiere explícitamente.
- Retención o purga de `registro_auditoria` y de `event_publication`.
- Interfaz genérica de "evento auditable" para que un módulo futuro se
  enganche sin escribir su propio listener — ADR 0013 lo difiere hasta que
  haya un segundo caso real.

## Accesibilidad (criterio de completitud, no un ticket aparte)

La única pantalla nueva es `PanelDeAuditoria`. Tiene que cumplir lo mismo
que ya cumplen `PanelDeUsuarios`/`PanelDeRoles`: navegable por teclado sin
trampas de foco, sin depender solo de color para transmitir información,
con roles ARIA de estado (`status`/`alert`) para carga y error, y con
encabezados de tabla correctamente asociados (`scope`). No agrega ningún
control interactivo nuevo (es de solo lectura), así que no necesita manejo
de foco propio más allá de lo que ya hace `PanelDeAdministracion` al
montar la vista.

## Orden de trabajo sugerido

1. Backend — Tarea 1 (mecanismo + `auditoria`) y Frontend (`PanelDeAuditoria`)
   pueden avanzar en paralelo: el contrato de `GET /api/auditoria` ya está
   fijo en esta spec.
2. Backend — Tarea 2 (`notificaciones`) después de la Tarea 1, porque
   depende de que `UsuarioCreado` ya exista.
3. Auditor sobre el diff completo antes de proponer el PR.
