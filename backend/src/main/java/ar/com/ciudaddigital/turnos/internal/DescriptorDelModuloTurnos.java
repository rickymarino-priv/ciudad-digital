package ar.com.ciudaddigital.turnos.internal;

import java.util.List;

import org.springframework.stereotype.Component;

import ar.com.ciudaddigital.entitlement.DescriptorDeModulo;

/**
 * Declara el módulo {@code turnos} ante el catálogo de entitlement (ADR
 * 0012 §1). El catálogo de actividades y de franjas horarias tienen
 * lectura pública y alta protegida (ADR 0026 §2/§3), mismo mecanismo que
 * {@code obras}/{@code arbolado}/{@code desarrollosocial}. La reserva de
 * un turno es alta pública anónima (ADR 0026 §4, mismo mecanismo que
 * {@code reclamos}); el listado de reservas para gestión no está entre
 * las rutas públicas de ningún tipo (ADR 0026 §5): es la única lectura de
 * este módulo detrás de sesión y permiso.
 */
@Component
class DescriptorDelModuloTurnos implements DescriptorDeModulo {

    static final String CODIGO = "turnos";

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public String nombre() {
        return "Turnos para actividades municipales";
    }

    @Override
    public String descripcion() {
        return "Catálogo público de actividades municipales de deporte, cultura y turismo, con franjas "
                + "horarias de cupo limitado y reserva pública anónima con cupo compartido entre solicitantes.";
    }

    @Override
    public List<String> prefijosDeApi() {
        return List.of("/api/turnos");
    }

    /** El listado de actividades y el de franjas de una actividad son públicos (ADR 0026 §2/§3). */
    @Override
    public List<String> rutasDeLecturaPublica() {
        return List.of("/api/turnos/actividades", "/api/turnos/franjas");
    }

    /** Solo el alta de reservas: un vecino reserva un turno sin cuenta (ADR 0026 §4). */
    @Override
    public List<String> rutasDeEscrituraPublica() {
        return List.of("/api/turnos/reservas");
    }
}
