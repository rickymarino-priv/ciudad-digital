# CD-39 — R29: Framework de reportes/BI (motor mínimo + primer tablero real)

Especificación técnica para implementar esta rebanada. Decisión de
arquitectura completa en
[ADR 0033](../docs/arquitectura/decisiones/0033-framework-de-reportes-bi-motor-de-metricas-agregadas-y-primer-tablero.md) —
leerlo antes de implementar, esta spec no repite el razonamiento, solo el
contrato a construir. En particular, no reinterpreten ni discutan de nuevo
la decisión de la ADR (motor por inversión de dependencia, sin eventos de
dominio, canon base sin `DescriptorDeModulo`): impleméntenla tal cual.

## Demo objetivo

Un administrador del municipio (con sesión y `reportes.ver`) entra a
Administración y ve una sección nueva "Reportes": una tabla "Reclamos por
estado" con la cantidad real de reclamos de ese municipio agrupados por
estado (nuevo/en proceso/resuelto/rechazado), y dos tablas más, "Expedientes
por tipo de trámite" y "Expedientes por estado", con datos reales de Mesa de
Entradas de ese mismo municipio. Si el municipio no tiene contratado
`reclamos`, esa tabla no aparece (aunque el usuario tenga `reportes.ver`); si
tampoco tiene `mesaentradas`, la sección muestra un mensaje de que no hay
indicadores disponibles. Los números de un municipio no aparecen nunca en el
tablero de otro.

## Alcance de esta rebanada (no diferible)

1. Módulo backend `reportes`: interfaz pública `FuenteDeMetricas` +
   `ReportesController` con `GET /api/reportes/tablero`.
2. Dos implementaciones de `FuenteDeMetricas`: `reclamos` y `mesaentradas`
   (dentro de sus respectivos `.internal`).
3. Permiso nuevo `reportes.ver` (migración Flyway), reservado a
   `administrador`.
4. Pantalla frontend `PanelDeReportes`, montada dentro de
   `PanelDeAdministracion` (no en `frontend/src/modulos/registro.ts` — no es
   un módulo contratable, ver ADR 0033 §6).
5. Test de aislamiento entre tenants (obligatorio, ver más abajo).
6. Accesibilidad WCAG en la pantalla nueva, mismo nivel que
   `PanelDeAuditoria` (foco lo maneja el `<h1>` de `PanelDeAdministracion`,
   no hace falta duplicarlo acá; sí `role="status"`/`role="alert"`, tablas
   con `<caption>` y `scope`).
7. `ModularityTests` sin romper — verificar en particular que `reportes` no
   importa nada de `reclamos.internal` ni `mesaentradas.internal`, y que
   `reclamos`/`mesaentradas` sí pueden depender de `reportes` sin ciclo.

Fuera de alcance, explícitamente (ver ADR 0033, no lo resuelvan por su
cuenta si aparece la tentación al implementar):

- Auditoría interna / Control de gestión como módulo completo.
- Cualquier mecanismo de eventos de dominio nuevo.
- Sumar `FuenteDeMetricas` a más módulos que `reclamos`/`mesaentradas`
  (`multas`, `turnos`, etc. quedan para después, sin tocar `reportes`).
- Gráficos/visualización (charts): solo tablas accesibles.
- Filtros por fecha, series temporales, caché/materialización.
- Exportación a PDF/Excel.
- `reportes` **no** tiene entidades ni tabla propia: no toca
  `ConfiguracionDePersistencia` (ver Tarea 1, "Registro de persistencia").

## Tarea 1 — Backend

### Paquete y estructura

`ar.com.ciudaddigital.reportes`, **sin** `package-info.java` con
`@ApplicationModule` especial (mismo caso que `entitlement`/`auditoria`/
`municipio`: ninguno tiene `package-info.java`, no le agreguen uno acá
tampoco — es un módulo canon base detectado igual por Spring Modulith sin
necesitar esa declaración).

Interfaz pública en `ar.com.ciudaddigital.reportes` (no en `.internal`,
tiene que ser visible desde otros módulos):

```java
public interface FuenteDeMetricas {
    String moduloCodigo();
    String moduloNombre();
    List<SerieDeMetricas> series();
}

public record SerieDeMetricas(String nombre, List<PuntoDeMetrica> puntos) {}

public record PuntoDeMetrica(String etiqueta, long cantidad) {}
```

Javadoc de `FuenteDeMetricas`: explicar que es la SPI que un módulo
funcional implementa para aportar indicadores agregados al tablero (ADR
0033 §2), calculados con una consulta agregada sobre su propia tabla, no
sobre eventos de dominio; y que `moduloCodigo()` tiene que coincidir con el
`codigo()` del `DescriptorDeModulo` del módulo que la implementa (mismo
código que ya usa el catálogo de entitlement, ADR 0012 §6), porque
`ReportesController` lo usa para filtrar contra lo contratado.

### `ReportesController` (en `reportes.internal`)

`GET /api/reportes/tablero`, `@PreAuthorize("hasAuthority('reportes.ver')")`.

Inyecta `List<FuenteDeMetricas> fuentes` (todos los beans del contexto que
implementan la interfaz — Spring los resuelve por tipo, sin que este
controller conozca `reclamos`/`mesaentradas`) y
`ar.com.ciudaddigital.entitlement.ModulosDelTenant modulosDelTenant`.

Lógica del endpoint:

1. Llamar `modulosDelTenant.habilitadosDelRequestEnCurso()`.
2. Si es `Optional.empty()` (no se pudo determinar): devolver lista vacía
   (no lanzar excepción — es un endpoint de lectura informativa, ver ADR
   0033 §4).
3. Si es un `Set<String>` (puede estar vacío): filtrar `fuentes` a las que
   `moduloCodigo()` esté en ese conjunto.
4. Mapear cada `FuenteDeMetricas` filtrada a un DTO de respuesta,
   ordenadas por `moduloNombre()` ascendente.

No hace falta una clase de servicio intermedia — pueden resolver el
filtrado y mapeo directo en el método del controller, mismo nivel de
simplicidad que `ConsolaDelMunicipioController`. Si prefieren extraer un
`@Service`, está bien, pero no es requisito de esta spec.

DTOs de respuesta (records anidados en el controller, mismo estilo que el
resto del proyecto):

```java
record FuenteDeMetricasResponse(String moduloCodigo, String moduloNombre, List<SerieResponse> series) {}
record SerieResponse(String nombre, List<PuntoResponse> puntos) {}
record PuntoResponse(String etiqueta, long cantidad) {}
```

Nombres de campo JSON exactos (el frontend los consume tal cual):
`moduloCodigo`, `moduloNombre`, `series`, `nombre`, `puntos`, `etiqueta`,
`cantidad`.

### `reclamos.internal.FuenteDeMetricasDeReclamos`

`@Component`, implementa `FuenteDeMetricas`:

- `moduloCodigo()` → `DescriptorDelModuloReclamos.CODIGO` (`"reclamos"`).
- `moduloNombre()` → `"Reclamos ciudadanos"`.
- `series()` → una sola serie, nombre `"Reclamos por estado"`, con los
  puntos de una consulta agregada nueva sobre `ReclamoRepository`:

  ```java
  interface ConteoPorEtiqueta {
      String getEtiqueta();
      long getCantidad();
  }

  @Query("select r.estado as etiqueta, count(r) as cantidad from ReclamoEntity r "
       + "group by r.estado order by r.estado asc")
  List<ConteoPorEtiqueta> contarPorEstado();
  ```

  (agregar este método — o el que el implementador considere equivalente,
  con la misma proyección — a `ReclamoRepository`; nombre de la interfaz de
  proyección libre). Mapear `estado` (enum) a `String` con `.name()` para
  `etiqueta`. Un estado sin ningún reclamo no aparece en la lista (no
  rellenar con cero, ADR 0033 §3).

### `mesaentradas.internal.FuenteDeMetricasDeMesaEntradas`

`@Component`, implementa `FuenteDeMetricas`:

- `moduloCodigo()` → `DescriptorDelModuloMesaDeEntradas.CODIGO`
  (`"mesaentradas"`).
- `moduloNombre()` → `"Mesa de Entradas"`.
- `series()` → dos series:
  1. `"Expedientes por tipo de trámite"`: `group by tipo` sobre
     `ExpedienteRepository`, mismo patrón de proyección que arriba.
  2. `"Expedientes por estado"`: `group by estado` sobre
     `ExpedienteRepository`.

  Igual criterio: ordenado por etiqueta ascendente, sin relleno de ceros.

### Registro de persistencia

**No tocar** `ConfiguracionDePersistencia`: `reportes` no tiene entidades
propias (las consultas agregadas viven en los repositorios existentes de
`reclamos`/`mesaentradas`, que ya están registrados). Si el implementador
siente la tentación de agregar un `PAQUETE_REPORTES`, no lo hagan — no hay
nada que escanear ahí.

### Migración Flyway

Nueva migración `V29__agregar_permiso_de_reportes.sql` en
`backend/src/main/resources/db/tenant/`. Mirar
`V18__agregar_permisos_de_consola_del_municipio.sql` como referencia de
estilo (comentario explicando por qué se reserva a administrador, insert en
`permiso`, insert en `rol_permiso` solo para `administrador`). Un único
permiso:

```sql
insert into permiso (codigo, area, modulo, accion, descripcion) values
    ('reportes.ver', 'Administración', 'reportes', 'ver',
     'Ver el tablero de indicadores agregados de los módulos operativos del municipio.');

insert into rol_permiso (rol_id, permiso_codigo)
select r.id, p.codigo from rol r, permiso p
where r.codigo = 'administrador'
  and p.codigo = 'reportes.ver';
```

No hay tabla nueva.

### Test de aislamiento entre tenants (obligatorio)

Nuevo `backend/src/test/java/ar/com/ciudaddigital/reportes/ReportesTest.java`
extendiendo `SoporteDeIntegracion`, mismo estilo que `ReclamosTest`/
`BromatologiaTest`. Cubrir al menos:

1. **Aislamiento real**: con `reclamos` y `mesaentradas` contratados en dos
   municipios A y B, cargar reclamos con distintos estados en A y en B (en
   cantidades distintas), y expedientes en A. Loguearse como administrador
   de A y llamar `GET /api/reportes/tablero`: los conteos de "Reclamos por
   estado" tienen que coincidir exactamente con lo cargado en A, nunca
   mezclar ni incluir nada de B. Repetir la verificación análoga para B
   (con sus propios números, distintos de los de A).
2. **Filtro por entitlement real, no incidental**: contratar `reclamos`
   para un municipio, cargar reclamos, verificar que aparece en el
   tablero; después quitar `reclamos` de los módulos contratados de ese
   mismo municipio (dejando los datos ya cargados en la tabla) y verificar
   que el tablero deja de incluir la fuente `"reclamos"` — demuestra que el
   filtro depende del entitlement vigente, no de si hay datos.
3. **Permiso**: sin `reportes.ver`, `GET /api/reportes/tablero` responde
   403; con sesión de administrador (que lo tiene por seed), responde 200.
4. Municipio sin `reclamos` ni `mesaentradas` contratados: el tablero
   responde 200 con lista vacía, no error.

### Otros tests

Cobertura de la agregación en sí (conteos correctos con datos de prueba
variados, incluida la ausencia de una etiqueta sin datos) y de
`ModularityTests` (correr el suite completo, no solo mirar que compile).

## Tarea 2 — Frontend

### Componente

`frontend/src/acceso/PanelDeReportes.tsx`. Usar `PanelDeAuditoria.tsx` como
referencia directa de convenciones: mismo patrón de `pedir` (de
`./api`), mismo tipo `EstadoLista` (`cargando`/`listo`/`error`), mismo
patrón de `vigente.current` para evitar setState sobre componente
desmontado, mismo `role="status"`/`role="alert"`.

Tipos:

```ts
type PuntoDeMetrica = { etiqueta: string; cantidad: number }
type SerieDeMetricas = { nombre: string; puntos: PuntoDeMetrica[] }
type FuenteDeMetricas = { moduloCodigo: string; moduloNombre: string; series: SerieDeMetricas[] }
```

Pedido: `pedir<FuenteDeMetricas[]>('/api/reportes/tablero', 'No se pudo cargar el tablero de reportes.')`.

Render:

- `<section aria-labelledby="titulo-reportes">` con `<h2 id="titulo-reportes">Reportes</h2>`.
- Mientras carga: `<p role="status">Cargando el tablero de reportes…</p>`.
- En error: `<p role="alert">{mensaje}</p>`.
- Listo, si la lista viene vacía: `<p role="status">No hay indicadores
  disponibles: el municipio no tiene contratado ningún módulo con datos
  para mostrar.</p>`.
- Listo, con datos: por cada fuente, un `<h3>{moduloNombre}</h3>` y, por
  cada serie de esa fuente, una tabla accesible (mismo patrón exacto que la
  tabla de `PanelDeAuditoria`: `<div className="tabla-contenedor">`,
  `<table className="tabla">`, `<caption>{nombre de la serie}</caption>`,
  `<thead>` con columnas "Categoría"/"Cantidad" (`scope="col"`), `<tbody>`
  con `<th scope="row">{etiqueta}</th><td>{cantidad}</td>` por punto). Usar
  `key` estable (ej. combinar `moduloCodigo` + nombre de serie + etiqueta).

No hace falta manejo de foco propio en este componente (el `<h1>` de
`PanelDeAdministracion` ya lo maneja al entrar a la vista, mismo criterio
que `PanelDeAuditoria`).

### Wiring en `PanelDeAdministracion.tsx`

Agregar:

```ts
const veReportes = puede('reportes.ver')
```

Renderizar `{veReportes && <PanelDeReportes />}` junto a los demás paneles
condicionales, y sumar `!veReportes` a la condición del mensaje de "no
tenés permisos para..." (agregar "ni ver reportes" al texto), mismo
patrón que ya existe para `veMiMunicipio`.

### Build/lint

Correr `npm run build` y el lint del proyecto (`npm run lint` si existe)
sobre `frontend/` al terminar, sin errores nuevos.

## Fila para `docs/producto/backlog-inicial.md`

Cuando ambas tareas estén implementadas y verificadas, el tech lead (no el
implementador) actualiza `docs/producto/backlog-inicial.md`: agrega la fila
de mapeo a Jira (`CD-39 (placeholder, sin confirmar) | R29 · Framework de
reportes/BI — motor mínimo y primer tablero real (parent: CD-1)`) y la
sección `### R29 · ...` correspondiente.
