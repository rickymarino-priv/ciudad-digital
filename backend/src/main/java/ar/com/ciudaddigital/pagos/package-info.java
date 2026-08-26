/**
 * Contrato de integración con una pasarela de pago externa (ADR 0018).
 *
 * <p>Módulo canon base, con el mismo estatus que
 * {@code seguimientoanonimo} (ADR 0017 §3): no es contratable, no tiene
 * {@link ar.com.ciudaddigital.entitlement.DescriptorDeModulo} propio y no
 * persiste nada. Declara la forma de la interacción que cualquier módulo
 * que cobre algo necesita de una pasarela —iniciar un cobro por un monto y
 * una referencia propia, y enterarse después de si se aprobó o no— sin
 * conocer todavía qué proveedor real la va a implementar (Mercado Pago,
 * Modo, PagoFácil/Rapipago). Hoy el único bean de este tipo en todo el
 * sistema es {@code PasarelaDePagoSimulada}, para desarrollo y demo: no
 * hace ninguna llamada de red.
 *
 * <p>Ningún módulo consumidor (hoy, {@code tasas}) recibe la confirmación
 * del pago a través de este módulo: cada uno declara su propio endpoint de
 * confirmación y es dueño de su propio estado (ADR 0018 §4). Este módulo
 * no expone ningún endpoint HTTP.
 */
package ar.com.ciudaddigital.pagos;
