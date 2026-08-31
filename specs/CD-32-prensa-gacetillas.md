# CD-32 · R23 — Prensa y Comunicación: gacetillas públicas, segunda rebanada de Fase 6

Ver [ADR 0027](../docs/arquitectura/decisiones/0027-prensa-y-comunicacion-gacetillas-segunda-rebanada-de-fase-6.md)
para el porqué de cada decisión de esta spec. Esta spec no reabre nada del
ADR: lo traduce a tareas concretas. Es, a propósito, casi un calco de
`boletin` (R7): mismo mecanismo (alta protegida por sesión y permiso,
lectura pública sin sesión, sin estado ni edición posterior), aplicado a
un dominio distinto (comunicados de prensa, no normas). La única
diferencia de fondo está en la Tarea 1, permisos: `prensa.publicar` va a
`administrador` **y** `agente`, no solo a `administrador` como
`boletin.publicar` — no la "corrijas" por simetría con Boletín, es
intencional (ADR 0027 §3).

## Demo objetivo

Un agente municipal (con sesión y `prensa.publicar` — sin necesitar ser
administrador) publica una gacetilla, "Se inaugura la nueva plaza del
barrio Centro" (categoría `OBRAS`), con fecha de publicación de hoy y el
texto completo del comunicado. Un vecino, sin sesión, entra al portal
público, filtra por categoría "Obras" y busca "plaza" en el título, y
encuentra esa gacetilla. La misma gacetilla no aparece en el portal de
otro municipio.

## Tarea 1 (backend) — módulo `prensa` completo

**Comportamiento observable**: con sesión y `prensa.publicar`, `POST
/api/prensa` publica una gacetilla y devuelve sus datos (201), incluido
`publicadoPorNombre`/`publicadoPorEmail` tomados del actor autenticado
(no del cuerpo del request). Sin sesión, `GET /api/prensa` devuelve el
listado de gacetillas del municipio en curso, con filtros opcionales
combinables `categoria` y `q` (`ILIKE` en `titulo`), ordenado por
`fechaPublicacion` descendente, sin paginado. Un municipio sin el módulo
`prensa` contratado rechaza ambas rutas con 403 `MODULO_NO_CONTRATADO`,
con o sin sesión. Sin sesión, o con sesión pero sin `prensa.publicar`,
`POST /api/prensa` da 401/403 (no está en `rutasDeEscrituraPublica()`).

Tomá `backend/src/main/java/ar/com/ciudaddigital/boletin/` (módulo
completo: `NormaEntity`, `TipoDeNorma`, `NormaRepository`,
`GestionDelBoletin`, `BoletinController`, `DescriptorDelModuloBoletin`,
`SolicitudInvalida`, `package-info.java`) como plantilla estructural
exacta. Los nombres cambian, la forma no.

**Modelo** (`prensa.internal`, módulo nuevo, prefijo `/api/prensa`):

- `CategoriaDeGacetilla`: enum `INSTITUCIONAL, OBRAS, CULTURA, DEPORTES,
  SALUD, SEGURIDAD, OTRAS`.
- `GacetillaEntity` (tabla `gacetilla`), sin columna de tenant (mismo
  criterio que `NormaEntity`/`RegistroAuditoriaEntity`):
  - `id` (`bigint generated always as identity`).
  - `categoria` (`varchar(20)`, not null, `check` de valores del enum).
  - `titulo` (`varchar(300)`, not null).
  - `texto` (`text`, not null).
  - `fecha_publicacion` (`date`, not null — puede cargarse en forma
    retroactiva, igual que en Boletín; no confundir con `creado_en`).
  - `publicado_por_nombre` (`varchar(150)`, not null),
    `publicado_por_email` (`varchar(200)`, not null) — copia del actor al
    momento de publicar, no una relación JPA, mismo criterio que
    `NormaEntity`/`RegistroAuditoriaEntity` (ADR 0013).
  - `creado_en` (`timestamptz`, not null, default `now()`).
  - **Sin `numero`**: a diferencia de `NormaEntity`, una gacetilla no
    tiene numeración legal correlativa que modelar (ADR 0027 §1). No lo
    agregues.
  - Sin métodos de mutación: una vez publicada, esta rebanada no edita ni
    borra una gacetilla (mismo criterio que `NormaEntity`).
  - Índice: `gacetilla_fecha_publicacion_idx on gacetilla (fecha_publicacion desc)`.

- `GestionDePrensa` (`@Service`), calco de `GestionDelBoletin`:
  - `publicar(CategoriaDeGacetilla categoria, String titulo, String texto, LocalDate fechaPublicacion, String publicadoPorNombre, String publicadoPorEmail)`:
    valida `categoria` no-null (400 `SolicitudInvalida` si falta o no
    matchea el enum), `titulo` no-blank y largo máximo 300, `texto`
    no-blank, `fechaPublicacion` no-null. `publicadoPorNombre`/
    `publicadoPorEmail` no llevan mensaje de "solicitud inválida" propio
    (salen del actor autenticado, no del request — mismo criterio que
    `GestionDelBoletin`), solo se valida su largo máximo por defensa.
  - `buscar(CategoriaDeGacetilla categoria, String textoEnTitulo)`: mismo
    patrón exacto que `GestionDelBoletin.buscar` — `textoEnTitulo` vacío o
    en blanco se trata como "sin filtro", el patrón `ILIKE` se arma acá,
    no en el repositorio.

**`DescriptorDelModuloPrensa`**:
- `codigo() = "prensa"`, `nombre() = "Prensa y Comunicación"`.
- `descripcion()`: algo como "Gacetillas y comunicados de prensa
  publicados por el municipio, buscables por cualquiera."
- `prefijosDeApi() = List.of("/api/prensa")`.
- `rutasDeLecturaPublica() = List.of("/api/prensa")`.

**Controller**: `PrensaController`, mismo patrón exacto que
`BoletinController`:
- `POST /api/prensa` con `@PreAuthorize("hasAuthority('prensa.publicar')")`,
  toma nombre/email del `ActorAutenticado` igual que `BoletinController`.
- `GET /api/prensa` sin `@PreAuthorize`, parámetros opcionales `categoria`
  y `q`.
- `@ExceptionHandler(SolicitudInvalida.class)` → 400, mismo formato de
  `ErrorResponse` que `BoletinController`.
- Records `PublicarGacetillaRequest(String categoria, String titulo,
  String texto, LocalDate fechaPublicacion)` y `GacetillaResponse(Long
  id, String categoria, String titulo, String texto, LocalDate
  fechaPublicacion, String publicadoPorNombre, String publicadoPorEmail,
  Instant creadoEn)`.

**Migración** `db/tenant/V23__crear_prensa.sql` (siguiente número
disponible tras `V22__crear_turnos.sql`): tabla `gacetilla` con los
comentarios de intención de columna que usa `V7__crear_boletin.sql` como
referencia de estilo, más el catálogo de permisos:

```sql
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('prensa.publicar', 'Prensa y Comunicación', 'prensa', 'publicar',
     'Publicar una gacetilla de prensa del municipio.');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo
from rol r, permiso p
where r.codigo in ('administrador', 'agente')
  and p.codigo = 'prensa.publicar';
```

Documentá en un comentario SQL, igual que hace `V7__crear_boletin.sql`
con `boletin.publicar`, **por qué** este permiso va a los dos roles y no
solo a `administrador` como `boletin.publicar` (ADR 0027 §3) — para que
quien lea la migración después no lo lea como una inconsistencia sin
explicar.

**`package-info.java`** de `prensa`: mismo estilo que el de `boletin`,
explicando que reutiliza el mecanismo de `rutasDeLecturaPublica()`
(ADR 0012 §1) sin agregar nada nuevo, y remitiendo a ADR 0027 para el
resto de las decisiones (incluida la de permisos).

## Tarea 2 (backend) — test de aislamiento entre tenants

**Obligatorio, no diferible (CLAUDE.md).** Crear
`backend/src/test/java/ar/com/ciudaddigital/prensa/PrensaTest.java`
(extiende `SoporteDeIntegracion`), calco estructural de
`BoletinTest.java` con estos casos:

- Publicación solo con el módulo contratado (201 con el módulo; 403
  `MODULO_NO_CONTRATADO` sin él, aunque haya sesión y permiso).
- Un agente **con** `prensa.publicar` puede publicar (201) — a diferencia
  de `BoletinTest`, donde el test análogo prueba que un agente **sin** el
  permiso de boletín recibe 403; acá el caso a probar es el opuesto,
  porque el agente sí tiene `prensa.publicar` por default (Tarea 1). Cubrí
  además el caso de alguien sin sesión intentando publicar (401/403 sin
  código de negocio).
- Publicación inválida (sin título, o con categoría inexistente) → 400.
- Lectura pública solo con el módulo contratado (sin sesión, con el
  módulo devuelve lo publicado; sin el módulo, 403
  `MODULO_NO_CONTRATADO` aun sin sesión).
- Filtros: por `categoria` y por texto en el título, igual que
  `BoletinTest.filtrosDeTipoYTexto`.
- **Aislamiento**: publica una gacetilla en el tenant A y otra en el
  tenant B (títulos con sufijo aleatorio, mismo criterio que
  `BoletinTest.aislamientoEntreTenants`), y verificá que
  `GET /api/prensa` en cada tenant devuelve solo la propia.

## Tarea 3 (frontend) — pantalla del módulo `prensa`

**Comportamiento observable**: pantalla nueva `PantallaDePrensa.tsx` en
`frontend/src/modulos/prensa/`, registrada en
`frontend/src/modulos/registro.ts` (clave `prensa`).

Tomá `frontend/src/modulos/boletin/PantallaDeBoletin.tsx` como plantilla
estructural exacta (misma forma: una única vista de búsqueda pública, con
la acción de publicar apareciendo adentro si `usuario?.permisos.includes('prensa.publicar')`,
sin pantallas separadas por permiso). Adaptá:

- Filtros: `categoria` (`<select>` con las 7 opciones de
  `CategoriaDeGacetilla`, etiquetas legibles: Institucional, Obras,
  Cultura, Deportes, Salud, Seguridad, Otras) y `q` (texto libre sobre el
  título).
- Tabla de resultados: columnas Categoría, Título, Fecha de publicación,
  Publicado por, Texto — mismas columnas que `PantallaDeBoletin`, sin
  columna "Número" (no existe en `GacetillaEntity`).
- Formulario de publicación (visible solo con `prensa.publicar`):
  Categoría (`<select>` obligatorio), Título (obligatorio), Fecha de
  publicación (`type="date"`, obligatorio), Texto (`textarea`,
  obligatorio) — sin campo "Número".
- Textos de la pantalla (`<h1>`, bajada, encabezados de sección) hablan
  de "gacetillas"/"comunicados de prensa", no de "normas"/"Boletín
  Oficial".

**Accesibilidad (obligatorio, no diferible, CLAUDE.md)**: replicá al pie
de la letra los patrones ya usados en `PantallaDeBoletin.tsx` — foco
gestionado por `useRef`+`tabIndex={-1}` al montar, anuncios con
`role="status"`/`role="alert"`, `aria-invalid`/`aria-describedby` en
campos con error, `aria-busy` en el botón de envío, `<label htmlFor>` en
todo input/select/textarea, tabla con `<caption>` y `scope="col"`/
`scope="row"`. No inventes un patrón nuevo de accesibilidad.

**Fuera de alcance**: routing de URLs, edición/derogación de una
gacetilla ya publicada, adjuntos/imágenes, paginado, integración con
redes sociales.

## Qué NO tocar

- El módulo `boletin`: código, tablas, permisos. `prensa` no depende de
  `boletin` ni de ningún otro módulo funcional — son dos módulos
  independientes que comparten forma, no una relación de dependencia
  (verificá que el test de modularidad de Spring Modulith siga en verde).
- `seguimientoanonimo`, `pagos`, `notificaciones`: no se usan en esta
  rebanada — no los enganches "por si acaso".
- `modulosHabilitados` de los tenants de prueba `sanmartin`/`moron`
  (`db/control/V2__sembrar_municipios_de_prueba.sql`): si hace falta
  `prensa` contratado para una demo manual, sembralo con el mecanismo ya
  existente. Los tests de integración contratan el módulo directamente
  contra la base de control de test, mismo patrón que `BoletinTest`.

## Instrucciones para los agentes implementadores

No hagas commit, push ni abras PR por tu cuenta: dejá los cambios en el
working tree. El tech lead revisa, commitea y coordina el PR.
