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

/**
 * Alta pública de reclamos y su gestión por el municipio (ADR 0014).
 *
 * <p>El alta no lleva {@code @PreAuthorize}: es la ruta que
 * {@code DescriptorDelModuloReclamos} declara como
 * {@code rutasDeEscrituraPublica()}, protegida solo por el gating de
 * entitlement y el {@code permitAll()} de {@code POST} que arma la cadena
 * de seguridad a partir de esa declaración (ADR 0014 §1). Listar y
 * gestionar sí requieren sesión y permiso.
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
        ReclamoEntity reclamo = gestion.cargar(categoria, request.descripcion(), request.direccion(),
                request.nombreContacto(), request.contacto());
        return ResponseEntity.status(HttpStatus.CREATED).body(ReclamoPublicoResponse.de(reclamo));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('reclamos.ver')")
    List<ReclamoResponse> listar() {
        return gestion.listar().stream().map(ReclamoResponse::de).toList();
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
     */
    record ReclamoPublicoResponse(Long id, String categoria, String estado, Instant creadoEn) {

        static ReclamoPublicoResponse de(ReclamoEntity reclamo) {
            return new ReclamoPublicoResponse(
                    reclamo.getId(), reclamo.getCategoria().name(), reclamo.getEstado().name(),
                    reclamo.getCreadoEn());
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
