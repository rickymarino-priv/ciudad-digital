# CD-40 — R30: Auditoría interna / Control de gestión (ampliación del tablero de reportes)

Especificación técnica para implementar esta rebanada. Decisión de
arquitectura completa en
[ADR 0034](../docs/arquitectura/decisiones/0034-auditoria-interna-control-de-gestion-ampliacion-del-tablero-de-reportes.md) —
leerlo antes de implementar, junto con
[ADR 0033](../docs/arquitectura/decisiones/0033-framework-de-reportes-bi-motor-de-metricas-agregadas-y-primer-tablero.md)
(el mecanismo que esta rebanada extiende, no reabre). Esta spec no repite
el razonamiento de ninguna de las dos ADRs, solo el contrato a construir.
**No hay tarea de frontend**: `PanelDeReportes.tsx` ya es genérico (ver
ADR 0034 §5) y no requiere ningún cambio.

## Demo objetivo

Un administrador del municipio (con sesión y `reportes.ver`, sin cambios
de permisos) entra a Administración → Reportes y ve, además de las tablas
ya existentes de "Reclamos por estado" y "Expedientes por tipo de
trámite"/"por estado", tres módulos nuevos con datos reales de ese mismo
municipio:

- **Multas**: una tabla "Multas por estado" con los conteos reales
  (notificadas, en descargo, confirmadas, anuladas, pagadas).
- **Bromatología**: dos tablas, "Comercios bromatológicos por estado
  sanitario" (habilitados/observados/clausurados) e "Inspecciones por
  resultado".
- **Turnos**: una tabla "Turnos reservados por actividad", con la
  cantidad de reservas de cada actividad municipal publicada.

Si el municipio no tiene contratado alguno de esos módulos, esa fuente no
aparece (aunque el usuario tenga `reportes.ver`), igual que ya pasa hoy
con `reclamos`/`mesaentradas`. Los números de un municipio no aparecen
nunca en el tablero de otro.

## Alcance de esta rebanada (no diferible)

1. Tres implementaciones nuevas de `reportes.FuenteDeMetricas`:
   `multas.internal.FuenteDeMetricasDeMultas`,
   `bromatologia.internal.FuenteDeMetricasDeBromatologia`,
   `turnos.internal.FuenteDeMetricasDeTurnos`.
2. Métodos de consulta agregada nuevos en los repositorios existentes de
   esos tres módulos (sin entidades ni tablas nuevas, sin migración
   Flyway: no hay permiso nuevo que sembrar, `reportes.ver` ya existe).
3. Extender `ReportesTest` con cobertura de aislamiento entre tenants y
   de filtro por entitlement para las tres fuentes nuevas (obligatorio,
   ver más abajo) — no crear un archivo de test nuevo, extender el
   existente, mismo estilo.
4. `ModularityTests` sin romper: verificar que `reportes` sigue sin
   importar nada de `multas.internal`, `bromatologia.internal` ni
   `turnos.internal`, y que esos tres módulos pueden depender de
   `reportes` sin ciclo (mismo patrón ya verificado para
   `reclamos`/`mesaentradas` en R29).

Fuera de alcance, explícitamente (ver ADR 0034, no lo resuelvan por su
cuenta si aparece la tentación al implementar):

- Cualquier cambio en `auditoria`/`RegistroAuditoriaEntity`/
  `AuditoriaController` (ADR 0034 §2): esta rebanada no toca el registro
  de auditoría de eventos de R5, solo el tablero de `reportes`.
- Visibilidad de indicadores por rol: `reportes.ver` sigue reservado a
  `administrador`, sin permisos nuevos ni distinción de qué fuente ve
  cada rol.
- Cambios en el frontend: `PanelDeReportes.tsx` no se toca.
- Sumar `FuenteDeMetricas` a más módulos que los tres de esta spec
  (`desarrollosocial`, `obras`, `arbolado`, `espaciosverdes`, `educacion`,
  `eventos`, `defensacivil`, etc. quedan para después, sin tocar
  `reportes`).
- Indicadores derivados de fecha (ej. habilitaciones vencidas), filtros
  por rango de fecha, series temporales, caché/materialización,
  exportación a PDF/Excel: todos fuera de alcance, sin cambios respecto
  de ADR 0033.
- Ningún cambio en `entitlement`, en la migración de permisos, ni en
  `ConfiguracionDePersistencia`.

## Tarea única — Backend

### `multas.internal.FuenteDeMetricasDeMultas`

`@Component`, implementa `ar.com.ciudaddigital.reportes.FuenteDeMetricas`:

- `moduloCodigo()` → `DescriptorDelModuloMultas.CODIGO` (`"multas"`).
- `moduloNombre()` → `"Multas de tránsito"`.
- `series()` → una sola serie, nombre `"Multas por estado"`, con los
  puntos de una consulta agregada nueva sobre `MultaRepository`, mismo
  patrón exacto que `ReclamoRepository.contarPorEstado()` (R29):

  ```java
  interface ConteoPorEtiqueta {
      String getEtiqueta();
      long getCantidad();
  }

  @Query("select m.estado as etiqueta, count(m) as cantidad from MultaEntity m "
       + "group by m.estado order by m.estado asc")
  List<ConteoPorEtiqueta> contarPorEstado();
  ```

  Agregar este método (o uno equivalente con la misma proyección, nombre
  de la interfaz libre) a `MultaRepository`. Mapear `estado`
  (`EstadoDeMulta`) a `String` con `.name()`. Un estado sin ninguna multa
  no aparece en la lista (no rellenar con cero).

### `bromatologia.internal.FuenteDeMetricasDeBromatologia`

`@Component`, implementa `FuenteDeMetricas`:

- `moduloCodigo()` → `DescriptorDelModuloBromatologia.CODIGO`
  (`"bromatologia"`).
- `moduloNombre()` → `"Bromatología"`.
- `series()` → dos series:
  1. `"Comercios bromatológicos por estado sanitario"`: `group by estado`
     sobre `ComercioBromatologicoEntity` vía un método nuevo en
     `ComercioBromatologicoRepository`, mismo patrón de proyección que
     arriba (campo `estado`, tipo `EstadoBromatologico`).
  2. `"Inspecciones por resultado"`: `group by resultado` sobre
     `InspeccionBromatologicaEntity` vía un método nuevo en
     `InspeccionBromatologicaRepository` (campo `resultado`, mismo tipo
     `EstadoBromatologico` reutilizado, ver Javadoc de
     `EstadoBromatologico`).

  Igual criterio en ambas: ordenado por etiqueta ascendente, sin relleno
  de ceros.

### `turnos.internal.FuenteDeMetricasDeTurnos`

`@Component`, implementa `FuenteDeMetricas`:

- `moduloCodigo()` → `DescriptorDelModuloTurnos.CODIGO` (`"turnos"`).
- `moduloNombre()` → `"Turnos y actividades municipales"`.
- `series()` → una sola serie, nombre `"Turnos reservados por
  actividad"`, con los puntos de una consulta agregada nueva sobre
  `TurnoRepository` que junta `TurnoEntity` (campo `franjaId`) con
  `FranjaHorariaEntity` (campo `actividadId`) y `ActividadEntity` (campo
  `nombre`) — las tres entidades viven en el mismo módulo
  (`turnos.internal`), así que un JPQL con dos `join` explícitos por
  igualdad de id es válido sin relación `@ManyToOne` declarada (mismo
  criterio de "id informativo, no referencial" que ya usa el proyecto en
  otros lados, ej. `RegistroAuditoriaEntity`):

  ```java
  @Query("select a.nombre as etiqueta, count(t) as cantidad "
       + "from TurnoEntity t, FranjaHorariaEntity f, ActividadEntity a "
       + "where f.id = t.franjaId and a.id = f.actividadId "
       + "group by a.nombre order by a.nombre asc")
  List<ConteoPorEtiqueta> contarPorActividad();
  ```

  (sintaxis de join implícito por `where`, o `join` explícito si el
  implementador lo prefiere — el resultado tiene que ser el mismo:
  agrupar la cantidad de turnos reservados por el nombre de la actividad
  a la que pertenece su franja). Una actividad sin ningún turno reservado
  no aparece en la lista (no rellenar con cero). Reutilizar la misma
  interfaz de proyección `ConteoPorEtiqueta` si el estilo del módulo lo
  permite, o declarar una propia en `TurnoRepository` — libre, mientras
  tenga `getEtiqueta()`/`getCantidad()`.

### Registro de persistencia

No tocar `ConfiguracionDePersistencia`: ninguna entidad nueva, las tres
fuentes consultan repositorios ya registrados. No hay migración Flyway
en esta rebanada.

### Extender `ReportesTest`

Agregar a `backend/src/test/java/ar/com/ciudaddigital/reportes/ReportesTest.java`
(no crear un archivo nuevo) cobertura equivalente a la que ya existe para
`reclamos`/`mesaentradas`, para las tres fuentes nuevas. Como mínimo:

1. **Aislamiento real**: con `multas`, `bromatologia` y `turnos`
   contratados en dos municipios (pueden ser dos de los ya usados en la
   clase, o municipios nuevos si hace falta mantener conteos exactos sin
   interferencia — seguir el criterio ya explicado en el Javadoc de la
   clase sobre por qué usa un municipio por escenario), cargar datos
   distintos en cada uno para las tres fuentes y verificar que el
   tablero de cada municipio devuelve exactamente sus propios conteos,
   nunca los del otro. Para crear multas, comercios bromatológicos,
   inspecciones y turnos/franjas/actividades en el test, seguir el
   patrón de alta ya usado en `MultasTest`, `BromatologiaTest` y
   `TurnosTest` respectivamente (mismos endpoints, mismos payloads) —no
   hace falta reinventar el flujo de alta de cada módulo.
2. **Filtro por entitlement real**: para al menos una de las tres fuentes
   nuevas (no hace falta repetir las tres, ya está cubierto el mecanismo
   general con `reclamos` en R29; alcanza con una prueba adicional que
   confirme que el mecanismo también aplica a un módulo nuevo, ej.
   `multas`), contratar el módulo, cargar datos, verificar que aparece,
   quitarlo de los contratados y verificar que la fuente desaparece del
   tablero sin borrar los datos.
3. Verificar que `permisoDeLecturaDelTablero` y
   `municipioSinModulosContratadosDaTableroVacio` (los tests ya
   existentes) siguen pasando sin modificación — no deberían verse
   afectados, pero confirmarlo corriendo la clase completa.

### Otros tests

Cobertura de la agregación en sí para las tres fuentes nuevas (conteos
correctos con datos variados, incluida la ausencia de una etiqueta sin
datos — puede ir en `ReportesTest` o en un test unitario/de repositorio
si el implementador lo prefiere, libre) y `ModularityTests` completo
(correr el suite, no solo mirar que compile).

### Build/tests

Correr el build y la suite de tests de `backend/` completa en foreground
al terminar (no en background), sin fallas nuevas. No hace falta correr
nada de `frontend/`: no hay cambios ahí.

## Fila para `docs/producto/backlog-inicial.md`

Cuando la tarea esté implementada y verificada, el tech lead (no el
implementador) actualiza `docs/producto/backlog-inicial.md`: agrega la
fila `| CD-40 | R30 · Auditoría interna / Control de gestión — el tablero
de reportes suma multas, bromatología y turnos (parent: CD-7) |` a la
tabla de mapeo a Jira, y la sección `### R30 · ...` correspondiente junto
a las demás rebanadas de Fase 6.
