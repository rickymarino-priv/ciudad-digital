# Roadmap por fases

Agrupación del [catálogo funcional](catalogo-funcional.md) en fases de
construcción. Los criterios de agrupación son: dependencia de la plataforma
base, complejidad/riesgo normativo, acoplamiento con sistemas legados, y
valor de venta/visibilidad política.

Esto es un roadmap de producto, no un compromiso de fechas.

## Fase 0 — Fundación de plataforma

No se vende sola; habilita todo lo demás.

- Identidad y accesos + infraestructura multi-tenant
- Administración de tenants: alta de municipio (creación de base,
  migraciones, activación) — mecánica de aprovisionamiento, no capa
  comercial
- Modelo de contrato/entitlement + gating de módulos en backend (ver
  [modelo comercial](modelo-comercial.md))
- Motor de notificaciones multicanal
- Motor de expediente/workflow configurable (base mínima)
- Estándar de accesibilidad (WCAG) para el portal ciudadano
- Framework de reportes/BI (el motor, no todos los tableros)

Decisiones técnicas de esta fase (estrategia de multi-tenancy,
aprovisionamiento de tenants, etc.) se documentan en
[decisiones de arquitectura](../arquitectura/decisiones/).

## Fase 1 — MVP vendible / módulos ancla

- Reclamos ciudadanos (311)
- Mesa de Entradas + subset chico de Trámites a Distancia (los 3-5 más
  pedidos: certificados, habilitación comercial simple, permiso de obra
  menor)
- Boletín Oficial digital
- Transparencia activa básica (presupuesto/sueldos, si el municipio tiene
  los datos digitalizados)
- Cementerio

Todo lo que entra acá tiene bajo acoplamiento con sistemas
contables/legados existentes, así que no depende de la capa de adaptadores
todavía.

## Fase 2 — Recaudación e integración con lo existente

- Tasas municipales + pago online (pasarelas)
- Portal de proveedores (registro y documentación)
- Capa de adaptadores a sistemas legados (AFIP/ARBA o equivalente
  provincial, pasarelas de pago)
- Consola del proveedor: contratos, módulos por municipio, estado de
  facturación (ver [modelo comercial](modelo-comercial.md))

Acá es donde empieza a pesar la interoperabilidad con sistemas ya
digitalizados de cada municipio — deliberadamente no se mezcla con el MVP
para no bloquear la primera venta con un problema de integración ajeno al
producto.

## Fase 3 — Compras y áreas normativamente pesadas

- Compras y Contrataciones / Licitaciones
- Presupuesto y Contabilidad (tipo RAFAM)
- Tesorería
- Legal y Técnica / Juzgado de Faltas
- Tránsito y Transporte
- Consola del municipio: módulos activos, facturas, solicitud de alta/baja
  de módulos, administración de usuarios

Son los módulos de mayor riesgo legal/normativo (cada provincia tiene su
propio régimen); conviene abordarlos con 2-3 clientes reales dando feedback
de sus circuitos concretos, no diseñando en el vacío.

## Fase 4 — Gestión territorial

- Obras Públicas
- Catastro
- Planeamiento Urbano / Uso del Suelo
- Ambiente y Servicios Públicos
- GIS como servicio consolidado (se usó desde Reclamos en Fase 1, acá se
  profundiza)

Catastro en particular suele depender de datos provinciales, conviene
tenerlo tarde, cuando ya haya patrones de integración probados en Fase 2.

## Fase 5 — Áreas sociales

- Desarrollo Social
- Discapacidad
- Salud municipal (si aplica)
- Educación municipal (si aplica)

Datos sensibles, cruces con programas de Nación/Provincia, y depende de si
el municipio específico tiene competencia en salud/educación.

## Fase 6 — Áreas de imagen / periféricas

- Cultura, Turismo, Deportes
- Prensa y Comunicación
- Auditoría interna / Control de gestión (candidato a subir de prioridad si
  algún piloto lo pide como argumento de venta)

## Fase 7 — Inteligencia artificial

Transversal sobre los módulos de las fases anteriores.

- Clasificador de reclamos (candidato a adelantarse a Fase 1 como
  diferenciador temprano)
- Asistente ciudadano con RAG
- Copiloto interno para agentes municipales
- Optimización de rutas (recolección, poda) — ligado a Ambiente
- Anti-fraude/anomalías en licitaciones — necesita el volumen histórico de
  Fase 3

## Sin fase fija

- **Seguridad/Defensa Civil** y **Bromatología**: candidatos a Fase 4-5,
  dependiendo de la prioridad que les dé el municipio piloto que se
  consiga.
- **Integración con facturación electrónica de ARCA (ex AFIP)**: diferida
  sin fase asignada; la emisión de facturas se maneja fuera del sistema
  hasta que el volumen de clientes la justifique (ver
  [modelo comercial](modelo-comercial.md)).
