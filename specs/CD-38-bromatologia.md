# CD-38 — R28: Bromatología (padrón de comercios + historial de inspecciones)

Especificación técnica para implementar la segunda y última rebanada del
Epic "Sin fase fija" (CD-36). Decisión de arquitectura completa en
[ADR 0032](../docs/arquitectura/decisiones/0032-bromatologia-padron-de-comercios-e-historial-de-inspecciones-protegido-segunda-rebanada-sin-fase-fija.md) —
leerlo antes de implementar, esta spec no repite el razonamiento, solo el
contrato a construir.

## Demo objetivo

Un agente municipal (con sesión y `bromatologia.gestionar`) da de alta un
comercio: nombre "Verdulería Don José", rubro "Verdulería", dirección "San
Martín 450", habilitado desde hoy, vencimiento en un año. Queda
"Habilitado" en el padrón. Un vecino, sin sesión, entra al portal público,
busca por rubro "Verdulería" y lo encuentra con su estado "Habilitado". El
mismo agente registra una inspección sobre ese comercio con resultado
"Observado" y una observación ("faltan matayuyos en la zona de depósito").
El padrón público pasa a mostrar el comercio como "Observado" (sin mostrar
la observación: eso es solo visible con sesión). El agente entra al
historial de inspecciones del comercio (requiere sesión + permiso) y ve
las dos inspecciones (o una, si es la primera) con su fecha, resultado y
observación. Ni el comercio ni sus inspecciones son visibles ni editables
desde otro municipio.

## Alcance de esta rebanada (no diferible)

1. Módulo backend `bromatologia`, entidades `ComercioBromatologicoEntity`
   e `InspeccionBromatologicaEntity`, endpoints según ADR 0032.
2. Pantalla frontend `PantallaDeBromatologia`, registrada en
   `frontend/src/modulos/registro.ts` bajo la clave `bromatologia`.
3. Test de aislamiento entre tenants (obligatorio, ver más abajo).
4. Accesibilidad WCAG en la pantalla nueva, mismo nivel que
   `PantallaDeDefensaCivil`/`PantallaDeArbolado` (foco al título al
   entrar, foco a errores y confirmaciones, `role="status"`/`role="alert"`,
   labels asociados, tablas con `<caption>`/`scope`).
5. Registro del paquete nuevo en `ConfiguracionDePersistencia`
   (`RepositoriosDeTenant` y el `EntityManagerFactory` de tenant).
6. `ModularityTests` sin romper.

Fuera de alcance, explícitamente (ver ADR 0032, no lo resuelvan por su
cuenta si aparece la tentación al implementar):

- Acta de infracción, tipificación por ordenanza, plazo de subsanación,
  expediente sancionatorio.
- Cualquier dato nominal de personas (manipuladores, titular del
  comercio, CUIT).
- Geolocalización/GIS, adjuntos/fotos.
- Vencimiento automático de la habilitación (no hay jobs/cron en el
  proyecto).
- Renovación de habilitación, edición de campos del alta después de
  creado.
- Notificaciones (push/SMS/email) de cualquier tipo.
- Cualquier relación de esquema con `proveedores`.

## Tarea 1 — Backend

### Paquete y estructura

`ar.com.ciudaddigital.bromatologia`, con `package-info.java` (documentar
brevemente el módulo, mismo estilo que `defensacivil/package-info.java`)
e implementación en `ar.com.ciudaddigital.bromatologia.internal`. Mirar
`ar.com.ciudaddigital.defensacivil` completo como referencia de forma
(nombres de clase, estilo de controller, manejo de errores, records de
request/response) — es el módulo más reciente con la misma forma general
(alta protegida + lectura pública + estado propio).

### Migración Flyway

Nueva migración `V28__crear_bromatologia.sql` en
`backend/src/main/resources/db/tenant/`. Mirar `V27__crear_defensacivil.sql`
como referencia de estilo (comentarios, índices, `comment on table`).
Debe crear:

- Tabla `comercio_bromatologico`: columnas según ADR 0032 §2 (`id`,
  `nombre` varchar(200) not null, `rubro` varchar(20) not null con
  `check` sobre los 6 valores del enum, `direccion` varchar(300) not
  null, `estado` varchar(10) not null default `'HABILITADO'` con `check`
  sobre los 3 valores, `fecha_habilitacion` date not null,
  `fecha_vencimiento_habilitacion` date not null,
  `publicado_por_nombre` varchar(150) not null, `publicado_por_email`
  varchar(200) not null, `creado_en`/`actualizado_en` timestamptz not
  null default now()). Índices sobre `creado_en desc`, `estado` y
  `rubro` (filtros del padrón público).
- Tabla `inspeccion_bromatologica`: `id`, `comercio_id` bigint not null
  references `comercio_bromatologico(id)`, `fecha` date not null,
  `resultado` varchar(10) not null con el mismo `check` de 3 valores que
  `estado` de comercio, `observaciones` text (nullable),
  `inspeccionado_por_nombre` varchar(150) not null,
  `inspeccionado_por_email` varchar(200) not null, `creado_en`
  timestamptz not null default now() (**sin** `actualizado_en`: es
  append-only, ver ADR 0032 §3). Índice sobre `(comercio_id, fecha
  desc)` para el historial por comercio.
- Seed de permiso: un único `bromatologia.gestionar`, área
  "Bromatología", asignado a `administrador` y `agente` — mismo bloque
  `insert` que `V27__crear_defensacivil.sql` al final, adaptado.

### Entidades y enums

- `RubroBromatologico`: `VERDULERIA, CARNICERIA, PANADERIA, RESTAURANTE,
  ALMACEN, OTRO`.
- `EstadoBromatologico`: `HABILITADO, OBSERVADO, CLAUSURADO`. Se reutiliza
  tal cual como tipo del campo `resultado` de la inspección (ADR 0032
  §3): no crear un segundo enum.
- `ComercioBromatologicoEntity` / `InspeccionBromatologicaEntity`: campos
  como en la migración, getters (el proyecto no usa Lombok en las
  entidades existentes — verificar convención mirando
  `AlertaDeDefensaCivilEntity`/`ObraPublicaEntity` y seguirla tal cual).

### Servicio de dominio

Un componente (p.ej. `GestionDeBromatologia`, o dos si el implementador
prefiere separar por entidad — decisión de detalle libre, no de
arquitectura) que resuelve:

- `registrarComercio(...)`: valida `nombre`/`direccion` no vacíos y
  dentro de largo máximo, `rubro` válido, `fechaHabilitacion` y
  `fechaVencimientoHabilitacion` no nulas y la segunda posterior a la
  primera (si no, `SolicitudInvalida`). Nace con `estado = HABILITADO`
  siempre (no es parámetro).
- `buscarComercios(rubro, estado, q)`: filtros combinables (AND), todos
  opcionales, `q` con `ILIKE` sobre `nombre`/`direccion`, orden
  `creadoEn desc`.
- `registrarInspeccion(comercioId, fecha, resultado, observaciones,
  actor)`: valida que el comercio exista en este tenant (si no,
  `ComercioNoEncontrado`, 404), valida `fecha`/`resultado` no nulos. En
  una única transacción: persiste la inspección y actualiza
  `comercio.estado = resultado` (más `actualizadoEn`). **No** rechaza
  `resultado` igual al `estado` actual (ADR 0032 §3, decisión explícita
  de permitir reinspecciones de rutina sin cambio).
- `buscarInspecciones(comercioId)`: valida que el comercio exista (si
  no, `ComercioNoEncontrado`), devuelve todas ordenadas por `fecha desc`.

Usar `ActorAutenticado` (mismo mecanismo que `defensacivil`/`obras`) para
copiar `publicadoPorNombre`/`publicadoPorEmail` e
`inspeccionadoPorNombre`/`inspeccionadoPorEmail`.

Excepciones: `SolicitudInvalida` (400) y `ComercioNoEncontrado` (404),
mismo patrón que `defensacivil.internal.SolicitudInvalida`/
`AlertaNoEncontrada`.

### Controller

`BromatologiaController` en `/api/bromatologia`:

- `POST /comercios` — `@PreAuthorize("hasAuthority('bromatologia.gestionar')")`.
- `GET /comercios` — sin `@PreAuthorize` (pública, declarada en el
  descriptor), filtros `rubro`/`estado`/`q` como `@RequestParam`
  opcionales.
- `POST /comercios/{id}/inspecciones` — `@PreAuthorize("hasAuthority('bromatologia.gestionar')")`.
- `GET /comercios/{id}/inspecciones` — **con**
  `@PreAuthorize("hasAuthority('bromatologia.gestionar')")` (protegida,
  no pública — ADR 0032 §4, es la pieza central de esta rebanada, no la
  dejen sin `@PreAuthorize` por descuido).

Records de request/response y manejo de excepciones con
`@ExceptionHandler`, mismo estilo que `DefensaCivilController`. El
`ComercioResponse` no incluye ningún campo de inspección; el
`InspeccionResponse` sí incluye `comercioId`.

### Descriptor de módulo

`DescriptorDelModuloBromatologia`: `codigo = "bromatologia"`,
`nombre = "Bromatología"`, `prefijosDeApi = ["/api/bromatologia"]`,
`rutasDeLecturaPublica = ["/api/bromatologia/comercios"]` — **sin**
incluir la ruta de inspecciones (queda protegida por defecto + el
`@PreAuthorize` del controller).

### Registro de persistencia

Agregar `PAQUETE_BROMATOLOGIA = "ar.com.ciudaddigital.bromatologia"` en
`ConfiguracionDePersistencia`, sumarlo a `RepositoriosDeTenant.basePackages`
y al `setPackagesToScan(...)` del `EntityManagerFactory` de tenant (mismo
lugar donde está `PAQUETE_DEFENSACIVIL`).

### Test de aislamiento entre tenants (obligatorio)

Test de integración (mismo estilo que
`backend/src/test/java/ar/com/ciudaddigital/defensacivil` si existe, o
`ArboladoTest`/`ObrasTest`) contra dos municipios reales de prueba, que
cubra al menos:

1. Un comercio dado de alta en el municipio A no aparece en
   `GET /comercios` del municipio B, ni en ningún filtro.
2. Un intento de `POST /comercios/{idDeA}/inspecciones` o
   `GET /comercios/{idDeA}/inspecciones` desde el municipio B da 404
   (`ComercioNoEncontrado`), no expone que el comercio existe en otro
   tenant.
3. Una inspección registrada en A no aparece en ningún listado del
   municipio B.

### Otros tests

Cobertura de servicio (validaciones, transición de estado por
inspección, reinspección con mismo resultado permitida, 404 de comercio
inexistente) y de controller (gating de permiso en las 3 rutas
protegidas, ruta pública sin sesión). Correr `ModularityTests` y
confirmar que `bromatologia` no depende de ningún otro módulo.

## Tarea 2 — Frontend

### Pantalla

`frontend/src/modulos/bromatologia/PantallaDeBromatologia.tsx`, registrada
en `registro.ts` bajo `bromatologia: PantallaDeBromatologia`. Usar
`PantallaDeDefensaCivil.tsx` como referencia directa de convenciones:
mismo patrón de `pedir`/`enviar`, mismo manejo de `MODULO_NO_CONTRATADO`,
mismo patrón de foco (`useRef` + `tabIndex={-1}` + `.focus()` en título,
errores y confirmaciones), mismo patrón de `role="status"`/`role="alert"`,
mismo patrón de formulario colapsable con botón que abre y foco al
primer campo, mismo patrón de `vigente.current` para evitar setState
sobre componente desmontado.

Estructura de la pantalla (una sola sección, a diferencia de
`PantallaDeDefensaCivil` que tiene dos independientes — acá hay una
relación real entre las dos entidades):

1. **Listado público de comercios** (tabla con `<caption>`, filtros
   `rubro`/`estado`/`q` combinables, visible sin sesión). Columnas:
   Rubro, Nombre, Dirección, Estado, Vencimiento de habilitación, y una
   columna de Acción visible solo con `bromatologia.gestionar`.
2. **Alta de comercio** ("Registrar comercio"), visible solo con el
   permiso: mismo patrón de formulario colapsable que "Publicar alerta"
   en `PantallaDeDefensaCivil`. Campos: rubro (`<select>`), nombre,
   dirección, fecha de habilitación (`type="date"`), fecha de
   vencimiento (`type="date"`).
3. **Historial de inspecciones por comercio**, solo con el permiso: en
   cada fila del padrón, un botón "Ver inspecciones" que expande/abre
   (mismo patrón de estado local que la edición de estado de un recurso
   en `PantallaDeDefensaCivil`: un único "registro en edición/expandido"
   a la vez, identificado por id) un panel con:
   - Un listado de las inspecciones ya registradas de ese comercio
     (fecha, resultado, observaciones, quién la hizo) — se carga con un
     `GET` protegido al abrir el panel, no de entrada con el resto del
     padrón.
   - Un formulario "Registrar inspección" (fecha, resultado
     `<select>` con las 3 opciones, observaciones `<textarea>`
     opcional). Al confirmar, hace `POST`, recarga tanto el historial de
     ese comercio como el padrón completo (el `estado` de la fila
     cambió), y anuncia el resultado con `role="status"`.

Accesibilidad: cada sección con su `<h2>`/`<h3>` y `aria-labelledby`
correspondiente donde aplique, inputs con `<label htmlFor>`, errores con
`role="alert"` + foco, confirmaciones con `role="status"` + foco, tabla
con `scope="col"`/`scope="row"` y `<caption>` descriptivo (igual criterio
que `PantallaDeDefensaCivil`/`PantallaDeArbolado`). El botón "Ver
inspecciones" tiene que anunciar claramente su estado (expandido/no) —
usar `aria-expanded` en el botón que abre/cierra el panel.

### Build/lint

Correr `npm run build` y el lint del proyecto (`npm run lint` si existe)
sobre `frontend/` al terminar, sin errores nuevos.

## Fila para `docs/producto/backlog-inicial.md`

Cuando ambas tareas estén implementadas y verificadas, el tech lead
(no el implementador) actualiza `docs/producto/backlog-inicial.md`:
agrega la fila de mapeo a Jira (`CD-38 (placeholder, sin confirmar) | R28
· ... (parent: CD-36)`) y la sección `### R28 · ...` bajo el Epic "Sin
fase fija", mismo formato que la sección de R27 ya existente.
