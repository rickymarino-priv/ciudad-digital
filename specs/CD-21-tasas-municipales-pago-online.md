# CD-21 · R13 — Un vecino paga una tasa municipal online

Rama: `CD-21-tasas-municipales-pago-online` (desde `develop`).

Primera rebanada de **Fase 2** (Recaudación e integración,
[roadmap](../docs/producto/roadmap-fases.md#fase-2--recaudación-e-integración-con-lo-existente)).
Requiere [ADR 0018](../docs/arquitectura/decisiones/0018-pasarela-de-pago-simulada.md)
(léanlo antes de tocar código: decide la interfaz `PasarelaDePago`, por
qué vive en un módulo canon base propio `pagos`, por qué el adaptador
simulado no redirige a ningún sitio externo, y por qué la confirmación de
pago no pasa por `pagos`). Esta spec no repite ese razonamiento, solo lo
traduce a tareas concretas. También aplica, sin extenderlos:
[ADR 0011](../docs/arquitectura/decisiones/0011-autorizacion-por-roles-con-permisos-granulares.md)
(permisos), [ADR 0012](../docs/arquitectura/decisiones/0012-declaracion-de-modulos-y-gating-por-ruta.md)
(catálogo de módulos y lectura pública) y
[ADR 0014](../docs/arquitectura/decisiones/0014-reclamos-ciudadanos-alta-publica-anonima-y-estado-propio.md)
§1 (escritura pública, solo `POST`).

## Demo

Un agente municipal, con sesión y el permiso `tasas.publicar`, da de alta
una tasa para un número de cuenta (concepto, período, monto). Un vecino,
sin sesión, entra al portal público de ese municipio, busca por ese
número de cuenta y ve la tasa pendiente. Hace clic en "Pagar", entra a una
pantalla rotulada explícitamente como "Simulador de pago (entorno de
prueba)" —no es un proveedor real, y se lo dice— con el monto a pagar y
dos botones, "Aprobar pago" y "Rechazar pago". Al aprobar, vuelve a ver su
tasa, ahora en estado "Pagada" con la fecha del pago. El mismo número de
cuenta y sus tasas no aparecen en el portal de otro municipio.

## Qué se construye

### Backend — Tarea 1 (bloqueante, agente `backend`): módulo `pagos`

Paquete nuevo `ar.com.ciudaddigital.pagos` (interfaz y records en la raíz,
son API pública que `tasas` va a usar directamente — mismo criterio que
`entitlement.DescriptorDeModulo`) más `ar.com.ciudaddigital.pagos.internal`
para la implementación.

- `package-info.java` en la raíz del módulo: explicar que es módulo canon
  base (no contratable, sin `DescriptorDeModulo`), sin persistencia
  propia, que declara el contrato de integración con una pasarela de pago
  y hoy solo tiene un adaptador simulado para desarrollo/demo (ADR 0018).

- `PasarelaDePago` (interfaz pública): un único método
  `ResultadoDeInicioDePago iniciarPago(SolicitudDePago solicitud)`.

- `SolicitudDePago` (record público): `String referenciaInterna,
  BigDecimal monto, String descripcion`. Validar en el llamador
  (`tasas`), no acá: este módulo no tiene reglas de negocio propias.

- `ResultadoDeInicioDePago` (record público): `String referenciaExterna,
  String urlDePago`.

- `pagos.internal.PasarelaDePagoSimulada` (`@Component implements
  PasarelaDePago`, único bean de este tipo en el contexto — ver ADR 0018
  §2): `iniciarPago(...)` genera `referenciaExterna = "SIM-" +
  UUID.randomUUID()` (o formato equivalente, a criterio del agente,
  siempre que sea no adivinable y no colisione en la práctica) y
  `urlDePago = null` (ADR 0018 §3: el frontend no navega a ningún lado en
  el adaptador simulado). No hace ninguna llamada de red ni valida el
  monto: eso es responsabilidad de `tasas` antes de llamar acá.

No hace falta test unitario dedicado: no tiene lógica de negocio propia
más allá de generar un identificador, y queda ejercitado por los tests de
integración de `tasas` (Tarea 2).

### Backend — Tarea 2 (después de la Tarea 1, mismo agente `backend`): módulo `tasas`

Paquete nuevo `ar.com.ciudaddigital.tasas`, con `DescriptorDelModuloTasas`
en la raíz (mismo patrón que `DescriptorDelModuloCementerio`) y el resto
en `ar.com.ciudaddigital.tasas.internal`.

**Migración** `V14__crear_tasas.sql` en `backend/src/main/resources/db/tenant/`:

```sql
-- Tasas municipales y su pago online (backlog R13, ADR 0018). Sin padrón
-- de contribuyentes real todavía: el "número de cuenta" es un
-- identificador simple que el municipio siembra al dar de alta cada
-- tasa, igual de informal que "numero" en norma (V7) o "numero_partida"
-- en partida_presupuestaria (V11).
create table tasa (
    id                        bigint generated always as identity primary key,
    numero_cuenta             varchar(50)   not null,
    concepto                  varchar(200)  not null,
    -- Texto libre (ej. "2026-08", "3er trimestre 2026"): la periodicidad
    -- real de cada tributo varía por municipio y por tasa, no se modela
    -- todavía como una entidad de calendario propia.
    periodo                   varchar(50)   not null,
    monto                     numeric(12,2) not null check (monto > 0),
    estado                    varchar(20)   not null default 'PENDIENTE'
        check (estado in ('PENDIENTE', 'PAGADA')),
    fecha_pago                timestamptz,
    -- Referencia que la pasarela (real o simulada) asigna al intento de
    -- pago en curso. Nula mientras no se inició ningún pago; se limpia
    -- si un pago se rechaza, para permitir reintentar (ver GestionDeTasas).
    referencia_externa_pago   varchar(100),
    -- Copia del actor al momento de publicar, no una relación con
    -- usuario: mismo criterio que publicado_por_nombre/email en norma
    -- (V7) y partida_presupuestaria (V11, ADR 0013).
    publicado_por_nombre      varchar(150)  not null,
    publicado_por_email       varchar(200)  not null,
    creado_en                 timestamptz   not null default now()
);

create index tasa_numero_cuenta_idx on tasa (numero_cuenta);

-- Única mientras no sea null: dos tasas distintas no pueden compartir un
-- intento de pago en curso.
create unique index tasa_referencia_externa_pago_idx on tasa (referencia_externa_pago)
    where referencia_externa_pago is not null;

comment on table tasa is
    'Tasas municipales sembradas por el municipio y su estado de pago online (backlog R13).';

-- Catálogo de permisos: área "Tasas". Publicar una tasa es un acto fiscal
-- del municipio (crea una deuda exigible), mismo nivel de sensibilidad
-- que boletin.publicar (V7) y transparencia.publicar (V11) — se asigna
-- SOLO a administrador.
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('tasas.publicar', 'Tasas', 'tasas', 'publicar',
     'Dar de alta una tasa municipal para un número de cuenta.');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo
from rol r, permiso p
where r.codigo = 'administrador'
  and p.codigo = 'tasas.publicar';
```

Confirmar al implementar que V14 es efectivamente el siguiente número
libre en `db/tenant/` (hoy el último es V13).

**`TasaEntity`** (`tasas.internal`): campos que mapean 1:1 la migración
(`numeroCuenta`, `concepto`, `periodo`, `monto` como `BigDecimal`,
`estado` como enum `EstadoDeTasa { PENDIENTE, PAGADA }`, `fechaPago`
nullable `Instant`, `referenciaExternaPago` nullable `String`,
`publicadoPorNombre`, `publicadoPorEmail`, `creadoEn`). Factory
`TasaEntity.nueva(...)` para el alta (estado inicial `PENDIENTE`, resto
`null`). Métodos de instancia (no setters públicos sueltos) para las dos
transiciones: `iniciarPago(String referenciaExterna)` (exige estado
`PENDIENTE`, si no lanza `SolicitudInvalida`) y
`confirmarPago(boolean aprobado)` (exige que `referenciaExternaPago` no
sea `null`; si `aprobado` pasa a `PAGADA` con `fechaPago = Instant.now()`
y deja `referenciaExternaPago` como está; si no, vuelve a dejar
`referenciaExternaPago = null` para permitir reintentar, sin cambiar
`estado`).

**`TasaRepository`** (`JpaRepository<TasaEntity, Long>`):
- `List<TasaEntity> findByNumeroCuentaOrderByCreadoEnDesc(String numeroCuenta)`.
- `Optional<TasaEntity> findByReferenciaExternaPago(String referenciaExternaPago)`.

**`GestionDeTasas`** (`@Service`):
- `TasaEntity publicar(String numeroCuenta, String concepto, String
  periodo, BigDecimal monto, String publicadoPorNombre, String
  publicadoPorEmail)`: valida (no vacíos, largos máximos razonables a
  criterio del agente —mismo estilo que `GestionDelCementerio`—, `monto`
  no nulo y mayor a cero) y guarda. Todo lo que no cumple lanza
  `SolicitudInvalida` (package-private, mismo patrón que en los demás
  módulos).
- `List<TasaEntity> buscarPorCuenta(String numeroCuenta)`: si
  `numeroCuenta` es nulo o vacío, lanza `SolicitudInvalida("Hay que
  indicar el número de cuenta.")` — **a propósito, no un filtro
  opcional**: a diferencia de la búsqueda de `cementerio` (que permite
  listar todo sin filtro), acá listar sin número de cuenta expondría
  montos y conceptos de todos los contribuyentes del municipio de una,
  que es más de lo que hace falta para que un vecino encuentre su propia
  tasa. No hay ninguna otra forma de listar tasas en esta rebanada (ni
  panel de gestión con el listado completo).
- `record TasaConToken(TasaEntity tasa, String urlDePago)` — no hace
  falta en realidad si `urlDePago` no se persiste ni se necesita después
  del alta del intento de pago; evaluar si conviene simplemente devolver
  el resultado de `PasarelaDePago` tal cual desde `iniciarPago` (ver
  abajo) en vez de forzar un record nuevo — decisión de detalle del
  agente, no bloqueante.
- `IniciarPagoResultado iniciarPago(Long tasaId)`, con `record
  IniciarPagoResultado(String referenciaExterna, String urlDePago)`
  (package-private): busca la tasa por id (si no existe, `404` — ver
  controller), valida que esté `PENDIENTE` (si no,
  `SolicitudInvalida("Esta tasa ya está pagada.")`), llama a
  `pasarelaDePago.iniciarPago(new SolicitudDePago(tasaId.toString(),
  tasa.getMonto(), tasa.getConcepto() + " - " + tasa.getPeriodo()))`,
  aplica `tasa.iniciarPago(resultado.referenciaExterna())`, guarda, y
  devuelve `referenciaExterna`/`urlDePago` del resultado de la pasarela.
- `TasaEntity confirmarPago(String referenciaExterna, boolean aprobado)`:
  busca por `referenciaExternaPago`; si no aparece nada, lanza una
  excepción nueva `PagoNoEncontrado` (package-private, `extends
  RuntimeException`, mismo patrón que `TokenNoEncontrado` de
  `reclamos`/`mesaentradas`, ADR 0017). Si aparece, llama a
  `tasa.confirmarPago(aprobado)` y guarda.

Inyectar `PasarelaDePago` (la interfaz de `pagos`, no la implementación
simulada directamente) en `GestionDeTasas`.

**`TasasController`** (`tasas.internal`), `@RequestMapping("/api/tasas")`:
- `POST /api/tasas` — `@PreAuthorize("hasAuthority('tasas.publicar')")`.
  Igual patrón que `CementerioController.registrar`: toma el actor
  autenticado del `Authentication` para `publicadoPorNombre`/
  `publicadoPorEmail` (si el principal no es `ActorAutenticado`, mismo
  `IllegalStateException` que ya usa `cementerio`, no debería pasar).
  Devuelve `TasaResponse` (ver abajo), `201`.
- `GET /api/tasas?numeroCuenta=...` — **pública**, sin `@PreAuthorize`
  (ver `rutasDeLecturaPublica()` abajo). `numeroCuenta` es
  `@RequestParam` **obligatorio** (no `required = false`: a diferencia de
  `cementerio`, acá no hay búsqueda "abierta" — ver el porqué en
  `GestionDeTasas.buscarPorCuenta`). Devuelve `List<TasaResponse>`.
- `POST /api/tasas/{id}/pagos` — **pública** (ruta de escritura pública,
  ver descriptor abajo): llama a `gestion.iniciarPago(id)`, devuelve
  `IniciarPagoResponse(String referenciaExterna, String urlDePago)`. Si
  la tasa no existe, `404` genérico (un `@ExceptionHandler` para
  `NoSuchElementException` o lo que use el agente para "no encontrado" —
  puede ser el mismo `Optional.orElseThrow` con una excepción dedicada,
  a su criterio, siempre que termine en 404 y no en 500).
- `POST /api/tasas/pagos/confirmar` — **pública** (ruta de escritura
  pública): body `ConfirmarPagoRequest(String referenciaExterna, boolean
  aprobado)`. Llama a `gestion.confirmarPago(...)`, devuelve
  `TasaResponse` con el estado actualizado. `@ExceptionHandler(PagoNoEncontrado.class)`
  → `404`, mensaje genérico ("No encontramos un pago con esa
  referencia."), mismo criterio de no distinguir motivos de falla que
  `ADR 0017` ya fija para `TokenNoEncontrado`.
- `@ExceptionHandler(SolicitudInvalida.class)` → `400`, mismo patrón que
  el resto de los módulos.

`record TasaResponse(Long id, String numeroCuenta, String concepto,
String periodo, BigDecimal monto, String estado, Instant fechaPago,
String publicadoPorNombre, String publicadoPorEmail, Instant creadoEn)`:
un único shape para alta, búsqueda pública y confirmación — a diferencia
de `cementerio`, acá no hay un dato de tercero que minimizar en la
versión pública (`publicadoPorNombre`/`publicadoPorEmail` es la firma
institucional del municipio, mismo criterio que `boletin`/
`transparencia`, no un dato privado de un contribuyente). No incluir
nunca `referenciaExternaPago` en ninguna respuesta: es un detalle interno
de la integración con la pasarela, no algo que el vecino necesite ver.

**`DescriptorDelModuloTasas`**:
- `codigo() = "tasas"`, `prefijosDeApi() = List.of("/api/tasas")`.
- `rutasDeLecturaPublica() = List.of("/api/tasas")`.
- `rutasDeEscrituraPublica() = List.of("/api/tasas/{id}/pagos",
  "/api/tasas/pagos/confirmar")` — confirmar al implementar que
  `HttpSecurity#requestMatchers(HttpMethod, String)` matchea la variable
  de path `{id}` igual que ya lo hace para
  `/api/reclamos/seguimiento/{token}` (CD-20); si no matcheara así,
  avisar antes de improvisar un patrón distinto.

**Test** `TasasTest.java` (integración, mismo estilo que
`CementerioTest.java`/`ReclamosTest.java`):
1. Con el módulo contratado y sesión de administrador: `POST /api/tasas`
   crea una tasa `PENDIENTE`.
2. Sin sesión: `GET /api/tasas?numeroCuenta=...` encuentra la tasa recién
   creada; `GET /api/tasas` sin el parámetro → `400`.
3. Sin sesión: `POST /api/tasas/{id}/pagos` sobre la tasa `PENDIENTE` →
   `200` con `referenciaExterna` no vacío; la tasa sigue `PENDIENTE` (no
   cambia hasta que se confirme).
4. Sin sesión: `POST /api/tasas/pagos/confirmar` con esa
   `referenciaExterna` y `aprobado: true` → `200`, tasa pasa a `PAGADA`
   con `fechaPago` no nulo; una segunda consulta pública (`GET
   /api/tasas?numeroCuenta=...`) refleja el nuevo estado.
5. Camino de rechazo: nueva tasa, iniciar pago, confirmar con
   `aprobado: false` → tasa sigue `PENDIENTE`; volver a iniciar pago
   sobre la misma tasa funciona de nuevo (no quedó bloqueada).
6. `POST /api/tasas/pagos/confirmar` con una referencia inventada →
   `404`.
7. Intentar `POST /api/tasas/{id}/pagos` sobre una tasa ya `PAGADA` →
   `400`.
8. Sin el módulo `tasas` contratado: las cuatro rutas (alta, búsqueda,
   iniciar pago, confirmar) rechazan con `403 MODULO_NO_CONTRATADO`,
   incluso sin sesión y con datos válidos — el gating de entitlement
   corre antes que cualquier regla de ruta pública (mismo test que ya
   existe para `reclamos`/`cementerio`/`mesaentradas`).
9. Usuario con sesión pero sin `tasas.publicar` (rol `agente`) →
   `POST /api/tasas` rechaza con `403` (no `MODULO_NO_CONTRATADO`, sino
   el rechazo de autorización por permiso).
10. **Aislamiento entre tenants**: publicar una tasa con un número de
    cuenta en el municipio A; buscar ese mismo número de cuenta contra el
    subdominio de B (con `tasas` contratado en B) → lista vacía. Iniciar
    y confirmar un pago en A, y confirmar esa misma `referenciaExterna`
    contra B → `404` (no porque la referencia esté "mal", sino porque la
    consulta corre contra la base de B, que no tiene esa fila — mismo
    razonamiento que el test de aislamiento de CD-20).

### Frontend — Tarea 3 (después de que el backend esté completo, agente `frontend`)

Nuevo `frontend/src/modulos/tasas/PantallaDeTasas.tsx`, registrado en
`registro.ts` (`tasas: PantallaDeTasas`) — no toca `Navegacion.tsx` ni
`CatalogoDeModulos.tsx` más allá de lo que el registro ya cubre
automáticamente (mismo mecanismo que los módulos anteriores).

Una única pantalla con vistas internas por estado local (mismo patrón
"una pantalla, vistas según estado/permiso" que
`PantallaDeReclamos`/`PantallaDeCementerio`/`PantallaDeMesaDeEntradas`;
no hay router de URLs en este frontend, confirmado en R12 — no inventar
uno acá):

1. **Vista de búsqueda** (default): `<form>` con un único campo (`<label
   htmlFor>` "Número de cuenta") y botón "Buscar". Al enviar,
   `pedir<TasaResponse[]>('/api/tasas?numeroCuenta=' +
   encodeURIComponent(numeroCuenta), '...')`. Foco en el `h1` al montar,
   mismo patrón que el resto de las pantallas.

2. **Vista de resultados**: lista de tasas encontradas (concepto,
   período, monto formateado, estado). Cada tasa `PENDIENTE` tiene un
   botón "Pagar"; cada tasa `PAGADA` muestra la fecha de pago en texto
   (no solo con color/ícono — WCAG). Si la búsqueda no encuentra nada,
   mensaje explícito (`role="status"`) de "no encontramos tasas para ese
   número de cuenta", no una lista vacía silenciosa. Botón para volver a
   buscar otro número de cuenta.

3. **Vista de simulador de pago** (al hacer clic en "Pagar" de una tasa
   pendiente): primero `POST /api/tasas/{id}/pagos`; con la respuesta
   (`referenciaExterna`), mostrar una vista con:
   - Título/rótulo explícito: "Simulador de pago (entorno de prueba)" —
     texto real, no solo un ícono, y aclarar en el cuerpo que en
     producción esto sería el sitio de una pasarela de pago real (ADR
     0018 §3: la vista tiene que ser honesta sobre ser una simulación).
   - Concepto, período y monto de la tasa que se está por pagar.
   - Dos botones: "Aprobar pago" y "Rechazar pago" (ninguno debe ser el
     único foco visual por color: usar texto/ícono con texto). Cada uno
     llama a `POST /api/tasas/pagos/confirmar` con
     `{referenciaExterna, aprobado: true|false}`.
   - Al volver (aprobado o rechazado), mostrar el resultado (`role
     ="status"` para aprobado, con foco; texto claro también para
     rechazado, sin necesariamente `role="alert"` ya que rechazar en un
     simulador no es un error del sistema) y volver a la vista de
     resultados con la tasa actualizada (re-consultar `GET
     /api/tasas?numeroCuenta=...` o actualizar el ítem local con la
     respuesta de `confirmar`, a criterio del agente).
   - Error de red/backend al iniciar o confirmar el pago: `role="alert"`,
     mismo patrón de foco que `errorRef` en los formularios existentes.

4. **Vista/sección de publicar tasa**, visible solo si `usuario` (prop
   ya existente en `PropsDePantallaDeModulo`) tiene el permiso
   `tasas.publicar` (mismo patrón de chequeo de permiso en frontend que
   ya usan `PantallaDeBoletin`/`PantallaDeTransparencia` para sus
   acciones de publicar — revisar cómo exponen el permiso del usuario
   esos componentes y reutilizar el mismo mecanismo, no inventar uno
   nuevo). Formulario con número de cuenta, concepto, período y monto
   (validación de campo obligatorio y de monto numérico positivo en el
   propio formulario, además de la validación del backend), botón
   "Publicar tasa". Confirmación de éxito con `role="status"` y foco,
   mismo patrón que el resto.

Todos los campos de formulario con `<label htmlFor>` explícito, mensajes
de error asociados por `aria-describedby` o el mecanismo que ya usen los
formularios existentes (revisar `PantallaDeCementerio.tsx` como
referencia más cercana, por tener también un formulario de alta protegido
+ búsqueda pública en la misma pantalla).

## Aislamiento entre tenants

Cubierto por el test 10 de la Tarea 2: búsqueda por número de cuenta y
confirmación de pago corren, igual que el resto de cada módulo, contra el
datasource ruteado por el `Host` del request (ADR 0001) — no hay lógica
nueva que pueda "cruzar" tenants, la superficie nueva es una tabla y dos
columnas de referencia dentro del mismo mecanismo de repositorio ya
scopeado por tenant.

## Accesibilidad (WCAG)

Pantalla nueva (`PantallaDeTasas`): mismo estándar ya validado en el
resto del frontend — `<label htmlFor>` explícito en todos los campos,
foco programático en el `h1` al entrar a cada vista interna, foco en el
mensaje de error al fallar una búsqueda o un pago (`role="alert"`),
resultado exitoso anunciado con `role="status"` y foco, estado de cada
tasa (pendiente/pagada) comunicado con texto además de con color. La
vista de simulador de pago en particular no puede depender solo de color
para distinguir "Aprobar" de "Rechazar", y el rótulo "entorno de prueba"
tiene que ser texto real, no una imagen ni un ícono sin texto alternativo.

## Fuera de alcance (explícitamente diferido, ver ADR 0018)

- Integración con un proveedor real de pasarela de pago (Mercado Pago,
  Modo, PagoFácil/Rapipago) y sus credenciales.
- Verificación de firma de webhook (no aplica al adaptador simulado).
- Padrón de contribuyentes real: el número de cuenta es un identificador
  simple sembrado por el municipio, sin relación con ningún dato
  catastral o de persona.
- Panel de gestión de tasas para el municipio más allá del alta
  (listado completo, edición, baja, exención, plan de pagos/moratoria).
- Notificación de vencimientos o de confirmación de pago al vecino
  (motor de notificaciones, ADR 0013) — ninguna integración transversal
  de auditoría/notificaciones en esta rebanada, mismo criterio que
  R6-R12.
- Rate limiting sobre los tres endpoints públicos nuevos.
- Conciliación contable / Tesorería (Fase 3).
- Redirección real del navegador a una URL de pasarela (`urlDePago` no
  se usa en esta rebanada — ver ADR 0018 §3).
- Capa de adaptadores a sistemas legados de recaudación (AFIP/ARBA o
  equivalente provincial): no es parte de esta rebanada, y el roadmap no
  la exige todavía para que el pago de una tasa funcione de punta a
  punta.

## Instrucción para los agentes implementadores

**No hagan commit, push, ni abran PR.** Dejen los cambios en el árbol de
trabajo sin commitear. El tech lead arma el commit, pushea la rama y abre
el PR contra `develop` una vez que backend, frontend y la auditoría estén
completos. Si el trabajo se corta por límite de sesión o cualquier otro
motivo, no reintenten commitear/pushear por su cuenta al retomar: avisen
el estado en el que quedó y esperen instrucción.
