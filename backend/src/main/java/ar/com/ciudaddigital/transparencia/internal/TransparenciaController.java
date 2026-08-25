package ar.com.ciudaddigital.transparencia.internal;

import java.math.BigDecimal;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.com.ciudaddigital.acceso.ActorAutenticado;

/**
 * Publicación protegida y búsqueda pública de partidas presupuestarias y
 * entradas de escala salarial de Transparencia Activa (backlog R11).
 *
 * <p>Los dos listados ({@code GET /api/transparencia/presupuesto},
 * {@code GET /api/transparencia/sueldos}) no llevan {@code @PreAuthorize}:
 * son las rutas que {@code DescriptorDelModuloTransparencia} declara como
 * {@code rutasDeLecturaPublica()}, protegidas solo por el gating de
 * entitlement y el {@code permitAll()} de {@code GET} que arma la cadena de
 * seguridad a partir de esa declaración (ADR 0012 §1) — mismo mecanismo que
 * {@code BoletinController}/{@code CementerioController}. Publicar sí
 * requiere sesión y el permiso {@code transparencia.publicar}.
 *
 * <p>Un único DTO de salida por recurso, sin versión reducida: a
 * diferencia de {@code cementerio}, ninguno de los dos recursos tiene un
 * dato de tercero que ocultar en la respuesta pública (escala salarial ya
 * nace sin columna de persona, mismo criterio que {@code NormaResponse} en
 * {@code boletin}).
 */
@RestController
@RequestMapping("/api/transparencia")
class TransparenciaController {

    private final GestionDeTransparencia gestion;

    TransparenciaController(GestionDeTransparencia gestion) {
        this.gestion = gestion;
    }

    @PostMapping("/presupuesto")
    @PreAuthorize("hasAuthority('transparencia.publicar')")
    ResponseEntity<PartidaPresupuestariaResponse> publicarPartida(
            @RequestBody PublicarPartidaRequest request, Authentication autenticacion) {

        ActorAutenticado actor = actorDe(autenticacion);
        PartidaPresupuestariaEntity partida = gestion.publicarPartida(request.anio(), request.area(),
                request.numeroPartida(), request.concepto(), request.montoAsignado(), request.montoEjecutado(),
                actor.nombre(), actor.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(PartidaPresupuestariaResponse.de(partida));
    }

    @GetMapping("/presupuesto")
    List<PartidaPresupuestariaResponse> buscarPartidas(
            @RequestParam(required = false) Integer anio, @RequestParam(required = false) String q) {

        return gestion.buscarPartidas(anio, q).stream().map(PartidaPresupuestariaResponse::de).toList();
    }

    @PostMapping("/sueldos")
    @PreAuthorize("hasAuthority('transparencia.publicar')")
    ResponseEntity<EscalaSalarialResponse> publicarCargo(
            @RequestBody PublicarCargoRequest request, Authentication autenticacion) {

        ActorAutenticado actor = actorDe(autenticacion);
        EscalaSalarialEntity escala = gestion.publicarCargo(request.anio(), request.area(), request.cargo(),
                request.cantidadCargos(), request.montoBrutoMensual(), actor.nombre(), actor.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(EscalaSalarialResponse.de(escala));
    }

    @GetMapping("/sueldos")
    List<EscalaSalarialResponse> buscarCargos(
            @RequestParam(required = false) Integer anio, @RequestParam(required = false) String q) {

        return gestion.buscarCargos(anio, q).stream().map(EscalaSalarialResponse::de).toList();
    }

    @ExceptionHandler(SolicitudInvalida.class)
    ResponseEntity<ErrorResponse> solicitudInvalida(SolicitudInvalida e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    private static ActorAutenticado actorDe(Authentication autenticacion) {
        // No debería pasar: el permiso ya exige sesión de acceso, así que el
        // principal siempre es un ActorAutenticado. Si no lo es, es un
        // problema del mecanismo de autenticación, no una solicitud
        // inválida del agente.
        if (autenticacion.getPrincipal() instanceof ActorAutenticado actor) {
            return actor;
        }
        throw new IllegalStateException("No hay un actor autenticado para firmar la publicación.");
    }

    record PublicarPartidaRequest(
            Integer anio,
            String area,
            String numeroPartida,
            String concepto,
            BigDecimal montoAsignado,
            BigDecimal montoEjecutado) {
    }

    record PartidaPresupuestariaResponse(
            Long id,
            Integer anio,
            String area,
            String numeroPartida,
            String concepto,
            BigDecimal montoAsignado,
            BigDecimal montoEjecutado,
            String publicadoPorNombre,
            String publicadoPorEmail,
            Instant creadoEn) {

        static PartidaPresupuestariaResponse de(PartidaPresupuestariaEntity partida) {
            return new PartidaPresupuestariaResponse(
                    partida.getId(),
                    partida.getAnio(),
                    partida.getArea(),
                    partida.getNumeroPartida(),
                    partida.getConcepto(),
                    partida.getMontoAsignado(),
                    partida.getMontoEjecutado(),
                    partida.getPublicadoPorNombre(),
                    partida.getPublicadoPorEmail(),
                    partida.getCreadoEn());
        }
    }

    record PublicarCargoRequest(
            Integer anio, String area, String cargo, Integer cantidadCargos, BigDecimal montoBrutoMensual) {
    }

    /** Sin ningún campo de nombre de persona a propósito: ver el Javadoc de {@link EscalaSalarialEntity}. */
    record EscalaSalarialResponse(
            Long id,
            Integer anio,
            String area,
            String cargo,
            Integer cantidadCargos,
            BigDecimal montoBrutoMensual,
            String publicadoPorNombre,
            String publicadoPorEmail,
            Instant creadoEn) {

        static EscalaSalarialResponse de(EscalaSalarialEntity escala) {
            return new EscalaSalarialResponse(
                    escala.getId(),
                    escala.getAnio(),
                    escala.getArea(),
                    escala.getCargo(),
                    escala.getCantidadCargos(),
                    escala.getMontoBrutoMensual(),
                    escala.getPublicadoPorNombre(),
                    escala.getPublicadoPorEmail(),
                    escala.getCreadoEn());
        }
    }

    record ErrorResponse(String error) {
    }
}
