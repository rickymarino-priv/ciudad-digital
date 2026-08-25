package ar.com.ciudaddigital.reclamos.internal;

import java.util.List;

import org.springframework.stereotype.Component;

import ar.com.ciudaddigital.entitlement.DescriptorDeModulo;

/**
 * Declara el módulo {@code reclamos} ante el catálogo de entitlement
 * (ADR 0012 §1). A diferencia de {@code ejemplo}, es funcionalidad de
 * producto real y contratable (ADR 0014 §2): un municipio puede no
 * tenerlo, igual que cualquier otro módulo de área.
 */
@Component
class DescriptorDelModuloReclamos implements DescriptorDeModulo {

    static final String CODIGO = "reclamos";

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public String nombre() {
        return "Reclamos ciudadanos";
    }

    @Override
    public String descripcion() {
        return "Reclamos y solicitudes del vecino (311): baches, alumbrado, poda y arbolado, "
                + "recolección de residuos, animales sueltos.";
    }

    @Override
    public List<String> prefijosDeApi() {
        return List.of("/api/reclamos");
    }

    /**
     * Ni el listado ni el detalle son públicos en esta rebanada: no hay
     * seguimiento anónimo por id, porque un id secuencial es adivinable y
     * expondría el contacto de cualquier reclamo (ADR 0014 §6).
     */
    @Override
    public List<String> rutasDeLecturaPublica() {
        return List.of();
    }

    /** Solo el alta: un vecino carga un reclamo sin cuenta (ADR 0014 §1). */
    @Override
    public List<String> rutasDeEscrituraPublica() {
        return List.of("/api/reclamos");
    }
}
