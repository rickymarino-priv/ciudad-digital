# 0016 - Datos propios de un tipo de trámite: columnas explícitas nullable en `expediente`, no JSON ni tabla por tipo

- Estado: Aceptada
- Fecha: 2026-08-25

## Contexto

[ADR 0015](0015-motor-de-expediente-workflow-minimo.md) §3 dejó
explícitamente pendiente la forma de los datos propios de cada tipo de
trámite ("¿JSON como `config` del [ADR 0007](0007-modelo-de-datos-del-tenant.md)?
¿tabla propia por tipo?"), a resolver "cuando el segundo tipo de trámite
aparezca y obligue a elegir con un caso real delante, no antes".

R10 (CD-18) es esa rebanada: suma **habilitación comercial simple** y
**permiso de obra menor** a Mesa de Entradas, completando el subset de
Trámites a Distancia del [roadmap](../../producto/roadmap-fases.md#fase-1--mvp-vendible--módulos-ancla)
(certificado de domicilio + estos dos). Con tres tipos reales delante, ya
hay información suficiente para decidir.

Cada tipo de trámite tiene hoy pocos campos propios (1 a 2): el
certificado de domicilio ya tenía `domicilioACertificar`; habilitación
comercial simple necesita rubro y dirección del local; permiso de obra
menor necesita dirección y descripción de la obra. No hay, todavía, un
caso de un tipo de trámite con muchos campos variables ni con estructura
anidada.

## Decisión

Los datos propios de cada tipo de trámite son **columnas explícitas y
nullable** en la propia tabla `expediente`, una columna por dato, con un
`check` de base de datos que la exige *solo* cuando `tipo` es el que la
usa (mismo mecanismo que ya usan los `check (tipo in (...))`/
`check (estado in (...))` de la migración V9). `ExpedienteEntity` expone
esos campos como propiedades normales (no un mapa ni un blob), y
`GestionDeExpedientes.iniciar()` valida, por `tipo`, cuáles son
obligatorios — exactamente el patrón que ya existía para
`domicilioACertificar`, ahora aplicado a los campos de los dos tipos
nuevos.

Es la misma decisión que el resto de los módulos de Fase 1 (Reclamos,
Boletín, Cementerio) ya toman para sus propios datos: entidades flat con
columnas explícitas, sin JSON de forma libre, en línea con el criterio de
[ADR 0014 §3](0014-reclamos-ciudadanos-alta-publica-anonima-y-estado-propio.md)
de no generalizar sin un caso real que lo pida. Con 3 tipos de trámite y
1-2 campos propios cada uno, columnas explícitas siguen siendo más simples
de validar, indexar y auditar que un JSON, y no hay hoy ninguna señal de
que la cantidad de tipos o de campos vaya a crecer lo suficiente como para
que el costo de una tabla ancha supere al de introducir una capa de
indirección nueva.

`EstadoDeExpediente` sigue siendo un enum **compartido** entre todos los
tipos (ADR 0015 §1): habilitación comercial simple agrega un estado nuevo
(`INSPECCION`) al enum común, usado únicamente por su propio
`CircuitoDeTramite` — agregar un estado al enum compartido no es tocar el
motor (`GestionDeExpedientes.avanzar` sigue siendo agnóstico), es
exactamente el tipo de extensión que ADR 0015 anticipaba como "agregar
código, no rediseñar".

## Alternativas consideradas

- **Columna JSON `datos` genérica** (ver ADR 0015 §3): más flexible a
  futuro, pero pierde el `check` de base de datos y la validación de tipo
  de columna, y ningún otro módulo del proyecto usa JSON para datos de
  negocio estructurados (el único precedente, `config` de `tenant`
  en ADR 0007, es configuración de plataforma, no datos de un trámite).
  Descartada por ahora: no hay un caso con suficientes campos variables o
  estructura anidada que la justifique. Si aparece un tipo de trámite con
  muchos campos opcionales y poco uso real de cada uno, vale la pena
  reabrir esta decisión con ese caso concreto delante.
- **Tabla propia por tipo** (`expediente_habilitacion_comercial`,
  `expediente_permiso_obra_menor`, con FK 1:1 a `expediente`): evita
  columnas nullable en la tabla común, pero obliga a un JOIN en cada
  lectura y a un repositorio/mapeo por tipo, más código para el mismo
  problema que 1-2 columnas nullable resuelven directo. Descartada por
  ahora con solo 1-2 campos por tipo; si un tipo futuro necesita muchos
  campos propios o una relación 1-a-muchos propia (no un valor escalar),
  conviene reconsiderar esta alternativa para ese tipo puntual, no
  migrar los tres tipos existentes de una.

## Consecuencias

- `expediente` crece una o dos columnas nullable por cada tipo de trámite
  nuevo, todas `null` para las filas de los demás tipos. Con 3 tipos y 5
  columnas propias en total (`domicilio_a_certificar`, `rubro_comercial`,
  `direccion_local`, `direccion_obra`, `descripcion_obra`) el ancho de la
  tabla sigue siendo manejable; si el catálogo de trámites crece mucho más
  allá del subset actual de Fase 1, esta decisión hay que revisarla con
  ese volumen delante (ver Pendiente de definir).
- El request/response de `POST /api/mesaentradas` y `GET /api/mesaentradas`
  necesariamente exponen los campos de los tres tipos, todos opcionales
  salvo los que exige el `tipo` de esa fila — el controller y el frontend
  ya no pueden asumir un único conjunto fijo de campos, tienen que ramificar
  por `tipo` (ver spec CD-18).
- Agregar un cuarto tipo de trámite sigue sin tocar el motor
  (`Expediente`, `MovimientoDeExpediente`, `GestionDeExpedientes.avanzar`):
  agrega un valor de enum, su circuito, sus columnas nullable con su
  `check`, y su rama en el request/response y el formulario — confirma la
  consecuencia que ADR 0015 ya declaraba.

## Pendiente de definir

- Si el catálogo de tipos de trámite crece mucho más allá del subset
  actual (3 tipos) o aparece un tipo con muchos campos propios o
  estructura anidada, reconsiderar JSON o tabla propia para ese tipo
  puntual, con ese caso real delante — no antes.
