package ar.com.ciudaddigital.mesaentradas.internal;

import java.util.List;

import org.springframework.stereotype.Component;

import ar.com.ciudaddigital.entitlement.DescriptorDeModulo;

/**
 * Declara el módulo {@code mesaentradas} ante el catálogo de entitlement
 * (ADR 0012 §1).
 */
@Component
class DescriptorDelModuloMesaDeEntradas implements DescriptorDeModulo {

    static final String CODIGO = "mesaentradas";

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public String nombre() {
        return "Mesa de Entradas";
    }

    @Override
    public String descripcion() {
        return "Mesa de Entradas digital: inicio y gestión de trámites del municipio, "
                + "con circuito de estados propio por tipo de trámite (ADR 0015).";
    }

    @Override
    public List<String> prefijosDeApi() {
        return List.of("/api/mesaentradas");
    }

    /** Solo el alta: un vecino inicia un trámite sin cuenta (ADR 0014 §1, ADR 0015 §4). */
    @Override
    public List<String> rutasDeEscrituraPublica() {
        return List.of("/api/mesaentradas");
    }

    /**
     * El vecino ahora sí puede consultar su trámite después de iniciarlo,
     * por posesión de un token no adivinable —no por id, que sigue sin ser
     * público (ADR 0015 §4, resuelto por ADR 0017)—.
     */
    @Override
    public List<String> rutasDeLecturaPublica() {
        return List.of("/api/mesaentradas/seguimiento/{token}");
    }
}
