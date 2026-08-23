# Catálogo funcional

Catálogo de módulos/áreas funcionales relevados para el producto, organizado
por quién lo usa. Cada ítem tiene una etiqueta de complejidad orientativa
(baja/media/alta) pensada para priorización, no para estimación formal.

Este catálogo es la base para el [roadmap por fases](roadmap-fases.md); no
implica que todo se construya en el MVP.

## 1. Portal del ciudadano

- **Trámites a distancia**: licencia de conducir, habilitación comercial,
  permisos de obra menor, cambio de titularidad, certificados varios
  (domicilio, buena conducta municipal, etc.) — *complejidad media-alta: cada
  trámite tiene su propio circuito y requisitos*.
- **Reclamos y solicitudes (311)**: baches, alumbrado, poda/arbolado,
  recolección de residuos, animales sueltos, con geolocalización —
  *complejidad baja-media, alto impacto*.
- **Pago de tasas municipales**: TGI/ABL, patente automotor (si aplica),
  tasa comercial, derecho de cementerio, planes de pago/moratorias —
  *complejidad alta: integración con pasarelas de pago y sistemas de
  recaudación existentes*.
- **Turnos online**: atención presencial, salud municipal, tránsito —
  *complejidad baja*.
- **Notificaciones al vecino**: vencimientos, estado de trámite, alertas de
  servicios (corte de agua, etc.) — *complejidad baja, requiere motor
  transversal*.
- **Transparencia activa**: presupuesto y ejecución, sueldos de
  funcionarios, licitaciones abiertas, declaraciones juradas — *complejidad
  baja, alto valor político*.
- **Participación ciudadana**: presupuesto participativo, encuestas,
  audiencias públicas — *complejidad media, valor variable según el
  municipio*.
- **Mapa ciudadano / GIS público**: estado de obras, recorrido de
  recolección, líneas de colectivo, puntos de interés — *complejidad media,
  depende de datos geográficos disponibles*.
- **Boletín Oficial Municipal digital**: ordenanzas, decretos, resoluciones
  publicadas y buscables — *complejidad baja, alto valor de transparencia*.

## 2. Gestión documental / expediente electrónico

Es un motor transversal, no un módulo aislado: casi todo lo demás (trámites,
compras, RRHH, licitaciones) "cuelga" de esto.

- **Mesa de Entradas digital**: caratulación, giro entre áreas, seguimiento
  de expediente — *complejidad alta, columna vertebral del sistema*.
- **Firma electrónica/digital** de documentos y actos administrativos —
  *complejidad alta: requisitos legales, posible integración con AFIP/ONTI*.
- **Workflow configurable por circuito** (cada municipio tiene sus propios
  pasos de aprobación) — *complejidad alta, pero es lo que hace
  modularizable de verdad al sistema*.

## 3. Áreas / secretarías internas

- **RRHH**: legajos, liquidación de haberes, licencias, asistencia,
  capacitaciones — *complejidad alta: normativa laboral/sindical específica
  por municipio*.
- **Presupuesto y Contabilidad**: ejecución presupuestaria, partidas,
  rendición de cuentas — *complejidad muy alta: régimen provincial propio
  (ej. RAFAM en PBA), rendición a Tribunal de Cuentas*.
- **Tesorería y Recaudación**: cobranzas, conciliación bancaria, gestión de
  deuda — *complejidad alta, ligado a Tasas del portal ciudadano*.
- **Compras y Contrataciones / Licitaciones**: pliegos, consultas de
  oferentes, apertura de sobres, adjudicación, orden de compra —
  *complejidad muy alta: régimen legal de contrataciones públicas*.
- **Patrimonio**: bienes muebles e inmuebles municipales, inventario —
  *complejidad media*.
- **Catastro**: parcelas, valuaciones, nomenclatura — *complejidad alta,
  suele depender de datos provinciales*.
- **Obras Públicas**: seguimiento de obra, certificaciones de avance,
  inspecciones — *complejidad media-alta*.
- **Planeamiento Urbano / Uso del Suelo**: zonificación, factibilidad,
  habilitaciones de construcción — *complejidad media*.
- **Tránsito y Transporte**: infracciones, actas, licencias de conducir,
  grúa/depósito de vehículos — *complejidad alta: interacción con juzgados
  de faltas*.
- **Juzgado de Faltas / Legal y Técnica**: expedientes de infracciones,
  dictámenes, contencioso — *complejidad alta*.
- **Salud municipal** (si el municipio tiene efectores propios): turnos,
  historia clínica básica, campañas — *complejidad muy alta, posible
  integración con sistemas de salud provinciales*.
- **Desarrollo Social**: programas, comedores, subsidios, padrón de
  beneficiarios — *complejidad media-alta: datos sensibles, cruces con
  Nación/Provincia*.
- **Discapacidad**: turnos y seguimiento para Junta Evaluadora de CUD (la
  emisión del certificado es provincial/nacional, el municipio suele
  gestionar turnos y derivación), exenciones de tasas, registro de
  instituciones y programas de inclusión, transporte accesible, deporte
  adaptado — *complejidad media*.
- **Ambiente y Servicios Públicos**: recolección de residuos, arbolado
  urbano, espacios verdes, alumbrado público — *complejidad media, buen
  candidato a IA con optimización de rutas*.
- **Seguridad / Defensa Civil**: cámaras, monitoreo de emergencias,
  protocolos — *complejidad alta, integraciones con hardware/CCTV*.
- **Bromatología / Inspección General**: control de comercios,
  habilitaciones, control alimentario — *complejidad media*.
- **Cementerio**: nichos, panteones, concesiones, sucesiones —
  *complejidad baja-media, buen módulo chico con valor real*.
- **Cultura, Turismo y Deportes**: agenda de eventos, polideportivos, turnos
  deportivos — *complejidad baja*.
- **Educación municipal** (si el municipio tiene competencia educativa) —
  *complejidad media*.
- **Prensa y Comunicación**: gacetillas, gestión de redes — *complejidad
  baja*.
- **Auditoría interna / Control de gestión**: tableros de cumplimiento,
  seguimiento de indicadores — *complejidad media, alto valor para
  intendente/gabinete*.

## 4. Proveedores

- **Portal de proveedores**: alta, documentación (constancias AFIP,
  seguros, antecedentes), registro único de proveedores — *complejidad
  media*.
- **Seguimiento de licitaciones**: acceso a pliegos, consultas, carga de
  ofertas — *complejidad alta, ligado al módulo de Compras*.
- **Estado de facturación y pagos**: seguimiento de órdenes de pago,
  facturación electrónica — *complejidad media-alta: integración con AFIP*.

## 5. Plataforma transversal

Servicios que consumen todos los módulos, no "módulos de área".

- **Identidad y accesos**: SSO, roles y permisos granulares por
  área/módulo, identidad ciudadana (posible integración a futuro con
  MiArgentina/RENAPER).
- **Accesibilidad (WCAG)**: estándar de accesibilidad para todo el portal
  ciudadano (lector de pantalla, alto contraste, navegación por teclado) —
  no es un módulo, es un requisito de diseño transversal.
- **Notificaciones multicanal**: email, SMS, WhatsApp Business API, push.
- **Motor de expediente/workflow** (ver sección 2).
- **Reportes y BI / tableros de gestión** para intendente y directores de
  área — alto valor de venta.
- **GIS como servicio**: capa de mapas reutilizable para Obras, Reclamos,
  Catastro, Ambiente.
- **Integraciones externas**: AFIP/ARCA, organismo de recaudación
  provincial equivalente a ARBA, Registro de la Propiedad, RENAPER, Correo
  Argentino, pasarelas de pago (Mercado Pago, Modo, PagoFácil/Rapipago).
- **Auditoría y trazabilidad transversal**: quién hizo qué, cuándo, con qué
  expediente — requisito casi obligatorio en sector público.
