# 0032 - Bromatología: padrón público de comercios e historial de inspecciones protegido, segunda y última rebanada del Epic sin fase fija

- Estado: Aceptada
- Fecha: 2026-09-01

## Contexto

[ADR 0031](0031-defensa-civil-alertas-publicas-y-recursos-primera-rebanada-sin-fase-fija.md)
abrió el Epic "Sin fase fija" (CD-36) con Defensa Civil, y descartó
Bromatología como primera rebanada porque, tal cual la describe el
[catálogo funcional](../../producto/catalogo-funcional.md) §3 ("control de
comercios, habilitaciones, control alimentario"), el circuito completo de
una inspección bromatológica real incluye acta, infracción y plazo de
subsanación — normativa que varía por municipio/provincia, sin un
municipio piloto real que la valide (mismo riesgo que ya descartaron
Compras, ADR 0021, y Catastro, ADR 0023, para sus respectivas fases). Esta
es la segunda y última rebanada construible de ese Epic: el
[roadmap](../../producto/roadmap-fases.md#sin-fase-fija) no deja más
candidatos sin fase fija además de Defensa Civil y Bromatología. Toca
ahora recortar Bromatología con el mismo criterio de descarte razonado,
en vez de seguir postergándola.

### Qué se puede construir sin inventar el circuito normativo de inspección, y qué no

El problema real de Bromatología no es "un comercio tiene un estado
sanitario" — eso ya es un patrón muy conocido del proyecto (Obras,
Arbolado, Espacios Verdes, Educación: alta protegida, lectura pública,
estado mutable). El problema es el **procedimiento administrativo formal**
detrás de un cambio de estado adverso: un acta de inspección firmada, una
infracción tipificada con su artículo de ordenanza, un plazo de
subsanación con reinspección obligatoria, y —si corresponde— un expediente
sancionatorio. Nada de eso se puede diseñar bien sin normativa real de un
municipio, exactamente el motivo por el que ADR 0031 descartó Bromatología
la primera vez.

Esta rebanada separa esas dos cosas: construye el padrón con estado (la
parte sin normativa específica) y un registro de inspecciones que
**motiva** los cambios de estado (a diferencia de Obras/Arbolado, donde el
estado se cambia con un PATCH directo), pero **sin** modelar acta,
infracción, plazo de subsanación ni expediente. Una inspección acá es un
registro de auditoría de lo que un inspector constató en una fecha —no un
acto administrativo con efectos jurídicos propios—, y el estado del
comercio es la consecuencia directa de la última constatación, no de un
proceso sancionatorio.

### Por qué no se reutiliza `proveedores` (R14)

El catálogo funcional podría sugerir que "una empresa que el municipio
registra" es siempre el mismo concepto que ya cubre `proveedores`
([ADR 0019](0019-consola-del-proveedor-ui-cross-tenant-y-contrato-minimo.md)/
[ADR 0020](0020-padron-fiscal-simulado-para-cuit-de-proveedores.md)). No lo
es: `proveedores` modela una empresa que **le vende algo al municipio**
(CUIT validado contra un padrón fiscal simulado, aprobación para poder
facturar, situación fiscal). Un comercio bromatológico es, exactamente al
revés, una empresa **a la que el municipio fiscaliza** por manipular
alimentos para el público — no hay ninguna transacción comercial entre el
comercio y el municipio, ni falta un CUIT validado para este propósito. Un
mismo negocio real (una verdulería, un restaurante) podría existir en
ambos módulos por motivos completamente independientes —si además le
vende algo al municipio, se registra por separado como proveedor—, o en
uno solo, o en ninguno. Modelarlos como la misma entidad o enlazarlos con
una clave foránea acoplaría dos ciclos de vida que no tienen ninguna razón
de negocio para depender uno del otro: aprobarse como proveedor no
implica estar habilitado en Bromatología, y viceversa. `ComercioBromatologicoEntity`
es una entidad nueva, sin relación de esquema con `ProveedorEntity`.

### Por qué no hay dato nominal de manipuladores de alimentos

El catálogo funcional y la tarea de esta rebanada mencionan "libreta
sanitaria de manipuladores" como parte típica de Bromatología en
municipios argentinos reales. Una libreta sanitaria acredita que una
persona identificable pasó un examen médico apto para manipular
alimentos — es, por definición, un dato de salud vinculado a una
identidad (mismo tipo de dato que
[ADR 0025](0025-desarrollo-social-inscripcion-a-programa-social-con-minimizacion-de-datos-sensibles.md)
descartó para Salud municipal y Discapacidad: "no hay una forma de
minimizarlo que no lo vacíe de sentido"). El producto no tiene hoy ningún
mecanismo de datos sensibles (cifrado en reposo por columna, auditoría de
cada lectura) que ese dato exigiría. Por eso esta rebanada no incluye
ningún registro de manipuladores, con o sin nombre: el comercio es la
unidad de habilitación e inspección, no la persona que trabaja ahí.
Mismo criterio, aplicado de nuevo, que ya evitó ese problema en
`defensacivil` (ADR 0031, "Minimización de datos") — acá con una
motivación más cercana a la de ADR 0025 porque el dato evitado sí sería,
en este caso, de salud.

### Por qué el historial de inspecciones no es público, aunque el estado del comercio sí lo es

Ningún módulo previo con estado propio (Obras, Arbolado, Espacios Verdes,
Recursos de Defensa Civil) separó "el estado actual, público" de "el
historial de lo que lo produjo, protegido". Acá hace falta separarlo: el
campo `observaciones` de una inspección es texto libre que un agente
municipal escribe sobre un comercio privado —una apreciación operativa,
no un hecho ya consumado y público en sí mismo como sí lo es una multa de
tránsito ya labrada ([ADR 0021](0021-multas-de-transito-alta-protegida-estado-propio-descuento-por-pago-temprano.md)
§6)—, sin ningún mecanismo de notificación previa ni descargo del comercio
(que sí tiene Multas). Publicar ese texto sin filtro sería exactamente el
tipo de riesgo reputacional/legal para un tercero que esta rebanada evita
al no construir el circuito de acta/infracción: si no hay debido proceso
formal detrás de una observación, tampoco hay que exponerla como si lo
hubiera. El **estado agregado** del comercio (`HABILITADO`/`OBSERVADO`/
`CLAUSURADO`) sigue siendo público —es la información de transparencia
que un vecino necesita antes de comprar en un comercio— pero el detalle
de cada inspección (fecha, resultado, observaciones, quién la hizo) queda
detrás de sesión y del mismo permiso que la registra.

Ningún ADR previo decide (a) un registro de historial que actualiza el
estado de otra entidad como efecto de su alta, en vez de un `PATCH`
directo de estado, ni (b) una entidad con estado público pero cuyo
historial de auditoría queda íntegramente protegido.

## Decisión

### 1. Módulo nuevo `bromatologia`, contratable, con dos entidades relacionadas (a diferencia de Defensa Civil)

`bromatologia` es un módulo funcional propio
([ADR 0009](0009-modelo-comercial-y-entitlement.md)), con su propio
`DescriptorDeModulo`, `código = "bromatologia"`, `nombre = "Bromatología"`
y prefijo `/api/bromatologia`. No depende de ningún otro módulo funcional
(ni de `proveedores`, ver Contexto). Dos entidades en
`bromatologia.internal`, ambas sin columna de tenant (aisladas por base
física, ADR 0001), **con** relación de esquema entre sí a diferencia de
`defensacivil` (ADR 0031 §1):

- `ComercioBromatologicoEntity` (tabla `comercio_bromatologico`): el
  padrón, público.
- `InspeccionBromatologicaEntity` (tabla `inspeccion_bromatologica`), con
  `comercio_id` como clave foránea obligatoria hacia
  `comercio_bromatologico`: cada inspección pertenece a un comercio
  concreto, es el primer módulo del proyecto con esta relación explícita
  entre una entidad con estado público y su historial protegido.

### 2. Comercio: padrón con rubros cerrados y vencimiento de habilitación, mismo patrón de alta protegida / lectura pública que Obras/Arbolado/Espacios Verdes

`ComercioBromatologicoEntity`:

- `nombre` (obligatorio, largo máximo 200): nombre comercial del local.
- `rubro`: enum cerrado `VERDULERIA, CARNICERIA, PANADERIA, RESTAURANTE,
  ALMACEN, OTRO` — cubre los rubros más comunes de un comercio de
  alimentos sin inventar un nomenclador más fino, mismo criterio que
  `TipoDeAlerta`/`CategoriaDeGacetilla`.
- `direccion` (texto libre, obligatorio, largo máximo 300, sin GIS, mismo
  criterio que `ubicacion` en Arbolado/Obras/Recursos de Defensa Civil).
- `estado`: enum `HABILITADO, OBSERVADO, CLAUSURADO`. Nace siempre
  `HABILITADO` (registrarlo en el padrón significa que el municipio ya le
  otorgó la habilitación inicial; no es un parámetro del alta). **No
  existe ningún `PATCH` que cambie este campo directamente**: la única vía
  de cambio es registrar una inspección (Decisión 4) — a diferencia de
  todo el resto del proyecto con estado propio (Obras, Arbolado, Espacios
  Verdes, Recursos de Defensa Civil), donde el estado se cambia con un
  `PATCH` explícito sin dejar más rastro que `actualizadoEn`. Acá el
  cambio de estado siempre queda acompañado de quién lo constató, cuándo,
  y por qué (Decisión 4) — sin llegar a modelar un acta de infracción
  formal (ver Contexto).
- `fechaHabilitacion` (obligatoria): cuándo se otorgó la habilitación.
- `fechaVencimientoHabilitacion` (obligatoria, tiene que ser posterior a
  `fechaHabilitacion`, rechazo con `SolicitudInvalida` si no): informativo
  en esta rebanada, no dispara ningún cambio de estado automático (no hay
  infraestructura de jobs/cron en el proyecto) ni ningún flujo de
  renovación — ver Pendiente de definir.
- `publicadoPorNombre`/`publicadoPorEmail` (copia del actor autenticado,
  ADR 0013), `creadoEn`, `actualizadoEn`.

`POST /api/bromatologia/comercios` requiere sesión y el permiso
`bromatologia.gestionar` (Decisión 5). `GET /api/bromatologia/comercios`
es lectura pública (`rutasDeLecturaPublica()`,
[ADR 0012](0012-declaracion-de-modulos-y-gating-por-ruta.md) §1), con
filtros combinables `rubro`, `estado` y `q` (`ILIKE` sobre
`nombre`/`direccion`), ninguno obligatorio — mismo perfil de riesgo que
Obras/Arbolado/Espacios Verdes, sin dato de tercero que proteger en el
padrón en sí. Orden por `creadoEn` descendente, el criterio por defecto
del proyecto.

Sin campo de titular, razón social o CUIT: no hace falta para el
propósito de esta rebanada (transparencia del estado sanitario, no
validación fiscal) y evita cualquier ambigüedad sobre si es un dato
personal (el dueño de un comercio unipersonal) — ver Alternativas.

### 3. Inspección: historial append-only, sin edición ni borrado, que actualiza el estado del comercio como efecto de su alta

`InspeccionBromatologicaEntity`:

- `comercioId` (obligatorio, clave foránea): a qué comercio corresponde.
- `fecha` (obligatoria): cuándo se hizo la inspección.
- `resultado`: reutiliza el mismo enum `HABILITADO`/`OBSERVADO`/
  `CLAUSURADO` que `ComercioBromatologicoEntity.estado` — no se define un
  segundo enum con nombres distintos para el mismo conjunto de valores
  (evita una tabla de mapeo entre "resultado de inspección" y "estado de
  comercio" que no aportaría nada).
- `observaciones` (texto libre, opcional): qué constató el inspector. No
  público (ver Contexto) — a diferencia de `descripcion`/
  `recomendaciones` en Alertas de Defensa Civil, que sí son públicas
  porque ahí el emisor es el propio municipio comunicándose con el
  vecino, no un juicio sobre un tercero privado.
- `inspeccionadoPorNombre`/`inspeccionadoPorEmail` (copia del actor
  autenticado, ADR 0013).
- `creadoEn`. **Sin `actualizadoEn`**: una inspección, una vez registrada,
  no se edita ni se borra en esta rebanada — es un registro de auditoría
  append-only, el primero del proyecto con esta forma (todo lo anterior
  con estado propio permite, como mínimo, que su propio estado cambie
  después del alta).

`POST /api/bromatologia/comercios/{id}/inspecciones` requiere sesión y
`bromatologia.gestionar`. En la misma transacción: crea la inspección y
actualiza `ComercioBromatologicoEntity.estado` al valor de `resultado`. Un
`comercioId` que no existe en la base de este tenant (inventado o de otro
municipio, ADR 0001) da 404 genérico (`ComercioNoEncontrado`), igual
criterio que el resto del proyecto para ids ajenos.

A diferencia de `RecursoDeDefensaCivilEntity` (ADR 0031 §5), acá **no** se
rechaza una inspección cuyo `resultado` coincide con el `estado` actual
del comercio: una reinspección de rutina que confirma que todo sigue
`HABILITADO` es, en sí misma, información válida que vale la pena dejar
registrada (cuándo fue la última vez que se controló ese comercio) — es
un registro de historial, no una "acción de cambiar de estado" donde un
no-op no tendría sentido (ver Alternativas).

### 4. Historial de inspecciones: lectura protegida, no pública

`GET /api/bromatologia/comercios/{id}/inspecciones` **no** está declarada
en `rutasDeLecturaPublica()`: requiere sesión (por defecto, al no ser
pública) y el permiso `bromatologia.gestionar` vía `@PreAuthorize`, igual
que las rutas de escritura del módulo. Devuelve todas las inspecciones
del comercio, ordenadas por `fecha` descendente. Un `comercioId`
inexistente en este tenant da el mismo 404 genérico que la Decisión 3.
Es el primer endpoint de lectura del proyecto sobre una entidad
directamente relacionada con un catálogo público que, sin embargo, no
tiene ninguna vía de lectura sin sesión (ver Contexto).

### 5. Un único permiso `bromatologia.gestionar`, no separado por sensibilidad

Igual que `defensacivil.gestionar` (ADR 0031 §3) y a diferencia de
Desarrollo Social (ADR 0025 §7): acá no hay dato personal de nadie
identificable en ninguna de las dos entidades (ver Contexto), así que no
hay una diferencia real de sensibilidad entre dar de alta un comercio y
registrar una inspección que justifique separar el permiso. Un único
`bromatologia.gestionar` cubre alta de comercio, alta de inspección y
lectura del historial de inspecciones. Asignado a `administrador` y
`agente` (el inspector de Bromatología en el terreno), mismo criterio que
`obras.gestionar`/`arbolado.gestionar`/`defensacivil.gestionar`.

### 6. Sin geolocalización, sin adjuntos, sin acta ni expediente, sin integración con `proveedores`

Mismos motivos que todos los módulos anteriores con registro público (ADR
0023 §6/§7/§8, ADR 0024 §6, ADR 0031 §7): sin GIS (`direccion` es texto
libre), sin fotos ni documentos adjuntos (ni actas firmadas
digitalmente), sin CUIT ni relación de esquema con `proveedores` (ver
Contexto). Sin acta de infracción, sin tipificación de infracciones por
ordenanza, sin plazo de subsanación con reinspección obligatoria, sin
expediente sancionatorio: es exactamente lo que esta rebanada evita
inventar sin normativa real (ver Contexto).

## Alternativas consideradas

- **Modelar el circuito completo de acta/infracción/plazo de
  subsanación**, tal cual lo sugiere el catálogo funcional: descartada —
  ver Contexto, es la razón por la que ADR 0031 ya había descartado
  Bromatología como primera rebanada del Epic.
- **Reutilizar `proveedores`/`ProveedorEntity` para el padrón de
  comercios bromatológicos**: descartada — ver Contexto, son dos
  conceptos de dominio inversos (quien le vende al municipio vs. a quien
  el municipio fiscaliza) sin relación de negocio necesaria.
- **Libreta sanitaria nominal de manipuladores**: descartada — dato de
  salud de una persona identificable, mismo criterio de minimización que
  ADR 0025 aplicó a Salud municipal/Discapacidad; sin mecanismo de datos
  sensibles maduro en el producto.
- **Campo `titular`/CUIT del comercio**: descartado para esta rebanada —
  no es necesario para el propósito de transparencia/fiscalización de
  esta rebanada y evita ambigüedad sobre si es un dato personal (dueño de
  un comercio unipersonal); revisable con un municipio piloto real.
- **`PATCH` directo de `estado` del comercio, mismo patrón que
  Obras/Arbolado/Espacios Verdes/Recursos de Defensa Civil**: descartada
  — ver Decisión 3. Un comercio no cambia de estado "porque sí": cambia
  porque alguien lo inspeccionó, y modelar el cambio como efecto de una
  inspección deja ese rastro sin inventar el aparato procedimental
  completo (acta/infracción) que sí se descartó.
- **Historial de inspecciones público**, mismo patrón que el resto de
  entidades con estado del proyecto: descartada — ver Contexto,
  expondría observaciones de texto libre sobre un tercero privado sin
  ningún mecanismo de notificación o descargo previo detrás.
- **Rechazar una inspección cuyo `resultado` coincide con el `estado`
  actual del comercio**, mismo criterio que `RecursoDeDefensaCivilEntity`
  (ADR 0031 §5): descartada — ver Decisión 3, una reinspección de rutina
  con el mismo resultado es información de historial válida, no una
  "acción de cambio de estado" donde un no-op no tendría sentido.
- **Permiso separado `bromatologia.gestionarComercios` /
  `bromatologia.registrarInspecciones`**, por simetría con Desarrollo
  Social: descartada — ver Decisión 5, no hay dato personal identificable
  en ninguna de las dos entidades que justifique la separación.
- **Vencimiento automático de la habilitación** (que el estado pase solo
  a algo al cumplirse `fechaVencimientoHabilitacion`): descartada para
  esta rebanada — el proyecto no tiene infraestructura de jobs/cron
  todavía; el campo es informativo, el cambio de estado sigue
  dependiendo de una inspección real (Decisión 2/3).

## Consecuencias

- `bromatologia` no depende de ningún otro módulo funcional (ni de
  `proveedores`); el test de modularidad de Spring Modulith lo verifica
  en el build.
- Primer módulo del proyecto con dos entidades relacionadas por clave
  foránea donde una (`ComercioBromatologicoEntity`) tiene lectura
  pública y la otra (`InspeccionBromatologicaEntity`) es enteramente
  protegida, y donde el estado de la primera se actualiza como efecto de
  un alta en la segunda en vez de por un `PATCH` directo.
- Primer módulo del proyecto con un registro append-only (sin
  `actualizadoEn`, sin edición ni borrado posterior).
- Bromatología queda con su alcance recortado tal como se decidió acá:
  agotado el Epic "Sin fase fija" (CD-36) con esta rebanada, no quedan
  más candidatos sin fase fija en el roadmap actual (ver
  [roadmap](../../producto/roadmap-fases.md#sin-fase-fija)).
- El costo de abuso de un alta protegida con datos falsos (un comercio o
  una inspección inventada) requiere sesión y permiso, mismo riesgo ya
  aceptado por el proyecto para cualquier cuenta de agente comprometida
  que ADR 0031 (Consecuencias) ya documenta para Defensa Civil.

## Pendiente de definir

- Acta de infracción, tipificación por ordenanza, plazo de subsanación
  con reinspección obligatoria y expediente sancionatorio: depende de
  normativa real de un municipio piloto, mismo motivo que ya postergó
  esta rebanada una vez (ver Contexto).
- Renovación de habilitación (actualizar `fechaVencimientoHabilitacion`
  tras un trámite): no existe en esta rebanada.
- Vencimiento automático de la habilitación por fecha: depende de
  infraestructura de jobs/cron que el proyecto todavía no tiene.
- Notificación al comercio de un resultado de inspección adverso o de un
  vencimiento próximo: depende del motor de notificaciones (ADR 0013),
  sin consumidor real todavía, mismo pendiente que arrastran los módulos
  anteriores.
- Libreta sanitaria de manipuladores: depende de un mecanismo de datos
  sensibles más maduro (cifrado por columna, auditoría de lectura) que
  habilitaría reabrirlo como candidata futura, mismo pendiente que ADR
  0025 dejó para Salud municipal/Discapacidad.
- CUIT/titular del comercio y su eventual cruce con `proveedores`: fuera
  de alcance, revisable con un municipio piloto real (ver Contexto,
  Alternativas).
- Geolocalización estructurada de `direccion`: depende de que exista GIS
  como servicio consolidado, mismo pendiente que arrastra el proyecto
  desde Reclamos.
- Edición de los campos del alta del comercio (`nombre`, `rubro`,
  `direccion`, fechas) después de creado: no existe en esta rebanada,
  mismo criterio que el resto del proyecto para sus propios campos de
  alta.
