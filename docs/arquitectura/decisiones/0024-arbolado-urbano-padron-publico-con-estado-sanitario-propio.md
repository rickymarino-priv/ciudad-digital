# 0024 - Arbolado urbano: padrón público con estado sanitario propio, segunda rebanada de Fase 4 (Ambiente y Servicios Públicos)

- Estado: Aceptada
- Fecha: 2026-08-28

## Contexto

[ADR 0023](0023-obras-publicas-registro-publico-con-estado-propio-actualizable.md)
abrió Fase 4 — Gestión territorial con Obras Públicas (R19, CD-27) y dejó
**Ambiente y Servicios Públicos** explícitamente como "candidata natural de
la siguiente rebanada de la fase, no descartada por inviable" (ADR 0023,
Contexto). Toca elegir, dentro de esa área, la segunda rebanada
demostrable de la fase, con el mismo criterio que ADR 0021 y ADR 0023 ya
aplicaron al abrir Fase 3 y Fase 4: preferir lo que se puede diseñar sin
inventar normativa o datos reales específicos de un municipio, diferir lo
que sí los necesita hasta tener un piloto real.

El [catálogo funcional](../../producto/catalogo-funcional.md) (§3) describe
Ambiente y Servicios Públicos como "recolección de residuos, arbolado
urbano, espacios verdes, alumbrado público". Además, esta rebanada tiene
que aportar algo que el módulo `reclamos` (R6, [ADR 0014](0014-reclamos-ciudadanos-alta-publica-anonima-y-estado-propio.md))
no da ya: `reclamos` cubre justamente "poda/arbolado, recolección de
residuos, alumbrado" como categorías de reclamo de texto libre que carga
un vecino ([catálogo funcional](../../producto/catalogo-funcional.md) §1).
Un módulo nuevo que fuera "otra forma de mandar un reclamo de texto libre"
no aportaría nada distinto; tiene que ser un registro/catálogo propio del
dominio, controlado por el municipio, como ya lo es Obras Públicas frente
a un reclamo genérico sobre una obra.

Candidatas dentro de Ambiente y Servicios Públicos:

- **Recolección de residuos (calendario por zona)**: para tener valor real
  necesita zonas que reflejen el reparto geográfico real de un municipio
  (barrios, rutas de recolección) o un contrato vigente con una empresa
  recolectora (frecuencia, días, cobertura). Cualquiera de los dos, sin un
  municipio piloto real, se inventaría — mismo riesgo que ya descartó
  Planeamiento Urbano/Uso del Suelo en ADR 0023 (zonificación propia de
  cada municipio) y Compras en ADR 0021 (umbrales por ordenanza propia).
  Descartada para esta rebanada.
- **Alumbrado público**: el catálogo funcional ya lo modela como una
  categoría de reclamo del vecino (luminaria rota), no como un registro
  que el municipio quiera publicar y curar. Un padrón de luminarias como
  activo físico (qué poste, qué columna, qué circuito) es inventario de
  infraestructura real específico de cada municipio — mismo riesgo que
  Catastro (ADR 0023, Contexto: "parcelas, valuaciones y nomenclatura...
  no se pueden diseñar sin datos reales de un municipio sin inventarlos").
  Descartada.
- **Espacios verdes (registro de plazas/parques)**: viable en el mismo
  sentido de riesgo bajo que Obras Públicas (nombre, ubicación, tipo,
  estado, sin depender de normativa provincial). Se descarta igual, no por
  inviable sino porque es, en forma, casi el mismo ejercicio que Obras
  Públicas ya demostró (catálogo con nombre/ubicación/tipo/estado):
  agregarlo ahora no suma una dimensión de dominio nueva al producto, y
  esta fase ya usó ese patrón para abrirse. Queda disponible como
  candidata futura si hace falta una rebanada chica.
- **Arbolado urbano (padrón de árboles con estado sanitario)**: informa
  qué árboles tiene registrados el municipio, dónde y en qué estado
  sanitario, sin depender de normativa provincial ni de un contrato real
  (a diferencia de recolección de residuos) ni de un inventario de
  infraestructura eléctrica real (a diferencia de alumbrado). A diferencia
  de Espacios Verdes, agrega una dimensión de dominio genuinamente nueva:
  activos vivos con un ciclo de estado sanitario propio (plantado → sano →
  requiere intervención → retirado), no una variación de "obra con
  estado". Elegida.

## Decisión

### 1. Módulo nuevo `arbolado`, contratable, sin depender de otros módulos

`arbolado` es un módulo funcional propio ([ADR 0009](0009-modelo-comercial-y-entitlement.md)),
con su propio `DescriptorDeModulo` y prefijo `/api/arbolado`. No depende de
`obras` ni de ningún otro módulo funcional — mismo criterio de
independencia que ADR 0023 §1 ya estableció para `obras`, verificado por
el mismo test de modularidad de Spring Modulith.

### 2. Mismo mecanismo de alta protegida / lectura pública que Obras Públicas, sin ADR nuevo para esa parte

`POST /api/arbolado` requiere sesión y el permiso `arbolado.gestionar`: el
registro lo origina el municipio, nunca el vecino — mismo criterio que
Obras Públicas (ADR 0023 §2) y que Multas (ADR 0021 §3): es información
oficial de gestión (qué árboles tiene registrados el municipio y en qué
estado), no un reporte ciudadano — eso ya lo cubre `reclamos`. `GET
/api/arbolado` es lectura pública sin sesión (`rutasDeLecturaPublica()`,
[ADR 0012](0012-declaracion-de-modulos-y-gating-por-ruta.md) §1), con
filtro opcional por `estado` y por texto (`q`) sobre `especie`/`ubicacion`
— mismo patrón `ILIKE` que Obras/Boletín. Sin identificador obligatorio de
búsqueda: es un registro público general, no una consulta puntual sobre un
dato del vecino.

No hay `rutasDeEscrituraPublica()`: ninguna mutación pública/anónima, igual
que Obras (ADR 0023 §2) — a diferencia de Reclamos.

### 3. Especie y ubicación son texto libre, no un catálogo fijo

`especie` (por ejemplo "Jacarandá", "Fresno americano") y `ubicacion` (por
ejemplo "Vereda de Av. San Martín 450") son texto libre, sin un enum
cerrado de especies ni geolocalización estructurada. Mismo criterio que
`ubicacion` en Obras (ADR 0023 §6) y `direccion` en Reclamos (ADR 0014
§5): un catálogo fijo de especies típicas de una zona sería, en la
práctica, inventar qué especies planta un municipio real que todavía no
existe como piloto — el mismo riesgo que llevó a descartar Catastro y
Planeamiento Urbano, aplicado acá a una escala menor. Texto libre lo evita
sin perder la función de búsqueda (`q` matchea `especie` y `ubicacion`).

A diferencia de Obras, este módulo **no** tiene un enum de "tipo": la
única enumeración fija es el estado sanitario (Decisión 4). No se agrega
un enum de tipo de árbol/especie sin un caso real que lo justifique.

### 4. Estado sanitario propio: enum fijo + tabla de transiciones en el servicio, mismo patrón que Obras/Multas/Reclamos

`EstadoDeArbol` es un enum de cuatro valores con una tabla de transiciones
codificada en el servicio, sin entidad de historial — mismo patrón que
`EstadoDeObra` (ADR 0023 §3), no el motor de expediente de [ADR 0015](0015-motor-de-expediente-workflow-minimo.md):

```
PLANTADO             → SANO
SANO                 → REQUIERE_INTERVENCION
REQUIERE_INTERVENCION → SANO
REQUIERE_INTERVENCION → RETIRADO
```

`RETIRADO` es terminal. Un árbol `SANO` no pasa directo a `RETIRADO`: tiene
que pasar primero por `REQUIERE_INTERVENCION`. A diferencia de la razón
que dio ADR 0023 §3 para `PARALIZADA → EN_EJECUCION` únicamente (evitar la
ambigüedad de "finalizada" para algo que nunca se retomó), acá la razón es
otra: un árbol no desaparece del padrón público sin que quede un estado
intermedio que documenta que hubo un motivo antes del retiro — evita que
un árbol registrado como sano se borre del seguimiento sin ningún rastro
de por qué. (No hay todavía un campo de texto para ese motivo — ver
Pendiente de definir.)

`PATCH /api/arbolado/{id}/estado` requiere sesión y `arbolado.gestionar`;
no está en `rutasDeEscrituraPublica()`.

### 5. Permiso único `arbolado.gestionar`, asignado a `administrador` y `agente`

Un solo permiso cubre alta y actualización de estado, mismo criterio que
`obras.gestionar` (ADR 0023 §5): registrar un árbol nuevo y actualizar su
estado sanitario son la misma clase de trabajo operativo de campo (el
catálogo funcional describe Ambiente y Servicios Públicos con "buen
candidato a optimización de rutas", trabajo de cuadrillas, no de
gabinete), sin una diferencia real de sensibilidad fiscal o discrecional
que amerite separar los permisos como sí hace Multas
([ADR 0021](0021-multas-de-transito-alta-protegida-estado-propio-descuento-por-pago-temprano.md)
§3/§4).

Se asigna a **ambos** roles de sistema. Un municipio que quiera
restringirlo más compone su propio rol ([ADR 0011](0011-autorizacion-por-roles-con-permisos-granulares.md)).

### 6. Sin geolocalización estructurada ni GIS, sin certificaciones, sin adjuntos

Mismos motivos que Obras Públicas (ADR 0023 §6/§7/§8), reafirmados acá:
GIS como servicio consolidado sigue sin existir en el producto; no hay
dato de costo/contratista de poda o tala (eso, si existiera, sería
Presupuesto y Contabilidad, todavía diferido); no hay carga de fotos del
estado del árbol en esta rebanada.

### 7. No se extrae abstracción compartida con `obras` todavía

[ADR 0023](0023-obras-publicas-registro-publico-con-estado-propio-actualizable.md)
(Consecuencias) dejó planteado que, si un segundo módulo necesitaba el
mismo patrón "alta protegida + lectura pública + estado propio mutable",
se decidiría en ese momento si valía la pena extraer algo común. `arbolado`
es ese segundo caso real. Decisión: **no se extrae nada todavía**.
`GestionDeArbolado`, `ArboladoController` y la tabla de transiciones de
`arbolado` se escriben desde cero, sin depender de ningún código de
`obras.internal` (que además es inalcanzable por convención de módulo) ni
de una superclase/interfaz compartida nueva. Con dos instancias del patrón
no hay todavía evidencia de qué parte generalizaría bien (la forma de la
tabla de transiciones ya varía entre los dos: cuatro estados pero con
reglas de negocio distintas); se decide extraer con un tercer caso real
delante, mismo criterio que [ADR 0015](0015-motor-de-expediente-workflow-minimo.md)
§5 y [ADR 0021](0021-multas-de-transito-alta-protegida-estado-propio-descuento-por-pago-temprano.md)
§2 ya aplicaron para no anticipar abstracciones sin un segundo/tercer caso
real.

## Alternativas consideradas

- **Elegir recolección de residuos, alumbrado público o espacios verdes
  como esta rebanada**: ver Contexto.
- **Enum fijo de especies en vez de texto libre**: descartada — ver
  Decisión 3. Inventaría qué especies planta un municipio real.
- **Permitir `SANO → RETIRADO` directo**: más simple, pero borra del
  padrón un árbol sano sin ningún estado que documente el motivo.
  Descartada — ver Decisión 4.
- **Reutilizar la tabla de transiciones o el servicio de `GestionDeObras`**:
  descartada — ver Decisión 7 y Decisión 4 (las reglas de transición no
  son las mismas: Obras permite `EN_EJECUCION → FINALIZADA` directo,
  Arbolado no permite el equivalente `SANO → RETIRADO` directo).
- **Separar `arbolado.registrar` de `arbolado.actualizarEstado`**: mismo
  patrón que Multas — descartada por Decisión 5, no hay diferencia real de
  sensibilidad entre las dos acciones.
- **Alta o reporte de estado por parte del vecino**: descartada. El
  registro lo controla el municipio porque es información oficial del
  padrón, no un reporte ciudadano — eso ya lo cubre `reclamos` (mismo
  criterio que ADR 0023 §2 aplicó para Obras frente a Reclamos).

## Consecuencias

- `arbolado` no depende de ningún otro módulo funcional; el test de
  modularidad de Spring Modulith lo verifica en el build.
- No hay ninguna ruta pública de escritura en este módulo, igual que
  `obras`.
- El registro no lleva quién hizo cada cambio de estado ni el motivo de un
  retiro (solo `actualizadoEn`) — ver Pendiente de definir.
- Ambiente y Servicios Públicos queda con dos temas todavía sin
  rebanada propia: recolección de residuos y alumbrado público, ambos
  diferidos por depender de datos reales de un municipio piloto (Contexto).
  Espacios verdes queda disponible como candidata futura, no descartada
  por inviable.
- Con `obras` y `arbolado` como los dos primeros casos del patrón "alta
  protegida + lectura pública + estado propio mutable" sin extraer nada en
  común (Decisión 7), un tercer caso que repita el patrón debería revisar
  si ya conviene extraer algo — no se anticipa en esta ADR.

## Pendiente de definir

- Motivo del retiro o de la intervención (texto libre asociado al cambio
  de estado): no existe en esta rebanada, solo el estado y `actualizadoEn`.
- Quién hizo cada cambio de estado y cuándo, más allá de `actualizadoEn`
  (sin historial de movimientos, mismo criterio que Obras).
- Edición de `especie`/`ubicacion`/`descripcion`/`fechaDePlantacion`
  después de creado el registro.
- Geolocalización estructurada / GIS como servicio: sigue dependiendo de
  que exista GIS como servicio consolidado o de que un tercer módulo lo
  justifique (ADR 0023, Pendiente de definir, reafirmado acá).
- Adjuntos/fotos del estado del árbol.
- Recolección de residuos y alumbrado público como áreas de Ambiente y
  Servicios Públicos: siguen diferidos por depender de datos reales de un
  municipio piloto (Contexto). Espacios verdes sigue disponible como
  candidata futura, no descartada por inviable.
- Si un tercer módulo necesita el mismo patrón "alta protegida + lectura
  pública + estado propio mutable", evaluar en ese momento si conviene
  extraer algo común entre `obras` y `arbolado` (Decisión 7).
