# CD-19 · R11 — Transparencia activa básica: presupuesto y escala salarial públicos

Rama: `CD-19-transparencia-activa-basica` (desde `develop`).

Sin ADR nuevo: esta rebanada reutiliza tal cual el mecanismo de lectura
pública/escritura protegida que ya cubren
[ADR 0011](../docs/arquitectura/decisiones/0011-autorizacion-por-roles-con-permisos-granulares.md)
(permisos) y
[ADR 0012](../docs/arquitectura/decisiones/0012-declaracion-de-modulos-y-gating-por-ruta.md)
§1 (`rutasDeLecturaPublica()`) — mismo patrón que `boletin` (R7) y
`cementerio` (R8): lectura pública sin sesión, escritura protegida por
sesión + permiso. Sin estado ni transiciones: publicar un dato de
transparencia es un alta y listo, igual que `cementerio`.

Ver la entrada de R11 en el
[backlog](../docs/producto/backlog-inicial.md) para el contexto completo
de por qué esta rebanada usa datos sembrados/de ejemplo en vez de esperar
un municipio piloto, y el razonamiento detrás de la decisión de
minimización de datos en sueldos. Esta spec no repite ese contexto, solo
lo aplica.

## Demo

Un agente municipal, **con sesión** y el permiso `transparencia.publicar`
(rol `administrador` únicamente), publica una partida presupuestaria
(año, área, número, concepto, monto asignado y, opcionalmente, monto
ejecutado) y una entrada de escala salarial (año, área, cargo/función,
cantidad de cargos y monto bruto mensual **por cargo**, sin nombre de
ninguna persona). Un vecino, **sin sesión**, entra al portal público de
ese municipio, ve ambos listados, filtra por año y por texto, y encuentra
los datos recién publicados. El mismo dato **no** aparece en el portal de
otro municipio.

## Decisión de minimización de datos en sueldos (no requiere ADR)

Documentada acá porque condiciona el modelo de datos, no solo la
respuesta de la API (a diferencia de `cementerio`, donde el dato privado
sí se guarda pero se oculta en la búsqueda pública): **la tabla de escala
salarial no tiene ninguna columna que identifique a una persona.** Se
publica cuánto gana en bruto cada cargo/función y cuántos cargos de ese
tipo hay, nunca un monto atado a un nombre. Esto es deliberado y no se
resuelve "ocultando un campo después": el campo simplemente no existe en
el modelo de esta rebanada. Sueldos de funcionarios nombrados
individualmente queda fuera de alcance (ver más abajo).

`quien publicó` el registro (el agente municipal que hizo el `POST`) sí
se identifica en la respuesta (`publicadoPorNombre`/`publicadoPorEmail`):
es la firma pública del acto administrativo de publicar, mismo criterio
que `norma.publicado_por_*` en `boletin` (ADR 0013, "copia no
referencia"). No es el mismo caso que el dato salarial de un tercero: acá
el nombre público es el de un funcionario en ejercicio de su función
oficial de publicar, no el de la persona cuyo sueldo se está informando
(que en esta rebanada no existe como tal, solo existe el cargo).

## Qué se construye

### Backend — Tarea única (bloqueante, un solo agente `backend`)

Seguir el mismo esqueleto de paquete/archivos que `boletin` y `cementerio`
(`backend/src/main/java/ar/com/ciudaddigital/boletin/internal/`,
`.../cementerio/internal/`): un `Controller`, un servicio de gestión, dos
entidades (una por recurso), dos repositorios, y su propia excepción
`SolicitudInvalida` package-private (no compartida entre módulos, mismo
criterio que ya usan `reclamos`/`boletin`/`cementerio`).

**1. Paquete `ar.com.ciudaddigital.transparencia`**

- `package-info.java` (copiar el estilo del de `boletin`/`cementerio`,
  explicando que es el complemento del mismo patrón lectura
  pública/escritura protegida, sin mecanismo nuevo).

- `transparencia.internal.DescriptorDelModuloTransparencia` implementa
  `DescriptorDeModulo`:
  - `codigo()` = `"transparencia"`.
  - `nombre()` = `"Transparencia Activa"`.
  - `descripcion()`: algo como "Publicación de presupuesto (partidas y
    montos) y escala salarial (cargos y montos, sin datos de personas)
    del municipio, con consulta pública."
  - `prefijosDeApi()` = `List.of("/api/transparencia")`.
  - `rutasDeLecturaPublica()` = `List.of("/api/transparencia/presupuesto",
    "/api/transparencia/sueldos")` — los dos listados son públicos, no hay
    detalle por id en esta rebanada.
  - Sin `rutasDeEscrituraPublica()`: publicar requiere sesión, así que no
    se declara (default vacío).

- `transparencia.internal.PartidaPresupuestariaEntity`: entidad JPA
  mapeada a la tabla `partida_presupuestaria` (ver DDL abajo), con método
  factory estático `nueva(...)` (mismo patrón que `NormaEntity.nueva(...)`).

- `transparencia.internal.EscalaSalarialEntity`: entidad JPA mapeada a la
  tabla `escala_salarial` (ver DDL abajo), mismo patrón `nueva(...)`.

- `transparencia.internal.PartidaPresupuestariaRepository extends
  JpaRepository`, con `List<PartidaPresupuestariaEntity> buscar(Integer
  anio, String patronDeTexto)` vía `@Query` (mismo patrón que
  `NormaRepository#buscar`: el patrón `ILIKE` ya viene armado desde el
  servicio, y acá además hay un filtro exacto opcional por `anio` — nulo
  significa "sin filtro de año"). El texto busca en `area` **o**
  `concepto`. Orden: `anio desc, creado_en desc`.

- `transparencia.internal.EscalaSalarialRepository extends
  JpaRepository`, con `List<EscalaSalarialEntity> buscar(Integer anio,
  String patronDeTexto)` mismo criterio, buscando en `area` **o** `cargo`.
  Mismo orden: `anio desc, creado_en desc`.

- `transparencia.internal.GestionDeTransparencia` (`@Service`), con
  cuatro métodos, todos `@Transactional("tenantTransactionManager")`
  los de escritura:
  - `publicarPartida(Integer anio, String area, String numeroPartida,
    String concepto, BigDecimal montoAsignado, BigDecimal montoEjecutado,
    String publicadoPorNombre, String publicadoPorEmail)`. Validaciones
    (mismo estilo defensivo que `GestionDelBoletin`/`GestionDelCementerio`,
    límites de largo explícitos, mensajes en español):
    - `anio` requerido, entre 2000 y 2100 (sanity check de dominio, ajustar
      el rango si al implementar aparece un criterio mejor, pero que
      exista alguno).
    - `area` requerido, máx. 150 caracteres.
    - `numeroPartida` requerido, máx. 50 caracteres.
    - `concepto` requerido, máx. 300 caracteres.
    - `montoAsignado` requerido, no puede ser negativo.
    - `montoEjecutado` opcional; si viene, no puede ser negativo. No se
      valida contra `montoAsignado` (un municipio puede ejecutar por
      encima de lo asignado con una modificación presupuestaria; no es
      esta rebanada la que arbitra esa regla).
    - `publicadoPorNombre`/`publicadoPorEmail` salen del actor
      autenticado, no de la solicitud (mismo comentario que
      `GestionDelBoletin.publicar` sobre por qué no llevan los mismos
      mensajes de `SolicitudInvalida`).
  - `buscarPartidas(Integer anio, String texto)`: arma el patrón `ILIKE`
    igual que `GestionDelBoletin.buscar` (vacío/blank = sin filtro de
    texto).
  - `publicarCargo(Integer anio, String area, String cargo, Integer
    cantidadCargos, BigDecimal montoBrutoMensual, String
    publicadoPorNombre, String publicadoPorEmail)`. Validaciones:
    - `anio`: mismo rango que arriba.
    - `area` requerido, máx. 150 caracteres.
    - `cargo` requerido, máx. 200 caracteres.
    - `cantidadCargos` opcional en el request; si no viene, default `1`;
      si viene, tiene que ser mayor a cero.
    - `montoBrutoMensual` requerido, no puede ser negativo.
    - `publicadoPorNombre`/`publicadoPorEmail`: mismo criterio que arriba.
  - `buscarCargos(Integer anio, String texto)`: mismo criterio que
    `buscarPartidas`.

- `transparencia.internal.TransparenciaController` (`@RequestMapping
  "/api/transparencia"`):
  - `@PostMapping("/presupuesto")`,
    `@PreAuthorize("hasAuthority('transparencia.publicar')")`. Toma el
    actor autenticado del `Authentication` igual que
    `BoletinController.publicar`/`CementerioController.registrar` (mismo
    bloque `ActorAutenticado`/`IllegalStateException`). Responde `201` con
    `PartidaPresupuestariaResponse` completo (sin DTO reducido: no hay
    dato de tercero que ocultar en este recurso, mismo criterio que
    `NormaResponse` en `boletin`, a diferencia de `cementerio`).
  - `@GetMapping("/presupuesto")`, sin `@PreAuthorize` (ruta de lectura
    pública, mismo comentario Javadoc que `BoletinController.buscar`).
    Parámetros opcionales `anio` (entero) y `q` (texto). Responde
    `List<PartidaPresupuestariaResponse>`.
  - `@PostMapping("/sueldos")`,
    `@PreAuthorize("hasAuthority('transparencia.publicar')")`. Responde
    `201` con `EscalaSalarialResponse` completo.
  - `@GetMapping("/sueldos")`, sin `@PreAuthorize`. Parámetros opcionales
    `anio` y `q`. Responde `List<EscalaSalarialResponse>`.
  - Mismo `@ExceptionHandler(SolicitudInvalida.class)` → 400, mismo
    `ErrorResponse` record que `boletin`/`cementerio`.
  - Records: `PublicarPartidaRequest`, `PartidaPresupuestariaResponse`,
    `PublicarCargoRequest`, `EscalaSalarialResponse` — un único DTO de
    salida por recurso (no dos como en `cementerio`), porque no hay dato
    de tercero que minimizar en la respuesta pública de ninguno de los
    dos recursos.

**2. Migración `V11__crear_transparencia.sql`** en
`backend/src/main/resources/db/tenant/`:

```sql
-- Transparencia activa básica: presupuesto (partidas y montos) y escala
-- salarial (cargos y montos, sin datos de personas) del municipio, con
-- consulta pública (backlog R11).
--
-- Sin columna de tenant: vive en la base del municipio, igual que norma
-- (V7) y sepultura (V8).
create table partida_presupuestaria (
    id                    bigint generated always as identity primary key,
    anio                  integer      not null
        check (anio between 2000 and 2100),
    area                  varchar(150) not null,
    -- Texto libre que asigna el municipio, mismo criterio que "numero" en
    -- norma (V7): la nomenclatura presupuestaria oficial (ej. RAFAM) es
    -- un problema de un municipio piloto real, no de esta rebanada.
    numero_partida        varchar(50)  not null,
    concepto              varchar(300) not null,
    monto_asignado        numeric(14,2) not null check (monto_asignado >= 0),
    -- Opcional: no todos los municipios llevan la ejecución al día.
    monto_ejecutado       numeric(14,2) check (monto_ejecutado is null or monto_ejecutado >= 0),
    -- Copia del actor al momento de publicar, no una relación con
    -- usuario: mismo criterio que publicado_por_nombre/email en norma
    -- (V7, ADR 0013). Es la firma pública del acto de publicar, no un
    -- dato de un tercero.
    publicado_por_nombre  varchar(150) not null,
    publicado_por_email   varchar(200) not null,
    creado_en             timestamptz  not null default now()
    -- Sin estado ni columnas de edición: un registro publicado no se
    -- edita ni se borra por esta rebanada, mismo criterio que norma.
);

create index partida_presupuestaria_anio_idx on partida_presupuestaria (anio desc, creado_en desc);

comment on table partida_presupuestaria is
    'Partidas presupuestarias publicadas por este municipio en Transparencia Activa (backlog R11).';

-- Escala salarial: cargo/función y monto, NUNCA una persona. A
-- diferencia de sepultura (V8), donde el dato privado se guarda y se
-- oculta en la búsqueda pública, acá el dato de persona directamente no
-- existe como columna: es una decisión de modelo, no de presentación
-- (ver la spec de R11 para el razonamiento completo).
create table escala_salarial (
    id                    bigint generated always as identity primary key,
    anio                  integer      not null
        check (anio between 2000 and 2100),
    area                  varchar(150) not null,
    cargo                 varchar(200) not null,
    cantidad_cargos       integer      not null default 1 check (cantidad_cargos > 0),
    monto_bruto_mensual   numeric(14,2) not null check (monto_bruto_mensual >= 0),
    publicado_por_nombre  varchar(150) not null,
    publicado_por_email   varchar(200) not null,
    creado_en             timestamptz  not null default now()
);

create index escala_salarial_anio_idx on escala_salarial (anio desc, creado_en desc);

comment on table escala_salarial is
    'Escala salarial por cargo/función publicada por este municipio en Transparencia Activa, sin datos de personas (backlog R11).';

-- Catálogo de permisos: área "Transparencia". Igual criterio que
-- boletin.publicar (V7): publicar presupuesto o escala salarial es un
-- acto de transparencia institucional del municipio, de mayor
-- sensibilidad que la operación diaria de reclamos/cementerio — se
-- asigna SOLO a administrador.
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('transparencia.publicar', 'Transparencia', 'transparencia', 'publicar',
     'Publicar una partida presupuestaria o una entrada de escala salarial en Transparencia Activa.');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo
from rol r, permiso p
where r.codigo = 'administrador'
  and p.codigo = 'transparencia.publicar';
```

Ajustar el DDL de arriba si al implementar aparece algo que no encaje con
las convenciones reales del proyecto (nombres de columnas, tipos), pero
mantener las decisiones: sin columna de tenant, sin columna de persona en
`escala_salarial`, permiso solo en `administrador`, montos no negativos.

**3. Test de integración `TransparenciaTest.java`**

En `backend/src/test/java/ar/com/ciudaddigital/transparencia/`, calcado
de `BoletinTest.java` (mismo `SoporteDeIntegracion`, dos municipios de
prueba nuevos —elegir dos que no se usen ya en otras clases de test, p.
ej. `lomas`/`quilmes`—, mismo helper `fijarModulos`). Casos obligatorios,
cubriendo **ambos** recursos (presupuesto y sueldos):

1. Publicar una partida con el módulo contratado y el permiso → 201, con
   el registro completo. Sin el módulo → 403 `MODULO_NO_CONTRATADO`,
   aunque haya sesión y permiso.
2. Publicar una partida con un usuario del rol `agente` (que **no** tiene
   `transparencia.publicar`) → 403 sin código (mismo patrón que
   `BoletinTest.publicacionSinElPermisoSeRechaza`).
3. Publicar una partida inválida (sin `area`, con `montoAsignado`
   negativo, o con `anio` fuera de rango) → 400.
4. Lectura pública de presupuesto sin sesión, con el módulo contratado,
   devuelve lo publicado (incluye `publicadoPorNombre`/`publicadoPorEmail`
   en el JSON, a diferencia de `cementerio`). Sin el módulo, 403 aun sin
   sesión.
5. Filtros de presupuesto: por `anio` y por `q` en área/concepto.
6. Publicar una entrada de escala salarial con el módulo y el permiso →
   201. Verificar explícitamente que la respuesta **no** tiene ningún
   campo de nombre de persona (no debería existir el campo siquiera, pero
   confirmar que el JSON solo trae `cargo`/`area`/montos/`publicadoPor*`).
   Con `agente` → 403. Inválida (`cargo` vacío, `montoBrutoMensual`
   negativo, `cantidadCargos` en cero) → 400.
7. Lectura pública de sueldos sin sesión, con filtros por `anio` y `q` en
   área/cargo.
8. **Aislamiento entre tenants**: una partida y una entrada de escala
   salarial publicadas en un municipio no aparecen en la búsqueda del
   otro (mismo patrón que `BoletinTest.aislamientoEntreTenants`, con
   `concepto`/`cargo` con sufijo `UUID.randomUUID()` para no chocar con
   filas de otros tests de la misma clase).

No hace falta test de gating adicional en `EntitlementDeModulosTest`: ese
test sigue usando `ejemplo`/`reclamos` como sujetos, no se toca en esta
rebanada.

### Frontend — Tarea única (después del backend, un solo agente `frontend`)

- `frontend/src/modulos/transparencia/PantallaDeTransparencia.tsx`,
  calcada estructuralmente de `PantallaDeCementerio.tsx` (mismo
  componente `PropsDePantallaDeModulo`, mismo patrón `vigente`/`useRef`,
  mismo manejo de `ErrorDeApi`/`MODULO_NO_CONTRATADO`, mismo patrón de
  foco en el `h1` al montar, foco en el primer campo al abrir cada
  formulario y en el error al fallar el envío):
  - Una única pantalla con **dos secciones independientes**, cada una con
    su propio estado de búsqueda/listado/formulario (no comparten estado
    entre sí, son dos mini-flujos como `cementerio` duplicados dentro del
    mismo componente):
    - **Sección "Presupuesto"**: formulario de búsqueda con `input` de
      año (`type="number"`, opcional) y `input` de texto libre ("Buscar
      por área o concepto"), botón "Buscar". Tabla de resultados con
      columnas Año, Área, Número de partida, Concepto, Monto asignado,
      Monto ejecutado (mostrar `—` si es `null`), con `<caption>`. Si
      `usuario?.permisos.includes('transparencia.publicar')`, debajo un
      botón "Publicar partida" que abre un formulario con los campos
      `anio` (`type="number"`, requerido), `area` (requerido),
      `numeroPartida` (requerido), `concepto` (requerido), `montoAsignado`
      (`type="number"`, requerido), `montoEjecutado` (`type="number"`,
      opcional).
    - **Sección "Sueldos (escala salarial)"**: formulario de búsqueda con
      `input` de año (opcional) y texto libre ("Buscar por área o
      cargo"), botón "Buscar". Tabla de resultados con columnas Año,
      Área, Cargo, Cantidad de cargos, Monto bruto mensual, con
      `<caption>` que aclare explícitamente que son montos **por cargo,
      no por persona** (accesible también para quien no lee el resto de
      la pantalla, ej. con lector de pantalla que solo anuncia el
      `<caption>` de la tabla). Si tiene el permiso, botón "Publicar
      cargo" con formulario: `anio` (requerido), `area` (requerido),
      `cargo` (requerido), `cantidadCargos` (`type="number"`, opcional,
      default 1 en el formulario), `montoBrutoMensual` (`type="number"`,
      requerido).
  - Formatear montos con `Intl.NumberFormat('es-AR', { style: 'currency',
    currency: 'ARS' })` (nuevo formatter local al componente, no existe
    todavía en el resto del frontend; si el agente `frontend` encuentra
    uno ya armado en otro lugar, reusarlo en vez de duplicar).
  - Cada campo con su `<label htmlFor>` explícito, `aria-invalid` y
    `aria-describedby` apuntando al mensaje de error cuando corresponda,
    mensajes de error con `role="alert"` y foco programático al aparecer,
    estado de carga con `role="status"` — mismo estándar ya usado en
    `PantallaDeBoletin`/`PantallaDeCementerio`, no hay componente ni
    patrón nuevo que inventar acá.
  - Al publicar (cualquiera de los dos formularios), `POST` al endpoint
    correspondiente, recargar la búsqueda vigente de esa sección y cerrar
    el formulario devolviendo el foco al botón que lo abrió (mismo patrón
    que `cementerio`).

- Registrar el componente en `frontend/src/modulos/registro.ts`:
  `transparencia: PantallaDeTransparencia` (import + entrada en el
  record), sin tocar las entradas existentes.

- No hace falta tocar `Navegacion.tsx`/`CatalogoDeModulos.tsx`/`App.tsx`:
  arman la navegación a partir de `registroDePantallasDeModulo` +
  `useModulos` sin necesitar conocer módulos nuevos por nombre —
  confirmarlo al implementar; si hiciera falta tocar algo ahí, avisar
  antes de improvisar (señal de que algo no sigue ADR 0012).

## Aislamiento entre tenants

Cubierto por el test 8 de la tarea de backend (arriba), para ambos
recursos. No hay nada adicional del lado del frontend: cada portal solo
pega contra su propio subdominio (mismo mecanismo que el resto de los
módulos).

## Accesibilidad (WCAG)

Cubierta por seguir al pie de la letra los patrones ya validados de
`PantallaDeCementerio.tsx`/`PantallaDeBoletin.tsx` (foco, labels,
`aria-*`, roles de estado/alerta, tablas con `<caption>` y encabezados de
columna con `scope="col"`/`scope="row"` donde corresponda). Atención
particular al `<caption>` de la tabla de sueldos: tiene que dejar claro
por sí solo que son montos por cargo, no por persona — no es solo
prosa de la pantalla, es parte de cómo se entiende el dato con lector de
pantalla. No hay pantalla de tipo distinto a lo ya construido: son dos
secciones del mismo patrón (búsqueda pública + formulario protegido
condicional) que `boletin`/`cementerio`, aplicado a dos dominios de datos
en la misma vista.

## Fuera de alcance (explícitamente diferido)

- Sueldos vinculados a funcionarios nombrados individualmente (ver la
  decisión de minimización de datos, arriba): esta rebanada no tiene
  ninguna columna ni endpoint que acepte un nombre de persona asociado a
  un monto.
- Licitaciones abiertas y declaraciones juradas: el catálogo funcional
  los agrupa bajo "Transparencia activa" junto con presupuesto/sueldos,
  pero son datos de forma completamente distinta (licitaciones depende de
  Compras/Contrataciones, Fase 3) y no entran en esta rebanada.
- Seguimiento temporal de ejecución presupuestaria (gráficos, series por
  mes), reconciliación entre lo asignado y lo ejecutado.
- Adjuntos/documentos (presupuesto en PDF oficial, recibos de sueldo).
- Motor de búsqueda full-text, paginado del listado.
- Edición o borrado de un registro ya publicado: se corrige publicando
  uno nuevo, mismo criterio que `boletin`.
- Cualquier integración con auditoría/notificaciones transversal (ADR
  0013) más allá de lo que este propio módulo cubre.
- Rate limiting/anti-abuso: no aplica, no hay escritura pública en este
  módulo.

## Instrucción para los agentes implementadores

**No hagan commit, push, ni abran PR.** Dejen los cambios en el árbol de
trabajo sin commitear. El tech lead arma el commit, pushea la rama y abre
el PR contra `develop` una vez que backend, frontend y la auditoría estén
completos. Si el trabajo se corta por límite de sesión o cualquier otro
motivo, no reintenten commitear/pushear por su cuenta al retomar: avisen
el estado en el que quedó y esperen instrucción.
