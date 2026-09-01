# 0033 - Framework de reportes/BI: motor de métricas agregadas y primer tablero real

- Estado: Aceptada
- Fecha: 2026-09-01

## Contexto

El [roadmap de Fase 0](../../producto/roadmap-fases.md#fase-0--fundación-de-plataforma)
pedía un "framework de reportes/BI (el motor, no todos los tableros)", pero
[backlog inicial](../../producto/backlog-inicial.md#movido-a-fase-1) lo movió
explícitamente fuera de esa fase: "no tienen consumidor todavía y no se
pueden construir como rebanada demostrable [...] se construyen [...] junto
con el primer módulo funcional que efectivamente los necesita". Desde
entonces, "Auditoría interna / Control de gestión" —candidata natural de
consumidor— se evaluó y se descartó tres veces seguidas por el mismo motivo
(ADR 0026 Contexto, ADR 0027 Contexto, ADR 0030 Contexto): un tablero cruzado
necesitaría, o bien tocar cada módulo funcional existente para que exponga un
conteo propio, o bien un mecanismo de agregación nuevo, y ninguna de las dos
cosas es una rebanada de una semana con algo nuevo para mostrar por sí sola.

Ese momento ya llegó: el producto tiene 15+ módulos funcionales con datos
reales de test (`reclamos`, `mesaentradas`, `multas`, `turnos`, etc.), varios
con un campo de estado propio y volumen suficiente para agregar. Esta ADR
construye el motor mínimo **con** un consumidor real —no una interfaz
vacía—, sin resolver Auditoría interna completa (que sigue siendo una
rebanada futura) y sin inventar eventos de dominio nuevos (eso es
precisamente lo que las tres rebanadas anteriores dejaron pendiente y esta
ADR no lo reabre).

### Qué es "el motor" acá: agregación directa sobre las tablas de cada módulo, no eventos

La opción "cada módulo expone su propio conteo" (la primera de las dos que
ADR 0026/0027/0030 vienen señalando) es la que se elige, y la que da forma al
diseño: **consultas agregadas directas** (`group by` sobre la tabla propia de
cada módulo, ej. `select estado, count(*) from reclamo group by estado`),
calculadas en el momento de cada request contra el datasource ya ruteado por
tenant (ADR 0001) —el mismo mecanismo de aislamiento que usa cualquier otro
endpoint del proyecto, sin nada nuevo que verificar—. Se descarta
explícitamente un mecanismo de eventos/proyección agregada sobre
`registro_auditoria` (ADR 0013): agregaría un modelo de agregación que hoy no
existe, sobre un mecanismo (`event_publication`/`registro_auditoria`) que
"guarda quién hizo qué, no un modelo de agregación" (ADR 0026, palabras
textuales), y el producto sigue teniendo un único evento de dominio real
(`UsuarioCreado`, `acceso`) — construir sobre eventos sería diseñar a ciegas
sobre una capacidad que casi ningún módulo usa.

### El problema de dependencias que resuelve el diseño

Spring Modulith prohíbe que un módulo nuevo lea las entidades internas de
otro (ADR 0003, verificado por `ModularityTests`). Un motor de reportes que
importara `ReclamoRepository`/`ExpedienteRepository` directamente violaría
esa regla, y —igual que advirtió ADR 0012 §"Restricción estructural" para el
catálogo de módulos— generaría un punto central que hay que tocar en cada
módulo funcional nuevo, exactamente el acoplamiento que Spring Modulith
existe para evitar.

## Decisión

### 1. Módulo nuevo `reportes`, canon base, sin `DescriptorDeModulo`

`reportes` es un módulo transversal, igual que `auditoria`: no publica un
`DescriptorDeModulo` (ADR 0012 §1) porque no es funcionalidad contratable por
área, es infraestructura de plataforma que ya sirve datos de módulos que sí
lo son (mismo criterio, textual, que ya documenta
`auditoria.internal.AuditoriaController`: "No hay `DescriptorDeModulo` para
`auditoria`: es canon base, no un módulo contratable"). El
[catálogo funcional](../../producto/catalogo-funcional.md) ya lo clasifica
así, bajo "5. Plataforma transversal: servicios que consumen todos los
módulos, no módulos de área" junto con Notificaciones, Motor de
expediente/workflow y Auditoría y trazabilidad transversal.

`GET /api/reportes/**` no pasa por el filtro de gating de entitlement (no
tiene prefijo declarado en ningún `DescriptorDeModulo`): queda protegido
únicamente por sesión y permiso (Decisión 5), igual que `/api/auditoria`.

### 2. La inversión de dependencia: `reportes` define la SPI, los módulos funcionales la implementan — nunca al revés

Mismo patrón exacto que `entitlement.DescriptorDeModulo` (ADR 0012 §2) y
`tenants.SolicitudesDeModulo`/`ContratoDelTenant` (ADR 0022 §1): `reportes`
publica una interfaz pública,

```java
public interface FuenteDeMetricas {
    String moduloCodigo();   // mismo código que el DescriptorDeModulo del módulo, ADR 0012 §6
    String moduloNombre();
    List<SerieDeMetricas> series();
}

public record SerieDeMetricas(String nombre, List<PuntoDeMetrica> puntos) {}
public record PuntoDeMetrica(String etiqueta, long cantidad) {}
```

y cada módulo funcional que quiera aportar un indicador registra un bean
`@Component` en su propio `internal` que la implementa, calculando sus
`series()` con una consulta agregada sobre su propio repositorio (Decisión
3). `reportes` nunca importa `reclamos.internal` ni `mesaentradas.internal`:
recolecta **todos** los beans de tipo `FuenteDeMetricas` presentes en el
contexto de Spring (`List<FuenteDeMetricas>` inyectado por tipo, resolución
estándar del contenedor, sin que `reportes` necesite conocer qué módulos
existen). La dependencia apunta hacia adentro (`reclamos`/`mesaentradas` →
`reportes`), nunca hacia afuera: verificado por `ModularityTests`, mismo
criterio que ya deja explícito ADR 0012 §2 para `entitlement`.

`reportes` sí depende de `entitlement.ModulosDelTenant` (Decisión 4), lo cual
no genera ciclo: `entitlement` no depende de ningún módulo funcional ni de
`reportes` (ADR 0012 §2 sin cambios), así que la cadena
`reclamos`/`mesaentradas` → `reportes` → `entitlement` es unidireccional.

### 3. Consumidor real de esta rebanada: `reclamos` y `mesaentradas`

Ambos tienen campo de estado propio y volumen real de datos de test, y son
los que la propia rebanada señala como candidatos obvios.

- `reclamos.internal.FuenteDeMetricasDeReclamos`: una serie, "Reclamos por
  estado", `group by estado` sobre `reclamo` vía una consulta nueva de
  `ReclamoRepository` (proyección `etiqueta`/`cantidad`, no trae las
  entidades completas — no hace falta para contar).
- `mesaentradas.internal.FuenteDeMetricasDeMesaEntradas`: dos series,
  "Expedientes por tipo de trámite" (`group by tipo`) y "Expedientes por
  estado" (`group by estado`), mismo mecanismo sobre `ExpedienteRepository`.

Cada serie se ordena por `etiqueta` ascendente (determinístico, mismo
criterio de orden explícito que ya usa el proyecto — ej. ADR 0030 §4 para
`eventos`). Un valor de estado sin ningún registro no aparece en la serie
—no se rellena con cero—: es un agregado real de lo que hay, no un catálogo
fijo pre-poblado.

No se agregan más módulos en esta rebanada (ver "Fuera de alcance"): el
objetivo es demostrar que el motor sirve para algo real con dos consumidores
independientes, no cubrir todos los módulos con estado propio del proyecto.

### 4. El tablero solo muestra fuentes de módulos contratados por el tenant actual

`reportes.internal.ReportesController` filtra las `FuenteDeMetricas`
recolectadas contra `entitlement.ModulosDelTenant.habilitadosDelRequestEnCurso()`
(ADR 0012 §2), comparando `moduloCodigo()` contra el conjunto habilitado.
Motivo: aunque no mostrar el conteo de un módulo no contratado no es una fuga
de datos (si el módulo no está contratado nunca hubo escritura posible sobre
él, porque el gating rechaza el `POST` con `MODULO_NO_CONTRATADO`), mostrar
un widget de "Reclamos por estado" a un municipio que nunca contrató
`reclamos` sería una inconsistencia de producto: el resto del sistema (menú,
catálogo `/api/modulos`) ya oculta lo no contratado, y el tablero de reportes
tiene que ser coherente con eso. Si `habilitadosDelRequestEnCurso()` devuelve
`Optional.empty()` (no se pudo determinar, fuera de un request con tenant
resuelto), el tablero devuelve una lista vacía: mismo espíritu fail-closed
que ADR 0012 §3, aplicado acá a "no mostrar" en vez de a "rechazar", porque
este endpoint es de lectura informativa, no de gating de escritura.

### 5. Permiso nuevo `reportes.ver`, reservado a `administrador`

Se crea un permiso nuevo (no se reutiliza uno existente): ningún permiso
actual cubre "ver indicadores agregados cruzando módulos". Se reserva a
`administrador`, mismo criterio que `municipio.verContrato` (ADR 0022 §4) y
`auditoria.ver`: es información de gestión para quien dirige el municipio, no
trabajo operativo cotidiano de un agente de un área — el propio catálogo
funcional describe esta capacidad como de "alto valor para
intendente/gabinete". No se separa en "ver"/"administrar" porque no hay
ninguna acción de escritura que administrar: el tablero es de solo lectura
(mismo criterio que llevó a un único permiso `auditoria.ver`).

`GET /api/reportes/tablero` es el único endpoint de esta rebanada, protegido
por `@PreAuthorize("hasAuthority('reportes.ver')")`.

### 6. Dónde vive la pantalla: dentro de `PanelDeAdministracion`, no en el registro de módulos

`reportes` no tiene pantalla de portal público ni entra en
`frontend/src/modulos/registro.ts`: ese registro es, por diseño (ADR 0012
§7, comentario del propio archivo), el mapeo de módulos **contratables** a
su pantalla, y `reportes` no es contratable (Decisión 1). Es una pantalla
más de administración, mismo patrón que `PanelDeAuditoria`/
`PanelDeMiMunicipio`/`PanelDeUsuarios`/`PanelDeRoles`: un componente nuevo
`PanelDeReportes` en `frontend/src/acceso/`, gateado en
`PanelDeAdministracion.tsx` por `puede('reportes.ver')`, sin ruta propia.

La pantalla renderiza, por cada fuente que devuelve el backend, una tabla
accesible por serie (`<caption>`, `scope="col"`/`scope="row"`), mismo patrón
exacto que `PanelDeAuditoria` (`role="status"` mientras carga, `role="alert"`
en error). No se introduce ninguna librería de gráficos: el proyecto no usa
ninguna hoy, y una tabla es más accesible por defecto que un gráfico
(navegable por teclado y lector de pantalla sin trabajo adicional) para el
volumen de datos de esta rebanada.

### 7. Sin caché ni materialización — cada request recalcula

Cada llamada a `GET /api/reportes/tablero` ejecuta las consultas agregadas
de todas las fuentes habilitadas en el momento. Sin caché, sin tarea
periódica, sin tabla de snapshot: es una optimización de performance sin
problema medido (fuera de alcance por criterio general del proyecto, ver
`CLAUDE.md` "Qué sí se difiere a tickets posteriores"), razonable para el
volumen de datos de test de esta rebanada y para una pantalla de
administración de bajo tráfico.

## Fuera de alcance de esta rebanada (explícito)

- **Auditoría interna / Control de gestión completa**: sigue siendo una
  rebanada futura propia. Esta ADR construye el motor y un tablero mínimo
  con dos consumidores, no el módulo de control de gestión del catálogo
  funcional (que probablemente necesite más fuentes, filtros por fecha,
  quizás alertas/umbrales, y su propia decisión de a qué rol se le muestra
  cada indicador — nada de eso se decide acá).
- **Eventos de dominio nuevos**: el motor no depende de ningún mecanismo de
  eventos; sigue sin haber más publishers de eventos de dominio que
  `acceso.UsuarioCreado`, sin cambios respecto de ADR 0026/0027/0030.
- **Más de dos módulos consumidores**: `multas`, `turnos`, `desarrollosocial`
  y cualquier otro módulo con estado propio pueden sumar su propia
  `FuenteDeMetricas` después, sin tocar `reportes` — es exactamente el punto
  de la inversión de dependencia (Decisión 2) —, pero no se hace en esta
  rebanada.
- **Geolocalización/mapas, exportación a PDF/Excel**: no aportan a demostrar
  que el motor funciona con un consumidor real; quedan disponibles como
  mejora futura si un caso real las pide.
- **Filtros por rango de fecha, comparación entre períodos, series
  históricas**: el tablero muestra el estado actual agregado, no una serie
  temporal — no hay ningún dato de "cuándo cambió de estado" que agregar
  todavía (el proyecto no registra historial de transición de estado fuera
  de `mesaentradas.MovimientoDeExpedienteEntity`, que es propio de ese
  módulo y no se generaliza acá).

## Alternativas consideradas

- **Mecanismo de eventos/proyección agregada sobre `registro_auditoria`**:
  descartado — ver Contexto. Es la opción que las tres ADRs anteriores ya
  identificaron como más cara y que la propia tarea de esta rebanada pide
  evitar explícitamente.
- **El motor consulta directamente las tablas de cada módulo funcional (sin
  interfaz)**: descartado — rompe el límite de Spring Modulith (ADR 0003) y
  genera exactamente el acoplamiento central que ADR 0012 ya evitó para el
  catálogo de módulos con el mismo argumento.
- **Auditoría interna completa en esta misma rebanada**: descartado por la
  propia tarea — sigue siendo más que una rebanada de una semana, y esta ADR
  ya resuelve el bloqueante real (el motor) sin necesitar cerrar todo el
  módulo de una vez.
- **Reutilizar `auditoria.ver` o `municipio.verContrato` en vez de un permiso
  nuevo**: descartado — ninguno de los dos cubre semánticamente "ver
  indicadores agregados de módulos operativos"; reutilizar uno existente
  confundiría el catálogo de permisos (un rol con `municipio.verContrato`
  para ver su contrato comercial no necesariamente debería ver también
  reportes operativos, y viceversa).
- **Registrar `reportes` en `frontend/src/modulos/registro.ts`**: descartado
  — ver Decisión 6, ese registro es para módulos contratables con pantalla
  de portal, y `reportes` no lo es.
- **Cachear o materializar los agregados**: descartado por ahora — sin
  problema de performance medido, agregaría complejidad (invalidación,
  frescura de datos) sin necesidad real todavía.

## Consecuencias

- `reportes` no tiene `DescriptorDeModulo` ni entra al gating de entitlement;
  `reclamos` y `mesaentradas` pasan a depender de `reportes` (implementan su
  SPI), y `reportes` depende de `entitlement`. `ModularityTests` verifica que
  no hay ciclo.
- Sumar un tercer consumidor (ej. `multas` por estado) es, de ahora en más,
  agregar un `@Component` que implemente `FuenteDeMetricas` en el módulo
  correspondiente — no toca `reportes` ni el frontend.
- `reportes` no tiene entidades propias ni repositorio: no se agrega a
  `ConfiguracionDePersistencia` (ni `RepositoriosDeTenant` ni
  `packagesToScan` del `EntityManagerFactory` de tenant), porque no hay
  nada que escanear ahí — mismo caso que `entitlement`, que tampoco figura
  en esa clase.
- Nueva migración en `db/tenant` que solo agrega el permiso `reportes.ver`
  al catálogo, sembrado para `administrador`. No hay tabla nueva: el motor
  es puramente de lectura sobre tablas que ya existen.
- Primer módulo del proyecto cuya única razón de ser es agregar datos de
  otros módulos por inversión de dependencia (no por evento de dominio ni
  por acceso directo a otra base).

## Pendiente de definir

- Auditoría interna / Control de gestión como módulo propio, con más fuentes,
  filtros y probablemente su propia decisión de a qué roles se les muestra
  cada indicador — candidata de una rebanada futura, ahora desbloqueada.
- Sumar `FuenteDeMetricas` a los módulos restantes con estado propio
  (`multas`, `turnos`, `desarrollosocial`, `obras`, `arbolado`,
  `espaciosverdes`, `educacion`, `eventos`, `bromatologia`, `defensacivil`):
  no se hace en esta rebanada, queda disponible sin cambios en `reportes`.
- Series temporales / comparación entre períodos: requiere que algún módulo
  empiece a registrar historial de cambio de estado de forma genérica, cosa
  que hoy no existe fuera de `mesaentradas`.
- Caché o materialización de agregados si el volumen real de datos de un
  municipio en producción lo justifica.
- Exportación a PDF/Excel del tablero: no resuelto, no es el foco de esta
  rebanada.
