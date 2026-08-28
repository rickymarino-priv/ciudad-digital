package ar.com.ciudaddigital.municipio.internal;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.com.ciudaddigital.acceso.ActorAutenticado;
import ar.com.ciudaddigital.tenants.ContratoDelTenant;
import ar.com.ciudaddigital.tenants.SolicitudDeModuloInvalida;
import ar.com.ciudaddigital.tenants.SolicitudesDeModulo;
import ar.com.ciudaddigital.tenants.SolicitudesDeModulo.SolicitudDeModuloInfo;

/**
 * Contrato mínimo y solicitudes de alta/baja de módulo del propio municipio
 * (ADR 0022): la contracara intra-tenant de la consola del proveedor, pero
 * protegida por permiso en vez de por sesión de plataforma.
 *
 * <p>No toca {@code TenantRepository} ni ninguna clase de
 * {@code tenants.internal} directamente: solo consume las dos interfaces
 * públicas que expone {@code tenants} para esto, mismo criterio de límite
 * de módulo que ya usa {@code acceso} con {@code TenantContext}.
 */
@RestController
@RequestMapping("/api/municipio")
class ConsolaDelMunicipioController {

    private final ContratoDelTenant contratoDelTenant;
    private final SolicitudesDeModulo solicitudesDeModulo;

    ConsolaDelMunicipioController(ContratoDelTenant contratoDelTenant, SolicitudesDeModulo solicitudesDeModulo) {
        this.contratoDelTenant = contratoDelTenant;
        this.solicitudesDeModulo = solicitudesDeModulo;
    }

    @GetMapping("/contrato")
    @PreAuthorize("hasAuthority('municipio.verContrato')")
    ResponseEntity<ContratoResponse> contrato() {
        return contratoDelTenant.actual()
                .map(contrato -> new ContratoResponse(contrato.tramoPoblacional(), contrato.estadoFacturacion()))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/solicitudes-de-modulo")
    @PreAuthorize("hasAuthority('municipio.verContrato')")
    List<SolicitudDeModuloResponse> solicitudes() {
        return solicitudesDeModulo.delTenantActual().stream()
                .map(SolicitudDeModuloResponse::de)
                .toList();
    }

    @PostMapping("/solicitudes-de-modulo")
    @PreAuthorize("hasAuthority('municipio.solicitarModulo')")
    ResponseEntity<SolicitudDeModuloResponse> solicitar(
            @RequestBody SolicitarModuloRequest request, Authentication autenticacion) {

        ActorAutenticado actor = actorDe(autenticacion);
        SolicitudDeModuloInfo solicitud = solicitudesDeModulo.crear(
                request.moduloCodigo(), request.tipo(), request.justificacion(),
                actor.nombre(), actor.email());

        return ResponseEntity.status(HttpStatus.CREATED).body(SolicitudDeModuloResponse.de(solicitud));
    }

    private static ActorAutenticado actorDe(Authentication autenticacion) {
        if (autenticacion.getPrincipal() instanceof ActorAutenticado actor) {
            return actor;
        }
        // No debería pasar: el permiso ya exige sesión de acceso, así que el
        // principal siempre es un ActorAutenticado (mismo criterio que
        // MultasController#actorDe).
        throw new IllegalStateException("No hay un actor autenticado para firmar la solicitud.");
    }

    @ExceptionHandler(SolicitudDeModuloInvalida.class)
    ResponseEntity<ErrorResponse> solicitudInvalida(SolicitudDeModuloInvalida e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    record ContratoResponse(String tramoPoblacional, String estadoFacturacion) {
    }

    record SolicitudDeModuloResponse(
            Long id, String moduloCodigo, String tipo, String justificacion, String estado,
            Instant creadaEn, Instant atendidaEn) {

        static SolicitudDeModuloResponse de(SolicitudDeModuloInfo solicitud) {
            return new SolicitudDeModuloResponse(
                    solicitud.id(), solicitud.moduloCodigo(), solicitud.tipo(), solicitud.justificacion(),
                    solicitud.estado(), solicitud.creadaEn(), solicitud.atendidaEn());
        }
    }

    record SolicitarModuloRequest(String moduloCodigo, String tipo, String justificacion) {
    }

    record ErrorResponse(String error) {
    }
}
