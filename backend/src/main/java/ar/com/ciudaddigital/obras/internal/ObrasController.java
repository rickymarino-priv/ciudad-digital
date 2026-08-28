package ar.com.ciudaddigital.obras.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.com.ciudaddigital.acceso.ActorAutenticado;

/**
 * Alta protegida, lectura pública y actualización de estado de obras
 * públicas (ADR 0023).
 *
 * <p>El listado no lleva {@code @PreAuthorize}: es la ruta que
 * {@code DescriptorDelModuloObras} declara como
 * {@code rutasDeLecturaPublica()}, protegida solo por el gating de
 * entitlement (ADR 0012 §1). Registrar y actualizar el estado sí
 * requieren sesión y el permiso {@code obras.gestionar}.
 */
@RestController
@RequestMapping("/api/obras")
class ObrasController {

    private final GestionDeObras gestion;

    ObrasController(GestionDeObras gestion) {
        this.gestion = gestion;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('obras.gestionar')")
    ResponseEntity<ObraPublicaResponse> registrar(
            @RequestBody RegistrarObraRequest request, Authentication autenticacion) {

        TipoDeObra tipo = tipoDe(request.tipo());
        ActorAutenticado actor = actorDe(autenticacion);
        ObraPublicaEntity obra = gestion.registrar(
                request.nombre(), tipo, request.ubicacion(), request.descripcion(),
                request.fechaInicioEstimada(), request.fechaFinEstimada(),
                actor.nombre(), actor.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(ObraPublicaResponse.de(obra));
    }

    @GetMapping
    List<ObraPublicaResponse> buscar(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String q) {

        EstadoDeObra estadoDeObra = estado == null || estado.isBlank() ? null : estadoDe(estado);
        TipoDeObra tipoDeObra = tipo == null || tipo.isBlank() ? null : tipoDe(tipo);
        return gestion.buscar(estadoDeObra, tipoDeObra, q).stream().map(ObraPublicaResponse::de).toList();
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('obras.gestionar')")
    ObraPublicaResponse actualizarEstado(@PathVariable Long id, @RequestBody ActualizarEstadoRequest request) {
        EstadoDeObra estadoNuevo = estadoDe(request.estadoNuevo());
        ObraPublicaEntity obra = gestion.actualizarEstado(id, estadoNuevo);
        return ObraPublicaResponse.de(obra);
    }

    private static ActorAutenticado actorDe(Authentication autenticacion) {
        if (autenticacion.getPrincipal() instanceof ActorAutenticado actor) {
            return actor;
        }
        // No debería pasar: el permiso ya exige sesión de acceso, así que el
        // principal siempre es un ActorAutenticado. Si no lo es, es un
        // problema del mecanismo de autenticación, no una solicitud
        // inválida del agente (mismo criterio que MultasController#actorDe).
        throw new IllegalStateException("No hay un actor autenticado para firmar la operación.");
    }

    private static TipoDeObra tipoDe(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar un tipo de obra.");
        }
        try {
            return TipoDeObra.valueOf(tipo);
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalida("El tipo de obra '" + tipo + "' no existe.");
        }
    }

    private static EstadoDeObra estadoDe(String estado) {
        if (estado == null || estado.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar un estado.");
        }
        try {
            return EstadoDeObra.valueOf(estado);
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalida("El estado '" + estado + "' no existe.");
        }
    }

    @ExceptionHandler(SolicitudInvalida.class)
    ResponseEntity<ErrorResponse> solicitudInvalida(SolicitudInvalida e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    /** Un id de obra que no existe (inventado o de otro municipio, ver ADR 0001) da 404 genérico. */
    @ExceptionHandler(ObraNoEncontrada.class)
    ResponseEntity<ErrorResponse> obraNoEncontrada(ObraNoEncontrada e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("No encontramos esa obra."));
    }

    record RegistrarObraRequest(
            String nombre, String tipo, String ubicacion, String descripcion,
            LocalDate fechaInicioEstimada, LocalDate fechaFinEstimada) {
    }

    record ActualizarEstadoRequest(String estadoNuevo) {
    }

    record ObraPublicaResponse(
            Long id,
            String nombre,
            String tipo,
            String ubicacion,
            String descripcion,
            String estado,
            LocalDate fechaInicioEstimada,
            LocalDate fechaFinEstimada,
            String publicadoPorNombre,
            String publicadoPorEmail,
            Instant creadoEn,
            Instant actualizadoEn) {

        static ObraPublicaResponse de(ObraPublicaEntity obra) {
            return new ObraPublicaResponse(
                    obra.getId(),
                    obra.getNombre(),
                    obra.getTipo().name(),
                    obra.getUbicacion(),
                    obra.getDescripcion(),
                    obra.getEstado().name(),
                    obra.getFechaInicioEstimada(),
                    obra.getFechaFinEstimada(),
                    obra.getPublicadoPorNombre(),
                    obra.getPublicadoPorEmail(),
                    obra.getCreadoEn(),
                    obra.getActualizadoEn());
        }
    }

    record ErrorResponse(String error) {
    }
}
