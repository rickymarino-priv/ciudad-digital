# 0019 - Consola del proveedor: UI cross-tenant sobre la API de administración existente, con contrato mínimo por tenant

- Estado: Aceptada
- Fecha: 2026-08-26

## Contexto

El [roadmap de Fase 2](../../producto/roadmap-fases.md#fase-2--recaudación-e-integración-con-lo-existente)
pide "Consola del proveedor: contratos, módulos por municipio, estado de
facturación". El nombre es ambiguo en un producto que, desde R14, también
tiene un actor llamado "proveedor" (la empresa que le vende al municipio,
[ADR 0017](0017-seguimiento-anonimo-por-token-en-reclamos-y-mesa-de-entradas.md)).
No es ese. [modelo-comercial.md](../../producto/modelo-comercial.md)
§"Superficies de administración" define **dos productos distintos**: la
consola del proveedor (cross-tenant, para el proveedor de la plataforma —
Ciudad Digital operando comercialmente sobre sus municipios clientes) y la
consola del municipio (intra-tenant, Fase 3). El propio backlog
([backlog-inicial.md](../../producto/backlog-inicial.md), R14) ya diferenció
explícitamente "consola del proveedor cross-tenant" como algo fuera del
alcance del portal de proveedores municipal. Esta ADR asume esa lectura —
la consola cross-tenant del negocio— sin margen de ambigüedad adicional.

La API cross-tenant ya existe: `/api/admin/municipios` (alta, listado,
migraciones, administrador de emergencia) y
`/api/admin/municipios/{slug}/modulos` (catálogo y entitlement, ADR 0012
§8), protegida por sesión de **usuario de plataforma**
([ADR 0010](0010-autenticacion-por-sesion-scopeada-al-tenant.md)). Lo que
falta, y es lo que esta rebanada (R15) resuelve, son dos cosas que dos ADRs
anteriores dejaron pendientes:

- [ADR 0009](0009-modelo-comercial-y-entitlement.md), pendiente: la
  granularidad del contrato (¿por módulo con fechas, o plan único?) y si la
  consola se despliega como aplicación separada del monolito.
- [ADR 0012](0012-declaracion-de-modulos-y-gating-por-ruta.md) §8,
  pendiente: "Consola del proveedor como superficie de UI para operar el
  entitlement; hoy se opera por API."

[modelo-comercial.md](../../producto/modelo-comercial.md) ya fijó, además,
que la facturación real (emisión de facturas, montos) queda **fuera del
sistema** en esta etapa, y que el entitlement está desacoplado del estado
de pago (apagar un módulo es siempre una decisión manual, nunca automática
por atraso). Cualquier decisión de esta ADR tiene que respetar esas dos
restricciones ya tomadas, no reabrirlas.

## Decisión

### 1. Granularidad del contrato: plan único por tenant, sin fechas por módulo

Se agregan tres columnas explícitas a `tenant` (mismo criterio de
[ADR 0007](0007-modelo-de-datos-del-tenant.md): dato estructural que se
consulta y se filtra, no configuración de negocio que cambia con
frecuencia):

- `tramo_poblacional` (`CHICO`/`MEDIANO`/`GRANDE`): determina el canon base
  ([modelo comercial](../../producto/modelo-comercial.md) §"Modelo de
  cobro"). El **monto** del canon no se modela — vive en la propuesta
  comercial de cada contrato, fuera del sistema —, solo el tramo, que es el
  dato que la plataforma necesita ver de un vistazo para saber cuánto
  debería estar facturando cada municipio.
- `estado_facturacion` (`AL_DIA`/`ATRASADO`): visibilidad manual del estado
  de cuenta, en línea con el ADR 0009 ("los atrasos se manejan con
  visibilidad y alertas en la consola del proveedor"). Se edita a mano por
  la plataforma; nada en el sistema lo cambia solo.
- `nota_facturacion` (texto libre, opcional): contexto humano ("esperando
  transferencia, contactado el 20/08"), sin estructura.

No se modela contrato por módulo con vigencia individual (fechas de alta/
baja por módulo) ni un historial de cambios de tramo o de estado: ningún
caso de uso real lo pide todavía, y sumarlo ahora sería inventar en el
vacío (mismo criterio que ya usó
[ADR 0015](0015-motor-de-expediente-workflow-minimo.md) para no generalizar
sin un segundo caso real). Queda explícitamente pendiente, igual que ya lo
dejaba el ADR 0012 §8, la vigencia por módulo.

### 2. Sigue sin haber motor de facturación real

`estado_facturacion` es un campo editado a mano, no un cálculo derivado de
pagos ni de vencimientos. No hay entidad "factura", ni importes, ni
integración con ningún sistema contable. Esto no es una limitación de esta
rebanada: es la decisión ya tomada en
[modelo-comercial.md](../../producto/modelo-comercial.md) §"Facturación"
("la emisión de facturas queda fuera del sistema"), aplicada acá sin
reabrirla.

### 3. Despliegue: no hay aplicación separada

La consola del proveedor se sirve desde el **mismo proyecto** Vite/React
del portal municipal ([ADR 0008](0008-react-como-framework-de-frontend.md)),
con un segundo componente raíz (`ConsolaDelProveedor`, en
`frontend/src/plataforma/`) que `main.tsx` monta en lugar de `App` cuando
el host de la página es el de la consola (en desarrollo, `admin.localhost`;
en producción, el subdominio de administración que se decida al desplegar).
No pasa por `useTenant()` ni por ninguna resolución de tenant: no hay
municipio que resolver, y el backend ni siquiera hace correr esa
resolución para `/api/admin/**`
(`TenantResolutionFilter.shouldNotFilter`, ya así desde R3).

Se prefiere esto a un proyecto frontend separado porque hoy hay un solo
consumidor de la API de administración, un equipo de frontend chico, y
separar en un segundo proyecto Vite/React duplicaría tooling (build,
lint, dependencias) sin ningún beneficio todavía. Es una decisión
revisable si la consola crece lo suficiente como para justificar su propio
ciclo de release.

### 4. Autenticación y autorización: la que ya existe, sin extenderla

La consola consume `/api/admin/sesion` para entrar/salir y
`/api/admin/municipios/**` para todo lo demás — la misma sesión de usuario
de plataforma del ADR 0010, sin agregar roles ni permisos nuevos dentro de
plataforma. Sigue siendo todo-o-nada: cualquier usuario de plataforma
autenticado puede operar cualquier municipio, tal como ya regía operando
por API. Un modelo de permisos granulares para operación comercial (por
ejemplo, "solo lectura" para alguien de soporte) queda pendiente hasta que
haya más de un usuario de plataforma con necesidades distintas.

### 5. La UI no expone nada que la API no expusiera ya

La consola muestra únicamente: identidad y estado del municipio, versión
de esquema, catálogo de módulos y cuáles tiene contratados (todo ya público
por API desde R2/R4), más lo nuevo de esta ADR (tramo, estado de
facturación, nota). **Nunca** datos operativos de un municipio —usuarios,
reclamos, tasas, proveedores, lo que sea que viva en su base de tenant—:
eso sigue existiendo exclusivamente dentro de la base de cada municipio
([ADR 0001](0001-multi-tenant-con-bd-por-tenant.md)) y ninguna sesión de
plataforma tiene ninguna vía para leerlo. La consola es sobre el contrato,
no sobre los datos del municipio.

## Alternativas consideradas

- **Proyecto frontend separado para la consola**: aísla el bundle y el
  ciclo de release de la plataforma del de los portales municipales, pero
  hoy duplica tooling para un único consumidor. Descartada por prematura;
  revisable más adelante.
- **Contrato con vigencia por módulo** (fechas de alta/baja individuales):
  es lo que el ADR 0009 dejó como pregunta abierta. Descartado por ahora:
  nadie lo pidió todavía y el modelo de "plan único" alcanza para lo que
  la consola necesita mostrar hoy (qué tramo paga, si está al día).
- **Motor de facturación con importes, vencimientos y estado derivado**:
  contradice una decisión ya tomada
  ([modelo comercial](../../producto/modelo-comercial.md)); no se reabre
  acá.
- **Corte automático de módulos por `estado_facturacion = ATRASADO`**:
  exactamente lo que el ADR 0009 descarta explícitamente. `estado_facturacion`
  es informativo; no interactúa con el gating de
  [ADR 0012](0012-declaracion-de-modulos-y-gating-por-ruta.md).
- **Permisos granulares dentro de plataforma** (roles de solo lectura,
  etc.): se difiere hasta que haya un caso real con más de un tipo de
  usuario de plataforma.

## Consecuencias

- Nueva migración en la base de control (`db/control`) que agrega las tres
  columnas a `tenant`, con sus `check` de valores válidos.
- `main.tsx` gana una bifurcación por host: hay que mantenerla si en el
  futuro aparece una tercera "aplicación" servida desde el mismo proyecto.
- El criterio de aislamiento de esta superficie no es "entre tenants" (es,
  por diseño, la única vista que cruza todos) sino "quién puede llegar a
  ella": se prueba verificando que solo una sesión de usuario de
  plataforma —nunca una sesión de municipio, nunca anónimo— puede leer o
  escribir en `/api/admin/municipios/**`, extendiendo la cobertura que ya
  existía para el resto de esa API.
- Cambiar el tramo o el estado de facturación de un municipio no queda
  registrado en ningún lado más allá del valor actual: no hay historial ni
  quién lo cambió. Es el mismo estado en el que ya queda hoy el cambio de
  módulos contratados (ADR 0012 §8, "Registro de quién prendió o apagó un
  módulo y cuándo" sigue pendiente pese a que la auditoría transversal de
  R5 ya existe) — extenderla a estos cambios es trabajo futuro, no de esta
  rebanada.

## Pendiente de definir

- Vigencia del contrato por módulo (fechas de alta/baja individuales) —
  sigue abierto desde el ADR 0012 §8.
- Auditoría de cambios comerciales (quién cambió el tramo, el estado de
  facturación o los módulos contratados de un municipio, y cuándo).
- Permisos granulares dentro de la sesión de usuario de plataforma.
- Alertas proactivas sobre municipios `ATRASADO` (hoy es un dato que hay
  que entrar a mirar, no una notificación).
- El monto real del canon por tramo poblacional, que vive fuera del
  sistema en la propuesta comercial de cada contrato.
