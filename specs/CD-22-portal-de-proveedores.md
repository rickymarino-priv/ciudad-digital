# CD-22 · R14 — Una empresa se registra como proveedor del municipio y el municipio la aprueba

Rama: `CD-22-portal-de-proveedores` (desde `develop`).

Segunda rebanada de **Fase 2** (Recaudación e integración,
[roadmap](../docs/producto/roadmap-fases.md#fase-2--recaudación-e-integración-con-lo-existente)),
después de R13 (Tasas + pago online, ADR 0018). No requiere ADR propio:
reutiliza sin extenderlos los patrones ya fijados por
[ADR 0011](../docs/arquitectura/decisiones/0011-autorizacion-por-roles-con-permisos-granulares.md)
(permisos), [ADR 0012](../docs/arquitectura/decisiones/0012-declaracion-de-modulos-y-gating-por-ruta.md)
(catálogo de módulos, lectura pública), [ADR 0014](../docs/arquitectura/decisiones/0014-reclamos-ciudadanos-alta-publica-anonima-y-estado-propio.md)
§1/§3 (escritura pública solo `POST`; estado de dominio fijo codificado en
el servicio, sin motor de workflow genérico) y
[ADR 0017](../docs/arquitectura/decisiones/0017-seguimiento-anonimo-por-token-en-reclamos-y-mesa-de-entradas.md)
(token de seguimiento anónimo — `proveedores` es el **tercer** consumidor
de `seguimientoanonimo`, junto con `reclamos` y `mesaentradas`). Lean esos
ADRs antes de tocar código: esta spec no repite ese razonamiento, solo lo
traduce a tareas concretas.

A diferencia de todo lo construido hasta ahora, el usuario final de la
escritura pública acá no es un vecino sino una empresa/persona que quiere
venderle al municipio — pero el mecanismo que necesita (alta sin cuenta +
consulta posterior de su propio estado por posesión de un secreto) es
exactamente el que ADR 0017 ya generalizó para dos consumidores; no hay
ninguna decisión de arquitectura nueva que tomar para el tercero.

## Demo

Una empresa, sin cuenta ni sesión, entra al portal público de un
municipio y se registra como proveedor: razón social, CUIT, rubro,
email y teléfono de contacto, domicilio, y qué documentación declara
tener (constancia de AFIP, seguro de responsabilidad civil, certificado
de antecedentes — checklist, sin subir ningún archivo). Al enviar recibe
un código de seguimiento y su registro queda "Pendiente". Un agente
municipal, con sesión y el permiso `proveedores.gestionar`, ve el
registro en la lista de proveedores y lo aprueba (o lo rechaza, con un
comentario). La empresa, sin sesión, vuelve más tarde, ingresa su código
de seguimiento y ve que su registro está "Aprobado" (o "Rechazado", con
el comentario del municipio). El mismo CUIT registrado en un municipio no
aparece ni es consultable en el portal de otro municipio — pero puede
volver a registrarse sin conflicto en ese otro municipio, porque la
unicidad de CUIT es por base de tenant, no global.

## Qué se construye

### Backend — Tarea única (agente `backend`): módulo `proveedores`

Paquete nuevo `ar.com.ciudaddigital.proveedores`, con
`DescriptorDelModuloProveedores` en la raíz (mismo patrón que
`DescriptorDelModuloReclamos`/`DescriptorDelModuloTasas`) y el resto en
`ar.com.ciudaddigital.proveedores.internal`. `package-info.java` en la
raíz explicando que es un módulo funcional contratable (no canon base),
que reutiliza `seguimientoanonimo` para su consulta pública.

**Migración** `V15__crear_proveedores.sql` en
`backend/src/main/resources/db/tenant/` (confirmar al implementar que V15
es efectivamente el siguiente número libre; hoy el último es V14):

```sql
-- Registro de proveedores del municipio (backlog R14): alta pública sin
-- cuenta (mismo criterio que reclamos/mesa de entradas, ADR 0014 §1) y
-- consulta posterior por token de seguimiento (ADR 0017, tercer
-- consumidor junto con reclamo y expediente). "Documentación" en esta
-- rebanada es una declaración (checklist + texto libre), no un archivo:
-- no hay infraestructura de almacenamiento de archivos en el proyecto
-- todavía (ver spec CD-22).
create table proveedor (
    id                                    bigint generated always as identity primary key,
    razon_social                         varchar(200)  not null,
    -- Normalizado a "XX-XXXXXXXX-X" antes de guardar (ver GestionDeProveedores):
    -- así dos altas con el mismo CUIT en formatos de entrada distintos
    -- (con o sin guiones) no evaden la unicidad de abajo.
    cuit                                  varchar(13)   not null,
    rubro                                 varchar(30)   not null
        check (rubro in ('CONSTRUCCION', 'SERVICIOS', 'INSUMOS_Y_SUMINISTROS',
                          'PROFESIONALES', 'TECNOLOGIA', 'OTRO')),
    email_contacto                        varchar(200)  not null,
    telefono_contacto                     varchar(50)   not null,
    domicilio                             varchar(300)  not null,
    declara_constancia_afip               boolean       not null default false,
    declara_seguro_responsabilidad_civil  boolean       not null default false,
    declara_certificado_antecedentes      boolean       not null default false,
    documentacion_adicional               varchar(500),
    estado                                varchar(20)   not null default 'PENDIENTE'
        check (estado in ('PENDIENTE', 'APROBADO', 'RECHAZADO')),
    comentario_gestion                    varchar(1000),
    token_hash                            varchar(64)   not null,
    creado_en                             timestamptz   not null default now(),
    actualizado_en                        timestamptz   not null default now()
);

-- Único por base de tenant: "registro único de proveedores" (catálogo
-- funcional §4) es único dentro de cada municipio, no cross-tenant — cada
-- municipio tiene su propia base (ADR 0001), así que esto no exige ningún
-- chequeo cruzado.
create unique index proveedor_cuit_idx on proveedor (cuit);
create unique index proveedor_token_hash_idx on proveedor (token_hash);

comment on table proveedor is
    'Registro de proveedores del municipio, con documentación declarada y estado de aprobación (backlog R14).';

-- Catálogo de permisos: área "Proveedores". Revisar y aprobar/rechazar un
-- proveedor es una tarea operativa de gestión del día a día (como
-- reclamos.gestionar o mesaentradas), no un acto fiscal como
-- tasas.publicar (V14) — se asigna a AMBOS roles de sistema.
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('proveedores.ver', 'Proveedores', 'proveedores', 'ver',
     'Ver el listado y el detalle de los proveedores registrados.'),
    ('proveedores.gestionar', 'Proveedores', 'proveedores', 'gestionar',
     'Aprobar o rechazar el registro de un proveedor.');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo
from rol r, permiso p
where r.codigo in ('administrador', 'agente')
  and p.codigo in ('proveedores.ver', 'proveedores.gestionar');
```

**`RubroProveedor`** (`proveedores.internal`, enum): `CONSTRUCCION`,
`SERVICIOS`, `INSUMOS_Y_SUMINISTROS`, `PROFESIONALES`, `TECNOLOGIA`,
`OTRO`. Categoría fija elegida por quien se registra, mismo criterio que
`CategoriaReclamo`: no hay catálogo de rubros configurable por municipio
en esta rebanada.

**`EstadoDeProveedor`** (enum): `PENDIENTE`, `APROBADO`, `RECHAZADO`.
Transiciones válidas, codificadas en `GestionDeProveedores` (no en la
entidad, no en un motor de workflow — ADR 0014 §3): `PENDIENTE` →
{`APROBADO`, `RECHAZADO`}; `APROBADO` y `RECHAZADO` son terminales, sin
transiciones salientes. A diferencia de `reclamos` (4 estados, con
"en proceso" intermedio), acá alcanza con 3: la revisión de un proveedor
es una única decisión del municipio, no un ciclo con pasos intermedios
que valga la pena reflejar en el estado.

**`ProveedorEntity`** (`proveedores.internal`), campos que mapean 1:1 la
migración (`razonSocial`, `cuit`, `rubro` enum `RubroProveedor`,
`emailContacto`, `telefonoContacto`, `domicilio`,
`declaraConstanciaAfip`/`declaraSeguroResponsabilidadCivil`/
`declaraCertificadoAntecedentes` booleanos, `documentacionAdicional`
nullable, `estado` enum `EstadoDeProveedor`, `comentarioGestion` nullable,
`tokenHash`, `creadoEn`, `actualizadoEn`). Mismo patrón que
`ReclamoEntity`: constructor protegido sin argumentos, factory estática
`ProveedorEntity.nuevo(...)` (estado inicial `PENDIENTE`,
`creadoEn = actualizadoEn = Instant.now()`, sin comentario), getters
package-private, y un método de instancia `cambiarEstado(EstadoDeProveedor
nuevoEstado, String comentario)` que fija estado + `actualizadoEn`, y el
comentario si no es blank — sin validar la transición (esa tabla vive en
`GestionDeProveedores`). El token en claro nunca llega a esta entidad, ni
tiene getter que lo exponga (ADR 0017 §4).

**`ProveedorRepository`** (`JpaRepository<ProveedorEntity, Long>`):
- `Optional<ProveedorEntity> findByCuit(String cuit)` (chequeo de
  unicidad antes del alta).
- `List<ProveedorEntity> findAllByOrderByCreadoEnDesc()`.
- `Optional<ProveedorEntity> findByTokenHash(String tokenHash)`.

**`GestionDeProveedores`** (`@Service`):
- `ProveedorCreado registrar(String razonSocial, String cuit, String
  rubro, String emailContacto, String telefonoContacto, String
  domicilio, boolean declaraConstanciaAfip, boolean
  declaraSeguroResponsabilidadCivil, boolean declaraCertificadoAntecedentes,
  String documentacionAdicional)`, con `record ProveedorCreado(ProveedorEntity
  proveedor, String tokenDeSeguimiento)` (mismo patrón que
  `GestionDeReclamos.ReclamoCreado`). Valida, en este orden, lanzando
  `SolicitudInvalida` (package-private, mismo patrón que el resto de los
  módulos) en el primer problema que encuentre:
  - `razonSocial`: no vacía, largo máximo 200.
  - `cuit`: no vacío; **normalizar** quitando cualquier carácter que no
    sea dígito, y validar que el resultado tenga exactamente 11 dígitos
    (`SolicitudInvalida("El CUIT tiene que tener 11 dígitos.")` si no); no
    se valida el dígito verificador (fuera de alcance, ver más abajo).
    Formatear el resultado normalizado como `"XX-XXXXXXXX-X"` antes de
    guardar y de buscar por unicidad. Si `proveedores.findByCuit(cuitNormalizado)`
    ya existe, `SolicitudInvalida("Ya existe un proveedor registrado con
    ese CUIT.")`.
  - `rubro`: no vacío, tiene que matchear un valor de `RubroProveedor`
    (mismo patrón de `SolicitudInvalida` con mensaje que ya usa
    `categoriaDe(...)` en `ReclamosController`, pero acá conviene resolver
    el enum en el propio controller antes de llamar al servicio — igual
    que `ReclamosController.categoriaDe`).
  - `emailContacto`: no vacío, largo máximo 200. Sin validación de
    formato de email más allá de "no vacío" (mismo nivel de rigor que el
    resto del proyecto con campos de contacto de texto libre).
  - `telefonoContacto`: no vacío, largo máximo 50.
  - `domicilio`: no vacío, largo máximo 300.
  - `documentacionAdicional`: opcional, largo máximo 500 si viene.
  - Genera el token (`TokenDeSeguimiento.generar()`/`.hash(...)`, mismo
    patrón que `GestionDeReclamos.cargar`), construye la entidad y la
    guarda.
- `List<ProveedorEntity> listar()`: `proveedores.findAllByOrderByCreadoEnDesc()`.
  Sin filtro: a diferencia de `tasas` (que evita exponer todo el padrón de
  contribuyentes sin filtro), acá es una lista protegida por permiso
  (`proveedores.ver`), no pública — mismo criterio que
  `GestionDeReclamos.listar()`.
- `ProveedorEntity consultarPorToken(String token)`: mismo patrón que
  `GestionDeReclamos.consultarPorToken` — token vacío o que no matchea
  ninguna fila lanza `TokenNoEncontrado` (package-private, nueva clase,
  mismo patrón que la de `reclamos`/`mesaentradas`), nunca
  `SolicitudInvalida`.
- `ProveedorEntity cambiarEstado(Long id, EstadoDeProveedor nuevoEstado,
  String comentario)`: busca por id (`SolicitudInvalida("No existe el
  proveedor " + id + ".")` si no existe — mismo criterio que
  `GestionDeReclamos.cambiarEstado`, no un 404 dedicado), valida la
  transición contra la tabla de transiciones válidas
  (`SolicitudInvalida` si no es válida), aplica `cambiarEstado` y guarda.

**`ProveedoresController`** (`proveedores.internal`),
`@RequestMapping("/api/proveedores")`:
- `POST /api/proveedores` — pública (ver descriptor abajo), sin
  `@PreAuthorize`. Body `RegistrarProveedorRequest` con los campos de
  arriba (nombres de propiedad en minúscula/camelCase, mismo estilo que
  `CrearReclamoRequest`). Devuelve `201` con `ProveedorPublicoResponse`
  (`id`, `razonSocial`, `cuit`, `rubro`, `estado`, `creadoEn`,
  `tokenDeSeguimiento`) — mismo criterio de minimización que
  `ReclamoPublicoResponse`: no repite contacto/domicilio/documentación en
  la confirmación del alta, porque quien la recibe ya los escribió él
  mismo un segundo antes.
- `GET /api/proveedores` — `@PreAuthorize("hasAuthority('proveedores.ver')")`.
  Devuelve `List<ProveedorResponse>` (shape completo, ver abajo).
- `GET /api/proveedores/seguimiento/{token}` — pública (ver descriptor
  abajo). Devuelve `SeguimientoDeProveedorResponse` (ver abajo).
- `PATCH /api/proveedores/{id}/estado` —
  `@PreAuthorize("hasAuthority('proveedores.gestionar')")`. Body
  `CambiarEstadoRequest(String estado, String comentario)`, mismo patrón
  que `ReclamosController.CambiarEstadoRequest`. Devuelve
  `ProveedorResponse` actualizado.
- `@ExceptionHandler(SolicitudInvalida.class)` → `400`, mismo patrón que
  el resto de los módulos.
- `@ExceptionHandler(TokenNoEncontrado.class)` → `404`, mensaje genérico
  ("No encontramos un proveedor con ese código."), mismo criterio de no
  distinguir motivos de falla que ADR 0017 §4 fija para `reclamos`.

Records de respuesta:
- `ProveedorPublicoResponse(Long id, String razonSocial, String cuit,
  String rubro, String estado, Instant creadoEn, String
  tokenDeSeguimiento)`.
- `SeguimientoDeProveedorResponse(Long id, String razonSocial, String
  cuit, String rubro, String estado, String comentarioGestion, boolean
  declaraConstanciaAfip, boolean declaraSeguroResponsabilidadCivil,
  boolean declaraCertificadoAntecedentes, String documentacionAdicional,
  Instant creadoEn, Instant actualizadoEn)` — mismo criterio de
  minimización que `SeguimientoDeReclamoResponse` (ADR 0017 §5):
  **deliberadamente sin** `emailContacto`/`telefonoContacto`/`domicilio`,
  son datos que la propia empresa ya tiene. Sí incluye la documentación
  declarada y el rubro: es "en qué quedó" el registro, información que la
  empresa necesita para saber si tiene que agregar algo.
- `ProveedorResponse(Long id, String razonSocial, String cuit, String
  rubro, String emailContacto, String telefonoContacto, String domicilio,
  boolean declaraConstanciaAfip, boolean declaraSeguroResponsabilidadCivil,
  boolean declaraCertificadoAntecedentes, String documentacionAdicional,
  String estado, String comentarioGestion, Instant creadoEn, Instant
  actualizadoEn)` — shape completo, solo para quien tiene
  `proveedores.ver`.

**`DescriptorDelModuloProveedores`**:
- `codigo() = "proveedores"`, `nombre() = "Portal de proveedores"`,
  `descripcion()` breve (registro y documentación declarada de
  proveedores del municipio).
- `prefijosDeApi() = List.of("/api/proveedores")`.
- `rutasDeLecturaPublica() = List.of("/api/proveedores/seguimiento/{token}")`.
- `rutasDeEscrituraPublica() = List.of("/api/proveedores")` (solo el
  alta; listar y cambiar estado siguen protegidos).

**Test** `ProveedoresTest.java` (integración, mismo estilo que
`ReclamosTest`/`TasasTest`, extendiendo `SoporteDeIntegracion`):
1. Alta pública sin sesión con datos válidos → `201`, `estado: PENDIENTE`,
   `tokenDeSeguimiento` no vacío.
2. Alta con un CUIT ya registrado en el mismo municipio → `400`.
3. Alta con un CUIT mal formado (letras, o una cantidad de dígitos
   distinta de 11) → `400`.
4. Alta con un CUIT en formato `"20-12345678-1"` y otra alta con el mismo
   CUIT como `"20123456781"` (sin guiones) → la segunda es rechazada por
   duplicado: confirma que la normalización realmente evita evadir la
   unicidad con el formato de entrada.
5. Consulta por el token recibido en el alta (`GET
   /api/proveedores/seguimiento/{token}`, sin sesión) → `200`, incluye
   `rubro`/documentación declarada/`estado`, **no** incluye
   `emailContacto`/`telefonoContacto`/`domicilio`.
6. Consulta con un token inventado → `404`, mensaje genérico.
7. `GET /api/proveedores` sin sesión → `403` (no `MODULO_NO_CONTRATADO`:
   ver test 10 para ese caso); con sesión de administrador o de agente
   (ambos tienen `proveedores.ver`) → `200` con el proveedor recién
   creado, shape completo (incluye contacto).
8. `PATCH /api/proveedores/{id}/estado` con `proveedores.gestionar`
   (`{"estado":"APROBADO"}`) sobre un proveedor `PENDIENTE` → `200`,
   `estado: APROBADO`; una consulta posterior por token (test 5) refleja
   el nuevo estado. Y el camino de rechazo:
   `{"estado":"RECHAZADO","comentario":"Falta el seguro"}` → `200`,
   `estado: RECHAZADO`, `comentarioGestion` se refleja en la consulta por
   token.
9. Transición inválida (`PATCH` con `estado: RECHAZADO` sobre un
   proveedor ya `APROBADO`) → `400`.
10. Sin el módulo `proveedores` contratado: las cuatro rutas (alta,
    listado, consulta por token, cambio de estado) rechazan con `403
    MODULO_NO_CONTRATADO`, incluso sin sesión y con datos/token válidos
    (mismo test que ya existe para `reclamos`/`tasas`/`mesaentradas`).
11. Usuario con sesión pero sin `proveedores.gestionar` (por ejemplo, uno
    creado sin roles, o verificar que ninguno de los dos roles de sistema
    puede... **no aplica acá** porque ambos roles tienen el permiso —
    en su lugar, verificar que un usuario sin ningún rol asignado
    recibe `403` sin código al intentar `PATCH /api/proveedores/{id}/estado`).
12. **Aislamiento entre tenants**: registrar un proveedor con CUIT `X` en
    el municipio A; consultar su token contra el subdominio de B (con
    `proveedores` contratado en B) → `404` (mismo razonamiento que el
    test de aislamiento de CD-20/CD-21: la consulta corre contra la base
    de B, que no tiene esa fila). `GET /api/proveedores` en B no incluye
    el proveedor de A. Y, deliberadamente al revés de lo que uno
    esperaría de una unicidad "global", **registrar el mismo CUIT `X` en
    el municipio B tiene que funcionar** (`201`, no `400`): la unicidad de
    CUIT es por base de tenant, no cross-tenant.

## Frontend — Tarea (después de que el backend esté completo, agente `frontend`)

Nuevo `frontend/src/modulos/proveedores/PantallaDeProveedores.tsx`,
registrado en `registro.ts` (`proveedores: PantallaDeProveedores`) — no
toca `App.tsx`, `Navegacion.tsx` ni `CatalogoDeModulos.tsx` más allá de lo
que el registro ya cubre automáticamente.

Seguir de cerca la estructura de
`frontend/src/modulos/reclamos/PantallaDeReclamos.tsx` (léanlo antes de
escribir código: mismo patrón de componente raíz que decide entre alta
pública y panel de gestión según permiso, mismo manejo de foco,
`role="alert"`/`role="status"`, `vigente.current` para evitar pisar estado
de un componente desmontado, y mismo mecanismo de copiar el token al
portapapeles). No hay router de URLs en este frontend (confirmado en R12
y R13): la sub-vista de consulta por token es un estado local más, igual
que en `PantallaDeReclamos`.

1. **Componente raíz** `PantallaDeProveedores`: si `usuario` tiene
   `proveedores.ver`, muestra `PanelDeGestion`; si no (incluido anónimo),
   muestra `FormularioDeAlta`. Mismo criterio "esconder por comodidad, no
   por seguridad" (ADR 0011): el backend vuelve a exigir el permiso en
   cada ruta.

2. **`FormularioDeAlta`** (vista por defecto, sin sesión): formulario con
   `<label htmlFor>` explícito en cada campo:
   - Razón social (`<input>` requerido).
   - CUIT (`<input>` requerido, `placeholder="20-12345678-1"`, con un
     `campo__ayuda` aclarando que se puede escribir con o sin guiones).
   - Rubro (`<select>` requerido con las 6 opciones de `RubroProveedor`,
     mismo patrón que el `<select>` de categoría en `PantallaDeReclamos`).
   - Email de contacto, teléfono de contacto, domicilio (`<input>`
     requeridos).
   - Documentación declarada: un `<fieldset>` con `<legend>`
     "Documentación que declarás tener" y tres `<input type="checkbox">`
     con su `<label>` (constancia de AFIP, seguro de responsabilidad
     civil, certificado de antecedentes), más un párrafo aclarando
     explícitamente **"En esta etapa no se suben archivos: esto es una
     declaración. El municipio puede pedir que la presentes por otro
     medio."** — para no generar la falsa impresión de que ya se adjuntó
     algo.
   - Observaciones / documentación adicional (`<textarea>` opcional).
   - Botón "Registrarme como proveedor". Al enviar, `POST
     /api/proveedores` con el CUIT tal cual lo escribió la empresa (la
     normalización es responsabilidad del backend).
   - Confirmación de éxito (`role="status"`, con foco): mismo patrón que
     `PantallaDeReclamos` — mostrar el `tokenDeSeguimiento` en un campo de
     solo lectura con botón "Copiar", y la misma advertencia de que es la
     única forma de volver a consultar el estado y que no se reenvía por
     ningún otro medio.
   - Error de red/validación: `role="alert"`, con foco, mismo patrón que
     el resto de los formularios. Si el error es "CUIT ya registrado" (u
     otro mensaje del backend), mostrarlo tal cual lo devuelve la API.
   - Un botón/enlace "¿Ya te registraste? Consultá el estado" que cambia
     a la vista de consulta (mismo patrón que el botón equivalente de
     `PantallaDeReclamos`).

3. **Sub-vista de consulta por token** (mismo patrón que
   `ConsultaDeSeguimiento` de `PantallaDeReclamos`): formulario con un
   único campo "Código de seguimiento", botón "Consultar". Al enviar,
   `GET /api/proveedores/seguimiento/{token}`. Resultado mostrado en una
   `<dl>` con `role="status"` y foco: razón social, CUIT, rubro, estado,
   documentación declarada (cada checkbox como texto "Sí"/"No", no solo
   con un ícono — WCAG), observaciones, comentario del municipio (o "Todavía
   no hay comentario del municipio" si es `null`, mismo patrón que
   `PantallaDeReclamos`), fecha de creación y de última actualización.
   Token no encontrado → `role="alert"` con foco, mensaje genérico que ya
   devuelve la API.

4. **`PanelDeGestion`** (con `proveedores.ver`, y acciones solo si además
   tiene `proveedores.gestionar` — mismo patrón que
   `puedeGestionar`/`puedeVer` en `PantallaDeReclamos`): tabla con
   columnas Razón social, CUIT, Rubro, Contacto (email + teléfono),
   Documentación declarada (texto, no solo íconos), Estado, Creado, y —
   solo si `proveedores.gestionar`— una columna de Acción. Cada fila
   `PENDIENTE` con `proveedores.gestionar` muestra botones "Aprobar" y
   "Rechazar" (ninguno el único foco visual por color); al hacer clic
   abre una edición inline con un campo de comentario opcional y un
   botón "Confirmar", que llama a `PATCH /api/proveedores/{id}/estado`
   con `{estado, comentario}` — mismo patrón de edición inline por fila,
   foco y manejo de error que `abrirEdicion`/`guardarEdicion` en
   `PantallaDeReclamos`. Filas `APROBADO`/`RECHAZADO` muestran su estado
   como texto, sin acciones ("Sin cambios disponibles").

Todos los campos de formulario con `<label htmlFor>` explícito, mensajes
de error asociados por `aria-describedby`, foco programático en el
`h1` de cada vista interna al montar/cambiar de vista (mismo patrón que
`titulo.current?.focus()` en `PantallaDeReclamos`).

## Aislamiento entre tenants

Cubierto por los tests 10 y 12 de la Tarea backend: alta, listado,
consulta por token y cambio de estado corren, igual que el resto de cada
módulo, contra el datasource ruteado por el `Host` del request (ADR
0001) — no hay lógica nueva que pueda "cruzar" tenants, la superficie
nueva es una tabla con dos índices únicos (`cuit`, `token_hash`) *dentro*
de la base de cada municipio. La unicidad de CUIT es, a propósito,
solo dentro de esa base: el mismo CUIT puede (y debe poder) registrarse
en más de un municipio, porque cada municipio lleva su propio registro de
proveedores.

## Accesibilidad (WCAG)

Pantalla nueva (`PantallaDeProveedores`): mismo estándar ya validado en
el resto del frontend — `<label htmlFor>` explícito en todos los campos
(incluidos los tres checkboxes de documentación, cada uno con su propio
`<label>`, agrupados en un `<fieldset>`/`<legend>`), foco programático en
el `h1` al entrar a cada vista interna, foco en el mensaje de error
(`role="alert"`), resultado exitoso anunciado con `role="status"` y foco,
estado del proveedor (pendiente/aprobado/rechazado) y cada documento
declarado comunicados con texto además de con color/ícono. El aviso de
"esto es una declaración, no se sube ningún archivo" tiene que ser texto
real, visible sin depender de color.

## Fuera de alcance (explícitamente diferido)

- Carga de archivos reales de documentación (constancias/seguros/
  antecedentes en PDF u otro formato) y la infraestructura de
  almacenamiento que eso requeriría (no existe en el proyecto todavía).
- Validación del CUIT contra un padrón real (AFIP) o del dígito
  verificador — solo se valida formato (11 dígitos).
- Reconsideración o edición de un registro `RECHAZADO` por la propia
  empresa: sin cuenta no hay forma de verificar que quien edita es quien
  creó (mismo criterio que ADR 0014 para reclamos). El municipio puede
  igualmente re-contactar a la empresa por fuera del sistema.
- Notificación a la empresa cuando cambia el estado de su registro (motor
  de notificaciones, ADR 0013) — ninguna integración transversal de
  auditoría/notificaciones en esta rebanada, mismo criterio que R6-R13.
- Vencimiento o renovación periódica de la documentación declarada.
- Catálogo de licitaciones/compras y participación del proveedor en un
  proceso de compra concreto (Fase 3, Compras y Contrataciones).
- Consola del proveedor cross-tenant (contratos, facturación — ver
  [modelo comercial](../docs/producto/modelo-comercial.md)): es un
  producto distinto (plataforma comercial, no portal de un municipio),
  fuera del roadmap de Fase 2 tal como está descripto acá.
- Rate limiting sobre los endpoints públicos nuevos.

## Instrucción para los agentes implementadores

**No hagan commit, push, ni abran PR.** Dejen los cambios en el árbol de
trabajo sin commitear. El tech lead arma el commit, pushea la rama y abre
el PR contra `develop` una vez que backend, frontend y la auditoría estén
completos. Si el trabajo se corta por límite de sesión o cualquier otro
motivo, no reintenten commitear/pushear por su cuenta al retomar: avisen
el estado en el que quedó y esperen instrucción.
