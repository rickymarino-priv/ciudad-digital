package ar.com.ciudaddigital.reclamos.internal;

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

import ar.com.ciudaddigital.reclamos.internal.GestionDeReclamos.ReclamoCreado;

/**
 * Alta pública de reclamos, su gestión por el municipio (ADR 0014) y la
 * consulta pública por token de seguimiento (ADR 0017).
 *
 * <p>El alta no lleva {@code @PreAuthorize}: es la ruta que
 * {@code DescriptorDelModuloReclamos} declara como
 * {@code rutasDeEscrituraPublica()}, protegida solo por el gating de
 * entitlement y el {@code permitAll()} de {@code POST} que arma la cadena
 * de seguridad a partir de esa declaración (ADR 0014 §1). La consulta por
 * token tampoco lleva {@code @PreAuthorize}: es la ruta que
 * {@code DescriptorDelModuloReclamos} declara como
 * {@code rutasDeLecturaPublica()} (ADR 0017 §4). Listar y gestionar sí
 * requieren sesión y permiso.
 */
@RestController
@RequestMapping("/api/reclamos")
class ReclamosController {

    private final GestionDeReclamos gestion;

    ReclamosController(GestionDeReclamos gestion) {
        this.gestion = gestion;
    }

    @PostMapping
    ResponseEntity<ReclamoPublicoResponse> cargar(@RequestBody CrearReclamoRequest request) {
        CategoriaReclamo categoria = categoriaDe(request.categoria());
        ReclamoCreado creado = gestion.cargar(categoria, request.descripcion(), request.direccion(),
                request.nombreContacto(), request.contacto());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ReclamoPublicoResponse.de(creado.reclamo(), creado.tokenDeSeguimiento()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('reclamos.ver')")
    List<ReclamoResponse> listar() {
        return gestion.listar().stream().map(ReclamoResponse::de).toList();
    }

    @GetMapping("/seguimiento/{token}")
    SeguimientoDeReclamoResponse consultarPorToken(@PathVariable String token) {
        return SeguimientoDeReclamoResponse.de(gestion.consultarPorToken(token));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('reclamos.gestionar')")
    ReclamoResponse cambiarEstado(@PathVariable Long id, @RequestBody CambiarEstadoRequest request) {
        EstadoReclamo nuevoEstado = estadoDe(request.estado());
        ReclamoEntity reclamo = gestion.cambiarEstado(id, nuevoEstado, request.comentario());
        return ReclamoResponse.de(reclamo);
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
                .body(new ErrorResponse("No encontramos un reclamo con ese código."));
    }

    private static CategoriaReclamo categoriaDe(String categoria) {
        if (categoria == null || categoria.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar una categoría.");
        }
        try {
            return CategoriaReclamo.valueOf(categoria);
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalida("La categoría '" + categoria + "' no existe.");
        }
    }

    private static EstadoReclamo estadoDe(String estado) {
        if (estado == null || estado.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar un estado.");
        }
        try {
            return EstadoReclamo.valueOf(estado);
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalida("El estado '" + estado + "' no existe.");
        }
    }

    record CrearReclamoRequest(
            String categoria, String descripcion, String direccion, String nombreContacto, String contacto) {
    }

    record CambiarEstadoRequest(String estado, String comentario) {
    }

    /**
     * Confirmación al vecino que cargó el reclamo: deliberadamente sin
     * {@code descripcion}/{@code direccion}/{@code contacto}/
     * {@code comentarioGestion} — no es una vista de gestión.
     * {@code tokenDeSeguimiento} es la única vez en toda la vida del
     * reclamo que ese valor viaja en claro (ADR 0017 §4): ni la entidad ni
     * ningún otro endpoint lo vuelven a exponer.
     */
    record ReclamoPublicoResponse(Long id, String categoria, String estado, Instant creadoEn,
            String tokenDeSeguimiento) {

        static ReclamoPublicoResponse de(ReclamoEntity reclamo, String tokenDeSeguimiento) {
            return new ReclamoPublicoResponse(
                    reclamo.getId(), reclamo.getCategoria().name(), reclamo.getEstado().name(),
                    reclamo.getCreadoEn(), tokenDeSeguimiento);
        }
    }

    /**
     * Lo que ve el vecino que consulta con su token de seguimiento (ADR
     * 0017 §5): mismo shape que {@link ReclamoPublicoResponse} más
     * {@code comentarioGestion}/{@code actualizadoEn}, que sí aportan "en
     * qué quedó" el reclamo. Deliberadamente sin {@code descripcion}/
     * {@code direccion}/{@code nombreContacto}/{@code contacto}: son datos
     * que el propio vecino ya tiene o que no hacen a este propósito, mismo
     * criterio que {@code ReclamoPublicoResponse}.
     */
    record SeguimientoDeReclamoResponse(
            Long id, String categoria, String estado, String comentarioGestion, Instant creadoEn,
            Instant actualizadoEn) {

        static SeguimientoDeReclamoResponse de(ReclamoEntity reclamo) {
            return new SeguimientoDeReclamoResponse(
                    reclamo.getId(),
                    reclamo.getCategoria().name(),
                    reclamo.getEstado().name(),
                    reclamo.getComentarioGestion(),
                    reclamo.getCreadoEn(),
                    reclamo.getActualizadoEn());
        }
    }

    record ReclamoResponse(
            Long id,
            String categoria,
            String descripcion,
            String direccion,
            String nombreContacto,
            String contacto,
            String estado,
            String comentarioGestion,
            Instant creadoEn,
            Instant actualizadoEn) {

        static ReclamoResponse de(ReclamoEntity reclamo) {
            return new ReclamoResponse(
                    reclamo.getId(),
                    reclamo.getCategoria().name(),
                    reclamo.getDescripcion(),
                    reclamo.getDireccion(),
                    reclamo.getNombreContacto(),
                    reclamo.getContacto(),
                    reclamo.getEstado().name(),
                    reclamo.getComentarioGestion(),
                    reclamo.getCreadoEn(),
                    reclamo.getActualizadoEn());
        }
    }

    record ErrorResponse(String error) {
    }
}
