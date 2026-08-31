# 0027 - Prensa y Comunicación: gacetillas públicas, segunda rebanada de Fase 6

- Estado: Aceptada
- Fecha: 2026-08-31

## Contexto

[ADR 0026](0026-turnos-actividades-municipales-reserva-con-cupo-primera-rebanada-de-fase-6.md)
abrió Fase 6 — Áreas de imagen y control de gestión con Turnos (R22, CD-31)
y dejó dos candidatas disponibles para la siguiente rebanada de la misma
fase: **Prensa y Comunicación** (sin explorar todavía) y **Auditoría
interna / Control de gestión** (descartada para R22, señalada como
"candidata fuerte para cuando el framework de reportes/BI [...] tenga un
consumidor real que lo justifique construir bien"). Educación municipal
sigue disponible como candidata de Fase 5 (ADR 0025/0026), sin cambios acá.

Toca decidir R23 con el mismo criterio de descarte razonado que vienen
aplicando ADR 0021/0023/0024/0025/0026 al planificar cada rebanada nueva.

### Se reevalúa Auditoría interna / Control de gestión, y se descarta otra vez

El argumento de ADR 0026 era que un tablero cruzado necesitaría, o bien que
cada uno de los módulos funcionales existentes exponga su propio conteo, o
bien un mecanismo de agregación nuevo sobre `registro_auditoria` que hoy no
existe. Se verificó contra el código actual si esa premisa cambió desde
R22: **no cambió**. `registro_auditoria`
([ADR 0013](0013-persistencia-de-eventos-y-mecanismo-transversal-de-notificaciones-y-auditoria.md))
solo recibe una fila por cada evento `UsuarioCreado`, publicado
exclusivamente por `acceso` (R5) — es, todavía, el único evento de dominio
que existe en todo el backend (`grep` de `ApplicationEventPublisher`/
`publishEvent` en todo `backend/src/main/java` da un único resultado).
Ningún módulo funcional agregado desde entonces (`multas`, `obras`,
`arbolado`, `desarrollosocial`, `turnos`) publica un evento propio. Un
dashboard de "control de gestión" sobre datos reales (reclamos por estado,
multas por estado, turnos reservados, etc.) seguiría necesitando
exactamente lo que ADR 0026 ya identificó: tocar cada módulo existente
para que exponga un conteo, o diseñar recién ahora el mecanismo de
agregación. Ninguna de las dos es una rebanada de una semana con algo
nuevo para mostrar; sigue siendo mejor candidata para cuando el framework
de reportes/BI (Fase 0, pendiente) tenga un consumidor real.

Queda, entonces, como única candidata razonable: **Prensa y Comunicación**.

### Prensa y Comunicación: gacetillas, no "gestión de redes"

El [catálogo funcional](../../producto/catalogo-funcional.md) describe el
área como "gacetillas, gestión de redes". Esta rebanada cubre solo
**gacetillas** (comunicados de prensa del municipio): un registro público,
buscable, de los comunicados que el municipio emite. "Gestión de redes"
—publicar o programar contenido en redes sociales externas (Twitter/X,
Facebook, Instagram)— queda fuera: es una integración con APIs de terceros
que este proyecto no tiene ningún patrón para resolver todavía (el
catálogo funcional la separa de "Integraciones externas" en la sección de
plataforma transversal, y ninguna de esas integraciones está construida),
y no aporta nada al objetivo de la semana de tener un comunicado visible
en el portal público. Se define explícitamente como fuera de alcance, no
como un olvido.

### Gacetillas es, en forma, el mismo patrón que Boletín Oficial (R7) — deliberado, no repetido por default

Una gacetilla es, estructuralmente, el mismo patrón que una norma del
Boletín Oficial (R7, sin ADR propio: reutilizó ADR 0011/0012 tal cual):
alta protegida por sesión y permiso, lectura pública sin sesión, sin
estado ni edición posterior. No hay una propiedad de dominio nueva que
justifique un mecanismo distinto — a diferencia de Turnos (ADR 0026), que
sí aportó una dimensión nueva (cupo compartido bajo concurrencia). Repetir
el patrón está permitido por el criterio del proyecto ("avance semanal
demostrable", no "arquitectura distinta cada vez") cuando es la mejor
opción disponible, y acá lo es: no hay dato sensible que minimizar (un
comunicado de prensa institucional no es un dato personal de nadie
identificable; la única persona nombrada es el actor autenticado que
publica, mismo criterio de "copia del actor, no una relación" que ya usa
`NormaEntity`/`RegistroAuditoriaEntity`), y no hay ninguna otra candidata
mejor disponible en el backlog actual (ver descarte de Auditoría arriba).

## Decisión

### 1. Módulo nuevo `prensa`, contratable, acotado a gacetillas

`prensa` es un módulo funcional propio ([ADR 0009](0009-modelo-comercial-y-entitlement.md)),
con su propio `DescriptorDeModulo` y prefijo `/api/prensa`. No depende de
ningún otro módulo funcional.

Una única entidad, `prensa.internal.GacetillaEntity` (tabla `gacetilla`),
en la base del tenant, sin columna de tenant explícita (mismo criterio que
todos los módulos anteriores, [ADR 0001](0001-multi-tenant-con-bd-por-tenant.md)).
Sin estado ni edición posterior, igual que `NormaEntity` (R7): una
gacetilla publicada no se corrige mutándola, se corrige publicando una
gacetilla nueva.

Campos: `categoria` (enum cerrado: `INSTITUCIONAL`, `OBRAS`, `CULTURA`,
`DEPORTES`, `SALUD`, `SEGURIDAD`, `OTRAS` — alcanza para separar los temas
más comunes de prensa municipal sin inventar un nomenclador más fino,
mismo criterio que `TipoDeNorma`/`TipoDeActividad`), `titulo` (texto,
obligatorio, largo máximo 300, igual que `NormaEntity.titulo`), `texto`
(texto completo del comunicado, obligatorio), `fechaPublicacion` (fecha,
obligatoria, puede cargarse en forma retroactiva igual que en Boletín),
`publicadoPorNombre`/`publicadoPorEmail` (copia del actor autenticado al
momento de publicar, mismo criterio que `NormaEntity`/
`RegistroAuditoriaEntity`, ADR 0013), `creadoEn`.

**A diferencia de `NormaEntity`, no lleva `numero`**: una norma tiene
numeración legal correlativa que el municipio le asigna (aunque esta
rebanada no la genere sola, R7); una gacetilla de prensa no es un acto
administrativo con numeración propia — es un comunicado. Agregar un campo
`numero` sin uso real sería inventar una convención que ningún municipio
real pidió.

### 2. Alta protegida / lectura pública, mismo mecanismo que Boletín (R7)

`POST /api/prensa` requiere sesión y el permiso `prensa.publicar`.
`GET /api/prensa` es lectura pública (`rutasDeLecturaPublica()`,
[ADR 0012](0012-declaracion-de-modulos-y-gating-por-ruta.md) §1), con
filtro opcional por `categoria` y por texto (`q`) sobre `titulo`, mismo
patrón `ILIKE` que Boletín. Sin endpoint de detalle por id, igual que
`boletin`.

### 3. `prensa.publicar` se asigna a `administrador` **y** `agente` — a diferencia de `boletin.publicar`

Esta es la única decisión de permisos que se aparta del precedente directo
de Boletín. `boletin.publicar` es solo de `administrador` porque publicar
una norma es un acto legal del municipio (backlog R7). Una gacetilla de
prensa no es un acto legal: es una comunicación operativa, del mismo nivel
de confianza que gestionar un reclamo, dar de alta una franja de turnos o
registrar una inhumación en el cementerio — tareas que ya asignan su
permiso a ambos roles de sistema (`reclamos.gestionar`,
`turnos.gestionar`, `cementerio.registrar`). No hay una razón real para
que solo un administrador pueda publicar un comunicado de prensa
institucional; en la práctica, esa tarea suele ser de un área de
comunicación operada por personal sin rol de administrador del sistema.

### 4. Sin adjuntos, sin integración con redes sociales, sin edición/derogación

Mismos motivos que todos los módulos anteriores con registro público
(ADR 0023 §6/§7/§8, ADR 0024 §6, ADR 0025 §9, ADR 0026 §8): sin fotos ni
documentos adjuntos (una gacetilla real suele llevar una imagen, pero
subir y servir archivos es una decisión de infraestructura de storage que
este proyecto no tomó todavía — mismo motivo que diferir adjuntos en
Boletín, R7). Sin integración con redes sociales externas (ver Contexto).
Sin edición ni derogación de una gacetilla ya publicada (mismo criterio
que Boletín). Sin notificación a suscriptores de que hay contenido nuevo
(mismo pendiente que arrastra el proyecto desde R6).

## Alternativas consideradas

- **Auditoría interna / Control de gestión**: ver Contexto — se reevaluó y
  se confirma que sigue necesitando tocar los módulos funcionales
  existentes (ninguno publica eventos propios más allá de `UsuarioCreado`
  de `acceso`) o diseñar a las apuradas el framework de reportes/BI
  pendiente. Descartada por segunda vez consecutiva, mismo motivo.
- **Educación municipal**: sigue disponible como candidata de Fase 5
  (ADR 0025/0026), no evaluada para esta rebanada porque ya había una
  candidata mejor en Fase 6 (Prensa) sin necesidad de reabrir esa
  comparación.
- **Incluir "gestión de redes" (publicación/programación en redes
  sociales externas) en esta rebanada**: descartada — ver Contexto. Es una
  integración con terceros sin patrón previo en el proyecto, y no aporta a
  la demo de "un comunicado visible en el portal público".
- **Agregar un campo `numero` a `GacetillaEntity`, por simetría con
  `NormaEntity`**: descartada — ver Decisión 1. Una gacetilla no tiene
  numeración legal real que modelar.
- **`prensa.publicar` solo para `administrador`, por simetría con
  `boletin.publicar`**: descartada — ver Decisión 3. La gacetilla no es un
  acto legal, es tarea operativa del mismo nivel que ya delega el proyecto
  a `agente` en otros módulos.
- **Adjuntar una imagen a la gacetilla vía URL de texto libre (sin subida
  de archivo real)**: se consideró como forma barata de acercarse al caso
  real (una gacetilla casi siempre lleva una foto), pero se descarta:
  ningún módulo anterior modela "una URL externa como si fuera un dato
  propio" y hacerlo acá sin necesidad real rompería la consistencia sin
  aportar nada verificable — si aparece la necesidad real de imágenes, se
  resuelve junto con la decisión de storage que el proyecto todavía no
  tomó, no con un campo de texto suelto.

## Consecuencias

- `prensa` no depende de ningún otro módulo funcional; el test de
  modularidad de Spring Modulith lo verifica en el build.
- Segunda rebanada de Fase 6 — Áreas de imagen y control de gestión, sin
  cerrarla: Auditoría interna / Control de gestión sigue disponible como
  candidata futura, mejor abordada cuando el framework de reportes/BI
  tenga diseño propio y un consumidor real.
- Primer módulo del proyecto donde el mismo permiso de "publicar contenido
  público" se asigna a ambos roles de sistema en vez de solo a
  `administrador`, documentado explícitamente para que no se lea como
  inconsistencia con Boletín.
- "Gestión de redes" queda fuera del alcance de `prensa` indefinidamente,
  hasta que el proyecto tenga un patrón de integración con APIs externas
  (ninguna existe todavía).

## Pendiente de definir

- Adjuntar imagen/documento a una gacetilla: depende de una decisión de
  storage de archivos que el proyecto no tomó (mismo pendiente que
  Boletín, R7).
- Integración con redes sociales externas ("gestión de redes" del
  catálogo funcional): sin patrón de integración con terceros todavía.
- Edición o derogación de una gacetilla ya publicada: no existe en esta
  rebanada, mismo criterio que Boletín.
- Notificación de contenido nuevo a suscriptores: mismo pendiente que
  arrastra el proyecto desde R6.
- Auditoría interna / Control de gestión y el framework de reportes/BI:
  siguen pendientes, candidatos de una rebanada futura de Fase 6.
