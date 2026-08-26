# CD-24 · R16 — Un proveedor se registra y el municipio ve su situación fiscal validada contra AFIP (simulado)

Rama: `CD-24-validacion-fiscal-cuit-proveedores` (desde `develop`).

Cierra el último ítem de Fase 2 del roadmap ("capa de adaptadores a
sistemas legados AFIP/ARBA o equivalente provincial") resolviendo el
diferido explícito que dejó R14 (spec `CD-22-portal-de-proveedores.md`):
"verificación de CUIT contra un padrón real (AFIP u otro)". Requiere
[ADR 0020](../docs/arquitectura/decisiones/0020-padron-fiscal-simulado-para-cuit-de-proveedores.md)
— **léanlo entero antes de tocar código**, esta spec no repite su
razonamiento, solo lo traduce a tareas concretas. Puntos que hay que tener
internalizados de ese ADR antes de empezar:

- El resultado de la consulta al padrón fiscal es **advisory, no
  bloqueante**: no impide el alta ni la aprobación de un proveedor, en
  ningún caso. No agreguen ninguna validación que rechace un alta o un
  cambio de estado por la situación fiscal — sería contradecir la decisión
  del ADR.
- El adaptador simulado (`PadronFiscalSimulado`) es determinístico según
  el último dígito del CUIT normalizado: `0` → `NO_ENCONTRADO`; par
  distinto de cero (`2/4/6/8`) → `ACTIVO`; impar (`1/3/5/7/9`) →
  `INHABILITADO`. No hace ninguna llamada de red.
- `situacionFiscal` es información interna para quien tiene
  `proveedores.ver`: **no** se expone en la confirmación del alta
  (`ProveedorPublicoResponse`) ni en la consulta pública por token
  (`SeguimientoDeProveedorResponse`).

## Demo

Una empresa se registra como proveedor del municipio con un CUIT que
termina en un dígito par (por ejemplo `20-12345678-2`). Un agente
municipal, con sesión y `proveedores.ver`, entra al panel de gestión y ve
esa fila con "Situación fiscal (AFIP): Activo". Otra empresa se registra
con un CUIT terminado en dígito impar (por ejemplo `...78-1`): el agente
ve "Inhabilitado — revisar antes de aprobar", pero puede aprobarlo o
rechazarlo igual, sin que el sistema se lo impida. Una tercera, con CUIT
terminado en `0` (por ejemplo `...780`), aparece como "No encontrado en el
padrón". El comportamiento de aprobar/rechazar no cambia en absoluto según
la situación fiscal — solo la información visible cambia.

## Qué se construye

### Backend — Tarea 1 (agente `backend`): módulo canon base `padronfiscal`

Paquete nuevo `ar.com.ciudaddigital.padronfiscal`, mismo estatus que
`ar.com.ciudaddigital.pagos` (léanlo como referencia de estructura: interfaz
pública en la raíz del paquete, `package-info.java` en la raíz, adaptador
en `padronfiscal.internal`).

**`PadronFiscal`** (interfaz pública, raíz del paquete):

```java
public interface PadronFiscal {
    SituacionFiscal consultar(String cuit);
}
```

**`SituacionFiscal`** (enum público, raíz del paquete): `ACTIVO`,
`INHABILITADO`, `NO_ENCONTRADO`.

**`package-info.java`** (raíz): explicar que es un módulo canon base
(ADR 0020 §1), sin persistencia ni endpoints, con un único adaptador hoy
(`PadronFiscalSimulado`), consumido por `proveedores`.

**`PadronFiscalSimulado`** (`padronfiscal.internal`, package-private,
`@Component implements PadronFiscal`): sin llamadas de red. Recibe el CUIT
ya normalizado (11 dígitos, con o sin guiones — para no asumir de más,
extraer solo dígitos con `replaceAll("\\D", "")` antes de mirar el último
carácter) y devuelve, según el último dígito:
- `'0'` → `NO_ENCONTRADO`
- dígito par distinto de cero (`'2'`, `'4'`, `'6'`, `'8'`) → `ACTIVO`
- dígito impar (`'1'`, `'3'`, `'5'`, `'7'`, `'9'`) → `INHABILITADO`

No hace falta manejar un CUIT vacío o mal formado acá: `GestionDeProveedores`
ya lo valida y normaliza antes de llamar a este adaptador (ver Tarea 2).

Sin test unitario dedicado si no lo pide el flujo; la cobertura real viene
del test de integración de `proveedores` (Tarea 2, punto de tests) que
ejercita los tres resultados a través del alta pública.

### Backend — Tarea 2 (agente `backend`, después de la Tarea 1): integrar `padronfiscal` en `proveedores`

**Migración** `V16__agregar_situacion_fiscal_a_proveedor.sql` en
`backend/src/main/resources/db/tenant/` (confirmar al implementar que V16
es el siguiente número libre; hoy el último es V15):

```sql
-- Situación fiscal del proveedor según el padrón consultado en el alta
-- (backlog R16, ADR 0020): advisory, no bloquea ni el alta ni la
-- aprobación — es información para que el municipio decida, no una
-- condición que el sistema imponga. Se calcula una única vez, en el
-- alta (GestionDeProveedores.registrar), contra el único adaptador que
-- existe hoy (PadronFiscalSimulado, sin llamadas de red).
alter table proveedor
    add column situacion_fiscal varchar(20) not null default 'NO_ENCONTRADO'
        check (situacion_fiscal in ('ACTIVO', 'INHABILITADO', 'NO_ENCONTRADO'));

comment on column proveedor.situacion_fiscal is
    'Resultado (advisory, no bloqueante) de consultar el CUIT contra el padrón fiscal simulado en el alta (ADR 0020).';
```

Recordatorio del entorno: si esto agrega una migración nueva, correr
`mvn clean` antes de levantar tests (trampa conocida de Flyway en este
repo).

**`ProveedorEntity`** (`proveedores.internal`): agregar campo
`situacionFiscal` (`@Enumerated(EnumType.STRING)`, `@Column(name =
"situacion_fiscal", nullable = false, length = 20)`, tipo
`ar.com.ciudaddigital.padronfiscal.SituacionFiscal`). `ProveedorEntity.nuevo(...)`
gana un parámetro más, `SituacionFiscal situacionFiscal`, al final de la
lista de parámetros actual, y lo asigna. Getter package-private
`getSituacionFiscal()`. No agrega ninguna lógica de validación ni de
transición: la entidad solo guarda el valor que le pasan, igual criterio
que el resto de sus campos.

**`GestionDeProveedores`**: recibe `PadronFiscal padronFiscal` por
constructor (inyección, mismo patrón que el resto de las dependencias del
servicio). En `registrar(...)`, después de normalizar el CUIT y antes de
construir la entidad (es decir, después de la validación de unicidad y
del resto de las validaciones de campos, justo antes de generar el
token/construir `ProveedorEntity.nuevo(...)`), llamar
`padronFiscal.consultar(cuitNormalizado)` y pasar el resultado a
`ProveedorEntity.nuevo(...)`. No agregar ningún `if` que rechace la
solicitud según el resultado — el ADR 0020 lo prohíbe explícitamente.

**`ProveedoresController`**: agregar `situacionFiscal` (como `String`,
`.name()` del enum) a `ProveedorResponse` únicamente. **No** agregarlo a
`ProveedorPublicoResponse` ni a `SeguimientoDeProveedorResponse` — es
información interna (ADR 0020 §3), y agregarla ahí sería una fuga de un
dato que el ADR decide explícitamente no exponer por esas vías.

**Tests** (extender `ProveedoresTest.java`, no crear una clase nueva):

1. Alta con un CUIT terminado en dígito par (por ejemplo, ajustar o sumar
   una variante de `cuitAleatorio()` que fuerce el último dígito, ya que
   hoy siempre termina en `1`) → el listado protegido
   (`GET /api/proveedores` con sesión de administrador) muestra
   `situacionFiscal: "ACTIVO"` para ese proveedor.
2. Alta con un CUIT terminado en dígito impar (el `cuitAleatorio()`
   existente ya sirve, termina en `1`) → `situacionFiscal: "INHABILITADO"`.
3. Alta con un CUIT terminado en `0` → `situacionFiscal: "NO_ENCONTRADO"`.
4. Ninguno de los tres altas de arriba es rechazado (siguen respondiendo
   `201` con `estado: PENDIENTE`), confirmando que la situación fiscal no
   bloquea el alta — este es el test que hace explícita la decisión de
   producto del ADR 0020, no solo un detalle incidental.
5. Un proveedor con `situacionFiscal: "INHABILITADO"` puede aprobarse
   igual (`PATCH .../estado` con `{"estado":"APROBADO"}` → `200`,
   `estado: "APROBADO"`) — confirma que tampoco bloquea la aprobación.
6. `situacionFiscal` **no** aparece en la respuesta del alta pública
   (`ProveedorPublicoResponse`, `POST /api/proveedores`) ni en la consulta
   por token (`GET /api/proveedores/seguimiento/{token}`) —
   `jsonPath("$.situacionFiscal").doesNotExist()` en ambos casos.
7. **Aislamiento entre tenants** (extender el test existente
   `aislamientoEntreTenants`, no duplicar toda la clase): la
   `situacionFiscal` de un proveedor registrado en el municipio A no es
   visible desde el listado del municipio B (ya cubierto indirectamente
   porque el proveedor de A no aparece en el listado de B, pero confirmen
   explícitamente que si buscaran ese campo tampoco lo encontrarían — por
   ejemplo, agregando la aserción sobre el mismo `jsonPath` que ya usa el
   test para confirmar que la fila entera está ausente).

No hace falta ningún cambio en `DescriptorDelModuloProveedores`
(`padronfiscal` no es contratable, no tiene rutas propias) ni en
`ModularityTests` (genérico, verifica automáticamente que `proveedores`
puede depender de `padronfiscal` y que `padronfiscal` no depende de nada
funcional).

### Frontend — Tarea 3 (agente `frontend`, después de que el backend esté completo)

Modificar `frontend/src/modulos/proveedores/PantallaDeProveedores.tsx`
únicamente (no hay pantalla nueva, es una extensión de la existente).

1. Tipo `Proveedor` (el que mapea `ProveedorResponse`): agregar
   `situacionFiscal: 'ACTIVO' | 'INHABILITADO' | 'NO_ENCONTRADO'`.

2. Un mapa `ETIQUETA_SITUACION_FISCAL` con texto explícito para cada
   valor — no solo la palabra del enum, sino algo accionable para quien
   gestiona, por ejemplo:
   - `ACTIVO` → `'Activo'`
   - `INHABILITADO` → `'Inhabilitado — revisar antes de aprobar'`
   - `NO_ENCONTRADO` → `'No encontrado en el padrón'`

3. En `PanelDeGestion`, agregar una columna nueva "Situación fiscal
   (AFIP)" a la tabla (entre "Documentación declarada" y "Estado", o donde
   quede mejor visualmente), mostrando `ETIQUETA_SITUACION_FISCAL[proveedor.situacionFiscal]`
   como texto plano — sin ícono ni color como único portador de la
   información (WCAG, mismo criterio que `textoSiNo` ya aplica a la
   documentación declarada en esta misma pantalla). Esta columna es
   puramente informativa: **no** condiciona ni oculta los botones
   "Aprobar"/"Rechazar" ni ninguna otra acción — la situación fiscal no
   bloquea nada (ADR 0020 §3), la UI tiene que reflejar exactamente eso.

4. No tocar `FormularioDeAlta` ni `ConsultaDeSeguimiento`: `situacionFiscal`
   no llega en la respuesta del alta pública ni en la consulta por token
   (backend, Tarea 2), así que no hay nada que mostrar ahí.

No hace falta ningún componente nuevo, ninguna ruta nueva, ni tocar
`registro.ts`/`App.tsx`/`Navegacion.tsx`: es una columna más en una tabla
que ya existe.

## Aislamiento entre tenants

No hay superficie nueva de aislamiento: `situacion_fiscal` es una columna
más de la tabla `proveedor`, que ya vive en la base de cada municipio
(ADR 0001) y ya está cubierta por el test de aislamiento existente de
`ProveedoresTest`. La Tarea 2, punto de tests, extiende ese test para
confirmarlo explícitamente en vez de asumirlo.

## Accesibilidad (WCAG)

No se agrega ninguna pantalla nueva, se extiende una tabla ya accesible.
El único requisito nuevo es que la situación fiscal se comunique con texto
explícito, no con color o ícono solamente — igual criterio que ya aplica
`itemsDeDocumentacion`/`textoSiNo` en la misma pantalla. La columna nueva
tiene que tener su `<th scope="col">` como el resto de la tabla.

## Fuera de alcance (explícitamente diferido, ver ADR 0020)

- Integración con un padrón real de AFIP/ARBA o equivalente provincial, y
  sus credenciales.
- Manejo de caída/timeout/reintentos de un servicio real (el adaptador
  simulado nunca falla).
- Re-consulta de la situación fiscal después del alta (por ejemplo,
  periódicamente, o a pedido antes de aprobar).
- Bloquear el alta o la aprobación según el resultado — decisión de
  negocio explícitamente no tomada en esta rebanada.
- Exponer la situación fiscal a la propia empresa registrada (ni en la
  confirmación del alta ni en la consulta por token).
- Aplicar el mismo mecanismo a `tasas` (no tiene hoy ningún campo de
  identidad fiscal del contribuyente).
- Rate limiting o cualquier endurecimiento de seguridad nuevo (no aplica,
  esta rebanada no agrega ningún endpoint público nuevo).

## Instrucción para los agentes implementadores

**No hagan commit, push, ni abran PR.** Dejen los cambios en el árbol de
trabajo sin commitear. El tech lead arma el commit, pushea la rama y abre
el PR contra `develop` una vez que backend, frontend y la auditoría estén
completos. Si el trabajo se corta por límite de sesión o cualquier otro
motivo, no reintenten commitear/pushear por su cuenta al retomar: avisen
el estado en el que quedó y esperen instrucción.
