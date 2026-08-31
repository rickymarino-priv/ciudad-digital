# 0029 - Espacios verdes: padrón público con estado propio, tercera rebanada de Fase 4 (Gestión territorial)

- Estado: Aceptada
- Fecha: 2026-08-31

## Contexto

[ADR 0024](0024-arbolado-urbano-padron-publico-con-estado-sanitario-propio.md)
abrió la segunda rebanada de Fase 4 con Arbolado urbano (R20, CD-29) y dejó
**Espacios verdes** explícitamente como candidata futura, descartada solo
para esa rebanada, no por inviable: "viable en el mismo sentido de riesgo
bajo que Obras Públicas [...] Se descarta igual, no por inviable sino
porque es, en forma, casi el mismo ejercicio que Obras Públicas ya
demostró [...] Queda disponible como candidata futura si hace falta una
rebanada chica" (ADR 0024, Contexto). Recolección de residuos y alumbrado
público quedaron diferidos por otro motivo — dependen de datos reales de
un municipio piloto (zonas de recolección, contrato con la empresa,
inventario de luminarias) — y ese motivo no cambió desde entonces.

Toca cerrar Fase 4 con una tercera rebanada, mismo criterio de descarte
razonado que ya aplicaron ADR 0021/0023/0024/0025/0028 al abrir o cerrar
cada fase.

### Candidatas

- **Recolección de residuos (calendario por zona)**: sigue dependiendo de
  zonas reales o de un contrato vigente con una empresa recolectora — sin
  cambios respecto de ADR 0024, Contexto. Descartada.
- **Alumbrado público**: sigue siendo, en esencia, inventario de
  infraestructura eléctrica real de un municipio (qué poste, qué columna,
  qué circuito) — sin cambios respecto de ADR 0024, Contexto. Descartada.
- **Catastro, Planeamiento Urbano/Uso del Suelo, GIS como servicio
  consolidado**: sin cambios respecto de ADR 0023, Contexto. Ninguna se
  reevalúa acá.
- **Espacios verdes (registro de plazas, parques y paseos)**: viable sin
  inventar normativa ni datos reales de un municipio piloto, mismo perfil
  de riesgo bajo que Obras/Arbolado/Educación. Elegida.

### Por qué de todos modos vale la pena, aunque el mecanismo técnico no sea nuevo

Mismo argumento que ya usó [ADR 0028](0028-educacion-municipal-padron-de-instituciones-segunda-rebanada-de-fase-5.md)
para Educación municipal al cerrar Fase 5: esta rebanada no pretende
aportar un mecanismo técnico nuevo — es, otra vez, "alta protegida +
lectura pública + estado propio mutable". Lo que aporta es **cobertura de
dominio**: cierra Fase 4 con tres rebanadas demostrables (Obras, Arbolado,
Espacios verdes), sin inventar normativa ni datos de un municipio piloto
que no existe, y sin tocar dato personal de nadie.

A diferencia de cuando ADR 0024 la descartó por ser "casi el mismo
ejercicio" que Obras, esta rebanada agrega dos elementos que ninguna de
las tres anteriores tiene: un campo de **magnitud numérica** (`superficie`
en m², Decisión 4) — ni Obras ni Arbolado ni Educación tienen un dato
cuantitativo propio, solo texto, enums y fechas — y una tabla de
transiciones de estado con una forma distinta a las tres anteriores
(Decisión 5). No es, entonces, una repetición vacía de Obras: comparte el
patrón general, pero no el contenido.

### Cuarto caso del patrón "alta protegida + lectura pública + estado propio mutable": ¿ahora sí se extrae algo?

[ADR 0024 §7](0024-arbolado-urbano-padron-publico-con-estado-sanitario-propio.md)
dejó la pregunta pendiente para un tercer caso; [ADR 0028](0028-educacion-municipal-padron-de-instituciones-segunda-rebanada-de-fase-5.md)
la revisó como tercer caso y decidió que no, con dos motivos: reglas de
transición distintas y campos propios distintos. Este es el cuarto caso, y
la pregunta se revisita con los cuatro casos delante, no solo con este.

Con las cuatro tablas de transición puestas una al lado de la otra:

| Módulo | Estados | Salto directo a estado terminal | Estado inicial parametrizable |
|---|---|---|---|
| Obras (ADR 0023) | 4 | Sí (`EN_EJECUCION → FINALIZADA`) | No (siempre `PLANIFICADA`) |
| Arbolado (ADR 0024) | 4 | No | No (siempre `PLANTADO`) |
| Educación (ADR 0028) | 3 | No | No (siempre `ACTIVA`) |
| Espacios verdes (acá) | 3 | No | No (siempre `DISPONIBLE`) |

Educación y Espacios verdes coinciden en la forma exacta de su tabla (tres
estados, `X → Y → {X, Z}`, `Z` terminal, sin salto directo `X → Z`): es la
primera coincidencia topológica exacta entre dos casos del patrón. Aun
así, **se decide, otra vez, no extraer nada**, por dos motivos que pesan
más que esa coincidencia:

1. **El código que coincide es trivial.** Lo único que realmente se
   repite entre los cuatro casos es una comprobación de una línea
   (`if (!transicionesValidas.get(actual).contains(nuevo)) throw ...`)
   sobre un `Map<Enum, Set<Enum>>` que cada servicio define con literales
   propios. Extraer una utilidad genérica para esa línea cambiaría muy
   poco código real a cambio de introducir una dependencia compartida
   entre módulos que hoy son deliberadamente independientes (ADR 0023 §1,
   ADR 0024 §1, ADR 0028 §1 — verificado por el test de modularidad de
   Spring Modulith en cada uno). El resto del método (`registrar`,
   `actualizarEstado`) sigue validando campos propios de cada dominio
   (`nombre`/`tipo`/`ubicacion` acá, `especie`/`fechaDePlantacion` en
   Arbolado, ninguno en Educación) que no generalizan.
2. **La coincidencia es de forma, no de significado.** Que Educación y
   Espacios verdes tengan tres estados con la misma topología es
   casualidad de dominio (dos tipos de "cierre con paso intermedio"), no
   evidencia de que vayan a evolucionar juntos: un módulo podría agregar
   un motivo de cierre, un historial de movimientos, o una cuarta
   transición sin que el otro lo necesite (ver Pendiente de definir de
   ADR 0024 y ADR 0028, todavía abiertos e independientes entre sí).
   Acoplar dos servicios por una coincidencia de forma que puede dejar de
   existir en la próxima rebanada de cualquiera de los dos es peor que no
   acoplarlos.

Se revisita otra vez si aparece un quinto caso, con esos cinco casos
delante — y, en particular, si en algún momento la lógica de transición
deja de ser una línea (por ejemplo, si se le agrega motivo obligatorio,
auditoría de quién hizo el cambio, o notificación), ese sería el momento
de evaluar una utilidad compartida, no antes.

## Decisión

### 1. Módulo nuevo `espaciosverdes`, contratable, sin depender de otros módulos

`espaciosverdes` es un módulo funcional propio ([ADR 0009](0009-modelo-comercial-y-entitlement.md)),
con su propio `DescriptorDeModulo` y prefijo `/api/espaciosverdes`. No
depende de `obras`, `arbolado`, `educacion` ni de ningún otro módulo
funcional — mismo criterio de independencia que los tres, verificado por
el mismo test de modularidad de Spring Modulith.

### 2. Mismo mecanismo de alta protegida / lectura pública que Obras/Arbolado/Educación, sin ADR nuevo para esa parte

`POST /api/espaciosverdes` requiere sesión y el permiso
`espaciosverdes.gestionar`: el registro lo origina el municipio, nunca el
vecino — mismo criterio que los tres casos anteriores. `GET
/api/espaciosverdes` es lectura pública sin sesión
(`rutasDeLecturaPublica()`, [ADR 0012](0012-declaracion-de-modulos-y-gating-por-ruta.md)
§1), con filtro opcional por `estado`, por `tipo` y por texto (`q`) sobre
`nombre`/`ubicacion`, mismo patrón `ILIKE` que Obras/Arbolado/Educación.
Sin identificador obligatorio de búsqueda: es un registro público general
(¿qué plazas y parques tiene el municipio?), no una consulta puntual sobre
un dato de un vecino.

No hay `rutasDeEscrituraPublica()`: ninguna mutación pública/anónima,
igual que Obras/Arbolado/Educación.

### 3. `tipo` es un enum cerrado, `ubicacion` es texto libre

`TipoDeEspacioVerde`: enum `PLAZA`, `PARQUE`, `PASEO`, `OTRA`. Mismo
criterio que `TipoDeInstitucionEducativa` (ADR 0028 §3), no el de
`especie` en Arbolado (ADR 0024 §3): a diferencia de una especie de árbol
(hay miles, ninguna lista cerrada tendría sentido), la clasificación de un
espacio verde municipal es un conjunto chico y estable — plaza, parque,
paseo — más una salida genérica (`OTRA`) para no bloquear un caso real que
no encaje, sin inventar una taxonomía específica de un municipio.

`ubicacion` (dirección o referencia del espacio verde) es texto libre,
mismo criterio que `ubicacion` en Obras/Arbolado/Educación: sin
geolocalización estructurada ni GIS (Decisión 6).

### 4. `superficie` opcional, en metros cuadrados: única columna numérica del alta

`superficie` es un `numeric(10,2)` opcional, en metros cuadrados, con
`check (superficie > 0)` cuando no es nulo. No es un dato que dependa de
normativa ni de un municipio piloto real para tener sentido — es una
magnitud física del espacio, análoga a cualquier medida que el municipio
conozca del lugar que administra, igual de "inventable a nivel demo" que
las fechas estimadas de Obras o la fecha de plantación de Arbolado (ambas
también opcionales y sin exigir un dato real). Es la primera columna
numérica de magnitud en las cuatro instancias del patrón: le da a esta
rebanada una dimensión de dato que Obras/Arbolado/Educación no tienen (ver
Contexto).

### 5. Estado propio: enum de tres valores + tabla de transiciones en el servicio, mismo patrón que Obras/Arbolado/Educación

`EstadoDeEspacioVerde`: `DISPONIBLE`, `EN_MANTENIMIENTO`, `CERRADO`.

```
DISPONIBLE       → EN_MANTENIMIENTO
EN_MANTENIMIENTO → DISPONIBLE
EN_MANTENIMIENTO → CERRADO
```

`CERRADO` es terminal. Un espacio `DISPONIBLE` no pasa directo a `CERRADO`:
tiene que pasar primero por `EN_MANTENIMIENTO` — mismo espíritu que
Arbolado (ADR 0024 §4) y Educación (ADR 0028 §4): una plaza no desaparece
del padrón público sin un estado intermedio que documente que hubo un
motivo antes del cierre. El estado inicial no es un parámetro del alta:
siempre nace `DISPONIBLE`.

`PATCH /api/espaciosverdes/{id}/estado` requiere sesión y
`espaciosverdes.gestionar`; no está en `rutasDeEscrituraPublica()`.

### 6. Sin geolocalización estructurada ni GIS, sin inventario de equipamiento, sin adjuntos

- Sin geolocalización estructurada ni GIS: mismos motivos que
  Obras/Arbolado/Educación (ADR 0023 §6, ADR 0024 §3/§6, ADR 0028 §3).
- Sin inventario de equipamiento (juegos, luminarias del parque, bancos,
  riego): eso es, otra vez, inventario de infraestructura física real de
  un municipio, mismo riesgo que ya descartó Alumbrado público como
  candidata de esta rebanada (Contexto). El padrón de esta rebanada es
  puramente informativo (qué espacio, dónde, qué tipo, cuánta superficie,
  en qué estado).
- Sin adjuntos/fotos: mismo criterio que Obras/Arbolado/Educación.

### 7. Permiso único `espaciosverdes.gestionar`, asignado a `administrador` y `agente`

Un solo permiso cubre alta y actualización de estado, mismo criterio que
`obras.gestionar`/`arbolado.gestionar`/`educacion.gestionar`: dar de alta
un espacio verde y actualizar su estado son la misma clase de trabajo
operativo de mantenimiento de espacios públicos, sin ninguna diferencia
real de sensibilidad fiscal, discrecional o de dato personal que amerite
separar los permisos. Se asigna a **ambos** roles de sistema. Un municipio
que quiera restringirlo más compone su propio rol ([ADR 0011](0011-autorizacion-por-roles-con-permisos-granulares.md)).

### 8. No se extrae abstracción compartida con `obras`/`arbolado`/`educacion`

Ver Contexto, última sección. `GestionDeEspaciosVerdes`,
`EspaciosVerdesController` y la tabla de transiciones se escriben desde
cero, sin depender de código de ningún otro módulo funcional (que además
es inalcanzable por convención de módulo) ni de una superclase/interfaz
compartida nueva.

## Alternativas consideradas

- **Elegir recolección de residuos o alumbrado público como esta
  rebanada**: descartadas — ver Contexto, sin cambios respecto de ADR
  0024.
- **No abrir una tercera rebanada de Fase 4 y dejarla en dos**: descartada
  — Espacios verdes queda disponible y viable desde ADR 0024, y no hay
  ningún motivo nuevo para seguir postergándola; mismo criterio que llevó
  a ADR 0028 a cerrar Fase 5 con una segunda rebanada en vez de dejarla en
  una sola.
- **Enum abierto/texto libre para `tipo`, mismo criterio que `especie` en
  Arbolado**: descartada — ver Decisión 3. A diferencia de una especie de
  árbol, la taxonomía de un espacio verde municipal es chica y estable.
- **Sin campo de superficie, para minimizar la rebanada**: descartada —
  ver Decisión 4 y Contexto. Sin él, esta rebanada repetiría el patrón sin
  ninguna dimensión de dato nueva, exactamente lo que ADR 0024 señaló como
  problema al descartarla en su momento.
- **Permitir `DISPONIBLE → CERRADO` directo**: más simple, pero borra del
  padrón un espacio disponible sin ningún estado que documente el motivo.
  Descartada — mismo criterio que Arbolado/Educación.
- **Extraer una abstracción común con `obras`/`arbolado`/`educacion` para
  el patrón "alta protegida + lectura pública + estado propio mutable"**,
  ahora con cuatro casos y una coincidencia topológica exacta entre dos de
  ellos: descartada — ver Contexto, última sección. La coincidencia es de
  forma, no de contenido, y el código realmente duplicado es de una línea
  por servicio.
- **Separar `espaciosverdes.registrar` de
  `espaciosverdes.actualizarEstado`**: descartada — mismo criterio que
  Obras/Arbolado/Educación, no hay diferencia real de sensibilidad entre
  las dos acciones.
- **Alta o reporte de estado por parte del vecino**: descartada. El
  registro lo controla el municipio porque es información oficial de
  gestión de espacios públicos, no un reporte ciudadano — eso ya lo cubre
  `reclamos` (categoría "espacios verdes"/similar, mismo criterio que ADR
  0023 §2 y ADR 0024 §2 ya aplicaron frente a Reclamos).

## Consecuencias

- `espaciosverdes` no depende de ningún otro módulo funcional; el test de
  modularidad de Spring Modulith lo verifica en el build.
- No hay ninguna ruta pública de escritura en este módulo, igual que
  `obras`/`arbolado`/`educacion`.
- Fase 4 — Gestión territorial queda con tres rebanadas demostrables
  (Obras Públicas, Arbolado urbano, Espacios verdes). Catastro,
  Planeamiento Urbano/Uso del Suelo, GIS como servicio consolidado,
  recolección de residuos y alumbrado público siguen diferidos, sin
  cambios respecto de ADR 0023/0024.
- Con `obras`, `arbolado`, `educacion` y `espaciosverdes` como cuatro
  instancias del mismo patrón sin abstracción común extraída (Decisión 8),
  un quinto caso que lo repita debería revisar de nuevo si ya conviene
  extraer algo — no se anticipa en esta ADR (ver Contexto, última
  sección, para el criterio de cuándo sí valdría la pena).
- El registro no lleva quién hizo cada cambio de estado ni motivo del
  cierre (solo `actualizadoEn`), mismo criterio que Obras/Arbolado/
  Educación.

## Pendiente de definir

- Motivo del cierre o del pase a mantenimiento (texto libre asociado al
  cambio de estado): no existe en esta rebanada.
- Quién hizo cada cambio de estado y cuándo, más allá de `actualizadoEn`
  (sin historial de movimientos, mismo criterio que Obras/Arbolado/
  Educación).
- Edición de `nombre`/`tipo`/`ubicacion`/`descripcion`/`superficie`
  después de creado el registro.
- Inventario de equipamiento del espacio verde (juegos, luminarias,
  bancos, riego) y recolección de residuos/alumbrado público como áreas
  de Ambiente y Servicios Públicos: siguen diferidos por depender de
  datos reales de un municipio piloto (Contexto).
- Rate limiting sobre las rutas de `espaciosverdes` (endurecimiento de
  seguridad diferido por [CLAUDE.md](../../../CLAUDE.md), aunque acá no
  hay ruta pública de escritura para abusar).
- Si un quinto módulo necesita el mismo patrón "alta protegida + lectura
  pública + estado propio mutable", evaluar en ese momento si conviene
  extraer algo común entre los cuatro casos existentes (Contexto, última
  sección).
