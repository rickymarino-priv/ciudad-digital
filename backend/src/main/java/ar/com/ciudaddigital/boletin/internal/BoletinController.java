package ar.com.ciudaddigital.boletin.internal;

import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.com.ciudaddigital.acceso.ActorAutenticado;

/**
 * Publicación protegida y búsqueda pública de normas del Boletín Oficial
 * (backlog R7).
 *
 * <p>El listado no lleva {@code @PreAuthorize}: es la ruta que
 * {@code DescriptorDelModuloBoletin} declara como
 * {@code rutasDeLecturaPublica()}, protegida solo por el gating de
 * entitlement y el {@code permitAll()} de {@code GET} que arma la cadena de
 * seguridad a partir de esa declaración (ADR 0012 §1) — mismo mecanismo que
 * {@code ejemplo} usa para su ping. Publicar sí requiere sesión y el
 * permiso {@code boletin.publicar}: es, a propósito, el complemento de
 * {@code ReclamosController}, donde la escritura era pública y la lectura
 * protegida.
 */
@RestController
@RequestMapping("/api/boletin")
class BoletinController {

    private final GestionDelBoletin gestion;

    BoletinController(GestionDelBoletin gestion) {
        this.gestion = gestion;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('boletin.publicar')")
    ResponseEntity<NormaResponse> publicar(
            @RequestBody PublicarNormaRequest request, Authentication autenticacion) {

        TipoDeNorma tipo = tipoDe(request.tipo());
        String nombre;
        String email;
        if (autenticacion.getPrincipal() instanceof ActorAutenticado actor) {
            nombre = actor.nombre();
            email = actor.email();
        } else {
            // No debería pasar: el permiso ya exige sesión de acceso, así
            // que el principal siempre es un ActorAutenticado. Si no lo es,
            // es un problema del mecanismo de autenticación, no una
            // solicitud inválida del vecino.
            throw new IllegalStateException("No hay un actor autenticado para firmar la publicación.");
        }

        NormaEntity norma = gestion.publicar(tipo, request.numero(), request.titulo(), request.texto(),
                request.fechaPublicacion(), nombre, email);
        return ResponseEntity.status(HttpStatus.CREATED).body(NormaResponse.de(norma));
    }

    @GetMapping
    List<NormaResponse> buscar(
            @RequestParam(required = false) String tipo, @RequestParam(required = false) String q) {

        TipoDeNorma tipoDeNorma = tipo == null || tipo.isBlank() ? null : tipoDe(tipo);
        return gestion.buscar(tipoDeNorma, q).stream().map(NormaResponse::de).toList();
    }

    @ExceptionHandler(SolicitudInvalida.class)
    ResponseEntity<ErrorResponse> solicitudInvalida(SolicitudInvalida e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    private static TipoDeNorma tipoDe(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar un tipo de norma.");
        }
        try {
            return TipoDeNorma.valueOf(tipo);
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalida("El tipo de norma '" + tipo + "' no existe.");
        }
    }

    record PublicarNormaRequest(
            String tipo, String numero, String titulo, String texto, LocalDate fechaPublicacion) {
    }

    record NormaResponse(
            Long id,
            String tipo,
            String numero,
            String titulo,
            String texto,
            LocalDate fechaPublicacion,
            String publicadoPorNombre,
            String publicadoPorEmail,
            Instant creadoEn) {

        static NormaResponse de(NormaEntity norma) {
            return new NormaResponse(
                    norma.getId(),
                    norma.getTipo().name(),
                    norma.getNumero(),
                    norma.getTitulo(),
                    norma.getTexto(),
                    norma.getFechaPublicacion(),
                    norma.getPublicadoPorNombre(),
                    norma.getPublicadoPorEmail(),
                    norma.getCreadoEn());
        }
    }

    record ErrorResponse(String error) {
    }
}
