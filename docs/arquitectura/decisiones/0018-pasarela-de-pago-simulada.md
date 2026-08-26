# 0018 - Pasarela de pago: interfaz `PasarelaDePago` con adaptador simulado, módulo canon base `pagos`

- Estado: Aceptada
- Fecha: 2026-08-26

## Contexto

Fase 2 ([roadmap](../../producto/roadmap-fases.md#fase-2--recaudación-e-integración-con-lo-existente))
arranca con **Tasas municipales + pago online**: un vecino tiene que poder
pagar una tasa desde el portal público. El propio roadmap advierte:

> Acá es donde empieza a pesar la interoperabilidad con sistemas ya
> digitalizados de cada municipio — deliberadamente no se mezcla con el MVP
> para no bloquear la primera venta con un problema de integración ajeno al
> producto.

Ningún municipio piloto con credenciales reales de una pasarela (Mercado
Pago, Modo, PagoFácil/Rapipago) existe todavía. Bloquear la primera
rebanada de Fase 2 hasta conseguir esas credenciales repetiría el error que
el proyecto ya evitó en R11 (Transparencia activa: no se esperó a un
municipio piloto real para tener datos publicables) y en R1-R12 en general
(nunca se dependió de infraestructura externa real para demostrar una
rebanada). El mecanismo equivalente que ya existe en el proyecto para esto
es Mailpit: el motor de notificaciones (R5,
[ADR 0013](0013-persistencia-de-eventos-y-mecanismo-transversal-de-notificaciones-y-auditoria.md))
manda emails reales por SMTP en cualquier ambiente, incluido dev, pero en
dev el SMTP apunta a Mailpit en vez de a un proveedor real — la app nunca
sabe la diferencia porque habla siempre el mismo protocolo (SMTP) contra un
`host`/`port` de configuración.

Un pago online no tiene un equivalente de "protocolo estándar" tan directo
como SMTP: cada pasarela real tiene su propio SDK/API REST, su propio
esquema de checkout (redirect vs. checkout embebido) y su propia forma de
notificar el resultado (webhook firmado). Lo que sí se puede fijar ahora,
sin conocer el proveedor real, es la **forma de la interacción** que
`tasas` necesita de cualquier pasarela: iniciar un cobro por un monto y una
referencia propia, y enterarse después de si se aprobó o no. Ningún ADR
previo decide esto.

## Decisión

### 1. Interfaz `PasarelaDePago`, en un módulo canon base nuevo `pagos`

```java
package ar.com.ciudaddigital.pagos;

public interface PasarelaDePago {
    ResultadoDeInicioDePago iniciarPago(SolicitudDePago solicitud);
}

public record SolicitudDePago(String referenciaInterna, BigDecimal monto, String descripcion) {}

public record ResultadoDeInicioDePago(String referenciaExterna, String urlDePago) {}
```

`pagos` tiene el mismo estatus que `seguimientoanonimo`
([ADR 0017](0017-seguimiento-anonimo-por-token-en-reclamos-y-mesa-de-entradas.md)
§3): canon base, no contratable, sin `DescriptorDeModulo`, sin persistencia
ni entidades propias. No define ningún endpoint HTTP. Vive en su propio
módulo (no dentro de `tasas.internal`) por el mismo motivo que
`seguimientoanonimo` no vive dentro de `reclamos`: es una pieza de
infraestructura de integración externa con un motivo de cambio propio y
distinto al de la lógica de negocio de tasas —el día que se agregue un
segundo módulo que cobre algo (por ejemplo, un futuro derecho de
cementerio pago, o el portal de proveedores facturando), reutiliza
`pagos` en vez de reimplementar el mismo contrato.

A diferencia del criterio de "esperar al segundo consumidor real" que
[ADR 0013](0013-persistencia-de-eventos-y-mecanismo-transversal-de-notificaciones-y-auditoria.md)
§3 y [ADR 0015](0015-motor-de-expediente-workflow-minimo.md) §3 aplican
para no generalizar un patrón interno sin un segundo caso concreto, acá el
"segundo caso" que justifica la interfaz no es otro módulo del sistema: es
la pasarela real que un municipio piloto va a exigir con sus propias
credenciales. Esa segunda implementación es certera (está en el nombre
mismo de la rebanada del roadmap), aunque todavía no exista el cliente que
la dispare. No es diseñar a ciegas: es exactamente el patrón adaptador que
ya se usa para SMTP/Mailpit, aplicado a un protocolo que no tiene un
estándar universal, así que hace falta nombrar la interfaz nosotros
mismos en vez de apoyarnos en una ya dada por la industria.

`SolicitudDePago.referenciaInterna` es el identificador propio de `tasas`
(el id de la tasa, como texto) — la pasarela real lo usa para poder
correlacionar su webhook con el cobro correcto; `ResultadoDeInicioDePago`
separa `referenciaExterna` (el id que la pasarela le asigna a esa
transacción, string opaco) de `urlDePago` (adonde redirigir al pagador,
vacío/no aplicable en el adaptador simulado — ver Decisión 3).

### 2. Un único bean activo: `PasarelaDePagoSimulada`, sin selección por proveedor todavía

`pagos.internal.PasarelaDePagoSimulada implements PasarelaDePago` es el
único bean de este tipo en todo el sistema, en todos los ambientes
(dev, test, y el único deploy que existe hoy). No hay flag de
configuración ni perfil de Spring que elija entre "simulado" y "real"
porque **no existe todavía una segunda implementación**: agregar ese
mecanismo de selección ahora sería, otra vez, generalizar sobre un caso
que no existe (mismo motivo por el que R5, ADR 0013 §4, no generaliza
`EventoAuditable`). Cuando aparezca el primer municipio piloto con
credenciales reales de una pasarela concreta, ese trabajo agrega:

- un segundo `@Component` (`PasarelaDePagoMercadoPago` o el proveedor que
  corresponda) implementando `PasarelaDePago`,
- una propiedad de configuración que decida cuál de los dos se expone como
  el bean de tipo `PasarelaDePago` (`@ConditionalOnProperty` o un
  `@Profile`, a decidir en ese momento con el proveedor real delante,
  igual criterio que el ADR 0013 §1 aplicó recién cuando hizo falta
  resolver una ambigüedad real de `@Primary`, no antes),
- credenciales del proveedor por municipio o por instancia (todavía no
  hay ninguna decisión tomada sobre si las credenciales de pasarela son
  por tenant o compartidas — ver Pendiente de definir).

`PasarelaDePagoSimulada.iniciarPago(...)` genera una `referenciaExterna`
no adivinable (`"SIM-" + UUID.randomUUID()` o equivalente — no necesita la
disciplina criptográfica de
[ADR 0017](0017-seguimiento-anonimo-por-token-en-reclamos-y-mesa-de-entradas.md)
porque no protege lectura de datos de terceros, solo evita que alguien
adivine una referencia de pago ajena y la confirme por las suyas) y
**no** hace ninguna llamada de red: aprueba o rechaza el pago según lo que
el propio flujo simulado le indique después (Decisión 3), nunca solo.

### 3. El "checkout" del adaptador simulado es una vista in-app, no un sitio externo ni una URL navegable real

A diferencia de Mailpit —que sí es un servicio real y separado, con su
propia UI web—, construir un sitio de checkout separado para simular una
pasarela es desproporcionado para esta rebanada: el frontend de este
proyecto todavía no tiene router de URLs
([spec CD-20](../../../specs/CD-20-seguimiento-anonimo-por-token.md),
confirmado ahí que no se inventa uno sin necesidad), así que no hay dónde
"redirigir" de verdad.

Se decide que `ResultadoDeInicioDePago.urlDePago` del adaptador simulado
sea informativo únicamente (puede ser `null` o una cadena descriptiva,
a criterio de quien implemente) y que el frontend, sabiendo que corre
contra el único adaptador que existe hoy, muestre en su lugar una vista
in-app rotulada explícitamente como **"Simulador de pago (entorno de
prueba)"** con el monto y dos acciones, "Aprobar pago" / "Rechazar pago",
que llaman al endpoint de confirmación de `tasas` (ver spec CD-21). Es
deliberadamente honesto sobre ser una simulación —no imita la marca ni el
flujo visual de ningún proveedor real— para no generar la falsa impresión
de que el producto ya integra una pasarela real.

Cuando exista un adaptador real, `urlDePago` sí es una URL navegable de
verdad y el frontend necesita agregar la redirección real
(`window.location.href = urlDePago`) — trabajo pendiente, explícito, no
resuelto por este ADR.

### 4. La confirmación del pago no pasa por `pagos`

`pagos` no expone ningún endpoint HTTP ni recibe el webhook de vuelta:
cada módulo que cobra algo es dueño de su propio endpoint de confirmación
y de su propio estado, mismo criterio que
[ADR 0017](0017-seguimiento-anonimo-por-token-en-reclamos-y-mesa-de-entradas.md)
§3 usa para `token_hash` ("cada módulo agrega su propia columna... el
módulo compartido no persiste nada"). `tasas` declara su propio
`POST /api/tasas/pagos/confirmar` como ruta de escritura pública
(reutilizando `rutasDeEscrituraPublica()`,
[ADR 0014](0014-reclamos-ciudadanos-alta-publica-anonima-y-estado-propio.md)
§1, sin extenderlo): es una nueva forma de usar ese mecanismo —no es un
alta anónima de un vecino, es la notificación de un sistema externo (o,
en el adaptador simulado, del propio frontend haciendo de esa pasarela)—
pero la propiedad que hace válido reusarlo es la misma: **solo `POST`, y
nunca a partir de datos que un tercero no debería poder inventar**. La
autenticidad de esta llamada, en el adaptador simulado, se apoya en que
`referenciaExterna` es una cadena no adivinable devuelta una única vez a
quien inició el pago (mismo principio que el token de
[ADR 0017](0017-seguimiento-anonimo-por-token-en-reclamos-y-mesa-de-entradas.md),
sin su misma criticidad porque acá no hay datos de terceros que proteger,
solo el estado de una tasa que de todos modos ya era pública). Un
proveedor real reemplaza esto por verificación de firma del webhook —
explícitamente pendiente, ver más abajo.

### 5. Montos en `numeric(12,2)`, moneda implícita ARS

El producto es para municipios argentinos; no hay ningún caso de uso
multi-moneda en el roadmap. Se usa `BigDecimal`/`numeric(12,2)` sin columna
de moneda: agregarla sin un caso real que la necesite es la misma
generalización prematura que este proyecto viene evitando en cada ADR de
Fase 1.

## Alternativas consideradas

- **Esperar a un municipio piloto con credenciales reales antes de tocar
  Tasas**: bloquea toda la Fase 2 por un problema de integración ajeno al
  producto, exactamente lo que el roadmap dice explícitamente que no hay
  que hacer.
- **Adaptador simulado con selección por perfil de Spring desde el día
  uno** (`pagos.proveedor=simulado|mercadopago`): anticipa una decisión de
  configuración (¿por tenant? ¿por instancia?) sin tener un segundo
  proveedor real que la valide. Descartada por ahora, ver Decisión 2.
- **Simular la pasarela con un servicio HTTP separado** (al estilo
  Mailpit, un proceso propio con su UI): más fiel a la analogía, pero es
  infraestructura nueva (otro proceso, otro puerto, otra imagen de
  Docker Compose) para un flujo que se puede simular in-app sin perder
  nada del contrato que `tasas` necesita ejercitar. Descartada por
  desproporcionada frente al beneficio; se puede reconsiderar si en algún
  momento hace falta probar timing real de webhooks asíncronos.
- **Que `tasas` llame directo a un SDK de un proveedor real ya en esta
  rebanada, aunque sea con credenciales de sandbox del proveedor**:
  acopla el módulo de negocio a un proveedor concreto antes de tener un
  cliente que lo exija, y depende de una cuenta de desarrollador con ese
  proveedor que hoy no existe. Descartada, es lo que este ADR existe para
  evitar.
- **Poner `PasarelaDePago` dentro de `tasas.internal`, sin módulo
  propio**: más simple hoy con un solo consumidor, pero mezcla dos motivos
  de cambio distintos (reglas de negocio de tasas vs. contrato de
  integración con pasarelas de pago) en el mismo módulo, y obliga a mover
  código el día que aparezca un segundo cobro en el sistema. Descartada
  por el mismo criterio de separación de módulos por motivo de cambio que
  ya usa el proyecto (ADR 0003, ADR 0017 §3).

## Consecuencias

- `tasas` depende de `pagos`; `pagos` no depende de ningún módulo
  funcional. El test de modularidad de Spring Modulith lo verifica en el
  build, igual que para `seguimientoanonimo`.
- El adaptador simulado no ejercita ninguna de las cosas que sí importan
  de un proveedor real: latencia de red, reintentos, verificación de
  firma de webhook, expiración de la sesión de checkout, medios de pago
  específicos (tarjeta, transferencia, efectivo en Rapipago/PagoFácil).
  Ninguna demo contra el adaptador simulado prueba nada de eso.
- El día que se integre un proveedor real, el contrato `PasarelaDePago`
  puede no alcanzar tal cual (por ejemplo, algunos proveedores devuelven
  más de un medio de pago posible, o requieren un paso previo de
  "preferencia" separado del cobro): se trata como una revisión esperable
  de este ADR con el proveedor real delante, no como un fracaso de diseño.
- `tasas` queda con una tercera vía de escritura pública además del alta
  protegida: el endpoint de confirmación de pago, sin autenticación de
  sesión, con el mismo riesgo de abuso ya aceptado y diferido para toda
  escritura pública del proyecto (spam de confirmaciones falsas contra
  referencias inventadas, que simplemente no encuentran nada — no hay
  forma de que alguien sin la referencia real cambie el estado de una
  tasa ajena).

## Pendiente de definir

- Selección de proveedor real de pasarela (Mercado Pago, Modo,
  PagoFácil/Rapipago) y sus credenciales, cuando exista un municipio
  piloto que lo requiera. Incluye si las credenciales son por tenant o
  compartidas por la plataforma.
- Verificación de firma/autenticidad de webhook de un proveedor real
  (hoy no aplica: el adaptador simulado no tiene firma que verificar).
- Medios de pago específicos, cuotas, y cualquier variación de UX de
  checkout que un proveedor real imponga.
- Conciliación entre lo que la pasarela real reporta y lo que el
  municipio ya cobró por otras vías (efectivo en ventanilla, por
  ejemplo) — pertenece a Tesorería y Recaudación (Fase 3), no a esta
  decisión.
- Rate limiting sobre el endpoint de confirmación de pago (endurecimiento
  de seguridad diferido por CLAUDE.md, mismo criterio que
  ADR 0014/ADR 0017).
- Reintentos/idempotencia si la misma referencia externa se confirma dos
  veces (hoy no se especifica; ver spec CD-21 para el comportamiento
  concreto de esta rebanada).
