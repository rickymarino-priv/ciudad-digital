package ar.com.ciudaddigital.tenants;

/**
 * Una solicitud de alta/baja de módulo con datos inválidos: código de
 * módulo inexistente, tipo distinto de {@code ALTA}/{@code BAJA}, o
 * justificación vacía (ADR 0022 §2).
 *
 * <p>Pública, no {@code .internal}: la atrapa un {@code @ExceptionHandler}
 * en el módulo {@code municipio}, que no tiene visibilidad de
 * {@code tenants.internal}.
 */
public class SolicitudDeModuloInvalida extends RuntimeException {

    public SolicitudDeModuloInvalida(String mensaje) {
        super(mensaje);
    }
}
