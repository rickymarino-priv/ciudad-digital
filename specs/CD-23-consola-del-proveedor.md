# CD-23 · R15 — El proveedor de la plataforma ve y gestiona el contrato de sus municipios clientes

Rama: `CD-23-consola-del-proveedor` (desde `develop`).

Tercera rebanada de **Fase 2** (Recaudación e integración,
[roadmap](../docs/producto/roadmap-fases.md#fase-2--recaudación-e-integración-con-lo-existente)),
después de R13 (Tasas + pago online, ADR 0018) y R14 (Portal de
proveedores). El roadmap la nombra "Consola del proveedor: contratos,
módulos por municipio, estado de facturación" — nombre ambiguo, resuelto
en [ADR 0019](../docs/arquitectura/decisiones/0019-consola-del-proveedor-ui-cross-tenant-y-contrato-minimo.md):
"el proveedor" acá es **Ciudad Digital operando comercialmente sobre sus
municipios clientes** (consola cross-tenant de
[modelo-comercial.md](../docs/producto/modelo-comercial.md)
§"Superficies de administración"), **no** la empresa proveedora del
municipio de R14 — el propio backlog de R14 ya había diferido "consola del
proveedor cross-tenant" como algo distinto. Lean el ADR 0019 completo antes
de tocar código: fija la granularidad del contrato, dónde vive la UI y qué
no se construye. Esta spec no repite ese razonamiento, solo lo traduce a
tareas concretas.

La API cross-tenant que la consola necesita **ya existe en su mayor
parte**: alta y listado de municipios (ADR 0005, R2) y catálogo/entitlement
de módulos (ADR 0012 §8, R4), protegidos por sesión de usuario de
plataforma (ADR 0010, R3). Esta rebanada agrega lo que falta: tres campos
de contrato/facturación mínimos, su endpoint de edición, y — lo que de
verdad falta y es el corazón de R15 — una **UI** para todo esto, que hoy no
existe (se opera por `curl`, ver `docs/desarrollo.md`).

## Demo

Un usuario de plataforma entra a `http://admin.localhost:5173` (fuera de
cualquier portal municipal: no hay municipio en ese host) e inicia sesión
con sus credenciales de plataforma. Ve una lista de todos los municipios
dados de alta, con su estado de aprovisionamiento, tramo poblacional,
estado de facturación y cantidad de módulos contratados. Entra al detalle
de uno, ve el catálogo completo de módulos con cuáles tiene prendidos,
prende uno nuevo, y edita el estado de facturación a "Atrasado" con una
nota ("Tesorería avisó demora, contactado el 20/08"). Vuelve a la lista y
ve reflejados los dos cambios sin recargar. En ningún momento la consola
muestra nada de lo que pasa *dentro* de un municipio (usuarios, reclamos,
tasas): solo el contrato.

## Qué se construye

### Backend — Tarea única (agente `backend`)

Todo dentro del módulo `tenants` existente (`ar.com.ciudaddigital.tenants`),
sin módulo nuevo: es una extensión de la API de administración que ya
vive ahí.

**Migración** `V3__agregar_contrato_a_tenant.sql` en
`backend/src/main/resources/db/control/` (confirmar al implementar que V3
es el siguiente número libre; hoy el último es V2):

```sql
-- Contrato mínimo por municipio (ADR 0019): tramo poblacional (determina
-- el canon, cuyo monto vive fuera del sistema) y estado de facturación
-- (visibilidad manual, desacoplado del entitlement — ADR 0009). Todos con
-- default para no romper los municipios ya dados de alta.
alter table tenant
    add column tramo_poblacional  varchar(20) not null default 'MEDIANO',
    add column estado_facturacion varchar(20) not null default 'AL_DIA',
    add column nota_facturacion   text;

alter table tenant
    add constraint tenant_tramo_poblacional_valido check (
        tramo_poblacional in ('CHICO', 'MEDIANO', 'GRANDE')
    ),
    add constraint tenant_estado_facturacion_valido check (
        estado_facturacion in ('AL_DIA', 'ATRASADO')
    );

comment on column tenant.tramo_poblacional is
    'Tramo de canon por tamaño de municipio (ADR 0019). El monto no se modela acá.';
comment on column tenant.estado_facturacion is
    'Visibilidad manual del estado de cuenta. No afecta el entitlement (ADR 0009).';
```

**`TramoPoblacional`** (`tenants.internal`, enum): `CHICO`, `MEDIANO`,
`GRANDE`.

**`EstadoFacturacion`** (`tenants.internal`, enum): `AL_DIA`, `ATRASADO`.

**`TenantEntity`**: agregar los tres campos (`tramoPoblacional` enum,
`estadoFacturacion` enum, `notaFacturacion` nullable), mapeados
`@Enumerated(EnumType.STRING)` los dos primeros, mismo patrón que
`estado`. En `nueva(...)`, inicializar `tramoPoblacional = MEDIANO`,
`estadoFacturacion = AL_DIA`, `notaFacturacion = null` — un municipio
nuevo arranca con el tramo intermedio y al día hasta que la plataforma
diga lo contrario; no se pide en el alta (`SolicitudDeAlta` no cambia).
Agregar un método de instancia `cambiarInformacionComercial(TramoPoblacional
tramo, EstadoFacturacion estadoFacturacion, String nota)` que fija los
tres campos juntos (mismo criterio que `cambiarConfig`: quien llama manda
el valor completo, sin merge parcial). Getters package-private para los
tres.

**`InformacionComercialDeMunicipios`** (`@Service`, nuevo,
`tenants.internal`), mismo estilo que `AdministracionDeModulos`:
- `TenantEntity actualizar(String slug, String tramoPoblacional, String
  estadoFacturacion, String notaFacturacion)`: busca el municipio (mismo
  `SolicitudInvalida` que `AdministracionDeModulos.municipio(slug)` si no
  existe — de hecho, considerar inyectar `AdministracionDeModulos` acá
  para reusar ese método de búsqueda en vez de duplicarlo, o extraer un
  `TenantFinder` chico si el auditor prefiere eso; cualquiera de las dos
  formas es aceptable, lo que no vale es copiar y pegar la búsqueda).
  Valida `tramoPoblacional` contra `TramoPoblacional.valueOf(...)` y
  `estadoFacturacion` contra `EstadoFacturacion.valueOf(...)`, lanzando
  `SolicitudInvalida("No existe el tramo poblacional " + valor + ".")` (o
  el mensaje equivalente para facturación) ante un valor que no matchea
  ningún enum — mismo patrón de validación que ya usa
  `AdministracionDeModulos.validarCatalogoDeModulos`. `notaFacturacion` se
  acepta tal cual, incluido `null` (limpiar la nota es una operación
  válida, no un olvido — a diferencia de la lista de módulos, acá no hace
  falta un marcador "vacío explícito" porque un string nulo ya es
  inequívoco). Llama `tenant.cambiarInformacionComercial(...)` y guarda.

**`AdministracionDeMunicipiosController`**: agregar

```java
@PatchMapping("/{slug}/comercial")
MunicipioResponse actualizarComercial(@PathVariable String slug,
        @RequestBody ComercialRequest request) {
    TenantEntity tenant = informacionComercial.actualizar(
            slug, request.tramoPoblacional(), request.estadoFacturacion(),
            request.notaFacturacion());
    return describir(tenant);
}
```

con `record ComercialRequest(String tramoPoblacional, String
estadoFacturacion, String notaFacturacion)`. `tramoPoblacional` y
`estadoFacturacion` son obligatorios (si vienen `null`, que la conversión a
enum falle con el mismo `SolicitudInvalida` de arriba — no hace falta una
validación de "campo requerido" separada, alcanza con que `null` no
matchee ningún valor del enum al intentar `valueOf` sobre un string vacío;
si se prefiere un mensaje más claro para el caso `null` explícito, está
bien agregarlo, pero no es obligatorio).

**Extender `MunicipioResponse`** (usado tanto por `listar()` como por
`darDeAlta(...)` y ahora por `actualizarComercial(...)`) con los tres
campos nuevos más `cantidadDeModulosContratados` (int, `tenant.getConfig()
== null ? 0 : tenant.getConfig().modulosHabilitados().size()`) — así la
lista de municipios no necesita un segundo pedido por fila para mostrar
cuántos módulos tiene cada uno. Ajustar el método privado `describir(...)`
en consecuencia.

**Test** — nuevo archivo `ConsolaDelProveedorTest.java`
(`backend/src/test/java/ar/com/ciudaddigital/tenants/`), extendiendo
`SoporteDeIntegracion`, mismo estilo que `AltaDeMunicipioTest`:

1. Un municipio recién dado de alta trae, en la respuesta del alta y en el
   listado, `tramoPoblacional: "MEDIANO"`, `estadoFacturacion: "AL_DIA"`,
   `notaFacturacion: null`, `cantidadDeModulosContratados: 0`.
2. `PATCH /api/admin/municipios/{slug}/comercial` con sesión de plataforma
   y body válido (`{"tramoPoblacional":"GRANDE","estadoFacturacion":"ATRASADO","notaFacturacion":"Esperando transferencia"}`)
   → `200`, la respuesta refleja los tres valores; un `GET
   /api/admin/municipios` posterior también los refleja (confirma que
   persiste, no solo que la respuesta del `PATCH` los devuelve).
3. Mismo `PATCH` con `notaFacturacion: null` explícito sobre un municipio
   que ya tenía una nota → `200`, la nota queda en `null` (limpiar la nota
   es una operación válida).
4. `tramoPoblacional` con un valor que no existe (`"ENORME"`) → `400`.
5. `estadoFacturacion` con un valor que no existe (`"MOROSO"`) → `400`.
6. `PATCH` sobre un slug inexistente → mismo código que ya devuelve el
   resto de la API de administración para "no existe el municipio" (`400`
   con `SolicitudInvalida`, verificar contra el comportamiento actual de
   `AdministracionDeModulos.municipio(...)` en vez de asumir `404`).
7. Prender un módulo con el `PUT /modulos` ya existente y verificar que
   `cantidadDeModulosContratados` en un `GET /api/admin/municipios`
   posterior pasó de `0` a `1` (confirma que el campo nuevo realmente lee
   `config`, no un valor cacheado o hardcodeado).
8. **Quién puede llegar a esta superficie** (el criterio de aislamiento
   que le toca a una vista legítimamente cross-tenant, en vez de un test
   de aislamiento entre tenants): `PATCH /api/admin/municipios/{slug}/comercial`
   sin sesión → `401`; con `MockHttpSession` de un usuario de un municipio
   (mismo helper `iniciarSesionDeAdministrador("sanmartin")` que ya usa
   `AltaDeMunicipioTest.lasCredencialesDeMunicipioNoSonDePlataforma`) →
   `401` — la sesión de municipio no vale acá, tal como ya vale para el
   resto de `/api/admin/**`.

### Frontend — Tarea (después de que el backend esté completo, agente `frontend`)

Nueva carpeta `frontend/src/plataforma/`, con un componente raíz separado
de `App.tsx` — la consola **no** es una vista más del portal municipal, es
una superficie servida en otro host, sin tenant resuelto y sin tema de
ningún municipio. Antes de escribir código, lean `frontend/src/App.tsx`,
`frontend/src/acceso/Login.tsx`, `frontend/src/acceso/useSesion.ts`,
`frontend/src/acceso/api.ts` y `frontend/src/acceso/PanelDeAdministracion.tsx`:
la consola reutiliza sus patrones (manejo de foco, `role="alert"`/
`role="status"`, `<label htmlFor>` explícito, `pedir`/`enviar` de
`acceso/api.ts` tal cual — el mecanismo de cookie CSRF es el mismo
`XSRF-TOKEN`/`X-XSRF-TOKEN` para ambas cadenas de seguridad, no hace falta
un cliente HTTP nuevo) en vez de inventar los suyos.

1. **`main.tsx`**: antes de montar, decidir qué raíz mostrar según
   `window.location.hostname`. Extraer la decisión a una función pura y
   testeable de nombre y ubicación libres (por ejemplo
   `esHostDeConsola(hostname: string): boolean` en
   `frontend/src/plataforma/esHostDeConsola.ts`), que devuelva `true` si el
   host es exactamente `admin.localhost` o empieza con `admin.` (para
   cubrir el subdominio real de producción sin hardcodear un dominio
   específico). Si es la consola, montar `<ConsolaDelProveedor />`; si no,
   `<App />` como hasta ahora. No hay test automatizado de frontend en este
   proyecto (confirmado: no hay ningún `*.test.*` ni framework de test
   configurado) — no agreguen uno nuevo solo para esto, la función solo
   necesita quedar aislada para que se pueda revisar a simple vista.

2. **`useSesionDePlataforma.ts`** (`frontend/src/plataforma/`): mismo
   patrón exacto que `acceso/useSesion.ts`, apuntando a
   `/api/admin/sesion` en vez de `/api/sesion`. El usuario autenticado es
   `{id, nombre, email}` — **sin** `permisos`: la sesión de plataforma no
   tiene permisos granulares (ADR 0019 §4), es todo-o-nada.

3. **`LoginDePlataforma.tsx`**: mismo patrón que `acceso/Login.tsx` (foco
   en el `h1` al montar, foco en el error, `role="alert"`), pero sin
   `nombreMunicipio` — el texto de bajada es algo como "Ingresá con tu
   usuario de plataforma para operar la consola del proveedor." y el
   título "Consola del proveedor". No lleva botón "Volver": no hay ningún
   portal público al que volver desde acá.

4. **`ListaDeMunicipios.tsx`**: al montar, `pedir<MunicipioResponse[]>('/api/admin/municipios', ...)`.
   Mientras carga, `role="status"`; si falla, `role="alert"`. Tabla
   (`<table>`/`<caption>`/`<th scope="col">`, mismo patrón que
   `PanelDeUsuarios`) con columnas: Municipio (nombre + `slug` en `<code>`,
   como ya hace `App.tsx` en "Estado de la plataforma"), Estado
   (`PENDIENTE`/`APROVISIONANDO`/`ACTIVO`/`SUSPENDIDO`/`ERROR`, texto tal
   cual), Tramo poblacional, Estado de facturación, Módulos contratados
   (el número), y una columna de Acción con un botón "Ver detalle" por
   fila que navega al detalle (estado local en `ConsolaDelProveedor`, no
   hay router — mismo criterio que el resto del frontend, confirmado en
   R12/R13/R14). El estado de facturación `ATRASADO` se comunica con texto
   ("Atrasado"), nunca solo con color — mismo criterio WCAG que ya aplica
   el resto del proyecto a estados (`reclamos`, `mesaentradas`, etc.).

5. **`DetalleDeMunicipio.tsx`** (recibe `slug`, con un botón "Volver a la
   lista"):
   - Al montar, dos pedidos en paralelo: `GET
     /api/admin/municipios/{slug}/modulos` (catálogo con `habilitado` por
     módulo) y los datos del municipio (pueden salir del `MunicipioResponse`
     que ya tiene `ListaDeMunicipios`, pasado por props, para no repetir el
     `GET /api/admin/municipios` completo solo para leer una fila — decisión
     de implementación libre entre pasar por props o volver a pedir, lo que
     no vale es inventar un endpoint nuevo de "un solo municipio" que hoy no
     existe).
   - Sección "Módulos contratados": lista de checkboxes (uno por módulo del
     catálogo, con su `nombre` y `descripcion` como ayuda), reflejando
     `habilitado`. Un botón "Guardar módulos" que hace `PUT
     /api/admin/municipios/{slug}/modulos` con la lista completa de códigos
     marcados (mismo contrato que ya exige el backend: mandar todos los que
     tienen que quedar contratados). Éxito → `role="status"`, refresca el
     catálogo. Error → `role="alert"`, con foco.
   - Sección "Información comercial": `<select>` para tramo poblacional
     (Chico/Mediano/Grande, con `<label htmlFor>`), `<select>` para estado
     de facturación (Al día/Atrasado), `<textarea>` para la nota
     (opcional, sin `required`). Botón "Guardar información comercial" que
     hace `PATCH /api/admin/municipios/{slug}/comercial` con los tres
     valores. Éxito → `role="status"` con foco; error → `role="alert"` con
     foco, mismo patrón que el resto de los formularios del proyecto.
   - Foco programático en el `h1` ("Municipio de {nombreMunicipio}") al
     montar el detalle.

6. **`ConsolaDelProveedor.tsx`** (componente raíz, análogo a `App.tsx` pero
   sin tenant): incluye el mismo link de salto
   (`<a className="salto-al-contenido" href="#contenido">`), un `<header>`
   simple ("Consola del proveedor — Ciudad Digital", sin logo ni tema —
   no hay tenant del que sacarlos) con, si hay sesión, el nombre del
   usuario y un botón "Cerrar sesión". Estado local de vista
   (`{tipo: 'lista'} | {tipo: 'detalle', slug: string}`, mismo patrón que
   `Vista` en `App.tsx`/`vista.ts`, pero un tipo propio de este módulo —
   no reutilizar `Vista` de `modulos/`, que es del portal municipal).
   Mientras `useSesionDePlataforma` está `cargando`, pantalla mínima
   (`role="status"`, "Cargando…"); si `anonimo`, `LoginDePlataforma`; si
   `autenticado`, `ListaDeMunicipios`/`DetalleDeMunicipio` según la vista.

Todos los formularios con `<label htmlFor>` explícito, mensajes de error
asociados por `aria-describedby` donde corresponda, foco programático en
el `h1`/mensaje de resultado de cada vista al montar o al cambiar,
consistente con el resto del frontend.

## Aislamiento

Esta rebanada agrega una superficie **legítimamente cross-tenant** (es su
propósito, ADR 0019 §5): no aplica un test de "un municipio no ve los
datos de otro" porque, acá, ver todos los municipios a la vez es el
comportamiento correcto. El criterio que sí aplica —y que cubren los
tests 8 del backend— es **quién puede llegar a esta vista**: ni una sesión
anónima ni una sesión de un usuario de municipio (de ningún municipio)
pueden leer ni escribir en `/api/admin/municipios/**`, solo una sesión de
usuario de plataforma. La consola tampoco expone, en ningún momento, datos
operativos que vivan dentro de la base de un municipio (ADR 0019 §5): solo
lo que ya vive en la base de control (`tenant`) más el catálogo de módulos
en código.

## Accesibilidad (WCAG)

Pantallas nuevas (`LoginDePlataforma`, `ListaDeMunicipios`,
`DetalleDeMunicipio`, el shell de `ConsolaDelProveedor`): mismo estándar ya
validado en el resto del frontend — `<label htmlFor>` explícito en todos
los campos, foco programático en el `h1` de cada vista al entrar, foco en
mensajes de error (`role="alert"`) y de éxito (`role="status"`), estado de
facturación y de aprovisionamiento comunicados con texto (nunca solo con
color), link de salto al contenido principal, tabla con `<caption>` y
`<th scope="col">`/`<th scope="row">` donde corresponda.

## Fuera de alcance (explícitamente diferido)

- Motor de facturación real: importes, vencimientos, facturas emitidas.
  `estado_facturacion` es un campo manual, no un cálculo (ADR 0019 §2).
- Vigencia del contrato por módulo (fechas de alta/baja individuales) —
  sigue abierto desde ADR 0012 §8.
- Auditoría de quién cambió el tramo, el estado de facturación o los
  módulos contratados de un municipio, y cuándo (ADR 0019, Consecuencias).
- Alertas proactivas (email, notificación) sobre municipios `ATRASADO`.
- Permisos granulares dentro de la sesión de usuario de plataforma (hoy
  todo usuario de plataforma puede operar cualquier municipio).
- Alta de municipios y migración de esquema desde la consola: siguen
  siendo operaciones por API/`curl` (`docs/desarrollo.md`), no se les
  agrega UI en esta rebanada — el roadmap de R15 es "contratos, módulos,
  facturación", no reconstruir toda la superficie de `/api/admin/**`.
- Cualquier dato operativo de un municipio (usuarios, reclamos, tasas,
  proveedores): la consola no tiene, ni puede tener, ninguna vía para
  leerlos (ADR 0019 §5).
- Proyecto frontend separado para la consola (ADR 0019 §3).
- Rate limiting sobre `/api/admin/**` (ya diferido desde R2/R3).

## Instrucción para los agentes implementadores

**No hagan commit, push, ni abran PR.** Dejen los cambios en el árbol de
trabajo sin commitear. El tech lead arma el commit, pushea la rama y abre
el PR contra `develop` una vez que backend, frontend y la auditoría estén
completos. Si el trabajo se corta por límite de sesión o cualquier otro
motivo, no reintenten commitear/pushear por su cuenta al retomar: avisen
el estado en el que quedó y esperen instrucción.
