package ar.com.ciudaddigital.tenants.internal;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import ar.com.ciudaddigital.entitlement.CatalogoDeModulos;
import ar.com.ciudaddigital.entitlement.DescriptorDeModulo;
import ar.com.ciudaddigital.tenants.SolicitudDeModuloInvalida;
import ar.com.ciudaddigital.tenants.SolicitudesDeModulo;
import ar.com.ciudaddigital.tenants.TenantContext;

/**
 * Implementación de la SPI de solicitudes con lo que este módulo ya sabe
 * del tenant resuelto: crea y lista las solicitudes de alta/baja de módulo
 * del municipio del request en curso (ADR 0022 §2), siempre tomando el
 * tenant de {@link TenantContext#requerido()}, nunca de un parámetro que
 * mande quien llama.
 *
 * <p>No lleva {@code @Transactional("controlTransactionManager")} explícito
 * en ninguno de los dos métodos: cada uno hace una sola llamada a
 * {@link SolicitudDeModuloRepository}, que ya resuelve solo al gestor de
 * control por su propia configuración ({@code RepositoriosDeControl}) —
 * mismo motivo por el que {@link InformacionComercialDeMunicipios#actualizar}
 * tampoco lo necesita.
 */
@Component
class SolicitudesDeModuloEnTenants implements SolicitudesDeModulo {

    private final SolicitudDeModuloRepository repositorio;
    private final CatalogoDeModulos catalogo;

    SolicitudesDeModuloEnTenants(SolicitudDeModuloRepository repositorio, CatalogoDeModulos catalogo) {
        this.repositorio = repositorio;
        this.catalogo = catalogo;
    }

    @Override
    public SolicitudDeModuloInfo crear(String moduloCodigo, String tipo, String justificacion,
            String nombreSolicitante, String emailSolicitante) {

        String codigoValidado = validarModuloCodigo(moduloCodigo);
        TipoDeSolicitudDeModulo tipoValidado = validarTipo(tipo);
        String justificacionValidada = validarJustificacion(justificacion);

        SolicitudDeModuloEntity solicitud = SolicitudDeModuloEntity.nueva(
                TenantContext.requerido().id(), codigoValidado, tipoValidado, justificacionValidada,
                nombreSolicitante, emailSolicitante);

        return aInfo(repositorio.save(solicitud));
    }

    @Override
    public List<SolicitudDeModuloInfo> delTenantActual() {
        return repositorio.findByTenantIdOrderByCreadaEnDesc(TenantContext.requerido().id()).stream()
                .map(SolicitudesDeModuloEnTenants::aInfo)
                .toList();
    }

    private String validarModuloCodigo(String codigo) {
        Set<String> validos = catalogo.catalogo().stream()
                .map(DescriptorDeModulo::codigo)
                .collect(Collectors.toSet());

        if (codigo == null || !validos.contains(codigo)) {
            throw new SolicitudDeModuloInvalida("No existe el módulo " + codigo + ".");
        }
        return codigo;
    }

    private TipoDeSolicitudDeModulo validarTipo(String tipo) {
        try {
            return TipoDeSolicitudDeModulo.valueOf(tipo);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new SolicitudDeModuloInvalida(
                    "El tipo de solicitud tiene que ser ALTA o BAJA, no " + tipo + ".");
        }
    }

    private String validarJustificacion(String justificacion) {
        String recortada = justificacion == null ? "" : justificacion.trim();
        if (recortada.isEmpty()) {
            throw new SolicitudDeModuloInvalida("La justificación no puede estar vacía.");
        }
        if (recortada.length() > 1000) {
            throw new SolicitudDeModuloInvalida("La justificación no puede superar los 1000 caracteres.");
        }
        return recortada;
    }

    private static SolicitudDeModuloInfo aInfo(SolicitudDeModuloEntity solicitud) {
        return new SolicitudDeModuloInfo(
                solicitud.getId(),
                solicitud.getModuloCodigo(),
                solicitud.getTipo().name(),
                solicitud.getJustificacion(),
                solicitud.getEstado().name(),
                aInstant(solicitud.getCreadaEn()),
                aInstant(solicitud.getAtendidaEn()));
    }

    private static Instant aInstant(OffsetDateTime fecha) {
        return fecha == null ? null : fecha.toInstant();
    }
}
