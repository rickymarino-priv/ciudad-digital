# CD-18 · R10 — Mesa de Entradas suma habilitación comercial simple y permiso de obra menor

Rama: `CD-18-mesa-de-entradas-nuevos-tramites` (desde `develop`).

Extiende el módulo `mesaentradas` construido en R9 (CD-17). Lean primero:

- El código actual de `backend/src/main/java/ar/com/ciudaddigital/mesaentradas/`
  (ya construido, no se reescribe desde cero).
- [ADR 0015](../docs/arquitectura/decisiones/0015-motor-de-expediente-workflow-minimo.md)
  — el motor: circuito fijo por tipo de trámite. **No se toca** en esta
  rebanada: ni `ExpedienteEntity`/`MovimientoDeExpedienteEntity` como
  mecanismo, ni `GestionDeExpedientes.avanzar()`, ni la forma en que
  `CircuitosDeTramite` resuelve un circuito. Esta rebanada es exactamente
  el caso que el ADR anticipó: agregar tipos de trámite es agregar código
  y migración, no rediseñar.
- [ADR 0016](../docs/arquitectura/decisiones/0016-datos-propios-por-tipo-de-tramite-columnas-explicitas.md)
  (nuevo, ya escrito) — resuelve el pendiente de ADR 0015 §3: los datos
  propios de cada tipo de trámite son columnas explícitas y nullable en
  `expediente`, con un `check` que las exige solo para su tipo. No JSON,
  no tabla propia por tipo.

## Demo

Un vecino, **sin sesión**, entra al portal público de un municipio y elige
qué trámite iniciar entre los tres que ofrece Mesa de Entradas:
**certificado de domicilio**, **habilitación comercial simple** o
**permiso de obra menor**, completando los campos propios del que eligió.
Un agente de Mesa de Entradas, **con sesión** y el permiso
`mesaentradas.gestionar`, ve cada trámite entrar a la cola en estado
"Iniciado" y lo avanza por el circuito que corresponde a su tipo:
certificado de domicilio y permiso de obra menor van
`Iniciado → En revisión → Aprobado/Rechazado`; habilitación comercial
simple agrega un paso más, `En revisión → En inspección → Aprobado/
Rechazado` (o puede rechazarse desde "en revisión" sin llegar a
inspección). Cada paso queda registrado con quién lo hizo y cuándo. El
mismo trámite no aparece en el portal de otro municipio.

## Qué se construye

### Backend — Tarea única (bloqueante, un solo agente `backend`)

Todo dentro de `mesaentradas.internal`, extendiendo (no reescribiendo) las
clases de R9.

**1. `EstadoDeExpediente`**

Agregar un valor: `INSPECCION`, entre `EN_REVISION` y `APROBADO`/
`RECHAZADO` en el orden del enum (no afecta lógica, solo legibilidad).
Usado únicamente por el circuito de `HABILITACION_COMERCIAL_SIMPLE`.

**2. `TipoDeTramite`**

Agregar dos valores: `HABILITACION_COMERCIAL_SIMPLE`,
`PERMISO_OBRA_MENOR`. Actualizar el javadoc de la clase: ya no describe
una limitación pendiente ("el alta no goza hoy de ese desacople") sino el
mecanismo real que queda armado con esta rebanada (ver punto 6, el record
`DatosPropiosDelTramite`) — sigue sin ser un desacople total (agregar un
tipo sigue tocando ese record, el controller y el frontend), pero ya no es
"un solo tipo hardcodeado", cítese ADR 0016.

**3. `CircuitosDeTramite`**

Agregar dos entradas al registro:

- `HABILITACION_COMERCIAL_SIMPLE`: estado inicial `INICIADO`, transiciones
  `INICIADO → {EN_REVISION}`, `EN_REVISION → {INSPECCION, RECHAZADO}`,
  `INSPECCION → {APROBADO, RECHAZADO}`, `APROBADO → {}`, `RECHAZADO → {}`.
  Es, a propósito, un circuito con una rama y un paso más que el de
  certificado de domicilio: valida que el motor de ADR 0015 soporta
  circuitos de distinta forma sin cambios.
- `PERMISO_OBRA_MENOR`: mismo circuito que `CERTIFICADO_DOMICILIO`
  (`INICIADO → EN_REVISION → {APROBADO, RECHAZADO}`, sin vuelta atrás):
  no hay necesidad de inventarle un paso adicional solo para que se vea
  distinto.

**4. `ExpedienteEntity`**

- `domicilioACertificar` deja de ser `nullable = false`: pasa a nullable
  a nivel columna/entidad (sigue siendo obligatorio *para
  `CERTIFICADO_DOMICILIO`*, pero eso ya no lo garantiza la columna sola —
  ver el `check` de la migración, punto 8).
- Agregar cuatro columnas/campos nuevos, todos nullable:
  `rubroComercial` (`varchar(200)`), `direccionLocal` (`varchar(300)`)
  para habilitación comercial simple; `direccionObra` (`varchar(300)`),
  `descripcionObra` (`varchar(500)`) para permiso de obra menor.
- Para no explotar la firma de `nuevo(...)` a siete parámetros, agrupar
  los cinco campos propios de trámite (los tres tipos) en un record
  package-private `DatosPropiosDelTramite(String domicilioACertificar,
  String rubroComercial, String direccionLocal, String direccionObra,
  String descripcionObra)`, todos sus componentes nullable. Firma nueva:
  `nuevo(TipoDeTramite tipo, String solicitanteNombre, String
  solicitanteContacto, DatosPropiosDelTramite datos)`. El resto de la
  lógica de `nuevo(...)` (fijar estado inicial vía
  `CircuitosDeTramite.de(tipo).estadoInicial()`, `creadoEn`/
  `actualizadoEn`, movimiento de alta) no cambia.
- Agregar los cinco getters correspondientes (los tres nuevos +
  mantener `getDomicilioACertificar()`).
- `avanzar(...)` no cambia.

**5. `MovimientoDeExpedienteEntity`, `ExpedienteRepository`,
`SolicitudInvalida`, `DescriptorDelModuloMesaDeEntradas`**

Sin cambios.

**6. `GestionDeExpedientes`**

- `iniciar(...)` cambia de firma para recibir los datos propios del
  trámite agrupados: `iniciar(TipoDeTramite tipo, String
  solicitanteNombre, String solicitanteContacto, DatosPropiosDelTramite
  datos)` (mismo record que `ExpedienteEntity`, punto 4).
- Validación de campos propios **por tipo** (antes de construir la
  entidad), mismo estilo defensivo y mismos límites de largo que ya
  existen para `domicilioACertificar` (200/300/300/300/500 según el
  campo, mensajes en español "Hay que indicar..."/"...no puede superar
  los N caracteres."):
  - `CERTIFICADO_DOMICILIO`: `domicilioACertificar` requerido, máx. 300.
  - `HABILITACION_COMERCIAL_SIMPLE`: `rubroComercial` requerido, máx. 200;
    `direccionLocal` requerido, máx. 300.
  - `PERMISO_OBRA_MENOR`: `direccionObra` requerido, máx. 300;
    `descripcionObra` requerido, máx. 500.
  - Usar un `switch` sobre `tipo` (o equivalente) para esta validación:
    es la única rama del módulo que necesariamente conoce los tres tipos
    a la vez, documentarlo con un comentario citando ADR 0016 (agregar un
    tipo cuarto agrega un `case` acá).
- `listar()` y `avanzar(...)` no cambian de firma ni de lógica (siguen
  siendo agnósticos al tipo, vía `CircuitosDeTramite`).

**7. `MesaDeEntradasController`**

- `IniciarExpedienteRequest` gana cuatro campos nullable:
  `rubroComercial`, `direccionLocal`, `direccionObra`, `descripcionObra`
  (además de los existentes `tipo`, `solicitanteNombre`,
  `solicitanteContacto`, `domicilioACertificar`). El método `iniciar(...)`
  arma el `DatosPropiosDelTramite` desde el request y lo pasa a
  `gestion.iniciar(...)`.
- `ExpedienteResponse` gana los mismos cuatro campos (aplanados, no
  anidados en un objeto `datos`: ADR 0016 evita reintroducir la discusión
  de "datos variables" como estructura propia). `ExpedienteResponse.de(...)`
  los lee de los getters nuevos de `ExpedienteEntity`.
- `ExpedientePublicoResponse` (la confirmación del alta) **no cambia**:
  sigue siendo `{id, tipo, estado, creadoEn}` para los tres tipos, mismo
  criterio que ya regía para certificado de domicilio.
- `tipoDe(String)`/`estadoDe(String)` no cambian (ya son genéricos vía
  `valueOf`).

**8. Migración `V10__agregar_habilitacion_comercial_y_obra_menor.sql`**
en `backend/src/main/resources/db/tenant/`:

```sql
-- Mesa de Entradas: sumar habilitación comercial simple y permiso de obra
-- menor al catálogo de trámites (backlog R10, ADR 0016), completando el
-- subset de Trámites a Distancia de Fase 1.

-- domicilio_a_certificar deja de ser obligatorio a nivel de columna: ya
-- no es el único tipo de trámite. Sigue siendo obligatorio para las filas
-- de CERTIFICADO_DOMICILIO vía el check agregado más abajo (ADR 0016).
alter table expediente alter column domicilio_a_certificar drop not null;

-- Ampliar los catálogos de tipo/estado. Los nombres de constraint
-- asumidos acá son los que Postgres autogenera para un `check` inline en
-- un `create table` sin nombre explícito (patrón `<tabla>_<columna>_check`);
-- confirmarlos contra el esquema real antes de dropearlos (por ejemplo,
-- con `\d expediente` / `\d movimiento_de_expediente` en psql, o
-- consultando `information_schema.check_constraints`), y ajustar el
-- nombre si no coincide.
alter table expediente drop constraint expediente_tipo_check;
alter table expediente add constraint expediente_tipo_check
    check (tipo in ('CERTIFICADO_DOMICILIO', 'HABILITACION_COMERCIAL_SIMPLE', 'PERMISO_OBRA_MENOR'));

alter table expediente drop constraint expediente_estado_check;
alter table expediente add constraint expediente_estado_check
    check (estado in ('INICIADO', 'EN_REVISION', 'INSPECCION', 'APROBADO', 'RECHAZADO'));

alter table movimiento_de_expediente drop constraint movimiento_de_expediente_estado_anterior_check;
alter table movimiento_de_expediente add constraint movimiento_de_expediente_estado_anterior_check
    check (estado_anterior in ('INICIADO', 'EN_REVISION', 'INSPECCION', 'APROBADO', 'RECHAZADO'));

alter table movimiento_de_expediente drop constraint movimiento_de_expediente_estado_nuevo_check;
alter table movimiento_de_expediente add constraint movimiento_de_expediente_estado_nuevo_check
    check (estado_nuevo in ('INICIADO', 'EN_REVISION', 'INSPECCION', 'APROBADO', 'RECHAZADO'));

-- Datos propios de habilitación comercial simple (ADR 0016): columnas
-- explícitas nullable, obligatorias solo para su tipo (check al final).
alter table expediente add column rubro_comercial varchar(200);
alter table expediente add column direccion_local varchar(300);

-- Datos propios de permiso de obra menor (ADR 0016).
alter table expediente add column direccion_obra varchar(300);
alter table expediente add column descripcion_obra varchar(500);

-- Cada tipo exige sus propios campos, y solo los suyos (ADR 0016): reemplaza,
-- para el dato propio de trámite, la garantía que antes daba el "not null"
-- de domicilio_a_certificar cuando era la única columna posible.
alter table expediente add constraint expediente_datos_por_tipo_check check (
    (tipo = 'CERTIFICADO_DOMICILIO' and domicilio_a_certificar is not null)
    or (tipo = 'HABILITACION_COMERCIAL_SIMPLE' and rubro_comercial is not null and direccion_local is not null)
    or (tipo = 'PERMISO_OBRA_MENOR' and direccion_obra is not null and descripcion_obra is not null)
);

comment on column expediente.rubro_comercial is
    'Dato propio de HABILITACION_COMERCIAL_SIMPLE (ADR 0016). Null para los demás tipos.';
comment on column expediente.direccion_local is
    'Dato propio de HABILITACION_COMERCIAL_SIMPLE (ADR 0016). Null para los demás tipos.';
comment on column expediente.direccion_obra is
    'Dato propio de PERMISO_OBRA_MENOR (ADR 0016). Null para los demás tipos.';
comment on column expediente.descripcion_obra is
    'Dato propio de PERMISO_OBRA_MENOR (ADR 0016). Null para los demás tipos.';
```

No hay permisos nuevos que insertar: `mesaentradas.ver`/
`mesaentradas.gestionar` (V9) ya cubren cualquier tipo de trámite del
módulo. Ajustar el DDL si al implementar aparece algo que no encaje con
las convenciones reales del proyecto (nombres reales de constraint,
sobre todo), pero mantener las decisiones: columnas explícitas nullable
por tipo, `check` combinado que reemplaza el `not null` perdido, sin
tabla nueva, sin JSON.

Recordatorio del entorno: si algo obliga a tocar una migración ya
aplicada en vez de agregar una nueva (no debería hacer falta acá, V10 es
aditiva), correr `mvn clean` después.

**9. Test de integración**, extendiendo
`backend/src/test/java/ar/com/ciudaddigital/mesaentradas/MesaDeEntradasTest.java`
(no crear una clase nueva). Casos nuevos, mismo estilo que los existentes
(helpers `iniciar`/`avanzarEstado`/`fijarModulos`/`crearAgenteYLoguear` ya
disponibles, reutilizarlos; generalizar `iniciar(subdominio, cuerpo)` para
aceptar el cuerpo JSON de cualquier tipo, ya lo hace):

1. Alta pública de `HABILITACION_COMERCIAL_SIMPLE` con
   `rubroComercial`/`direccionLocal` válidos → 201. Sin `rubroComercial` o
   sin `direccionLocal` → 400.
2. Alta pública de `PERMISO_OBRA_MENOR` con `direccionObra`/
   `descripcionObra` válidos → 201. Sin alguno de los dos → 400.
3. Circuito de `HABILITACION_COMERCIAL_SIMPLE`: iniciar uno, avanzar
   `INICIADO → EN_REVISION` → 200, `EN_REVISION → INSPECCION` → 200,
   `INSPECCION → APROBADO` → 200; y probar que `EN_REVISION → APROBADO`
   directo (saltando `INSPECCION`) da 400 — la rama de circuito que no
   tienen los otros dos tipos.
4. Circuito de `PERMISO_OBRA_MENOR`: iniciar uno, encadenar
   `INICIADO → EN_REVISION → RECHAZADO` → 200 en cada paso (circuito igual
   al de certificado de domicilio, para confirmar que el motor lo trata
   igual sin código nuevo salvo el registro en `CircuitosDeTramite`).
5. El listado protegido (`GET /api/mesaentradas`) devuelve, para cada
   tipo, los campos propios correctos y `null` en los de los otros dos
   tipos (por ejemplo: un expediente `PERMISO_OBRA_MENOR` tiene
   `direccionObra`/`descripcionObra` con valor y `domicilioACertificar`/
   `rubroComercial`/`direccionLocal` en `null`).
6. **Aislamiento entre tenants**: extender el test
   `aislamientoEntreTenants` existente (no duplicarlo en un test nuevo)
   para que uno de los dos expedientes que compara sea de un tipo nuevo
   (por ejemplo, el del municipio B pasa a ser `PERMISO_OBRA_MENOR` en vez
   de `CERTIFICADO_DOMICILIO`), de modo que las columnas nuevas también
   queden cubiertas por la comparación cruzada de tenants. El mecanismo de
   aislamiento en sí (datasource por tenant, ADR 0001) no cambia en esta
   rebanada: no hace falta una clase de test nueva ni repetir la cobertura
   completa por cada tipo.

No hace falta tocar `EntitlementDeModulosTest` (sigue sin usar
`mesaentradas` como sujeto, mismo criterio que R9).

### Frontend — Tarea única (después del backend, un solo agente `frontend`)

Todo en `frontend/src/modulos/mesaentradas/PantallaDeMesaDeEntradas.tsx`
(extender, no reescribir). No hace falta ningún archivo nuevo.

**1. Tipos**

- `Estado` gana `'INSPECCION'`.
- Nuevo `type TipoDeTramite = 'CERTIFICADO_DOMICILIO' |
  'HABILITACION_COMERCIAL_SIMPLE' | 'PERMISO_OBRA_MENOR'`.
- `Expediente`/`RespuestaAlta`: cambiar `tipo: 'CERTIFICADO_DOMICILIO'`
  por `tipo: TipoDeTramite`, y agregar los campos propios nullable:
  `rubroComercial: string | null`, `direccionLocal: string | null`,
  `direccionObra: string | null`, `descripcionObra: string | null`
  (`domicilioACertificar` pasa a `string | null`).
- `ETIQUETA_ESTADO`: agregar `INSPECCION: 'En inspección'`.
- Nuevo `ETIQUETA_TIPO: Record<TipoDeTramite, string>`: `'Certificado de
  domicilio'`, `'Habilitación comercial simple'`, `'Permiso de obra
  menor'`.
- `TRANSICIONES_VALIDAS` (antes un único `Record<Estado, Estado[]>`) pasa
  a `Record<TipoDeTramite, Record<Estado, Estado[]>>`, con los tres
  circuitos calcados de `CircuitosDeTramite` del backend (certificado de
  domicilio y permiso de obra menor: `INICIADO: ['EN_REVISION']`,
  `EN_REVISION: ['APROBADO', 'RECHAZADO']`, resto `[]`; habilitación
  comercial simple: `INICIADO: ['EN_REVISION']`, `EN_REVISION:
  ['INSPECCION', 'RECHAZADO']`, `INSPECCION: ['APROBADO', 'RECHAZADO']`,
  resto `[]`). Mismo comentario existente sobre que esto solo decide qué
  mostrar en el `<select>`, el enforcement real es del backend.

**2. `FormularioDeAlta`**

- Agregar estado `tipo: TipoDeTramite` (default `'CERTIFICADO_DOMICILIO'`)
  y estados para los cuatro campos nuevos (`rubroComercial`,
  `direccionLocal`, `direccionObra`, `descripcionObra`), vacíos por
  default.
- Un `<select id="tramite-tipo">` con `<label>` "Tipo de trámite" y las
  tres opciones (texto de `ETIQUETA_TIPO`), como **primer campo** del
  formulario (antes de nombre/contacto): el vecino elige qué trámite
  quiere iniciar antes de ver los campos propios de ese trámite. Cambiar
  el `tipo` resetea a vacío los campos propios de los otros tipos (para no
  enviar basura si el vecino cambió de opinión a mitad de completar el
  formulario).
- Debajo de nombre/contacto (que siguen siendo comunes a los tres tipos),
  renderizar condicionalmente el grupo de campos del tipo elegido:
  - `CERTIFICADO_DOMICILIO`: el campo `domicilioACertificar` existente,
    sin cambios de texto/ayuda.
  - `HABILITACION_COMERCIAL_SIMPLE`: `rubroComercial` (`input`, requerido,
    label "Rubro del comercio", ayuda breve: "Por ejemplo, kiosco,
    restaurante, peluquería."), `direccionLocal` (`textarea` o `input`,
    requerido, label "Dirección del local a habilitar").
  - `PERMISO_OBRA_MENOR`: `direccionObra` (requerido, label "Dirección de
    la obra"), `descripcionObra` (`textarea`, requerido, label
    "Descripción de la obra", ayuda: "Qué se va a hacer: por ejemplo,
    arreglo de vereda, cerco, revoque de fachada.").
  - Mismo patrón de `<label htmlFor>` explícito, `required`,
    `aria-invalid`/`aria-describedby` apuntando al error cuando
    corresponda, que ya usan `solicitanteNombre`/`domicilioACertificar`.
- `enviarTramite`: arma el body con `tipo` y únicamente los campos propios
  del tipo elegido (los de los otros dos tipos, `null` o ausentes — a
  criterio de la implementación, el backend los trata igual porque son
  nullable).
- Al confirmar, limpiar también los cuatro campos propios nuevos (además
  de los que ya se limpian). El texto de confirmación puede seguir siendo
  genérico ("Tu trámite quedó registrado con el número..."): no hace falta
  que mencione el tipo elegido, ya lo eligió el propio vecino en el
  formulario.

**3. `PanelDeGestion`**

- La tabla gana una columna **"Tipo"** (antes de "Solicitante" o después,
  a criterio del agente `frontend`) con `ETIQUETA_TIPO[expediente.tipo]`.
- La columna fija "Domicilio a certificar" se reemplaza por una columna
  **"Detalle del trámite"** que muestra, según `expediente.tipo`, los
  campos propios de ese trámite en texto legible (por ejemplo: para
  `HABILITACION_COMERCIAL_SIMPLE`, algo como "Rubro: {rubroComercial} ·
  Local: {direccionLocal}"; para `PERMISO_OBRA_MENOR`, "Obra en
  {direccionObra}: {descripcionObra}"; para `CERTIFICADO_DOMICILIO`, el
  texto que ya se mostraba con `domicilioACertificar`). Implementar como
  una función `textoDetalle(expediente): string` con un `switch` sobre
  `expediente.tipo`, mismo criterio de "una rama por tipo, documentada"
  que se pide en el backend.
- `abrirEdicion`/las opciones del `<select>` de nuevo estado: usar
  `TRANSICIONES_VALIDAS[expediente.tipo][expediente.estado]` en vez del
  mapa único anterior (única línea que cambia en esa lógica).
- El resto del panel (edición por fila, foco, mensajes de error,
  `aria-*`) no cambia de patrón.

## Aislamiento entre tenants

Cubierto por el test 6 de la tarea de backend (arriba): el mecanismo en sí
(datasource ruteado por tenant, ADR 0001) no cambia en esta rebanada, pero
se extiende el test existente para que las columnas nuevas también
aparezcan en la comparación cruzada. Nada adicional del lado del
frontend: cada portal sigue pegando solo contra su propio subdominio.

## Accesibilidad (WCAG)

No hay patrón de UI nuevo: es la misma pantalla de R9 con más campos
condicionales y una columna más en la tabla. Mismos requisitos que ya
cumple `PantallaDeMesaDeEntradas.tsx`, verificar que se mantengan al
extenderla:

- El `<select>` de tipo de trámite tiene su `<label htmlFor>` explícito.
- Los campos que aparecen/desaparecen según el tipo elegido mantienen
  `<label htmlFor>`, `required`, `aria-invalid`/`aria-describedby` como
  los campos existentes — no agregar un campo nuevo sin su label.
- Cambiar el `<select>` de tipo no debe robar el foco de forma inesperada
  (el usuario sigue en el `<select>` después de elegir una opción); no
  hace falta mover el foco al primer campo nuevo automáticamente.
- La tabla del panel de gestión sigue con `<caption>`, encabezados
  `scope="col"`, y la columna nueva "Tipo"/"Detalle del trámite" con su
  propio `<th scope="col">`.

## Fuera de alcance (explícitamente diferido)

- Circuitos configurables **por municipio** (ADR 0015, Pendiente de
  definir): los tres tipos usan el mismo circuito para todos los
  municipios que contraten `mesaentradas`.
- Seguimiento del trámite por el vecino anónimo con un código/token
  (mismo pendiente de ADR 0014/ADR 0015, sin resolver todavía para
  ninguno de los tres tipos).
- Un cuarto/quinto tipo de trámite del roadmap más allá del subset ya
  nombrado (certificado, habilitación comercial simple, permiso de obra
  menor): el roadmap habla de "3-5", esta rebanada completa los 3
  nombrados explícitamente y no inventa tipos adicionales.
- Reconsiderar JSON o tabla propia para los datos por tipo (ADR 0016,
  Pendiente de definir): solo si un tipo futuro concreto lo justifica.
- Giro entre áreas/derivación, caratulación y numeración correlativa
  oficial, generación de documentos y firma electrónica, notificaciones al
  vecino de cambios de estado, cualquier integración con auditoría/
  notificaciones transversal más allá de lo que el propio módulo cubre,
  rate limiting/anti-abuso sobre el alta pública, paginado del listado,
  una vista de detalle separada del listado — mismos diferidos que R9,
  sin cambios.

## Instrucción para los agentes implementadores

**No hagan commit, push, ni abran PR.** Dejen los cambios en el árbol de
trabajo sin commitear. El tech lead arma el commit, pushea la rama y abre
el PR contra `develop` una vez que backend, frontend y la auditoría estén
completos. Si el trabajo se corta por límite de sesión o cualquier otro
motivo, no reintenten commitear/pushear por su cuenta al retomar: avisen
el estado en el que quedó y esperen instrucción.
