package ar.com.ciudaddigital.cementerio.internal;

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
 * Registro protegido y búsqueda pública de sepulturas del cementerio
 * municipal (backlog R8).
 *
 * <p>El listado no lleva {@code @PreAuthorize}: es la ruta que
 * {@code DescriptorDelModuloCementerio} declara como
 * {@code rutasDeLecturaPublica()}, protegida solo por el gating de
 * entitlement y el {@code permitAll()} de {@code GET} que arma la cadena de
 * seguridad a partir de esa declaración (ADR 0012 §1) — mismo mecanismo que
 * {@code BoletinController}. Registrar sí requiere sesión y el permiso
 * {@code cementerio.registrar}.
 */
@RestController
@RequestMapping("/api/cementerio")
class CementerioController {

    private final GestionDelCementerio gestion;

    CementerioController(GestionDelCementerio gestion) {
        this.gestion = gestion;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('cementerio.registrar')")
    ResponseEntity<SepulturaCompletaResponse> registrar(
            @RequestBody RegistrarSepulturaRequest request, Authentication autenticacion) {

        TipoDeParcela tipo = tipoDe(request.tipoParcela());
        String nombre;
        String email;
        if (autenticacion.getPrincipal() instanceof ActorAutenticado actor) {
            nombre = actor.nombre();
            email = actor.email();
        } else {
            // No debería pasar: el permiso ya exige sesión de acceso, así
            // que el principal siempre es un ActorAutenticado. Si no lo es,
            // es un problema del mecanismo de autenticación, no una
            // solicitud inválida del agente.
            throw new IllegalStateException("No hay un actor autenticado para firmar el registro.");
        }

        SepulturaEntity sepultura = gestion.registrar(tipo, request.sector(), request.fila(), request.numero(),
                request.nombreDifunto(), request.fechaFallecimiento(), request.fechaInhumacion(),
                request.nombreTitular(), request.contactoTitular(), request.observaciones(), nombre, email);
        return ResponseEntity.status(HttpStatus.CREATED).body(SepulturaCompletaResponse.de(sepultura));
    }

    /**
     * Lectura pública, sin sesión: es la ruta que sirve tanto a un vecino
     * anónimo como a quien tiene sesión, no hay una segunda ruta protegida
     * con más datos en esta rebanada. Por eso devuelve
     * {@link SepulturaPublicaResponse} —sin {@code nombreTitular},
     * {@code contactoTitular}, {@code observaciones},
     * {@code registradoPorNombre} ni {@code registradoPorEmail}— en vez del
     * registro completo: son datos de terceros (titular de la concesión,
     * agente municipal) que no hace falta exponer para que un vecino
     * encuentre dónde está sepultado un familiar. Es una decisión
     * deliberada de minimización de datos, no un olvido.
     */
    @GetMapping
    List<SepulturaPublicaResponse> buscar(
            @RequestParam(required = false) String tipoParcela, @RequestParam(required = false) String q) {

        TipoDeParcela tipo = tipoParcela == null || tipoParcela.isBlank() ? null : tipoDe(tipoParcela);
        return gestion.buscar(tipo, q).stream().map(SepulturaPublicaResponse::de).toList();
    }

    @ExceptionHandler(SolicitudInvalida.class)
    ResponseEntity<ErrorResponse> solicitudInvalida(SolicitudInvalida e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    private static TipoDeParcela tipoDe(String tipoParcela) {
        if (tipoParcela == null || tipoParcela.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar un tipo de parcela.");
        }
        try {
            return TipoDeParcela.valueOf(tipoParcela);
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalida("El tipo de parcela '" + tipoParcela + "' no existe.");
        }
    }

    record RegistrarSepulturaRequest(
            String tipoParcela,
            String sector,
            String fila,
            String numero,
            String nombreDifunto,
            LocalDate fechaFallecimiento,
            LocalDate fechaInhumacion,
            String nombreTitular,
            String contactoTitular,
            String observaciones) {
    }

    /** Respuesta completa del alta: quien acaba de registrar el dato tiene que ver lo que cargó. */
    record SepulturaCompletaResponse(
            Long id,
            String tipoParcela,
            String sector,
            String fila,
            String numero,
            String nombreDifunto,
            LocalDate fechaFallecimiento,
            LocalDate fechaInhumacion,
            String nombreTitular,
            String contactoTitular,
            String observaciones,
            String registradoPorNombre,
            String registradoPorEmail,
            Instant creadoEn) {

        static SepulturaCompletaResponse de(SepulturaEntity sepultura) {
            return new SepulturaCompletaResponse(
                    sepultura.getId(),
                    sepultura.getTipoParcela().name(),
                    sepultura.getSector(),
                    sepultura.getFila(),
                    sepultura.getNumero(),
                    sepultura.getNombreDifunto(),
                    sepultura.getFechaFallecimiento(),
                    sepultura.getFechaInhumacion(),
                    sepultura.getNombreTitular(),
                    sepultura.getContactoTitular(),
                    sepultura.getObservaciones(),
                    sepultura.getRegistradoPorNombre(),
                    sepultura.getRegistradoPorEmail(),
                    sepultura.getCreadoEn());
        }
    }

    /**
     * Respuesta pública y reducida del listado: sin los datos privados del
     * titular/contacto/observaciones ni de quién registró el dato (ver el
     * Javadoc de {@link #buscar}).
     */
    record SepulturaPublicaResponse(
            Long id,
            String tipoParcela,
            String sector,
            String fila,
            String numero,
            String nombreDifunto,
            LocalDate fechaFallecimiento,
            LocalDate fechaInhumacion,
            Instant creadoEn) {

        static SepulturaPublicaResponse de(SepulturaEntity sepultura) {
            return new SepulturaPublicaResponse(
                    sepultura.getId(),
                    sepultura.getTipoParcela().name(),
                    sepultura.getSector(),
                    sepultura.getFila(),
                    sepultura.getNumero(),
                    sepultura.getNombreDifunto(),
                    sepultura.getFechaFallecimiento(),
                    sepultura.getFechaInhumacion(),
                    sepultura.getCreadoEn());
        }
    }

    record ErrorResponse(String error) {
    }
}
