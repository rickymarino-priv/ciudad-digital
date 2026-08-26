package ar.com.ciudaddigital.padronfiscal;

/**
 * Contrato de integración con un padrón fiscal externo (ADR 0020 §1).
 *
 * <p>Quien llama a {@code consultar} le pasa el CUIT ya normalizado a 11
 * dígitos (el formato {@code "XX-XXXXXXXX-X"} que ya persiste
 * {@code proveedores}): este módulo no repite ninguna validación de
 * formato, esa responsabilidad ya es de quien llama.
 */
public interface PadronFiscal {

    SituacionFiscal consultar(String cuit);
}
