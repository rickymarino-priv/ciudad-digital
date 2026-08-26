# 0021 - Multas de tránsito: alta protegida, estado propio sin reutilizar el motor de mesaentradas, descuento por pago voluntario temprano

- Estado: Aceptada
- Fecha: 2026-08-26

## Contexto

Fase 3 ([roadmap](../../producto/roadmap-fases.md#fase-3--compras-y-áreas-normativamente-pesadas))
agrupa "Compras y Contrataciones", "Presupuesto y Contabilidad", "Legal y
Técnica/Juzgado de Faltas" y "Tránsito y Transporte" bajo un mismo criterio:
son los módulos de mayor riesgo legal/normativo, mejor abordados con
feedback de municipios reales. Sin un piloto real todavía, se elige
**multas de tránsito** (Juzgado de Faltas) como primera rebanada de la fase,
por descarte razonado de las otras dos candidatas obvias:

- **Presupuesto y Contabilidad**: la Provincia de Buenos Aires (y otras)
  ya proveen gratis un sistema homologado (RAFAM), con adopción muy alta
  en los municipios bonaerenses. Construir esto ahora compite con algo
  gratuito y adoptado, sin saber si el municipio piloto necesita
  integrarse con RAFAM o reemplazarlo. Descartado para esta rebanada.
- **Compras y Contrataciones**: los montos que definen licitación
  pública/privada/concurso de precios/compra directa varían por provincia
  y por ordenanza municipal propia. Sin un municipio real, cualquier
  umbral sería inventado — mismo error que el roadmap ya advierte para
  toda la Fase 3. Descartado para esta rebanada.
- **Multas de tránsito**: el circuito (vecino consulta por patente/DNI,
  paga con descuento por pronto pago, o presenta un descargo que el
  Juzgado de Faltas resuelve) es consistente entre jurisdicciones
  argentinas, ciudadano-facing, demostrable, y reutiliza infraestructura
  ya construida (`pagos`, patrón de búsqueda pública por identificador de
  `tasas`). Elegido.

A diferencia de todo lo construido hasta ahora (`reclamos`, `mesaentradas`,
`proveedores`: el vecino inicia el registro), acá **el municipio inicia**:
un agente de tránsito labra un acta contra una patente/titular, y el vecino
solo consulta/paga/objeta después. Esto invierte la pregunta de acceso
público que [ADR 0014](0014-reclamos-ciudadanos-alta-publica-anonima-y-estado-propio.md)
resolvió para reclamos: ahí el problema era "cómo permitir un alta sin
cuenta"; acá el alta ya está protegida por diseño, y el problema es "cómo
dar acceso de lectura/pago/impugnación público sobre un registro que el
vecino no creó".

[ADR 0015](0015-motor-de-expediente-workflow-minimo.md) construyó un motor
de expediente/workflow (`Expediente` + `MovimientoDeExpediente` +
`CircuitoDeTramite`) para Mesa de Entradas, pensado para varios **tipos de
trámite**, cada uno con su propio circuito de estados, todos ellos
iniciados por un alta pública. Multas no encaja en ese problema: hay un
único "tipo" (el acta de infracción), su alta es protegida en vez de
pública, y su cierre tiene **dos vías** (pago o resolución de un descargo)
en vez de una progresión lineal de estados de gestión interna. Además,
`TipoDeTramite`, `EstadoDeExpediente`, `CircuitoDeTramite` y compañía viven
en `mesaentradas.internal` (ADR 0015 §5, decisión explícita de no extraer
un módulo transversal con un solo consumidor real) — no son alcanzables
desde otro módulo ni por diseño ni por el test de modularidad de Spring
Modulith que verifica los límites entre paquetes `internal`.

Ningún ADR previo decide alta protegida (no pública) de un registro
consultable después por el ciudadano, ni el criterio de descuento por pago
voluntario temprano, ni quién puede resolver una impugnación con impacto
fiscal.

## Decisión

### 1. Módulo nuevo `multas`, no una extensión de `mesaentradas`

`multas` es un módulo funcional propio y contratable
([ADR 0009](0009-modelo-comercial-y-entitlement.md)), con su propio
`DescriptorDeModulo` y prefijo `/api/multas`. No reutiliza el motor de
`mesaentradas` (Decisión 2) ni vive dentro de ese módulo: el motivo de
cambio es distinto (Juzgado de Faltas / tránsito, no trámites a distancia)
y, aunque lo fuera, el motor de ADR 0015 es código interno inalcanzable
desde otro módulo por decisión explícita de esa misma ADR.

### 2. Estado propio: enum fijo + tabla de transiciones en el servicio, mismo patrón que Reclamos (ADR 0014 §3), no el motor de ADR 0015

`EstadoDeMulta` es un enum con cinco valores y una única entidad
`MultaEntity` (sin tabla de movimientos/historial separada):

```
NOTIFICADA → PAGADA        (el vecino paga, con o sin descuento — Decisión 8)
NOTIFICADA → EN_DESCARGO    (el vecino presenta un descargo)
EN_DESCARGO → CONFIRMADA    (el municipio rechaza el descargo: la multa se mantiene)
EN_DESCARGO → ANULADA       (el municipio hace lugar al descargo: fin, no se debe nada)
CONFIRMADA → PAGADA         (el vecino paga la multa ya confirmada, sin descuento — Decisión 8)
```

`PAGADA` y `ANULADA` son estados terminales. Mientras una multa está
`EN_DESCARGO`, **no se puede pagar** (`GestionDeMultas.iniciarPago` rechaza
con `SolicitudInvalida` si el estado no es `NOTIFICADA` o `CONFIRMADA`):
cobrar un monto que todavía puede anularse por una impugnación en curso es
un error de negocio, no un caso de borde a permitir y arreglar después.

Se elige el patrón de Reclamos (enum + tabla de transiciones codificada,
sin motor genérico) y no el de Mesa de Entradas (`CircuitoDeTramite` por
tipo, `MovimientoDeExpediente` con historial) porque:

- Hay un único "tipo" de multa en esta rebanada: no hay hoy un segundo
  tipo de infracción con un circuito distinto que justifique la
  indirección de `CircuitoDeTramite` (mismo criterio "no generalizar sobre
  un único caso" que ADR 0014 §3 y ADR 0015 §3 ya aplican).
- El ciclo de vida de una multa tiene una bifurcación real (pago directo
  vs. descargo con dos desenlaces posibles) que una tabla de transiciones
  simple expresa sin necesitar una entidad de historial: a diferencia de
  un expediente de Mesa de Entradas, que puede tener varios pasos de
  gestión interna dignos de listarse uno por uno, una multa tiene como
  mucho un descargo (Decisión 5) y una resolución — dos campos en la
  propia fila alcanzan, mismo criterio que Reclamos usa un único
  `comentarioGestion` en vez de un historial de movimientos (ADR 0014 §3).
- Aunque no existiera el problema de alcanzabilidad entre módulos
  (`mesaentradas.internal`), forzar el modelo de Mesa de Entradas sobre un
  caso con alta protegida y dos vías de cierre sería adaptar un motor
  pensado para otro problema, no reutilizarlo genuinamente.

El costo declarado, simétrico al que ADR 0015 §5 ya aceptó: si en el
futuro aparece un segundo módulo con el mismo patrón "estado fijo + tabla
de transiciones", hoy se duplica en vez de reutilizarse. Igual que ADR
0015 §5, se prefiere no extraer una abstracción común con dos casos que ya
son, mirados de cerca, distintos entre sí (uno varía por tipo de trámite y
tiene alta pública; el otro tiene alta protegida y dos vías de cierre).

### 3. Alta protegida por permiso nuevo `multas.labrar`, asignado a `administrador` y `agente`

A diferencia de todo alta anterior del proyecto (siempre pública y
anónima, ADR 0014 §1 / ADR 0015 §4), `POST /api/multas` requiere sesión y
el permiso `multas.labrar` — no entra en `rutasDeEscrituraPublica()`: la
multa la origina el municipio, nunca el vecino.

`multas.labrar` se asigna a **ambos** roles de sistema, `administrador` y
`agente` (mismo criterio que `reclamos.ver`/`reclamos.gestionar`, ADR 0014
§8): labrar un acta es trabajo operativo cotidiano y de alto volumen de un
agente de tránsito en la calle, no un acto administrativo de gabinete.
Es, a propósito, una sensibilidad distinta de `tasas.publicar` (ADR 0018,
reservado solo a `administrador`): publicar una tasa es fijar una
obligación fiscal desde una función de back-office; labrar una multa es
constatar en el terreno una infracción ya definida por el código de
tránsito, función que el propio roadmap nombra explícitamente como "un
agente de tránsito municipal". El municipio que necesite restringirlo más
(por ejemplo, un rol "Agente de tránsito" separado de un agente
administrativo genérico) ya puede componerlo con roles propios (ADR 0011:
el municipio crea y edita sus propios roles); el seed de sistema no lo
anticipa sin un caso real que lo pida.

### 4. Resolución de un descargo: permiso nuevo `multas.resolverDescargo`, reservado solo a `administrador`

Confirmar o anular una multa en descargo es, a la vez, (a) un acto con
impacto fiscal directo — puede anular una deuda ya labrada, exactamente
el mismo tipo de decisión que ADR 0018 ya trató con cautela para
`tasas.publicar` (crear la deuda) — y (b) un acto de naturaleza
cuasi-judicial: es, literalmente, lo que resuelve un Juzgado de Faltas
sobre una impugnación, no una tarea operativa de rutina. Por ambos
motivos, más sensible que `tasas.publicar`, no menos: revierte lo que otro
agente ya constató, con criterio discrecional.

Se reserva `multas.resolverDescargo` **solo a `administrador`**, mismo
patrón de reserva que `tasas.publicar` (ADR 0018) y `boletin.publicar`/
`transparencia.publicar`. Un agente de tránsito puede labrar actas, pero
no puede anular las suyas ni las de otro agente.

### 5. Descargo: texto libre + contacto opcional, un único ciclo por multa, sin historial de movimientos

`MultaEntity` gana columnas propias para el descargo (`descargoTexto`,
`descargoContacto` opcional, `descargoPresentadoEn`) y para su resolución
(`resolucionComentario`, `resueltoPorNombre`/`resueltoPorEmail` — copia
del actor, mismo criterio "copia, no referencia" que ADR 0013/ADR 0015 §2
— y `resueltoEn`). No hay una tabla de movimientos: una multa admite **un
único descargo por ciclo** (presentar uno nuevo requiere que la multa
vuelva a `NOTIFICADA`, lo que esta rebanada no habilita — ver Pendiente de
definir). Es la misma simplificación deliberada que Decisión 2 ya
justifica: con un único descargo posible, un historial separado no aporta
nada sobre columnas planas en la propia fila.

`descargoContacto` es opcional y sin verificar, mismo criterio que
`contactoDelVecino` en Reclamos (ADR 0014 §4): dato informativo para que
el municipio pueda responder, no una identidad.

### 6. Búsqueda pública por patente o DNI, mismo patrón que `tasas` por número de cuenta

`GET /api/multas?patente=...` o `GET /api/multas?dni=...` (exactamente uno
de los dos parámetros, no ambos ni ninguno) es lectura pública
(`rutasDeLecturaPublica()`, ADR 0012 §1), igual mecanismo que
`GestionDeTasas.buscarPorCuenta`: el identificador de búsqueda es
obligatorio a propósito, nunca un filtro opcional — listar sin patente ni
DNI expondría todas las multas del municipio. No hay ningún otro endpoint
de listado público.

### 7. Pago: reutiliza `pagos`/`PasarelaDePago` tal cual (ADR 0018), sin extenderlo

`multas` depende de `pagos` exactamente como `tasas` lo hace hoy:
`POST /api/multas/{id}/pagos` y `POST /api/multas/pagos/confirmar` son
rutas de escritura pública (`rutasDeEscrituraPublica()`), mismo mecanismo,
mismos criterios de "referencia no adivinable como única verificación de
autenticidad" y "confirmación de pago no distingue formato inválido de no
encontrado" (ADR 0018 §4). No se modifica la interfaz `PasarelaDePago` ni
`PasarelaDePagoSimulada`.

### 8. Descuento por pago voluntario temprano: 20% dentro de los 10 días corridos desde la notificación, solo si no hubo descargo

Criterio de producto, no normativo (ningún municipio piloto real todavía
— mismo tipo de decisión "provisoria pero necesaria" que ADR 0018 §5 toma
para moneda/montos): **20% de descuento sobre el monto de la multa** si el
pago se inicia dentro de los **10 días corridos** desde `notificadaEn`,
**y** la multa nunca pasó por `EN_DESCARGO`. Una vez que la multa pasa por
`EN_DESCARGO` (haya sido `CONFIRMADA` o no), se pierde el derecho al
descuento para siempre, sin importar cuánto tiempo haya pasado: "pago
voluntario temprano" es, por definición, incompatible con haber
impugnado la multa — quien discute una multa ya no está haciendo el pago
voluntario que el descuento premia.

`GestionDeMultas.iniciarPago` calcula el monto a cobrar en el momento de
iniciar el pago (no al notificar ni al confirmar): `MultaEntity` guarda
`montoOriginal` (fijo, lo que labra el agente) y expone
`montoAPagar(Instant ahora)` que aplica el descuento solo si
`estado == NOTIFICADA && ahora.isBefore(notificadaEn.plus(10, DAYS))`.
`SolicitudDePago.monto` recibe ese valor calculado, nunca `montoOriginal`
directo.

Ambas constantes (`20%`, `10 días`) son literales en `GestionDeMultas`
(mismo nivel de informalidad que otros valores de producto sin normativa
real detrás en el proyecto, p. ej. los tramos poblacionales de ADR 0019):
si un municipio piloto real pide un porcentaje o plazo distinto, esta
decisión se revisa con ese caso delante, no antes.

## Alternativas consideradas

- **Reutilizar el motor de `mesaentradas`** (agregar `MULTA` como un
  `TipoDeTramite` más, con su `CircuitoDeTramite`): descartado por dos
  motivos independientes, cualquiera de los dos alcanza — (a) el motor
  vive en `mesaentradas.internal` por decisión explícita de ADR 0015 §5,
  inalcanzable para otro módulo sin romper el límite que Spring Modulith
  verifica en el build; (b) aunque fuera alcanzable, el modelo no encaja:
  `Expediente` asume alta pública (ADR 0015 §4) y una progresión lineal de
  estados de gestión, no una bifurcación pago/descargo con dos desenlaces.
- **Extraer un motor de "expediente" transversal ahora, del que
  `mesaentradas` y `multas` dependan**: exactamente el error que ADR 0015
  §5 ya evitó con un solo consumidor real; acá hay dos consumidores, pero
  con formas distintas (alta pública de un lado, protegida del otro; una
  vía de cierre de un lado, dos del otro) — generalizar sobre dos casos
  que difieren en las dos decisiones más importantes del modelo no ahorra
  diseño, lo empeora. Descartada; revisable si aparece un tercer caso más
  parecido a alguno de los dos.
- **Descuento calculado y "congelado" al momento de notificar la multa**
  (una columna `montoConDescuento` fija desde el alta): más simple de
  leer, pero pierde la propiedad de que el descuento depende de si
  finalmente hubo descargo o no, que solo se sabe después. Descartada:
  obligaría a "recalcular hacia atrás" el monto congelado si se presenta
  un descargo, más complejo que calcular el monto en el momento de pagar.
- **Permitir pagar con descuento aun si la multa fue `CONFIRMADA` dentro
  del plazo de 10 días**: técnicamente posible (el reloj no se detiene
  durante el descargo), pero premiaría con el descuento a quien impugnó y
  perdió, exactamente lo opuesto de lo que "pago voluntario temprano"
  busca incentivar. Descartada.
- **`multas.labrar` reservado solo a `administrador`**, igual que
  `tasas.publicar`: descartada porque asimila dos actos de sensibilidad
  distinta (fijar una obligación fiscal desde una oficina vs. constatar
  una infracción en la calle) solo porque ambos "crean una deuda" — ver
  Decisión 3.
- **`multas.resolverDescargo` asignado también a `agente`**: descartada
  por el argumento de Decisión 4 (impacto fiscal + naturaleza
  cuasi-judicial), consistente con cómo el proyecto ya trata actos
  fiscales sensibles (ADR 0018).

## Consecuencias

- `multas` depende de `pagos`, igual que `tasas`; no depende de
  `mesaentradas` ni de ningún otro módulo funcional. El test de
  modularidad de Spring Modulith lo verifica en el build.
- Un municipio que quiera que solo un subconjunto de agentes labre multas
  o que separe "agente de tránsito" de "agente" genérico tiene que crear
  su propio rol compuesto con `multas.labrar` (ADR 0011); el seed de
  sistema no lo hace por él.
- Presentar un segundo descargo sobre la misma multa no está soportado en
  esta rebanada (ver Pendiente de definir): una vez `CONFIRMADA` o
  `ANULADA`, el ciclo de esa multa terminó.
- El costo de abuso de las rutas de escritura pública (confirmación de
  pago con referencias inventadas) es el mismo, ya aceptado y diferido,
  que ADR 0018 §4 ya documenta para `tasas`.
- Si en el futuro aparece un segundo módulo con "estado fijo + tabla de
  transiciones + alta protegida", hay que decidir en ese momento si vale
  la pena extraer un patrón común con `multas`, con ese segundo caso
  real delante — no se anticipa acá.

## Pendiente de definir

- Reapertura de una multa `CONFIRMADA` para un segundo descargo, o
  reglamentación de plazos para presentar el descargo (hoy no hay límite
  de tiempo para pasar de `NOTIFICADA` a `EN_DESCARGO`, solo para el
  descuento de pago temprano).
- Notificación al vecino (email/SMS) de que se labró una multa a su
  nombre: hoy el único canal es que el vecino la busque por patente/DNI
  por su cuenta; integrarlo con el motor de notificaciones (ADR 0013)
  queda para cuando el módulo lo necesite de verdad.
- Identificación real del titular de una patente (hoy `patente` y `dni`
  son texto libre que el agente carga a mano, sin padrón de conductores
  ni de vehículos real, mismo nivel de informalidad que
  `padronfiscal`/ADR 0020 antes de tener un municipio piloto real).
- Notificación fehaciente del acta (cédula, notificación por edictos,
  plazos procesales de la Ley de Procedimiento Administrativo provincial
  correspondiente): fuera de alcance, es exactamente el tipo de riesgo
  normativo por provincia que el roadmap de Fase 3 señala.
- Rate limiting sobre las rutas públicas de `multas` (endurecimiento de
  seguridad diferido por CLAUDE.md, mismo criterio que el resto del
  proyecto).
- Prueba y ajuste del porcentaje/plazo de descuento (Decisión 8) con un
  municipio piloto real.
- Un segundo tipo de infracción con circuito distinto (por ejemplo, actas
  que requieren inspección técnica antes de confirmarse): no hay hoy un
  caso real, se decide cuando aparezca (mismo criterio que ADR 0015 §3
  aplica a tipos de trámite).
