# Modelo comercial

Cómo se vende y se cobra el producto, y qué implica eso para el sistema.

## Contexto: vender SaaS al sector público argentino

Un municipio no se comporta como un cliente SaaS típico. No contrata con
tarjeta ni se autogestiona el plan: compra por orden de compra o
licitación, paga por transferencia con ciclos lentos (30/60/90 días o más),
y el pago depende de disponibilidad presupuestaria y de la firma de
Tesorería. Además, los cambios de gestión política (cada 4 años) son un
riesgo real de baja o de renegociación.

Esto rompe varios supuestos del SaaS estándar (cobro automático, corte por
falta de pago, alta self-service) y condiciona el diseño de toda la capa
comercial del producto.

## Modelo de cobro

**Canon base + módulos adicionales, con el canon escalonado por tamaño de
municipio.**

- El **canon base** cubre la plataforma (fundación + módulos ancla). La
  plataforma no se vende sola: es la condición para todo lo demás.
- Los **módulos** del [catálogo funcional](catalogo-funcional.md) son la
  unidad comercial: el municipio arranca con unos pocos y va sumando.
- El canon se escalona **por tramos de cantidad de habitantes**, no como
  variable mes a mes. Un municipio de 15.000 habitantes no puede pagar lo
  mismo que uno de 300.000, pero el sector público necesita un monto
  presupuestable y estable — por eso tramos fijos y no cobro por uso.

### Alternativas descartadas

- **Venta perpetua con licencia por módulo**: es a lo que el sector público
  está más acostumbrado y encaja bien con una licitación única, pero genera
  ingreso irregular, difícil de sostener para el desarrollo continuo.
- **Suscripción pura por volumen de uso** (por habitante o por trámite
  procesado): alinea precio con tamaño real, pero un gasto variable es
  difícil de aprobar y presupuestar para un organismo público.

## Superficies de administración

Son dos productos distintos, no uno con permisos diferenciados:

### Consola del proveedor (cross-tenant)

Para la operación del negocio: municipios dados de alta, módulos
contratados por cada uno, contratos y precios, estado de facturación, uso
por tenant, salud del aprovisionamiento.

Es la única superficie del sistema que ve datos de todos los municipios a
la vez, y por lo tanto el punto donde el aislamiento por DB-per-tenant
([ADR 0001](../arquitectura/decisiones/0001-multi-tenant-con-bd-por-tenant.md))
se puede romper si está mal construida. Requiere tratamiento más estricto
que el resto del sistema: autenticación reforzada y auditoría de todo
acceso cross-tenant.

### Consola del municipio (intra-tenant)

Para el cliente: qué módulos tiene activos, sus facturas y su estado,
**solicitud** de alta o baja de módulos, administración de sus usuarios.

Es "solicitar", no "contratar con un clic": el alta de un módulo nuevo en
un organismo público requiere orden de compra, no es self-service
instantáneo.

## Entitlement de módulos

Qué módulos tiene habilitados cada municipio se almacena en la
configuración del tenant
([ADR 0007](../arquitectura/decisiones/0007-modelo-de-datos-del-tenant.md)),
y se hace cumplir en el backend, no solo ocultando opciones en el frontend
(ver [ADR 0009](../arquitectura/decisiones/0009-modelo-comercial-y-entitlement.md)).

**El entitlement está desacoplado del estado de pago.** Un módulo se apaga
cuando termina un contrato — una decisión comercial deliberada — nunca de
forma automática porque una factura se atrasó. Cortarle el sistema de
reclamos a un municipio porque Tesorería demoró una transferencia es un
problema político, reputacional y potencialmente contractual. Los atrasos
se manejan con visibilidad y alertas, y la decisión de suspender es siempre
manual.

## Facturación

La emisión de facturas queda **fuera del sistema** en las primeras etapas:
el sistema registra contratos, módulos contratados y estado de facturas,
pero la factura se emite con las herramientas contables que ya se usen.

La integración con facturación electrónica de ARCA (ex AFIP) es deseable
pero se difiere a una etapa muy posterior: solo se justificaría con un
volumen de clientes que todavía no existe.

## Ubicación en el roadmap

- **Fase 0**: modelo de contrato/entitlement y gating de módulos en el
  backend (afecta al modelo de datos del tenant y al interceptor de
  resolución, que ya se construyen en esta fase).
- **Fase 2**: consola del proveedor.
- **Fase 3**: consola del municipio.
- **Diferido sin fase**: integración con facturación electrónica de ARCA.

Nota: el módulo de administración de tenants de
[ADR 0005](../arquitectura/decisiones/0005-aprovisionamiento-de-tenant.md)
(alta de municipio, creación de base, migraciones, activación) es Fase 0 y
no debe confundirse con la consola del proveedor: es la mecánica de
aprovisionamiento, no la capa comercial. Sin ella no se puede dar de alta
el segundo municipio; la capa comercial, en cambio, se puede administrar
por fuera del sistema mientras haya uno o dos clientes.
