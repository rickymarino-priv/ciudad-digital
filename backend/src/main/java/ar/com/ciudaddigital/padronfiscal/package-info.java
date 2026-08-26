/**
 * Contrato de integración con un padrón fiscal externo (AFIP/ARBA o
 * equivalente provincial), ADR 0020.
 *
 * <p>Módulo canon base, con el mismo estatus que {@code pagos} (ADR 0018
 * §1): no es contratable, no tiene
 * {@link ar.com.ciudaddigital.entitlement.DescriptorDeModulo} propio y no
 * persiste nada ni expone ningún endpoint HTTP. Declara la forma de la
 * interacción que cualquier módulo que necesite verificar un CUIT
 * necesita de un padrón fiscal —consultarlo y enterarse de su
 * situación— sin conocer todavía qué proveedor real la va a implementar.
 * Hoy el único bean de este tipo en todo el sistema es
 * {@code PadronFiscalSimulado}, consumido por {@code proveedores}: no
 * hace ninguna llamada de red.
 */
package ar.com.ciudaddigital.padronfiscal;
