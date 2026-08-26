package ar.com.ciudaddigital.proveedores.internal;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.com.ciudaddigital.proveedores.internal.GestionDeProveedores.ProveedorCreado;

/**
 * Alta pública de proveedores, su gestión por el municipio (ADR 0014) y la
 * consulta pública por token de seguimiento (ADR 0017).
 *
 * <p>El alta no lleva {@code @PreAuthorize}: es la ruta que
 * {@code DescriptorDelModuloProveedores} declara como
 * {@code rutasDeEscrituraPublica()}, protegida solo por el gating de
 * entitlement y el {@code permitAll()} de {@code POST} que arma la cadena
 * de seguridad a partir de esa declaración (ADR 0014 §1). La consulta por
 * token tampoco lleva {@code @PreAuthorize}: es la ruta que
 * {@code DescriptorDelModuloProveedores} declara como
 * {@code rutasDeLecturaPublica()} (ADR 0017 §4). Listar y gestionar sí
 * requieren sesión y permiso.
 */
@RestController
@RequestMapping("/api/proveedores")
class ProveedoresController {

    private final GestionDeProveedores gestion;

    ProveedoresController(GestionDeProveedores gestion) {
        this.gestion = gestion;
    }

    @PostMapping
    ResponseEntity<ProveedorPublicoResponse> registrar(@RequestBody RegistrarProveedorRequest request) {
        RubroProveedor rubro = rubroDe(request.rubro());
        ProveedorCreado creado = gestion.registrar(request.razonSocial(), request.cuit(), rubro,
                request.emailContacto(), request.telefonoContacto(), request.domicilio(),
                request.declaraConstanciaAfip(), request.declaraSeguroResponsabilidadCivil(),
                request.declaraCertificadoAntecedentes(), request.documentacionAdicional());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProveedorPublicoResponse.de(creado.proveedor(), creado.tokenDeSeguimiento()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('proveedores.ver')")
    List<ProveedorResponse> listar() {
        return gestion.listar().stream().map(ProveedorResponse::de).toList();
    }

    @GetMapping("/seguimiento/{token}")
    SeguimientoDeProveedorResponse consultarPorToken(@PathVariable String token) {
        return SeguimientoDeProveedorResponse.de(gestion.consultarPorToken(token));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('proveedores.gestionar')")
    ProveedorResponse cambiarEstado(@PathVariable Long id, @RequestBody CambiarEstadoRequest request) {
        EstadoDeProveedor nuevoEstado = estadoDe(request.estado());
        ProveedorEntity proveedor = gestion.cambiarEstado(id, nuevoEstado, request.comentario());
        return ProveedorResponse.de(proveedor);
    }

    @ExceptionHandler(SolicitudInvalida.class)
    ResponseEntity<ErrorResponse> solicitudInvalida(SolicitudInvalida e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    /**
     * Mensaje genérico, siempre el mismo, sin importar si el token no
     * matchea ninguna fila o el string ni siquiera tiene forma de token
     * (ADR 0017 §4).
     */
    @ExceptionHandler(TokenNoEncontrado.class)
    ResponseEntity<ErrorResponse> tokenNoEncontrado(TokenNoEncontrado e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("No encontramos un proveedor con ese código."));
    }

    private static RubroProveedor rubroDe(String rubro) {
        if (rubro == null || rubro.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar un rubro.");
        }
        try {
            return RubroProveedor.valueOf(rubro);
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalida("El rubro '" + rubro + "' no existe.");
        }
    }

    private static EstadoDeProveedor estadoDe(String estado) {
        if (estado == null || estado.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar un estado.");
        }
        try {
            return EstadoDeProveedor.valueOf(estado);
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalida("El estado '" + estado + "' no existe.");
        }
    }

    record RegistrarProveedorRequest(
            String razonSocial,
            String cuit,
            String rubro,
            String emailContacto,
            String telefonoContacto,
            String domicilio,
            boolean declaraConstanciaAfip,
            boolean declaraSeguroResponsabilidadCivil,
            boolean declaraCertificadoAntecedentes,
            String documentacionAdicional) {
    }

    record CambiarEstadoRequest(String estado, String comentario) {
    }

    /**
     * Confirmación a la empresa que se registró: deliberadamente sin
     * contacto/domicilio/documentación — no es una vista de gestión, y
     * quien la recibe ya escribió esos datos él mismo un segundo antes.
     * {@code tokenDeSeguimiento} es la única vez en toda la vida del
     * registro que ese valor viaja en claro (ADR 0017 §4): ni la entidad ni
     * ningún otro endpoint lo vuelven a exponer.
     */
    record ProveedorPublicoResponse(
            Long id, String razonSocial, String cuit, String rubro, String estado, Instant creadoEn,
            String tokenDeSeguimiento) {

        static ProveedorPublicoResponse de(ProveedorEntity proveedor, String tokenDeSeguimiento) {
            return new ProveedorPublicoResponse(
                    proveedor.getId(), proveedor.getRazonSocial(), proveedor.getCuit(),
                    proveedor.getRubro().name(), proveedor.getEstado().name(), proveedor.getCreadoEn(),
                    tokenDeSeguimiento);
        }
    }

    /**
     * Lo que ve la empresa que consulta con su token de seguimiento (ADR
     * 0017 §5): deliberadamente sin {@code emailContacto}/
     * {@code telefonoContacto}/{@code domicilio} —son datos que la propia
     * empresa ya tiene—, pero sí con el rubro y la documentación declarada:
     * es "en qué quedó" el registro, información que la empresa necesita
     * para saber si tiene que agregar algo.
     */
    record SeguimientoDeProveedorResponse(
            Long id,
            String razonSocial,
            String cuit,
            String rubro,
            String estado,
            String comentarioGestion,
            boolean declaraConstanciaAfip,
            boolean declaraSeguroResponsabilidadCivil,
            boolean declaraCertificadoAntecedentes,
            String documentacionAdicional,
            Instant creadoEn,
            Instant actualizadoEn) {

        static SeguimientoDeProveedorResponse de(ProveedorEntity proveedor) {
            return new SeguimientoDeProveedorResponse(
                    proveedor.getId(),
                    proveedor.getRazonSocial(),
                    proveedor.getCuit(),
                    proveedor.getRubro().name(),
                    proveedor.getEstado().name(),
                    proveedor.getComentarioGestion(),
                    proveedor.isDeclaraConstanciaAfip(),
                    proveedor.isDeclaraSeguroResponsabilidadCivil(),
                    proveedor.isDeclaraCertificadoAntecedentes(),
                    proveedor.getDocumentacionAdicional(),
                    proveedor.getCreadoEn(),
                    proveedor.getActualizadoEn());
        }
    }

    /** Shape completo, solo para quien tiene {@code proveedores.ver}. */
    record ProveedorResponse(
            Long id,
            String razonSocial,
            String cuit,
            String rubro,
            String emailContacto,
            String telefonoContacto,
            String domicilio,
            boolean declaraConstanciaAfip,
            boolean declaraSeguroResponsabilidadCivil,
            boolean declaraCertificadoAntecedentes,
            String documentacionAdicional,
            String estado,
            String comentarioGestion,
            Instant creadoEn,
            Instant actualizadoEn) {

        static ProveedorResponse de(ProveedorEntity proveedor) {
            return new ProveedorResponse(
                    proveedor.getId(),
                    proveedor.getRazonSocial(),
                    proveedor.getCuit(),
                    proveedor.getRubro().name(),
                    proveedor.getEmailContacto(),
                    proveedor.getTelefonoContacto(),
                    proveedor.getDomicilio(),
                    proveedor.isDeclaraConstanciaAfip(),
                    proveedor.isDeclaraSeguroResponsabilidadCivil(),
                    proveedor.isDeclaraCertificadoAntecedentes(),
                    proveedor.getDocumentacionAdicional(),
                    proveedor.getEstado().name(),
                    proveedor.getComentarioGestion(),
                    proveedor.getCreadoEn(),
                    proveedor.getActualizadoEn());
        }
    }

    record ErrorResponse(String error) {
    }
}
