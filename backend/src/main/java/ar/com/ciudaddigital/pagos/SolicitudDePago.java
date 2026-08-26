package ar.com.ciudaddigital.pagos;

import java.math.BigDecimal;

/**
 * Lo que un módulo pide al iniciar un cobro (ADR 0018 §1).
 *
 * <p>{@code referenciaInterna} es el identificador propio de quien pide el
 * cobro (por ejemplo, el id de una tasa, como texto): una pasarela real lo
 * usa para correlacionar su webhook con el cobro correcto.
 *
 * <p>Este módulo no valida nada de esto —ni que el monto sea positivo, ni
 * que la referencia no esté vacía—: no tiene reglas de negocio propias, esa
 * responsabilidad es de quien arma la solicitud antes de llamar a
 * {@link PasarelaDePago#iniciarPago(SolicitudDePago)}.
 */
public record SolicitudDePago(String referenciaInterna, BigDecimal monto, String descripcion) {
}
