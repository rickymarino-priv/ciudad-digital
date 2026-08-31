package ar.com.ciudaddigital.prensa.internal;

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
 * Publicación protegida y búsqueda pública de gacetillas de prensa
 * municipal (ADR 0027).
 *
 * <p>El listado no lleva {@code @PreAuthorize}: es la ruta que
 * {@code DescriptorDelModuloPrensa} declara como
 * {@code rutasDeLecturaPublica()}, protegida solo por el gating de
 * entitlement y el {@code permitAll()} de {@code GET} que arma la cadena de
 * seguridad a partir de esa declaración (ADR 0012 §1). Publicar sí
 * requiere sesión y el permiso {@code prensa.publicar} — mismo mecanismo
 * exacto que {@code BoletinController}, con la diferencia de que ese
 * permiso lo tienen tanto {@code administrador} como {@code agente}
 * (ADR 0027 §3).
 */
@RestController
@RequestMapping("/api/prensa")
class PrensaController {

    private final GestionDePrensa gestion;

    PrensaController(GestionDePrensa gestion) {
        this.gestion = gestion;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('prensa.publicar')")
    ResponseEntity<GacetillaResponse> publicar(
            @RequestBody PublicarGacetillaRequest request, Authentication autenticacion) {

        CategoriaDeGacetilla categoria = categoriaDe(request.categoria());
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

        GacetillaEntity gacetilla = gestion.publicar(categoria, request.titulo(), request.texto(),
                request.fechaPublicacion(), nombre, email);
        return ResponseEntity.status(HttpStatus.CREATED).body(GacetillaResponse.de(gacetilla));
    }

    @GetMapping
    List<GacetillaResponse> buscar(
            @RequestParam(required = false) String categoria, @RequestParam(required = false) String q) {

        CategoriaDeGacetilla categoriaDeGacetilla =
                categoria == null || categoria.isBlank() ? null : categoriaDe(categoria);
        return gestion.buscar(categoriaDeGacetilla, q).stream().map(GacetillaResponse::de).toList();
    }

    @ExceptionHandler(SolicitudInvalida.class)
    ResponseEntity<ErrorResponse> solicitudInvalida(SolicitudInvalida e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    private static CategoriaDeGacetilla categoriaDe(String categoria) {
        if (categoria == null || categoria.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar una categoría.");
        }
        try {
            return CategoriaDeGacetilla.valueOf(categoria);
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalida("La categoría '" + categoria + "' no existe.");
        }
    }

    record PublicarGacetillaRequest(
            String categoria, String titulo, String texto, LocalDate fechaPublicacion) {
    }

    record GacetillaResponse(
            Long id,
            String categoria,
            String titulo,
            String texto,
            LocalDate fechaPublicacion,
            String publicadoPorNombre,
            String publicadoPorEmail,
            Instant creadoEn) {

        static GacetillaResponse de(GacetillaEntity gacetilla) {
            return new GacetillaResponse(
                    gacetilla.getId(),
                    gacetilla.getCategoria().name(),
                    gacetilla.getTitulo(),
                    gacetilla.getTexto(),
                    gacetilla.getFechaPublicacion(),
                    gacetilla.getPublicadoPorNombre(),
                    gacetilla.getPublicadoPorEmail(),
                    gacetilla.getCreadoEn());
        }
    }

    record ErrorResponse(String error) {
    }
}
