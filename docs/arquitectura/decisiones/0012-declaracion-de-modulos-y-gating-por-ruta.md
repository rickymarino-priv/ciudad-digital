# 0012 - Declaración de módulos contratables y gating por prefijo de ruta

- Estado: Aceptada
- Fecha: 2026-08-23

## Contexto

El [ADR 0009](0009-modelo-comercial-y-entitlement.md) decidió **que** el
entitlement se hace cumplir en el backend con un guard que rechaza requests
a módulos no contratados, y que la unidad de gating es el módulo de Spring
Modulith. No definió tres cosas que hacen falta para implementarlo:

1. **Dónde vive el catálogo** de módulos contratables: qué módulos existen,
   cómo se llaman y quién los declara.
2. **Cómo se decide a qué módulo pertenece un request** entrante.
3. **Qué pasa con las rutas que no son de ningún módulo** funcional —la
   plataforma base: sesión, tema, usuarios, roles—.

Además, el [ADR 0011](0011-autorizacion-por-roles-con-permisos-granulares.md)
fijó que el orden de evaluación es entitlement primero y permiso después, y
dejó pendiente cómo se refleja en la UI un permiso sobre un módulo que el
municipio no contrató.

Restricción estructural: el backend es un monolito modular con límites
verificados en el build ([ADR 0003](0003-spring-modulith-para-el-backend.md)).
Con 30+ módulos previstos, cualquier solución donde el componente de gating
tenga que conocer a cada módulo funcional genera un punto central que se
toca en cada alta de módulo, y —peor— un ciclo de dependencias entre el
módulo de tenants y los módulos funcionales.

## Decisión

### 1. El catálogo de módulos vive en código, no en la base

Un módulo contratable existe porque hay código que lo implementa. El mismo
criterio que el ADR 0011 usa para el catálogo de permisos.

Cada módulo funcional publica un bean `DescriptorDeModulo` con su `codigo`
(identificador comercial estable), `nombre`, `descripcion`, los
`prefijosDeApi` que le pertenecen y cuáles de sus rutas son de **lectura
pública** (accesibles sin sesión, como el portal que ve un vecino). El
catálogo es la suma de los descriptores presentes en el contexto.

Que las rutas públicas viajen en el descriptor es lo que evita que agregar
un módulo obligue a editar la cadena de seguridad, que es código de otro
módulo: la cadena las consume del catálogo. Un módulo que necesite exponer
**escritura** anónima —un trámite que un vecino inicia sin cuenta— no está
contemplado acá y requiere su propia decisión cuando aparezca; no se
anticipa.

La base de control guarda, por tenant, **solo la lista de códigos
habilitados** en `config` ([ADR 0007](0007-modelo-de-datos-del-tenant.md)).
Un código habilitado que no corresponde a ningún descriptor se ignora al
resolver, y no se acepta al escribir: la API de administración valida
contra el catálogo.

### 2. Módulo transversal `entitlement`, sin dependencias hacia arriba

El gating vive en un módulo propio, `entitlement`, que:

- **define** la interfaz `DescriptorDeModulo` que implementan los módulos
  funcionales, y
- **consume** una SPI `ModulosDelTenant` —"qué códigos tiene habilitados el
  tenant del request en curso"— que implementa el módulo de tenants.

`entitlement` no depende de `tenants` ni de ningún módulo funcional: las
dependencias apuntan hacia él. Sin esta inversión habría un ciclo
(`tenants` necesita el catálogo para validar lo que le mandan, y el gating
necesita la configuración del tenant), y Spring Modulith haría fallar el
build — correctamente.

La configuración comercial del tenant sigue siendo asunto interno del
módulo de tenants: no se agrega a `TenantInfo`, que es la vista pública del
tenant resuelto. Hacia afuera, la única forma de consultar entitlement es
la fachada de `entitlement`.

### 3. Gating por prefijo de ruta, antes de la cadena de seguridad

El request se atribuye a un módulo por **prefijo de ruta de API**
(`/api/ejemplo/**` pertenece al módulo `ejemplo`). Es la información que
está disponible antes de que exista handler resuelto, y es la que permite
rechazar sin que el módulo apagado ejecute nada.

La ruta que se compara tiene que ser **la misma que después usa Spring MVC
para elegir el handler**: decodificada y normalizada. Comparar contra la
URI cruda deja pasar cualquier variante equivalente —`/api/%65jemplo/ping`
es la misma ruta para el que despacha y otra distinta para el que gatea—,
y el request termina en el handler de un módulo apagado. Toda decisión de
gating que se tome sobre una forma de la ruta distinta de la que despacha
es, por construcción, evitable.

El filtro corre **después de la resolución de tenant**
([ADR 0004](0004-resolucion-de-tenant-por-subdominio.md)) y **antes de la
cadena de Spring Security**. Ese orden es lo que materializa el "entitlement
primero, permiso después" del ADR 0011: un usuario con `ejemplo.usar` en un
municipio que no contrató `ejemplo` recibe el rechazo por módulo, no por
permiso, y el rechazo es idéntico para cualquier usuario porque no depende
de quién sea.

El filtro es **fail-closed**: si la ruta pertenece a un módulo declarado y
no se puede determinar qué tiene habilitado el tenant, rechaza. Un error de
resolución nunca abre un módulo.

### 4. Lo que no pertenece a ningún módulo declarado es canon base

Las rutas que no matchean ningún `prefijoDeApi` no se gatean. El producto
se vende como canon base + módulos
([modelo comercial](../../producto/modelo-comercial.md)): la plataforma
—sesión, identidad visual, usuarios, roles, administración del municipio—
no es apagable, y no debe poder serlo. Un municipio al que se le apaga la
administración de usuarios queda inoperable y sin forma de recuperarse por
sí mismo.

Esto también evita tener que declarar un descriptor "de mentira" para la
plataforma con el único fin de marcarlo como no apagable.

### 5. El rechazo es 403 con código `MODULO_NO_CONTRATADO`

Cuerpo JSON `{"error": "...", "codigo": "MODULO_NO_CONTRATADO", "modulo":
"<codigo>"}`, consistente con el `{"error": ...}` que ya devuelve el resto
de la API.

No 404: el catálogo de módulos del producto es información comercial
pública, no un secreto que proteger, y un 404 volvería indistinguible "no
contratado" de "ruta inexistente" justo cuando alguien está diagnosticando
por qué un municipio no ve algo. No 402 (`Payment Required`): el ADR 0009
desacopla explícitamente el entitlement del estado de pago.

### 6. El código del módulo es el prefijo de sus permisos

`ejemplo` ↔ `ejemplo.usar`. El ADR 0011 ya identifica los permisos como
`modulo.accion`; fijar que ese `modulo` es el mismo código del descriptor
permite cruzar entitlement y permisos sin una tabla de mapeo, tanto en el
backend como en la UI de roles.

### 7. El catálogo se expone público; la navegación la arma el frontend

`GET /api/modulos` devuelve, para el tenant del request, el catálogo
completo con un flag `habilitado` por módulo. Es público porque es lo que
el portal necesita para pintarse antes de que haya sesión, y porque qué
módulos ofrece el producto —y cuáles tiene contratados un municipio, que se
ve en su propio portal de todos modos— no es información protegida. El
enforcement no depende de ocultar esto.

El descriptor **no** lleva rutas ni títulos de pantalla del frontend: el
backend no conoce la navegación del frontend. El frontend mantiene su
propio registro de pantalla por código de módulo y muestra en la navegación
los módulos que están habilitados **y** tienen pantalla registrada.

### 8. Prender y apagar es una operación de plataforma, no del municipio

El cambio se hace por la API de administración cross-tenant
(`/api/admin/municipios/{slug}/modulos`), con sesión de usuario de
plataforma ([ADR 0010](0010-autenticacion-por-sesion-scopeada-al-tenant.md)).
No hay ninguna superficie en el portal del municipio que permita cambiar
los módulos —ni los propios ni los de otro—: es una decisión comercial del
proveedor, y darle al municipio la capacidad de editar su propio
entitlement sería habilitar el autoservicio de lo que justamente se está
haciendo cumplir.

### 9. En la UI de roles, los permisos de módulos no contratados se muestran etiquetados

Resuelve el pendiente del ADR 0011. Se muestran, no se ocultan: un rol
puede prepararse antes de que el módulo se contrate, y ocultarlos haría
desaparecer sin explicación permisos ya asignados cuando un módulo se da de
baja. Se muestran con una etiqueta textual explícita ("módulo no
contratado"), no solo con un cambio de color.

### 10. Módulo de ejemplo como sujeto de prueba

Hasta la Fase 1 no hay ningún módulo funcional real. Para poder construir y
demostrar el mecanismo, la Fase 0 incluye un módulo `ejemplo` deliberadamente
mínimo (un ping y un eco), marcado en su documentación como módulo de
demostración del mecanismo de contratación, **no** como funcionalidad de
producto. Se elimina cuando el primer módulo real de Fase 1 pueda ocupar su
lugar como sujeto de los tests de gating.

## Alternativas consideradas

- **Anotar los controllers** (`@ModuloContratable("ejemplo")`) en vez de
  declarar prefijos: más localizado, pero el gating pasa a depender de que
  el handler ya esté resuelto, lo que empuja la verificación a un
  interceptor de MVC después de la cadena de seguridad — rompiendo el orden
  entitlement→permiso del ADR 0011. Descartada.
- **Mapa central ruta→módulo** en el módulo de tenants o en configuración:
  simple hoy, pero es el punto único que hay que tocar en cada alta de
  módulo y el que nadie actualiza cuando una ruta cambia. Descartada.
- **Catálogo de módulos en la base de control**, editable sin deploy:
  permitiría dar de alta un módulo comercialmente antes de que exista el
  código, que es exactamente el estado inconsistente que no se quiere
  (vender lo que no se puede servir). Descartada, con la misma lógica con
  la que el ADR 0011 descarta un catálogo de permisos editable.
- **Gating dentro del filtro de resolución de tenant**, como sugería el
  ADR 0009: evita un filtro más, pero mete en el módulo de tenants el
  conocimiento del catálogo de módulos funcionales. Se separa en un módulo
  propio manteniendo el orden que el ADR 0009 pedía (el tenant ya viene
  resuelto cuando corre el gating).
- **Solo ocultar en el frontend**: ya descartada por el ADR 0009.

## Consecuencias

- Agregar un módulo funcional implica tres cosas explícitas: publicar su
  `DescriptorDeModulo` —incluidas sus rutas de lectura pública—, agregar
  sus permisos al catálogo por migración (ADR 0011) y registrar su pantalla
  en el frontend. Ninguna toca código compartido: ni la cadena de
  seguridad, ni el filtro de gating, ni el módulo de tenants.
- Eliminar un módulo también tiene su costo explícito: sus permisos quedan
  sembrados en la base de cada municipio y hace falta una migración de
  limpieza. Vale en particular para el módulo `ejemplo` cuando se lo dé de
  baja en Fase 1.
- El módulo `entitlement` no puede depender de módulos funcionales; el test
  de modularidad de Spring Modulith lo verifica en el build.
- Un módulo que se apaga deja permisos asignados en roles del municipio.
  Es deliberado: volver a contratarlo restituye el acceso sin tener que
  rearmar los roles.
- Los tenants dados de alta antes de que existiera un módulo arrancan sin
  él: la lista vacía significa "ningún módulo contratado", no "todos".
- El gating cubre requests HTTP. Un módulo apagado cuyo código corra por
  eventos de dominio o tareas de fondo no queda cubierto por este
  mecanismo.

## Pendiente de definir

- Vigencia del contrato por módulo (desde cuándo, hasta cuándo), que el
  ADR 0009 dejó abierta: hoy la lista de habilitados no tiene fechas.
- Registro de quién prendió o apagó un módulo y cuándo: el ADR 0009 lo pide
  y depende de la auditoría transversal de R5.
- Gating de lo que no es un request HTTP (eventos, jobs).
- Consola del proveedor como superficie de UI para operar el entitlement;
  hoy se opera por API.
