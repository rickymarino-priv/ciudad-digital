# CD-25 · R17 — Multas de tránsito (Juzgado de Faltas), primera rebanada de Fase 3

Ver [ADR 0021](../docs/arquitectura/decisiones/0021-multas-de-transito-alta-protegida-estado-propio-descuento-por-pago-temprano.md)
para el porqué de cada decisión de esta spec. Esta spec no reabre nada del
ADR: lo traduce a tareas concretas.

## Demo objetivo

Un agente de tránsito (con sesión y permiso `multas.labrar`) labra una
multa contra una patente. Un vecino, sin sesión, busca esa multa por
patente o DNI, ve el monto con descuento vigente, la paga con el
simulador de `pagos`, y la ve pasar a `PAGADA`. En otro caso, el vecino
presenta un descargo de texto libre contra una multa; un administrador
(con `multas.resolverDescargo`) la confirma o la anula, y el vecino ve el
resultado al volver a buscarla.

## Tarea 1 (backend) — módulo `multas`: modelo, alta protegida, búsqueda pública

**Comportamiento observable**: con sesión y `multas.labrar`,
`POST /api/multas` da de alta una multa en estado `NOTIFICADA` y devuelve
sus datos. Sin sesión, `GET /api/multas?patente=...` o
`GET /api/multas?dni=...` devuelve las multas de esa patente/DNI (lista,
puede ser vacía). Sin `patente` ni `dni`, o con ambos a la vez, 400. Un
municipio sin el módulo `multas` contratado rechaza ambas rutas con 403
`MODULO_NO_CONTRATADO`, con o sin sesión.

**Modelo** (`multas.internal`, nuevo módulo, prefijo `/api/multas`):

- `EstadoDeMulta`: enum `NOTIFICADA, EN_DESCARGO, CONFIRMADA, ANULADA, PAGADA`.
- `MultaEntity` (tabla `multa`), sin columna de tenant (mismo criterio que
  `TasaEntity`):
  - `id`, `patente` (`varchar(20)`, not null), `dni` (`varchar(20)`,
    nullable — no toda infracción tiene DNI del conductor identificado en
    el momento, la patente alcanza para labrar), `descripcionInfraccion`
    (`varchar(500)`, not null), `montoOriginal` (`numeric(12,2)`, not
    null, `check > 0`), `estado` (`varchar(20)`, not null, default
    `NOTIFICADA`, `check` de valores válidos), `notificadaEn`
    (`timestamptz`, not null, default `now()`).
  - Copia del actor que labra (ADR 0013/0018 §2): `labradaPorNombre`,
    `labradaPorEmail` (not null).
  - Descargo (ADR 0021 §5, todas nullable): `descargoTexto`
    (`varchar(2000)`), `descargoContacto` (`varchar(200)`),
    `descargoPresentadoEn` (`timestamptz`).
  - Resolución del descargo (todas nullable): `resolucionComentario`
    (`varchar(2000)`), `resueltoPorNombre`, `resueltoPorEmail`,
    `resueltoEn` (`timestamptz`).
  - Pago, mismo patrón que `TasaEntity`: `fechaPago` (`timestamptz`,
    nullable), `referenciaExternaPago` (`varchar(100)`, nullable, índice
    único parcial `where not null`, mismo criterio que
    `tasa_referencia_externa_pago_idx`).
  - `montoAPagar(Instant ahora)`: si `estado == NOTIFICADA` y
    `descargoPresentadoEn == null` y `ahora` está dentro de los 10 días
    corridos desde `notificadaEn`, devuelve `montoOriginal * 0.80`
    (redondeado a 2 decimales, `RoundingMode.HALF_UP`); si no, devuelve
    `montoOriginal`. Constantes `PORCENTAJE_DESCUENTO = new
    BigDecimal("0.20")` y `DIAS_PLAZO_DESCUENTO = 10` en `MultaEntity` o
    `GestionDeMultas` (a tu criterio de dónde queda más claro).
  - Índices: `multa_patente_idx on multa (patente)`,
    `multa_dni_idx on multa (dni)`.

- `GestionDeMultas` (`@Service`), con `@Transactional("tenantTransactionManager")`
  en los métodos de escritura (mismo patrón que `GestionDeTasas`):
  - `labrar(patente, dni, descripcionInfraccion, monto, labradaPorNombre, labradaPorEmail)`:
    valida no-blank y largos máximos (mismos límites de columna), monto
    `> 0`. `dni` puede ser null/blank (queda `null`). Guarda `NOTIFICADA`.
  - `buscar(patente, dni)`: exactamente uno de los dos no-blank, el otro
    debe ser null/blank — si los dos vienen con valor, o ninguno,
    `SolicitudInvalida`. Devuelve `findByPatenteOrderByNotificadaEnDesc`
    o `findByDniOrderByNotificadaEnDesc` según cuál vino.

**Fuera de alcance de esta tarea**: pago, descargo, permisos (van en las
tareas 2 y 3). Dejá `GestionDeMultas` preparado para que la Tarea 2 le
agregue los métodos de pago sin tocar `labrar`/`buscar`.

## Tarea 2 (backend) — pago con descuento, reutilizando `pagos`

**Comportamiento observable**: `POST /api/multas/{id}/pagos` (sin sesión)
inicia un pago por `montoAPagar(now())` contra `PasarelaDePago` y devuelve
`{referenciaExterna, urlDePago}`, igual shape que
`TasasController.IniciarPagoResponse`. Si la multa está `EN_DESCARGO`, o
ya es `PAGADA`/`ANULADA`, 400 con mensaje claro ("no se puede pagar una
multa con un descargo en trámite" / "esta multa ya está pagada" / "esta
multa fue anulada"). `POST /api/multas/pagos/confirmar` con
`{referenciaExterna, aprobado}` funciona exactamente como
`TasasController.confirmarPago` (aprobado → `PAGADA` + `fechaPago`;
rechazado → limpia `referenciaExternaPago`, sigue en su estado previo
para reintentar). Referencia inexistente o vacía → 404 genérico, mismo
mensaje sin distinguir formato inválido de no encontrada (ADR 0017 §4,
ADR 0018 §4).

**Implementación**:
- `multas` depende de `pagos` (`PasarelaDePago`, `SolicitudDePago`,
  `ResultadoDeInicioDePago`) igual que `tasas`. `SolicitudDePago.referenciaInterna`
  = `id.toString()`; `descripcion` = `descripcionInfraccion` (truncar si
  hace falta para no romper ningún límite del lado de `pagos`, que no
  tiene límite declarado — no es necesario truncar salvo que compile con
  error, `pagos` no valida largo).
- `GestionDeMultas.iniciarPago(Long id)`: busca la multa, valida estado
  (`NOTIFICADA` o `CONFIRMADA` — no `EN_DESCARGO`, no `PAGADA`, no
  `ANULADA`), calcula `montoAPagar(Instant.now())`, llama a
  `pasarelaDePago.iniciarPago(...)`, guarda `referenciaExternaPago` en la
  entidad (agregar método `iniciarPago(String referenciaExterna)` a
  `MultaEntity`, mismo patrón que `TasaEntity.iniciarPago`, con el mismo
  chequeo de estado dentro de la entidad como segunda barrera).
- `GestionDeMultas.confirmarPago(String referenciaExterna, boolean aprobado)`:
  mismo patrón que `GestionDeTasas.confirmarPago`. Agregar
  `MultaEntity.confirmarPago(boolean aprobado)` análogo a
  `TasaEntity.confirmarPago`.
- Excepciones: reutilizar/crear `SolicitudInvalida`, `MultaNoEncontrada`,
  `PagoNoEncontrado` dentro de `multas.internal` (no reutilices las
  clases de `tasas.internal`: son package-private de otro módulo,
  Spring Modulith lo va a rechazar en el build igual).

## Tarea 3 (backend) — descargo y su resolución, permisos, descriptor de módulo

**Comportamiento observable**:
- Sin sesión, `POST /api/multas/{id}/descargo` con
  `{texto, contacto?}` pasa la multa de `NOTIFICADA` a `EN_DESCARGO` y
  guarda `descargoTexto`/`descargoContacto`/`descargoPresentadoEn`. Si la
  multa no está `NOTIFICADA` (ya tiene descargo en curso, ya fue pagada,
  confirmada o anulada), 400 con mensaje claro. `texto` es obligatorio
  (no-blank, máx. 2000 caracteres); `contacto` opcional.
- Con sesión y `multas.resolverDescargo`,
  `POST /api/multas/{id}/resolver-descargo` con
  `{comentario, confirmar: boolean}` pasa `EN_DESCARGO` a `CONFIRMADA`
  (si `confirmar=true`) o `ANULADA` (si `confirmar=false`), guardando
  `resolucionComentario`, `resueltoPorNombre`/`resueltoPorEmail` (del
  actor autenticado) y `resueltoEn`. Si la multa no está `EN_DESCARGO`,
  400. Sin el permiso, 403 (verificar con `@PreAuthorize`, mismo patrón
  que `TasasController.publicar`).
- Con sesión y `multas.labrar` (no hace falta `multas.resolverDescargo`),
  hay que poder **listar todas las multas del municipio para gestión**
  (no solo buscar por patente/DNI como el vecino): agregá
  `GET /api/multas/gestion` protegido por `multas.labrar` **o**
  `multas.resolverDescargo` (`@PreAuthorize("hasAnyAuthority('multas.labrar', 'multas.resolverDescargo')")`),
  que devuelve todas las multas ordenadas por `notificadaEn` descendente
  — sin este endpoint, un administrador no tiene forma de ver qué multas
  están `EN_DESCARGO` esperando resolución, y esta rebanada tiene que
  poder demostrar ese flujo de punta a punta.

**Migración** (`V17__crear_multas.sql`, tenant): tabla `multa` completa
(columnas de Tarea 1 y 2 juntas), catálogo de permisos:

```sql
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('multas.labrar', 'Multas', 'multas', 'labrar',
     'Labrar un acta de infracción de tránsito.'),
    ('multas.resolverDescargo', 'Multas', 'multas', 'resolverDescargo',
     'Confirmar o anular una multa con descargo presentado.');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo from rol r, permiso p
where r.codigo in ('administrador', 'agente') and p.codigo = 'multas.labrar';

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo from rol r, permiso p
where r.codigo = 'administrador' and p.codigo = 'multas.resolverDescargo';
```

(Ver ADR 0021 §3/§4 para el porqué de cada asignación de rol — no lo
reabras.)

**`DescriptorDelModuloMultas`** (`multas.internal`, `@Component`):
- `codigo() = "multas"`, prefijo `/api/multas`.
- `rutasDeLecturaPublica() = List.of("/api/multas")` (la búsqueda por
  patente/DNI).
- `rutasDeEscrituraPublica() = List.of("/api/multas/{id}/pagos", "/api/multas/pagos/confirmar", "/api/multas/{id}/descargo")`
  — el descargo es alta pública anónima igual que reclamos/expedientes
  (ADR 0014 §1), el vecino no tiene cuenta. `POST /api/multas` (labrar) y
  `POST /api/multas/{id}/resolver-descargo` **no** van acá: quedan
  protegidas por sesión + permiso.

**Controller** (`MultasController`, `/api/multas`): junta las tres
tareas. Seguí el estilo de `TasasController` (mismos nombres de patrón:
`ErrorResponse`, `@ExceptionHandler` por tipo de excepción, records para
request/response). Response único `MultaResponse` con todos los campos
salvo que haya un motivo real de minimizar — acá no lo hay (nadie más
que el propio vecino/gestión ve esto, no hay dato de tercero que
proteger más allá de lo que ya es público por diseño al buscar por
patente/DNI, igual que `TasaResponse` no minimiza nada).

**Fuera de alcance**: notificación al vecino, un segundo descargo sobre
la misma multa, expiración de plazos para presentar descargo (ADR 0021,
Pendiente de definir) — no los resuelvas ni los bloquees con validación
extra, simplemente no los construyas.

## Tarea 4 (backend) — test de aislamiento entre tenants

**Obligatorio, no diferible (CLAUDE.md).** Agregar a
`backend/src/test/java/ar/com/ciudaddigital/multas/MultasTest.java` un
test `aislamientoEntreTenants` con el mismo patrón que
`TasasTest.aislamientoEntreTenants`: labrar una multa en el tenant A,
verificar que buscarla por patente/DNI desde el tenant B no la encuentra
(lista vacía), y que confirmar un pago con la `referenciaExterna` de una
multa del tenant A, ejecutado contra el tenant B, da 404 (no
"filtra y confirma la del otro tenant"). Sumale también un test de
permisos: un usuario con solo `multas.labrar` no puede pegarle a
`resolver-descargo` (403), y viceversa si hace falta un usuario de
soporte con solo `multas.resolverDescargo` para probarlo (podés crear el
usuario de test con los roles que necesites, no estás atado a los dos
roles de sistema para tests).

Cubrí además, en tests normales (no de aislamiento): circuito feliz
completo (labrar → buscar → pagar con descuento dentro de los 10 días →
queda `PAGADA`), circuito con descargo anulado (labrar → descargo →
resolver con `confirmar=false` → queda `ANULADA` → intentar pagar da 400),
circuito con descargo confirmado (labrar → descargo → resolver con
`confirmar=true` → queda `CONFIRMADA` → pagar da el monto **sin**
descuento), y el caso de monto sin descuento por estar fuera de los 10
días (podés mockear o construir la entidad con `notificadaEn` en el
pasado si el test lo permite, mismo mecanismo que uses para tests de
tiempo en otros módulos del proyecto — revisá si `ReclamosTest` o
`TasasTest` ya tienen algún precedente de manipular timestamps en tests
de integración antes de inventar uno nuevo).

## Tarea 5 (frontend) — pantalla del módulo `multas`

**Comportamiento observable**: pantalla nueva `PantallaDeMultas.tsx` en
`frontend/src/modulos/multas/`, registrada en `frontend/src/modulos/registro`
igual que el resto (mirá cómo `tasas` se registra ahí, mismo mecanismo).

Vistas (sin router, mismo patrón por estado local que `PantallaDeTasas`):

1. **Búsqueda** (default, pública): formulario con dos campos, "Patente"
   y "DNI", con la validación en el propio formulario de que se cargue
   uno u otro (no los dos, no ninguno) antes de habilitar "Buscar" — el
   mensaje de error del backend (400) también tiene que mostrarse si
   igual se manda una combinación inválida. Mismo texto explicativo que
   `tasas` sobre "no hace falta cuenta".

2. **Resultados**: tabla con columnas Patente, Infracción, Monto a pagar
   (usar el campo que el backend calcule y devuelva — ver nota abajo),
   Estado, Fecha de notificación, Acción. Fila con estado `NOTIFICADA` o
   `CONFIRMADA` → botón "Pagar". Fila `NOTIFICADA` → botón adicional
   "Presentar descargo". Fila `EN_DESCARGO` → texto "Descargo presentado,
   en revisión" en vez de acciones. Fila `PAGADA`/`ANULADA` → sin
   acciones, mostrar la fecha correspondiente.

   **Nota importante**: el backend expone `montoOriginal` (fijo) y
   necesita exponer también el monto vigente a pagar en este momento —
   agregá al `MultaResponse` del backend (Tarea 3) un campo
   `montoAPagar` calculado con `montoAPagar(Instant.now())` en el momento
   de serializar la respuesta, para que el frontend no tenga que
   reimplementar la regla de descuento. Si la multa ya está `PAGADA`,
   `montoAPagar` puede devolver el monto que efectivamente se cobró (no
   hace falta recalcular con la fecha actual, usá el monto con el que se
   confirmó si lo tenés a mano, o `montoOriginal` si no complicás la
   entidad para guardarlo — a tu criterio, no es crítico para la demo).

3. **Presentar descargo**: formulario con "Texto del descargo"
   (`textarea`, obligatorio) y "Contacto" (opcional). Al confirmar, la
   multa pasa a `EN_DESCARGO` y se muestra la confirmación.

4. **Pago (simulador)**: mismo patrón exacto que el simulador de
   `PantallaDeTasas` (mismo texto "Simulador de pago (entorno de
   prueba)", mismos dos botones Aprobar/Rechazar) — el monto mostrado es
   `montoAPagar`, no `montoOriginal`, para que se vea el descuento
   aplicado si corresponde.

5. **Labrar multa** (visible solo con `usuario?.permisos.includes('multas.labrar')`,
   mismo patrón que la sección "Publicar una tasa" de `PantallaDeTasas`):
   formulario con Patente, DNI (opcional), Descripción de la infracción,
   Monto. Al confirmar, muestra "Se labró la multa a la patente X" con
   confirmación accesible (mismo patrón `role="status"` + foco que
   `tasaPublicada` en `PantallaDeTasas`).

6. **Gestión de descargos** (visible solo con
   `usuario?.permisos.includes('multas.resolverDescargo')`): sección con
   una tabla de las multas en `EN_DESCARGO` (llamando a
   `GET /api/multas/gestion` y filtrando client-side por estado, o si
   preferís que el backend filtre agregá un query param — a tu criterio,
   documentalo en un comentario si te desviás de lo que dice esta spec),
   mostrando patente, texto del descargo, contacto, y dos botones
   "Confirmar multa" / "Hacer lugar (anular)", cada uno con un campo de
   comentario obligatorio antes de confirmar la acción.

**Accesibilidad (obligatorio, no diferible, CLAUDE.md)**: seguí al pie de
la letra los patrones ya usados en `PantallaDeTasas.tsx` — foco
gestionado por `useRef`+`tabIndex={-1}` al cambiar de vista, anuncios con
`role="status"`/`role="alert"`, `aria-invalid`/`aria-describedby` en
campos con error, `aria-busy` en botones de acción en curso, `<label
htmlFor>` en todo input, tabla con `<caption>` y `scope="col"`/`scope="row"`.
No inventes un patrón nuevo de accesibilidad: replicá el que ya existe.

**Fuera de alcance**: routing de URLs (no existe en este frontend, ADR
0018 §3 ya lo confirma), notificación del vecino, exportar/imprimir el
acta.

## Qué NO tocar

- La interfaz `PasarelaDePago` ni `PasarelaDePagoSimulada` (ADR 0018): se
  consumen tal cual.
- El motor de `mesaentradas` (`Expediente`, `MovimientoDeExpediente`,
  `CircuitoDeTramite`): no se extiende, no se le agrega un tipo `MULTA`
  (ADR 0021 §1/§2).
- Los permisos de `tasas`, `reclamos`, `proveedores` u otro módulo
  existente.

## Instrucciones para los agentes implementadores

No hagas commit, push ni abras PR por tu cuenta: dejá los cambios en el
working tree. El tech lead revisa, commitea y coordina el PR.
