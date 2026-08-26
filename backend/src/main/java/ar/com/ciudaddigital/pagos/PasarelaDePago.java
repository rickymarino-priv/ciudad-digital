package ar.com.ciudaddigital.pagos;

/**
 * Contrato de integración con una pasarela de pago (ADR 0018 §1).
 *
 * <p>Cada módulo que necesita cobrar algo (hoy {@code tasas}) inyecta esta
 * interfaz, nunca una implementación concreta, para no acoplarse al único
 * adaptador que existe hoy ({@code PasarelaDePagoSimulada}): el día que
 * aparezca un proveedor real, el consumidor no cambia una línea.
 */
public interface PasarelaDePago {

    ResultadoDeInicioDePago iniciarPago(SolicitudDePago solicitud);
}
