# 0031 - Defensa Civil: alertas públicas y recursos, primera rebanada sin fase fija

- Estado: Aceptada
- Fecha: 2026-09-01

## Contexto

El [roadmap](../../producto/roadmap-fases.md#sin-fase-fija) deja **Seguridad
/ Defensa Civil** y **Bromatología** como candidatos "a Fase 4-5, dependiendo
de la prioridad que les dé el municipio piloto que se consiga" — sin
municipio piloto real todavía, no hay ninguna prioridad real que consultar.
Toca elegir con el mismo criterio de descarte razonado que ya aplicaron
ADR 0021 (abrió Fase 3), ADR 0023 (abrió Fase 4) y ADR 0026 (abrió Fase 6):
preferir lo que se puede diseñar sin inventar normativa ni datos reales de un
municipio, sobre lo que sí los necesita.

Se elige **Seguridad / Defensa Civil** sobre Bromatología por alcance
disponible sin piloto: Bromatología (control de comercios, habilitaciones,
control alimentario) necesita un circuito de inspección con acta,
infracción y plazo de subsanación — normativa bromatológica que varía por
municipio/provincia, mismo riesgo que ya descartó Compras (ADR 0021) y
Catastro (ADR 0023). Queda disponible como candidata futura bajo el mismo
Epic sin fase fija (CD-36), no descartada por inviable.

### Qué cubre "Seguridad / Defensa Civil" en esta rebanada, y qué no

El [catálogo funcional](../../producto/catalogo-funcional.md) §3 describe el
área como "cámaras, monitoreo de emergencias, protocolos — complejidad alta,
integraciones con hardware/CCTV". Esa descripción mezcla dos problemas de
naturaleza muy distinta:

- **Seguridad/vigilancia** (cámaras, CCTV, monitoreo en tiempo real):
  requiere integración con hardware físico que este producto no tiene
  ningún patrón para resolver (ni siquiera una integración con un sistema
  externo por API, que sí tiene precedentes fallidos de alcance como
  facturación ARCA). Fuera de alcance de esta rebanada y de cualquier
  rebanada cercana: es, en sí mismo, un problema de integración de
  infraestructura, no de dominio.
- **Defensa Civil** (alertas, recomendaciones, recursos de emergencia):
  información institucional que el municipio ya produce hoy por otros
  medios (redes, prensa, cartelería) sin depender de ningún hardware ni de
  normativa municipal específica. Es el recorte de esta rebanada.

Por ese motivo, el módulo se llama `defensacivil`, no `seguridad`: nombrarlo
"Seguridad" prometería una cobertura (vigilancia, protocolos policiales) que
esta rebanada no construye — mismo criterio que ADR 0027 §"Prensa y
Comunicación: gacetillas, no gestión de redes" ya aplicó para no prometer de
más con el nombre de un módulo.

Dentro de Defensa Civil, tres candidatas:

- **Alertas y recomendaciones públicas** (estado meteorológico, riesgo de
  inundación, ola de calor, con recomendaciones): sin dato personal, sin
  GIS, sin normativa específica de un municipio — el nivel de alerta usa
  una convención real ya establecida en Argentina (amarillo/naranja/rojo
  del Servicio Meteorológico Nacional), no una escala inventada para esta
  demo. Aporta una dimensión de dominio genuinamente nueva: **contenido con
  vigencia** (una alerta está vigente o finalizada, y solo importa mostrar
  con fuerza lo vigente), a diferencia de todo el contenido público
  construido hasta ahora (`boletin`/`prensa`: inmutable desde que se
  publica, sin estado). Elegida.
- **Registro de recursos de Defensa Civil** (refugios, puntos de encuentro,
  centros de acopio): informativo, sin dato de personas — mismo perfil de
  riesgo bajo que Obras/Arbolado/Espacios Verdes (nombre, ubicación, tipo,
  estado). Igual que ADR 0024 señaló para Educación municipal frente a
  Obras/Arbolado, por sí solo no aportaría una dimensión de dominio nueva:
  sería, otra vez, el mismo catálogo nombre/ubicación/tipo/estado. Se
  incluye igual **junto con** Alertas, no como rebanada propia: es el
  complemento natural de una alerta ("¿y ahora adónde voy?"), la misma
  pantalla de Defensa Civil gana sentido real con las dos cosas juntas, y
  el costo marginal de agregarlo es bajo porque reutiliza el mismo
  mecanismo de alta protegida/lectura pública/estado propio que Alertas ya
  necesita. Elegida, como segunda entidad del mismo módulo.
- **Reporte ciudadano de situación de riesgo** (árbol caído, poste
  peligroso, zona anegada): descartada por solapamiento real, no aparente,
  con `reclamos` (R6, [ADR 0014](0014-reclamos-ciudadanos-alta-publica-anonima-y-estado-propio.md)):
  ese módulo ya cubre exactamente este caso de uso — un vecino reporta sin
  cuenta una situación puntual del espacio público, con categoría de texto
  libre y estado de gestión — y `reclamos` ni siquiera necesita una
  categoría nueva para admitir "árbol caído" o "zona anegada": ya son
  variantes de las categorías que declara (poda/arbolado, alumbrado,
  espacio público). Construir un segundo canal de alta ciudadana con la
  misma forma, en un módulo distinto, sería exactamente el antipatrón que
  ADR 0024 (Contexto) ya evitó para Ambiente y Servicios Públicos:
  "otra forma de mandar un reclamo de texto libre" no aporta nada nuevo.
  Descartada, no por trivial sino por redundante.

### Minimización de datos: por qué esta rebanada no repite el problema de ADR 0025

A diferencia de Fase 5 (ADR 0025), acá no hay ninguna tensión de
minimización que resolver: ni Alertas ni Recursos guardan un dato de una
persona identificable. Una alerta es información pública dirigida a toda la
población de una zona, no a un individuo; un recurso es una ubicación
física gestionada por el municipio (un edificio, un punto de encuentro), no
la ubicación de una persona vulnerable. `capacidad` de un refugio es un
número agregado (cuántas personas caben), nunca quién está ahí. No hay
ningún campo de contacto de un vecino en ningún lado de este módulo: quien
"contacta" acá es el municipio informando, no un vecino identificándose.
Esto es, a propósito, lo opuesto del riesgo que la tarea de esta rebanada
señala explícitamente ("ubicación de personas vulnerables, denuncias con
datos de terceros"): ninguna de las dos entidades de este módulo se acerca
a ese perfil de dato.

Ningún ADR previo decide contenido público con estado de **vigencia**
(vigente/finalizada) en vez de estado de **gestión** (nuevo → en proceso →
resuelto) o de **publicación inmutable** (boletín/prensa), ni un nivel de
severidad tomado de una convención externa real en vez de inventado para el
producto.

## Decisión

### 1. Módulo nuevo `defensacivil`, contratable, con dos entidades independientes

`defensacivil` es un módulo funcional propio
([ADR 0009](0009-modelo-comercial-y-entitlement.md)), con su propio
`DescriptorDeModulo`, `código = "defensacivil"`, `nombre = "Defensa Civil"`
(no "Seguridad", ver Contexto) y prefijo `/api/defensacivil`. No depende de
ningún otro módulo funcional. Dos entidades en `defensacivil.internal`,
ambas sin columna de tenant (aisladas por base física, ADR 0001):

- `AlertaDeDefensaCivilEntity` (tabla `alerta_defensa_civil`).
- `RecursoDeDefensaCivilEntity` (tabla `recurso_defensa_civil`).

Ninguna depende de la otra a nivel de esquema (sin clave foránea entre
ambas): una alerta no referencia un recurso concreto ni viceversa, son dos
catálogos independientes que comparten pantalla por afinidad de dominio, no
por relación de datos.

### 2. Alta protegida / lectura pública en ambas entidades, mismo mecanismo que Obras/Arbolado/Espacios Verdes

`POST /api/defensacivil/alertas` y `POST /api/defensacivil/recursos`
requieren sesión y el permiso `defensacivil.gestionar`: el municipio
origina ambos registros, nunca el vecino — es información oficial que el
municipio decide publicar, no un canal de reporte ciudadano (ese ya existe,
es `reclamos`, ver Contexto). `GET /api/defensacivil/alertas` y `GET
/api/defensacivil/recursos` son lectura pública sin sesión
(`rutasDeLecturaPublica()`, [ADR 0012](0012-declaracion-de-modulos-y-gating-por-ruta.md)
§1). Sin `rutasDeEscrituraPublica()`: ninguna mutación pública/anónima,
igual que Obras/Arbolado/Espacios Verdes/Educación/Eventos.

### 3. Un único permiso `defensacivil.gestionar` para las dos entidades, no separado por sensibilidad

A diferencia de Desarrollo Social (ADR 0025 §7), que separó permisos porque
había una diferencia real de sensibilidad entre el catálogo público de
programas y las inscripciones con dato personal, acá **no existe esa
diferencia**: Alertas y Recursos son, ambas, información institucional
pública sin dato de persona identificable (ver Contexto). Separar el
permiso igual, solo por tratarse de dos entidades distintas, agregaría una
distinción sin una razón de sensibilidad real detrás — mismo criterio que
ya evitó dividir `arbolado.gestionar`/`obras.gestionar` entre alta y
cambio de estado (ADR 0024 §5). `defensacivil.gestionar` cubre alta y
cambio de estado de ambas entidades, asignado a `administrador` y
`agente`: personal de Defensa Civil operando en el terreno, mismo nivel de
confianza operativo que Obras/Arbolado/Multas.labrar.

### 4. Alerta: nivel de severidad tomado de la convención real del SMN, no inventado

`AlertaDeDefensaCivilEntity`:

- `tipo`: enum cerrado `METEOROLOGICA, INUNDACION, OLA_DE_CALOR, INCENDIO,
  OTRA` — alcanza para separar los motivos más comunes de una alerta de
  Defensa Civil municipal sin inventar un nomenclador más fino, mismo
  criterio que `CategoriaDeGacetilla`/`TipoDeActividad`.
- `nivel`: enum cerrado `AMARILLO, NARANJA, ROJO`. No es una escala
  inventada para este producto: es la clasificación de alertas
  meteorológicas ya en uso público en Argentina (Servicio Meteorológico
  Nacional), lo que evita el riesgo que el proyecto viene evitando en cada
  rebanada de inventar un criterio que un municipio piloto real después
  contradiga (mismo motivo por el que ADR 0021 §8 marca sus propios
  porcentajes como "provisorios pero necesarios" — acá, en cambio, no hace
  falta esa salvedad porque la escala ya es un estándar externo real, no
  una invención del producto).
- `titulo` (obligatorio, largo máximo 300, igual criterio que
  `NormaEntity`/`GacetillaEntity`), `descripcion` (texto obligatorio: qué
  está pasando), `recomendaciones` (texto obligatorio: qué tiene que hacer
  el vecino) — separados en dos campos en vez de uno solo porque cumplen
  función distinta en la pantalla pública (qué pasa vs. qué hacer), mismo
  espíritu de separar campos por función que `descargoTexto`/
  `resolucionComentario` en Multas (ADR 0021 §5).
- `zonaAfectada`: texto libre, opcional, largo máximo 300 — mismo criterio
  que `ubicacion` en Arbolado (ADR 0024 §3): sin geolocalización
  estructurada ni GIS, que este producto todavía no tiene.
- `estado`: enum `VIGENTE, FINALIZADA`. Nace siempre `VIGENTE` (no es un
  parámetro del alta). Única transición válida: `VIGENTE → FINALIZADA`
  (terminal, sin retorno) — misma topología de un solo salto que
  `EstadoDeEvento` (ADR 0030 §"decide también una topología de estado
  nueva"), justificada acá por el mismo motivo: una alerta finalizada no
  vuelve a estar vigente, si la situación se repite el municipio publica
  una alerta nueva (mismo criterio que Boletín/Gacetillas: se corrige
  publicando de nuevo, no revirtiendo lo ya publicado).
- `publicadoPorNombre`/`publicadoPorEmail`: copia del actor autenticado
  (ADR 0013), mismo criterio que el resto del proyecto.
- `creadoEn`, `actualizadoEn`.

No hay campo de "área geográfica estructurada" ni de "población afectada
estimada": `zonaAfectada` en texto libre alcanza para esta rebanada, mismo
criterio de no inventar estructura sin un municipio real que la pida.

### 5. Recurso: catálogo informativo con estado operativo simple, mismo patrón que Programa Social sin el problema de sensibilidad

`RecursoDeDefensaCivilEntity`:

- `tipo`: enum cerrado `REFUGIO, PUNTO_DE_ENCUENTRO, CENTRO_DE_ACOPIO,
  OTRO`.
- `nombre` (obligatorio, largo máximo 200), `direccion` (texto libre,
  obligatorio, largo máximo 300, mismo criterio que `ubicacion` en
  Arbolado/Obras — sin GIS), `capacidad` (entero, opcional, sin relación
  con ninguna persona: es la capacidad física del lugar, no quién está
  ahí), `telefonoContacto` (texto libre, opcional, largo máximo 50 —
  contacto institucional del recurso, no de una persona), `descripcion`
  (texto, opcional).
- `estado`: enum `ACTIVO, INACTIVO`, transición libre en ambos sentidos
  (mismo criterio que `EstadoDePrograma` en Desarrollo Social, ADR 0025
  §3: un refugio se activa y se desactiva según la situación, no hay una
  progresión unidireccional que modelar). Nace siempre `ACTIVO`.
  `estadoNuevo` igual al estado actual se rechaza con `SolicitudInvalida`
  (no hay ninguna transición "a sí mismo" en ningún módulo previo del
  proyecto; se mantiene la misma expectativa acá).
- `publicadoPorNombre`/`publicadoPorEmail`, `creadoEn`, `actualizadoEn`:
  mismo criterio que el resto del proyecto.

### 6. Filtros de lectura pública: mismo patrón `ILIKE` combinable que el resto del proyecto

`GET /api/defensacivil/alertas` acepta `tipo`, `nivel`, `estado` y `q`
(ILIKE sobre `titulo`/`descripcion`), todos opcionales y combinables (AND).
`GET /api/defensacivil/recursos` acepta `tipo`, `estado` y `q` (ILIKE sobre
`nombre`/`direccion`). Ambos ordenan por `creadoEn` descendente, el orden
por defecto del proyecto (a diferencia de Eventos, ADR 0030 §4: acá no hay
una noción de "próximo en el tiempo" que ordenar, una alerta no tiene fecha
de vigencia futura programada, nace vigente en el momento en que se
publica). Ningún filtro es obligatorio: a diferencia de Multas (búsqueda
por patente/DNI), acá no hay ningún riesgo de exponer datos de terceros al
listar sin filtro — es exactamente el mismo perfil que Obras/Arbolado.

### 7. Sin geolocalización, sin adjuntos, sin notificación push, sin integración con hardware

Mismos motivos que todos los módulos anteriores con registro público (ADR
0023 §6/§7/§8, ADR 0024 §6, ADR 0025 §9, ADR 0027 §4, ADR 0030 §8): sin GIS
(zona/dirección son texto libre), sin fotos ni documentos adjuntos, sin
integración con sistemas de alerta temprana ni con notificaciones push/SMS
al vecino (el motor de notificaciones de ADR 0013 sigue pendiente de un
consumidor real). Sin ninguna integración con cámaras, sensores o hardware
de monitoreo (ver Contexto): esta rebanada es información editorial que el
municipio carga a mano, no un sistema de vigilancia.

## Alternativas consideradas

- **Elegir Bromatología en vez de Seguridad/Defensa Civil**: descartada —
  ver Contexto, necesita normativa de inspección específica por
  municipio/provincia sin piloto real que la valide.
- **Cubrir "cámaras, monitoreo de emergencias, protocolos" tal cual lo
  describe el catálogo funcional**: descartada — ver Contexto, es un
  problema de integración de hardware/CCTV, no de dominio, sin ningún
  patrón de integración externa en el proyecto.
- **Reporte ciudadano de situación de riesgo** (árbol caído, poste
  peligroso, zona anegada): descartada por solapamiento real con
  `reclamos` — ver Contexto.
- **Registro de recursos como rebanada propia, sin Alertas**: descartada,
  mismo motivo que ADR 0024 descartó Educación municipal por sí sola — no
  aporta una dimensión de dominio nueva frente a Obras/Arbolado/Espacios
  Verdes. Se incluye junto con Alertas, no solo.
- **Nombrar el módulo `seguridad`**: descartada — ver Contexto, prometería
  una cobertura (vigilancia, protocolos) que esta rebanada no construye.
- **Escala de nivel de alerta inventada para el producto** (por ejemplo,
  `BAJO/MEDIO/ALTO`): descartada — ver Decisión 4. Existe una convención
  real (SMN) que cumple la misma función sin inventar nada.
- **Permiso separado `defensacivil.publicarAlertas` /
  `defensacivil.gestionarRecursos`**, por simetría con Desarrollo Social:
  descartada — ver Decisión 3. No hay una diferencia real de sensibilidad
  entre las dos entidades que la justifique.
- **Alerta con estado de gestión (`nuevo → en_proceso → resuelto`), igual
  que Reclamos**: descartada. Una alerta de Defensa Civil no es un caso que
  alguien "gestiona" hasta resolverlo — es un aviso que rige mientras la
  situación persiste y se da de baja cuando termina. `VIGENTE/FINALIZADA`
  refleja eso; un flujo de gestión de tres o cuatro pasos no tendría
  sentido de negocio acá.
- **Alerta inmutable, sin estado, igual que Boletín/Gacetillas**:
  descartada. A diferencia de una norma o una gacetilla, una alerta pierde
  vigencia con el tiempo y el municipio necesita poder decirlo
  explícitamente (por ejemplo, cuando pasa la tormenta) sin que quede
  mostrada como si siguiera rigiendo — de ahí la Decisión 4.

## Consecuencias

- `defensacivil` no depende de ningún otro módulo funcional; el test de
  modularidad de Spring Modulith lo verifica en el build.
- Primer módulo del proyecto con contenido público que tiene **vigencia**
  (`VIGENTE`/`FINALIZADA`) en vez de estado de gestión o publicación
  inmutable — un precedente distinto de Reclamos/Multas/Obras/Arbolado
  (estado de gestión) y de Boletín/Prensa (inmutable).
- Bromatología queda disponible como candidata futura del mismo Epic sin
  fase fija (CD-36), no descartada por inviable.
- "Seguridad" en el sentido de vigilancia/CCTV/protocolos policiales queda,
  a propósito, fuera de cualquier alcance previsible de este producto hasta
  que exista un patrón de integración con hardware externo — no es un
  pendiente de esta rebanada, es un problema de otra naturaleza.
- El costo de abuso de un alta protegida con datos falsos (una alerta o un
  recurso inventado) requiere sesión y permiso, a diferencia de los módulos
  con escritura pública (Reclamos/Desarrollo Social/Multas/pago): el riesgo
  es el mismo que ya acepta el proyecto para cualquier cuenta de agente
  comprometida, no un riesgo nuevo de este módulo.

## Pendiente de definir

- Notificación push/SMS al vecino cuando se publica o finaliza una alerta:
  depende del motor de notificaciones (ADR 0013), sin consumidor real
  todavía.
- Geolocalización estructurada de `zonaAfectada`/`direccion`: depende de
  que exista GIS como servicio consolidado (mismo pendiente que arrastra el
  proyecto desde Reclamos, ADR 0014 §5).
- Integración con fuentes externas de alerta temprana (SMN, organismos
  provinciales): el nivel `AMARILLO/NARANJA/ROJO` toma la convención real
  del SMN, pero esta rebanada no integra con ninguna API externa — el
  municipio carga la alerta a mano.
- Reapertura de una alerta `FINALIZADA` (por ejemplo, si la situación
  reaparece antes de que valga la pena distinguirla como un aviso nuevo):
  no existe en esta rebanada; el criterio elegido es publicar una alerta
  nueva.
- Motivo de la finalización de una alerta o de la inactivación de un
  recurso (texto libre asociado al cambio de estado): no existe en esta
  rebanada, solo el estado y `actualizadoEn`, mismo nivel de informalidad
  que Arbolado (ADR 0024, Pendiente de definir).
- Bromatología como próxima candidata del mismo Epic sin fase fija.
- Vigilancia/CCTV/monitoreo de emergencias (ver Consecuencias): sin patrón
  de integración de hardware en el proyecto, no se anticipa cuándo se
  aborda.
