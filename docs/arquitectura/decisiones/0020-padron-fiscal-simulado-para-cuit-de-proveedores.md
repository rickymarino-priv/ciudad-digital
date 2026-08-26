# 0020 - Padrón fiscal: interfaz `PadronFiscal` con adaptador simulado, módulo canon base `padronfiscal`, resultado advisory no bloqueante

- Estado: Aceptada
- Fecha: 2026-08-26

## Contexto

El [roadmap](../../producto/roadmap-fases.md#fase-2--recaudación-e-integración-con-lo-existente)
cierra Fase 2 con la "capa de adaptadores a sistemas legados (AFIP/ARBA o
equivalente provincial, pasarelas de pago)". La parte de pasarelas de pago
ya se resolvió en R13 ([ADR 0018](0018-pasarela-de-pago-simulada.md)).
Queda el adaptador a AFIP/ARBA, que [R14](../../producto/backlog-inicial.md#r14--una-empresa-se-registra-como-proveedor-del-municipio-y-el-municipio-la-aprueba)
había diferido explícitamente: "verificación de CUIT contra un padrón real
(AFIP u otro)". CLAUDE.md exige que una pieza técnica como "una capa de
adaptadores" se resuelva contra un caso demostrable concreto, no como
ticket propio en abstracto; el diferido de R14 es exactamente ese caso: al
registrarse un proveedor, saber si su CUIT existe y en qué situación
fiscal está (activo/inhabilitado) es información real que un municipio
quiere tener antes de aprobar a alguien que va a facturarle.

Se evaluó también aplicar esto a R13 (validar el contribuyente de una
tasa contra un padrón), pero `tasas` no tiene ningún campo de identidad
fiscal hoy (solo un número de cuenta interno del municipio, ADR 0018 /
spec CD-21) — agregar un CUIT a `tasas` para este propósito sería una
extensión de alcance de ese módulo no pedida por ninguna rebanada, y el
caso de proveedores ya es autocontenido (el CUIT ya existe, ya está
normalizado, ya tiene unicidad por tenant). Se descarta por ahora; queda
como "Pendiente de definir" si en el futuro `tasas` incorpora identidad
fiscal del contribuyente.

Igual que en R13, ningún municipio piloto tiene credenciales reales de
AFIP/ARBA ni acceso a su servicio de consulta de constancia de inscripción
(que además varía por padrón provincial: ARBA para Buenos Aires, otros
organismos para otras provincias). Bloquear esta rebanada hasta conseguir
esas credenciales repite el problema que ADR 0018 ya evitó para
pasarelas: se aplica el mismo patrón interfaz + adaptador simulado.

A diferencia de `PasarelaDePago` (ADR 0018), acá no hay una decisión de
producto ya tomada sobre si el resultado bloquea algo: un municipio
probablemente quiere saber que un proveedor tiene problemas fiscales, pero
bloquear el alta —o directamente impedir aprobarlo— por completo es una
decisión de negocio (¿un CUIT inhabilitado nunca puede ser proveedor? ¿ni
siquiera con una explicación razonable, un trámite en curso, un error del
padrón?) que no tiene un municipio piloto real delante que la confirme.
Este ADR también fija esa decisión, no solo la forma del adaptador.

## Decisión

### 1. Interfaz `PadronFiscal`, en un módulo canon base nuevo `padronfiscal`

```java
package ar.com.ciudaddigital.padronfiscal;

public interface PadronFiscal {
    SituacionFiscal consultar(String cuit);
}

public enum SituacionFiscal { ACTIVO, INHABILITADO, NO_ENCONTRADO }
```

`padronfiscal` tiene el mismo estatus que `pagos` (ADR 0018 §1) y
`seguimientoanonimo` (ADR 0017 §3): canon base, no contratable, sin
`DescriptorDeModulo`, sin persistencia ni entidades propias, sin ningún
endpoint HTTP. Vive en su propio módulo, no dentro de `proveedores.internal`,
por el mismo motivo que `pagos` no vive dentro de `tasas`: es una pieza de
integración externa con un motivo de cambio propio (qué padrón se
consulta, con qué credenciales, contra qué protocolo) distinto de la
lógica de negocio de proveedores. El día que aparezca un segundo consumidor
(por ejemplo, si `tasas` incorpora identidad fiscal del contribuyente),
reutiliza `padronfiscal` en vez de reimplementar el contrato — mismo
razonamiento que ADR 0018 §1 aplica a que "la pasarela real" sea el
segundo caso cierto que justifica la interfaz, aunque hoy solo exista un
consumidor.

`consultar` recibe el CUIT ya normalizado a 11 dígitos (el string
formateado `"XX-XXXXXXXX-X"` que produce `GestionDeProveedores`, mismo
valor que ya se persiste en `proveedor.cuit`): `padronfiscal` no repite
ninguna validación de formato, esa responsabilidad ya es de quien llama.
No hay `SolicitudDeConsulta`/registro de request como en `PasarelaDePago`
porque la entrada es un único dato simple; se evita el registro
intermedio que ADR 0018 sí justifica para una solicitud con tres campos.

### 2. Un único bean activo: `PadronFiscalSimulado`, determinístico y sin red

`padronfiscal.internal.PadronFiscalSimulado implements PadronFiscal` es el
único bean de este tipo, en todos los ambientes, igual que
`PasarelaDePagoSimulada` (ADR 0018 §2): no hay flag de configuración ni
perfil de Spring que elija entre "simulado" y "real" porque no existe
todavía una segunda implementación. Cuando exista un municipio piloto con
acceso real a AFIP/ARBA, ese trabajo agrega un segundo `@Component` y un
mecanismo de selección — igual criterio que ADR 0018 §2, no antes.

`PadronFiscalSimulado.consultar(cuit)` no hace ninguna llamada de red:
responde de forma determinística según el último dígito del CUIT
normalizado (los 11 dígitos, sin guiones), para que la demo sea
reproducible y los tests puedan fijar el resultado eligiendo el CUIT:

- último dígito `0` → `NO_ENCONTRADO` (simula un CUIT que no existe en el
  padrón).
- último dígito par distinto de cero (`2`, `4`, `6`, `8`) → `ACTIVO`.
- último dígito impar (`1`, `3`, `5`, `7`, `9`) → `INHABILITADO`.

La regla es arbitraria a propósito (no imita ningún algoritmo real de
AFIP/ARBA, que no es de dominio público) y solo existe para poder
demostrar los tres resultados posibles eligiendo el CUIT de prueba. Un
CUIT que ya falló la validación de formato de `GestionDeProveedores` nunca
llega hasta acá.

### 3. El resultado es advisory: no bloquea el alta ni la aprobación

`GestionDeProveedores.registrar(...)` consulta `padronfiscal` como parte
del alta (después de normalizar y validar unicidad del CUIT) y guarda el
resultado en una columna nueva, `situacion_fiscal`, en la misma fila del
proveedor — no en una tabla ni un módulo aparte, es un dato más del
registro. **La situación fiscal no impide el alta**: un CUIT `INHABILITADO`
o `NO_ENCONTRADO` se registra igual, en `PENDIENTE`, mismo criterio que ya
usa el resto de la validación de este módulo (el formato del CUIT se
valida, la existencia real no). **Tampoco impide aprobar**: `cambiarEstado`
no consulta ni valida la situación fiscal — un agente puede aprobar un
proveedor con CUIT inhabilitado si tiene razones para hacerlo (un trámite
en curso con AFIP, un error del padrón, una explicación fuera del
sistema).

La razón de no bloquear, en ninguno de los dos puntos, es la que el propio
enunciado de esta rebanada señala: bloquear el alta o la aprobación por
completo es una decisión de negocio que ningún municipio piloto validó
todavía, y una vez tomada (bloquear) es mucho más difícil de revertir sin
fricción (proveedores que ya fueron rechazados automáticamente) que
adoptarla más tarde con un caso real delante. Lo que sí hace esta rebanada
es **hacer visible** la situación fiscal a quien decide: el panel de
gestión (`proveedores.ver`) muestra la situación fiscal de cada proveedor
como una columna más, con texto explícito (no solo color) cuando es
`INHABILITADO` o `NO_ENCONTRADO`, para que la persona que aprueba lo vea
antes de decidir — la responsabilidad de la decisión queda en el agente
municipal, el sistema solo informa.

`situacionFiscal` **no** se expone en `ProveedorPublicoResponse` (la
confirmación del alta) ni en `SeguimientoDeProveedorResponse` (la consulta
pública por token, ADR 0017 §5): es información para la decisión interna
del municipio, no algo que la empresa que se registra necesite ver por
esta vía todavía — mismo criterio de minimización ya aplicado a otros
campos internos de `proveedores` (comentario de gestión sí se expone
porque es la respuesta a la empresa; contacto no se repite porque ya lo
tiene). Si en el futuro se decide comunicarle a la empresa su propia
situación fiscal, es una decisión de producto nueva, no una consecuencia
automática de este ADR.

### 4. Columna nueva, no tabla aparte

`proveedor.situacion_fiscal` (`varchar(20) not null`, con
`check (situacion_fiscal in ('ACTIVO', 'INHABILITADO', 'NO_ENCONTRADO'))`,
`default 'NO_ENCONTRADO'` para no romper migraciones sobre bases con filas
existentes). Se calcula una única vez, en el alta: esta rebanada no agrega
una forma de re-consultar la situación fiscal de un proveedor ya
registrado (ver Pendiente de definir).

## Alternativas consideradas

- **Esperar a un municipio piloto con credenciales reales de AFIP/ARBA
  antes de construir esto**: repite el problema que ADR 0018 ya evitó para
  pasarelas de pago — bloquea el cierre de Fase 2 por un problema de
  integración ajeno al producto.
- **Bloquear el alta si el CUIT no está `ACTIVO`**: es la lectura más
  "estricta" del enunciado de esta rebanada, pero le quita a la empresa la
  chance de que el municipio revise el caso a mano (padrones reales tienen
  falsos negativos, CUITs con trámites en curso, etc.), y es una política
  de negocio que ningún cliente real pidió todavía. Descartada por ahora,
  documentada como decisión explícita en la Decisión 3, no como omisión.
- **Bloquear solo la aprobación (`cambiarEstado` a `APROBADO`) si la
  situación fiscal no es `ACTIVO`**: intermedia entre bloquear el alta y
  no bloquear nada, pero sigue siendo una política que le quita al agente
  municipal la decisión final sin que ningún piloto la haya pedido.
  Descartada por el mismo motivo que la anterior; queda como la primera
  candidata si en el futuro un municipio real pide que sí se bloquee.
- **Aplicar el padrón fiscal a `tasas` (validar el contribuyente) en vez
  de a `proveedores`**: descartado por ahora, ver Contexto — `tasas` no
  tiene hoy ningún campo de identidad fiscal, y agregarlo sería extender
  el alcance de ese módulo sin que ninguna rebanada lo pida.
- **Consultar el padrón fiscal en el momento de aprobar/rechazar, en vez
  de en el alta**: mantendría el dato "fresco" al momento de la decisión,
  pero con el adaptador simulado no hay ninguna diferencia observable
  (siempre determinístico según el CUIT) y complica el flujo (¿qué pasa si
  se cambia de estado dos veces? ¿se re-consulta cada vez?). Se prefiere
  una única consulta en el alta, igual criterio que "la unicidad de CUIT
  se valida en el alta, no en cada operación posterior".
- **Registro de consulta (`ConsultaFiscal` con más campos que solo el
  CUIT)**: innecesario hoy, la entrada es un único dato; se evita el
  registro extra que ADR 0018 sí justifica para una solicitud con tres
  campos (`referenciaInterna`, `monto`, `descripcion`).

## Consecuencias

- `proveedores` depende de `padronfiscal`; `padronfiscal` no depende de
  ningún módulo funcional. El test de modularidad de Spring Modulith lo
  verifica en el build, igual que para `pagos`/`seguimientoanonimo`.
- El adaptador simulado no ejercita nada de lo que sí importa de un
  padrón real: latencia de red, caída del servicio de AFIP/ARBA (que tiene
  historial de intermitencia), timeouts, reintentos, credenciales y su
  rotación, ni la diferencia real entre padrones provinciales (ARBA no es
  lo mismo que AFIP nacional, y cada provincia puede tener el suyo).
  Ninguna demo contra el adaptador simulado prueba nada de eso.
- El día que se integre un padrón real, el contrato `PadronFiscal` puede
  no alcanzar tal cual: un servicio real puede devolver más estados
  (por ejemplo, "en trámite de baja"), la razón social registrada (útil
  para detectar que el CUIT no corresponde a la razón social declarada,
  algo que el adaptador simulado no puede ejercitar), o requerir
  reintentos/circuit breaker por la intermitencia conocida del servicio.
  Se trata como una revisión esperable de este ADR con el proveedor real
  delante, mismo criterio que ADR 0018 Consecuencias.
- La política de "no bloquea nada" (Decisión 3) es reversible pero no
  gratis: si más adelante se decide bloquear, hay que decidir qué pasa con
  los proveedores ya aprobados con situación fiscal desfavorable — esta
  rebanada no resuelve eso, porque no existe todavía.

## Pendiente de definir

- Selección de padrón/servicio real (AFIP nacional vía su servicio de
  constancia de inscripción, ARBA u otro organismo provincial según el
  municipio) y sus credenciales, cuando exista un municipio piloto que lo
  requiera.
- Si la situación fiscal se re-consulta alguna vez después del alta (por
  ejemplo, periódicamente, o a pedido del agente antes de aprobar) — hoy
  se consulta una única vez, en el alta.
- Si en algún momento se decide bloquear el alta o la aprobación según el
  resultado (ver Alternativas consideradas) — requiere un municipio piloto
  real que lo pida, no se decide en el vacío.
- Si la empresa que se registra debería poder ver su propia situación
  fiscal en la consulta por token (`SeguimientoDeProveedorResponse`) — hoy
  deliberadamente no se expone (Decisión 3).
- Aplicar el mismo mecanismo a `tasas` si ese módulo incorpora identidad
  fiscal del contribuyente en el futuro (ver Contexto y Alternativas).
- Manejo de caída del servicio real (timeouts, reintentos, qué pasa con el
  alta si el padrón no responde — hoy no aplica, el adaptador simulado
  nunca falla).
