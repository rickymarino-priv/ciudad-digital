package ar.com.ciudaddigital.multas.internal;

import java.util.List;

import org.springframework.stereotype.Component;

import ar.com.ciudaddigital.entitlement.DescriptorDeModulo;

/**
 * Declara el módulo {@code multas} ante el catálogo de entitlement (ADR
 * 0012 §1). A diferencia de {@code tasas}, el alta (labrar) no es pública:
 * la origina el municipio, nunca el vecino (ADR 0021 §3).
 */
@Component
class DescriptorDelModuloMultas implements DescriptorDeModulo {

    static final String CODIGO = "multas";

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public String nombre() {
        return "Multas de tránsito";
    }

    @Override
    public String descripcion() {
        return "Alta protegida de actas de infracción de tránsito por un agente municipal, "
                + "búsqueda y pago público con descuento por pronto pago, y descargo del vecino "
                + "con resolución por el Juzgado de Faltas (ADR 0021).";
    }

    @Override
    public List<String> prefijosDeApi() {
        return List.of("/api/multas");
    }

    /** Solo la búsqueda por patente/DNI es pública para lectura (ADR 0021 §6). */
    @Override
    public List<String> rutasDeLecturaPublica() {
        return List.of("/api/multas");
    }

    /**
     * Iniciar/confirmar un pago son escrituras anónimas legítimas, mismo
     * criterio que {@code tasas} (ADR 0018 §4). Presentar un descargo
     * también lo es: es una alta pública anónima, el vecino no tiene
     * cuenta (ADR 0014 §1, ADR 0021 §5). Labrar una multa y resolver un
     * descargo no están acá: requieren sesión y permiso.
     */
    @Override
    public List<String> rutasDeEscrituraPublica() {
        return List.of("/api/multas/{id}/pagos", "/api/multas/pagos/confirmar", "/api/multas/{id}/descargo");
    }
}
