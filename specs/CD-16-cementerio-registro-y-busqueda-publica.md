# CD-16 · R8 — Un vecino busca dónde está sepultado un familiar y el municipio administra el registro del cementerio

Rama: `CD-16-cementerio-registro-y-busqueda-publica` (desde `develop`).

Sin ADR nuevo: esta rebanada reutiliza tal cual el mecanismo de lectura
pública protegida/escritura protegida que ya cubren
[ADR 0011](../docs/arquitectura/decisiones/0011-autorizacion-por-roles-con-permisos-granulares.md)
(permisos) y
[ADR 0012](../docs/arquitectura/decisiones/0012-declaracion-de-modulos-y-gating-por-ruta.md)
§1 (`rutasDeLecturaPublica()`) — el mismo patrón que ya usa `boletin` (R7,
[backlog](../docs/producto/backlog-inicial.md#r7--el-municipio-publica-una-norma-en-el-boletín-oficial-y-cualquiera-la-encuentra)):
lectura pública sin sesión, escritura protegida por sesión + permiso. No
hay escritura anónima (a diferencia de `reclamos`/ADR 0014) ni nada que
requiera un motor de workflow: el registro de una sepultura no tiene
estados ni transiciones, es un alta y listo — más simple todavía que
`reclamos`.

## Demo

Un vecino, **sin sesión**, entra al portal público de un municipio, busca
por el nombre de un familiar fallecido y encuentra dónde está sepultado
(tipo de parcela, sector, fila, número). Un agente municipal, **con
sesión** y el permiso `cementerio.registrar`, carga un nuevo registro de
inhumación con los datos de la parcela, el difunto, la fecha y
(opcionalmente) el titular de la concesión. El registro recién cargado
aparece de inmediato en la búsqueda pública. El mismo registro **no**
aparece en el portal de otro municipio.

## Qué se construye

### Backend — Tarea única (bloqueante, un solo agente `backend`)

Seguir el mismo esqueleto de paquete/archivos que `boletin`
(`backend/src/main/java/ar/com/ciudaddigital/boletin/internal/`): un
`Controller`, un servicio de gestión, una entidad, un repositorio, un enum
de tipo, y su propia excepción `SolicitudInvalida` package-private (no
compartida entre módulos, mismo criterio que ya usan `reclamos` y
`boletin`).

**1. Paquete `ar.com.ciudaddigital.cementerio`**

- `package-info.java` (copiar el estilo del de `boletin`).
- `cementerio.internal.DescriptorDelModuloCementerio` implementa
  `DescriptorDeModulo`:
  - `codigo()` = `"cementerio"`.
  - `nombre()` = `"Cementerio"`.
  - `descripcion()`: algo como "Registro de sepulturas del cementerio
    municipal: parcelas, nichos y panteones, con búsqueda pública por
    nombre del difunto."
  - `prefijosDeApi()` = `List.of("/api/cementerio")`.
  - `rutasDeLecturaPublica()` = `List.of("/api/cementerio")` — solo el
    listado/búsqueda es público, no hay detalle por id en esta rebanada
    (mismo criterio que `boletin`).
  - Sin `rutasDeEscrituraPublica()`: el alta requiere sesión, así que no
    se declara (default vacío).

- `cementerio.internal.TipoDeParcela`: enum `NICHO`, `PANTEON`, `PARCELA`,
  `BOVEDA`.

- `cementerio.internal.SepulturaEntity`: entidad JPA mapeada a la tabla
  `sepultura` (ver DDL abajo), con método factory estático `nueva(...)`
  (mismo patrón que `NormaEntity.nueva(...)`).

- `cementerio.internal.SepulturaRepository extends JpaRepository`, con un
  método de búsqueda `List<SepulturaEntity> buscar(TipoDeParcela tipo,
  String patronNombreDifunto)` con `@Query` (mismo patrón que
  `NormaRepository#buscar`: el patrón `ILIKE` ya viene armado desde el
  servicio, el repositorio no decide si hay filtro o no). Orden de
  resultados: `nombreDifunto` ascendente (a diferencia de `boletin`/
  `reclamos` que ordenan por fecha; acá tiene más sentido alfabético,
  como una guía telefónica, porque la búsqueda típica es "por apellido").

- `cementerio.internal.GestionDelCementerio` (`@Service`):
  - `registrar(TipoDeParcela tipo, String sector, String fila, String
    numero, String nombreDifunto, LocalDate fechaFallecimiento, LocalDate
    fechaInhumacion, String nombreTitular, String contactoTitular, String
    observaciones, String registradoPorNombre, String
    registradoPorEmail)`, `@Transactional("tenantTransactionManager")`.
    Validaciones (mismo estilo defensivo que `GestionDelBoletin`, con
    límites de largo explícitos y mensajes en español):
    - `tipo` requerido.
    - `sector` requerido, máx. 100 caracteres.
    - `fila` opcional, máx. 50 caracteres.
    - `numero` requerido, máx. 50 caracteres.
    - `nombreDifunto` requerido, máx. 200 caracteres.
    - `fechaFallecimiento` requerida.
    - `fechaInhumacion` requerida, y no puede ser **anterior** a
      `fechaFallecimiento` (validación de dominio: no se puede sepultar
      antes de fallecer). Rechazar con `SolicitudInvalida` si lo es.
    - `nombreTitular` opcional, máx. 200 caracteres.
    - `contactoTitular` opcional, máx. 200 caracteres.
    - `observaciones` opcional, sin límite adicional de servicio (columna
      `text`, mismo criterio que el `texto` de `NormaEntity`: sin cap
      explícito).
    - `registradoPorNombre`/`registradoPorEmail` salen del actor
      autenticado, no de la solicitud: mismo comentario que
      `GestionDelBoletin.publicar` sobre por qué no llevan los mismos
      mensajes de `SolicitudInvalida` que el resto (si faltaran sería un
      problema del mecanismo de autenticación, no una solicitud inválida
      del agente).
  - `buscar(TipoDeParcela tipo, String textoEnNombreDifunto)`: arma el
    patrón `ILIKE` igual que `GestionDelBoletin.buscar` (vacío/blank =
    sin filtro de texto, no búsqueda del string vacío) y devuelve
    `List<SepulturaEntity>`.

- `cementerio.internal.CementerioController` (`@RequestMapping
  "/api/cementerio"`):
  - `POST` sin ruta adicional, `@PreAuthorize("hasAuthority('cementerio.registrar')")`.
    Toma el actor autenticado del `Authentication` igual que
    `BoletinController.publicar` (mismo bloque de
    `ActorAutenticado`/`IllegalStateException` si no hay principal
    autenticado — no debería pasar nunca, mismo comentario). Responde
    `201` con el registro **completo**, incluidos `nombreTitular`,
    `contactoTitular`, `observaciones`, `registradoPorNombre`,
    `registradoPorEmail`: quien acaba de cargar el registro tiene que ver
    lo que cargó.
  - `GET` sin ruta adicional, sin `@PreAuthorize` (ruta de lectura
    pública, mismo comentario Javadoc que `BoletinController.buscar`
    explicando por qué no lleva anotación). Parámetros opcionales
    `tipoParcela` y `q`. Responde una lista con un DTO **público**,
    reducido: `id`, `tipo`, `sector`, `fila`, `numero`, `nombreDifunto`,
    `fechaFallecimiento`, `fechaInhumacion`, `creadoEn`. **No** incluye
    `nombreTitular`, `contactoTitular`, `observaciones`,
    `registradoPorNombre` ni `registradoPorEmail`: son datos de terceros
    (titular, agente municipal) que no hace falta exponer para que un
    vecino encuentre una sepultura, y este mismo endpoint lo sirve
    también a quien tiene sesión (es la ruta pública declarada en el
    descriptor, no hay una segunda ruta protegida con más datos en esta
    rebanada — ver "Fuera de alcance"). Documentarlo con un comentario
    explícito en el controller, mismo estilo que el resto del código:
    esto es una decisión deliberada de minimización de datos, no un
    olvido.
  - Mismo `@ExceptionHandler(SolicitudInvalida.class)` → 400, mismo
    `ErrorResponse` record que `boletin`/`reclamos`.
  - Records: uno para el request de alta (`RegistrarSepulturaRequest`),
    uno para la respuesta completa del alta (`SepulturaCompletaResponse`),
    y uno para la respuesta pública del listado (`SepulturaPublicaResponse`)
    — dos DTOs de salida distintos a propósito, no uno solo con campos
    nulleables.

**2. Migración `V8__crear_cementerio.sql`** en `backend/src/main/resources/db/tenant/`:

```sql
create table sepultura (
    id                     bigint generated always as identity primary key,
    tipo_parcela           varchar(20)  not null
        check (tipo_parcela in ('NICHO', 'PANTEON', 'PARCELA', 'BOVEDA')),
    sector                 varchar(100) not null,
    fila                   varchar(50),
    numero                 varchar(50)  not null,
    nombre_difunto         varchar(200) not null,
    fecha_fallecimiento    date         not null,
    fecha_inhumacion       date         not null,
    -- Titular de la concesión y su contacto: privados, no se exponen en
    -- la búsqueda pública (minimización de datos de terceros vivos). Solo
    -- se devuelven en la respuesta del alta, a quien lo acaba de cargar.
    nombre_titular         varchar(200),
    contacto_titular       varchar(200),
    observaciones          text,
    -- Copia del actor al momento de registrar, no una relación con
    -- usuario: mismo criterio que publicado_por_nombre/email en norma
    -- (V7, ADR 0013).
    registrado_por_nombre  varchar(150) not null,
    registrado_por_email   varchar(200) not null,
    creado_en              timestamptz  not null default now()
    -- Sin estado ni motor de workflow: registrar una sepultura no tiene
    -- transiciones, es un alta y listo (a diferencia de reclamo, V6).
    -- Sin columna de tenant: vive en la base del municipio, igual que
    -- reclamo (V6) y norma (V7).
);

-- La búsqueda pública es principalmente por nombre del difunto
-- (alfabética, como una guía telefónica); a diferencia de
-- reclamo_creado_en_idx (V6) y norma_fecha_publicacion_idx (V7), acá el
-- orden natural no es temporal.
create index sepultura_nombre_difunto_idx on sepultura (nombre_difunto);

comment on table sepultura is
    'Registros de inhumación del cementerio municipal de este municipio (backlog R8).';

-- Catálogo de permisos: área "Cementerio". Igual criterio que
-- reclamos.ver/reclamos.gestionar (V6): es funcionalidad operativa real
-- que el personal del cementerio necesita desde el día uno, no un acto
-- legal como publicar una norma (boletin.publicar, V7, solo
-- administrador) — se asigna a AMBOS roles de sistema.
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('cementerio.registrar', 'Cementerio', 'cementerio', 'registrar',
     'Registrar una sepultura (inhumación) en el cementerio municipal.');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo
from rol r, permiso p
where r.codigo in ('administrador', 'agente')
  and p.codigo = 'cementerio.registrar';
```

Ajustar el DDL de arriba si al implementar aparece algo que no encaje con
las convenciones reales del proyecto (nombres de columnas, tipos), pero
mantener las decisiones: sin columna de tenant, `check` para el enum,
campos privados nulleables, permiso en ambos roles.

**3. Test de integración `CementerioTest.java`**

En `backend/src/test/java/ar/com/ciudaddigital/cementerio/`, calcado de
`BoletinTest.java` (mismo `SoporteDeIntegracion`, mismos dos municipios de
prueba `tandil`/`olavarria`, mismo helper `fijarModulos`, mismo helper
`crearAgenteYLoguear` si hace falta). Casos obligatorios:

1. Alta con el módulo contratado y el permiso → 201, con el registro
   completo (incluye `nombreTitular`/`observaciones` en la respuesta).
   Sin el módulo contratado → 403 `MODULO_NO_CONTRATADO`, aunque haya
   sesión y permiso.
2. Alta con un usuario del rol `agente` (que sí tiene
   `cementerio.registrar`, a diferencia del caso de `boletin.publicar`)
   → 201. Esto es lo contrario de `BoletinTest.publicacionSinElPermisoSeRechaza`:
   acá hace falta un caso que **confirme** que `agente` puede, no que lo
   rechace — es el punto central de la decisión "ambos roles".
3. Alta inválida (sin `numero`, o con `fechaInhumacion` anterior a
   `fechaFallecimiento`, o con `tipoParcela` inexistente) → 400.
4. Lectura pública sin sesión, con el módulo contratado, devuelve lo
   registrado **sin** `nombreTitular`/`contactoTitular`/`observaciones`/
   `registradoPorNombre`/`registradoPorEmail` en el JSON de respuesta
   (verificar explícitamente con `jsonPath(...).doesNotExist()` sobre esos
   campos, no solo que el status sea 200). Sin el módulo, 403
   `MODULO_NO_CONTRATADO` aun sin sesión.
5. Filtros: por `tipoParcela` y por `q` en `nombreDifunto`.
6. **Aislamiento entre tenants**: una sepultura registrada en un
   municipio no aparece en la búsqueda del otro (mismo patrón que
   `BoletinTest.aislamientoEntreTenants`, con `nombreDifunto` con sufijo
   `UUID.randomUUID()` para no chocar con filas de otros tests de la
   misma clase).

No hace falta test de gating adicional en `EntitlementDeModulosTest`: ese
test sigue usando `ejemplo`/`reclamos` como sujetos, no se toca en esta
rebanada (mismo criterio que R7 no lo tocó).

### Frontend — Tarea única (después del backend, un solo agente `frontend`)

- `frontend/src/modulos/cementerio/PantallaDeCementerio.tsx`, calcada
  estructuralmente de `PantallaDeBoletin.tsx` (mismo componente
  `PropsDePantallaDeModulo`, mismo patrón de `vigente`/`useRef` para
  evitar `setState` después de desmontar, mismo manejo de
  `ErrorDeApi`/`MODULO_NO_CONTRATADO`, mismo patrón de foco en el `h1` al
  montar, mismo patrón de foco en el primer campo al abrir el formulario y
  en el error al fallar el envío):
  - Una única vista, igual que `boletin`: búsqueda pública arriba (visible
    para cualquiera) y, si `usuario?.permisos.includes('cementerio.registrar')`,
    una sección "Registrar sepultura" debajo con el formulario de alta,
    oculta para quien no tiene el permiso (comodidad de UI, el backend
    vuelve a exigirlo — ADR 0011).
  - Formulario de búsqueda: `select` de tipo de parcela (`Todos`, Nicho,
    Panteón, Parcela, Bóveda) + `input` de texto libre ("Buscar por
    nombre del difunto"), botón "Buscar". Tabla de resultados con
    columnas: Tipo de parcela, Sector, Fila, Número, Nombre del difunto,
    Fecha de fallecimiento, Fecha de inhumación — con `<caption>`
    explicando qué se ve, mismo patrón que la tabla de `boletin`.
  - Formulario de alta (dentro de un `<section>` propio, con su propio
    `h2`): campos `tipoParcela` (select, requerido), `sector` (requerido),
    `fila` (opcional), `numero` (requerido), `nombreDifunto` (requerido),
    `fechaFallecimiento` (`type="date"`, requerido), `fechaInhumacion`
    (`type="date"`, requerido), `nombreTitular` (opcional),
    `contactoTitular` (opcional), `observaciones` (`textarea`, opcional).
    Al confirmar, `POST /api/cementerio`, recargar la búsqueda vigente y
    cerrar el formulario devolviendo el foco al botón que lo abrió (mismo
    patrón que `publicarNorma`/`cerrarFormulario` en `boletin`).
  - Cada campo con su `<label htmlFor>` explícito, `aria-invalid` y
    `aria-describedby` apuntando al mensaje de error cuando corresponda,
    mensajes de error con `role="alert"` y foco programático al aparecer,
    estado de carga con `role="status"` — mismo estándar de accesibilidad
    ya usado en `PantallaDeBoletin`/`PantallaDeReclamos`, no hay
    componente ni patrón nuevo que inventar acá.
  - Formatear las fechas de fallecimiento/inhumación con el mismo
    `formatearFecha` (componentes locales, sin desfasaje UTC) que ya usa
    `boletin` para `fechaPublicacion` — copiar la función o extraerla si
    el agente `frontend` lo considera más prolijo, a su criterio, siempre
    que no rompa nada de `boletin`.

- Registrar el componente en `frontend/src/modulos/registro.ts`:
  `cementerio: PantallaDeCementerio` (import + entrada en el record), sin
  tocar las entradas existentes de `ejemplo`/`reclamos`/`boletin`.

- No hace falta tocar `Navegacion.tsx`/`CatalogoDeModulos.tsx`/`App.tsx`:
  ya arman la navegación a partir de `registroDePantallasDeModulo` +
  `useModulos` (módulos habilitados del tenant) sin necesitar conocer
  módulos nuevos por nombre — confirmarlo al implementar; si hiciera falta
  tocar algo ahí, es una señal de que algo no sigue el mecanismo del
  ADR 0012 y hay que avisar antes de improvisar.

## Aislamiento entre tenants

Cubierto por el test 6 de la tarea de backend (arriba). No hay nada
adicional del lado del frontend: cada portal solo pega contra su propio
subdominio (mismo mecanismo que el resto de los módulos).

## Accesibilidad (WCAG)

Cubierta por seguir al pie de la letra los patrones ya validados de
`PantallaDeBoletin.tsx` (foco, labels, `aria-*`, roles de estado/alerta,
tabla con caption y encabezados de columna con `scope="col"`/`scope="row"`
donde corresponda). No hay pantalla nueva de tipo distinto a lo ya
construido: es la misma forma (búsqueda pública + formulario protegido
condicional) que `boletin`, aplicada a otro dominio de datos.

## Fuera de alcance (explícitamente diferido)

- Gestión de concesiones: renovación, transferencia de titularidad,
  vencimiento. Esta rebanada solo registra inhumaciones, no administra el
  ciclo de vida de la concesión de una parcela.
- Más de un registro de inhumación por parcela a lo largo del tiempo
  (reutilización de nichos): cada fila es un registro de inhumación
  independiente; no hay una entidad "parcela" separada que agrupe varios
  difuntos. Si en el futuro hace falta, es una normalización a evaluar
  cuando haya un segundo caso real que la necesite (mismo criterio que
  ADR 0014 usa para diferir generalizaciones).
- Un panel protegido de consulta/detalle con los datos completos
  (titular, contacto, observaciones) más allá del momento del alta: hoy
  esos datos solo se ven en la respuesta del `POST`. Editar o borrar un
  registro ya cargado tampoco entra en esta rebanada (mismo criterio que
  `boletin`: se corrige con un registro nuevo, no mutando el viejo — y acá
  ni siquiera hace falta decirlo porque no hay UI de edición en absoluto).
- Adjuntos/documentación (actas, fotos), geolocalización del cementerio o
  de las parcelas, plano interactivo.
- Cualquier integración con notificaciones/auditoría transversal (ADR
  0013) — mismo criterio que R6/R7: se agrega cuando el módulo lo
  necesite de verdad.
- Rate limiting / anti-abuso: no aplica igual que en `reclamos`, acá ni
  siquiera hay escritura pública, así que el riesgo es menor todavía.
- Paginado del listado: mismo criterio que `reclamos`/`boletin`, fuera de
  alcance.

## Instrucción para los agentes implementadores

**No hagan commit, push, ni abran PR.** Dejen los cambios en el árbol de
trabajo sin commitear. El tech lead arma el commit, pushea la rama y abre
el PR contra `develop` una vez que backend, frontend y la auditoría estén
completos. Si el trabajo se corta por límite de sesión o cualquier otro
motivo, no reintenten commitear/pushear por su cuenta al retomar: avisen
el estado en el que quedó y esperen instrucción.
