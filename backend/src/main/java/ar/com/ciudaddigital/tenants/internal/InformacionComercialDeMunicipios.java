package ar.com.ciudaddigital.tenants.internal;

import org.springframework.stereotype.Service;

import ar.com.ciudaddigital.tenants.internal.AltaDeMunicipio.SolicitudInvalida;

/**
 * Contrato mínimo de un municipio: tramo poblacional y estado de
 * facturación (ADR 0019).
 *
 * <p>Operación de plataforma, igual que {@link AdministracionDeModulos}:
 * la usa exclusivamente {@link AdministracionDeMunicipiosController}, con
 * sesión de usuario de plataforma. No hay ninguna superficie de esto en el
 * portal de municipio.
 */
@Service
class InformacionComercialDeMunicipios {

    private final TenantRepository repositorio;
    private final AdministracionDeModulos administracionDeModulos;

    InformacionComercialDeMunicipios(TenantRepository repositorio,
            AdministracionDeModulos administracionDeModulos) {
        this.repositorio = repositorio;
        this.administracionDeModulos = administracionDeModulos;
    }

    /**
     * Reemplaza los tres campos de contrato juntos. {@code notaFacturacion}
     * se acepta tal cual, incluido {@code null}: limpiar la nota es una
     * operación válida, no un olvido.
     */
    TenantEntity actualizar(String slug, String tramoPoblacional, String estadoFacturacion,
            String notaFacturacion) {

        TenantEntity tenant = administracionDeModulos.municipio(slug);
        TramoPoblacional tramo = validarTramoPoblacional(tramoPoblacional);
        EstadoFacturacion estado = validarEstadoFacturacion(estadoFacturacion);

        tenant.cambiarInformacionComercial(tramo, estado, notaFacturacion);
        return repositorio.save(tenant);
    }

    private TramoPoblacional validarTramoPoblacional(String valor) {
        try {
            return TramoPoblacional.valueOf(valor);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new SolicitudInvalida("No existe el tramo poblacional " + valor + ".");
        }
    }

    private EstadoFacturacion validarEstadoFacturacion(String valor) {
        try {
            return EstadoFacturacion.valueOf(valor);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new SolicitudInvalida("No existe el estado de facturación " + valor + ".");
        }
    }
}
