package ar.com.ciudaddigital.defensacivil.internal;

import java.time.Instant;
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
 * Alta protegida, lectura pública, finalización de alertas y cambio de
 * estado de recursos de Defensa Civil (ADR 0031).
 *
 * <p>Un único controller para las dos sub-rutas ({@code /alertas} y
 * {@code /recursos}) del mismo módulo, mismo patrón que
 * {@code DesarrolloSocialController}/{@code MesaDeEntradasController} que
 * agrupan varias sub-rutas cuando comparten prefijo. Ninguno de los dos
 * listados lleva {@code @PreAuthorize}: son las rutas que
 * {@code DescriptorDelModuloDefensaCivil} declara como
 * {@code rutasDeLecturaPublica()}, protegidas solo por el gating de
 * entitlement (ADR 0012 §1). Publicar/registrar y cambiar de estado sí
 * requieren sesión y el único permiso {@code defensacivil.gestionar}
 * (ADR 0031 §3).
 */
@RestController
@RequestMapping("/api/defensacivil")
class DefensaCivilController {

    private final GestionDeAlertas gestionDeAlertas;
    private final GestionDeRecursos gestionDeRecursos;

    DefensaCivilController(GestionDeAlertas gestionDeAlertas, GestionDeRecursos gestionDeRecursos) {
        this.gestionDeAlertas = gestionDeAlertas;
        this.gestionDeRecursos = gestionDeRecursos;
    }

    @PostMapping("/alertas")
    @PreAuthorize("hasAuthority('defensacivil.gestionar')")
    ResponseEntity<AlertaResponse> publicarAlerta(
            @RequestBody PublicarAlertaRequest request, Authentication autenticacion) {

        TipoDeAlerta tipo = tipoDeAlertaDe(request.tipo());
        NivelDeAlerta nivel = nivelDe(request.nivel());
        ActorAutenticado actor = actorDe(autenticacion);
        AlertaDeDefensaCivilEntity alerta = gestionDeAlertas.publicar(
                tipo, nivel, request.titulo(), request.descripcion(), request.recomendaciones(),
                request.zonaAfectada(), actor.nombre(), actor.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(AlertaResponse.de(alerta));
    }

    @GetMapping("/alertas")
    List<AlertaResponse> buscarAlertas(
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String nivel,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String q) {

        TipoDeAlerta tipoDeAlerta = tipo == null || tipo.isBlank() ? null : tipoDeAlertaDe(tipo);
        NivelDeAlerta nivelDeAlerta = nivel == null || nivel.isBlank() ? null : nivelDe(nivel);
        EstadoDeAlerta estadoDeAlerta = estado == null || estado.isBlank() ? null : estadoDeAlertaDe(estado);
        return gestionDeAlertas.buscar(tipoDeAlerta, nivelDeAlerta, estadoDeAlerta, q)
                .stream().map(AlertaResponse::de).toList();
    }

    @PatchMapping("/alertas/{id}/estado")
    @PreAuthorize("hasAuthority('defensacivil.gestionar')")
    AlertaResponse actualizarEstadoDeAlerta(@PathVariable Long id, @RequestBody ActualizarEstadoRequest request) {
        EstadoDeAlerta estadoNuevo = estadoDeAlertaDe(request.estadoNuevo());
        // La única transición válida es VIGENTE → FINALIZADA (ADR 0031 §4):
        // se valida acá que el pedido sea exactamente ese destino antes de
        // llamar a GestionDeAlertas#finalizar, que ya no recibe el estado
        // nuevo como parámetro porque no hay otro destino posible.
        if (estadoNuevo != EstadoDeAlerta.FINALIZADA) {
            throw new SolicitudInvalida("No se puede pasar a " + estadoNuevo + ".");
        }
        AlertaDeDefensaCivilEntity alerta = gestionDeAlertas.finalizar(id);
        return AlertaResponse.de(alerta);
    }

    @PostMapping("/recursos")
    @PreAuthorize("hasAuthority('defensacivil.gestionar')")
    ResponseEntity<RecursoResponse> registrarRecurso(
            @RequestBody RegistrarRecursoRequest request, Authentication autenticacion) {

        TipoDeRecurso tipo = tipoDeRecursoDe(request.tipo());
        ActorAutenticado actor = actorDe(autenticacion);
        RecursoDeDefensaCivilEntity recurso = gestionDeRecursos.registrar(
                tipo, request.nombre(), request.direccion(), request.capacidad(), request.telefonoContacto(),
                request.descripcion(), actor.nombre(), actor.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(RecursoResponse.de(recurso));
    }

    @GetMapping("/recursos")
    List<RecursoResponse> buscarRecursos(
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String q) {

        TipoDeRecurso tipoDeRecurso = tipo == null || tipo.isBlank() ? null : tipoDeRecursoDe(tipo);
        EstadoDeRecurso estadoDeRecurso = estado == null || estado.isBlank() ? null : estadoDeRecursoDe(estado);
        return gestionDeRecursos.buscar(tipoDeRecurso, estadoDeRecurso, q).stream().map(RecursoResponse::de).toList();
    }

    @PatchMapping("/recursos/{id}/estado")
    @PreAuthorize("hasAuthority('defensacivil.gestionar')")
    RecursoResponse actualizarEstadoDeRecurso(@PathVariable Long id, @RequestBody ActualizarEstadoRequest request) {
        EstadoDeRecurso estadoNuevo = estadoDeRecursoDe(request.estadoNuevo());
        RecursoDeDefensaCivilEntity recurso = gestionDeRecursos.actualizarEstado(id, estadoNuevo);
        return RecursoResponse.de(recurso);
    }

    private static ActorAutenticado actorDe(Authentication autenticacion) {
        if (autenticacion.getPrincipal() instanceof ActorAutenticado actor) {
            return actor;
        }
        // No debería pasar: el permiso ya exige sesión de acceso, así que el
        // principal siempre es un ActorAutenticado. Si no lo es, es un
        // problema del mecanismo de autenticación, no una solicitud
        // inválida del agente (mismo criterio que ObrasController#actorDe).
        throw new IllegalStateException("No hay un actor autenticado para firmar la operación.");
    }

    private static TipoDeAlerta tipoDeAlertaDe(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar un tipo de alerta.");
        }
        try {
            return TipoDeAlerta.valueOf(tipo);
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalida("El tipo de alerta '" + tipo + "' no existe.");
        }
    }

    private static NivelDeAlerta nivelDe(String nivel) {
        if (nivel == null || nivel.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar un nivel de alerta.");
        }
        try {
            return NivelDeAlerta.valueOf(nivel);
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalida("El nivel de alerta '" + nivel + "' no existe.");
        }
    }

    private static EstadoDeAlerta estadoDeAlertaDe(String estado) {
        if (estado == null || estado.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar un estado.");
        }
        try {
            return EstadoDeAlerta.valueOf(estado);
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalida("El estado '" + estado + "' no existe.");
        }
    }

    private static TipoDeRecurso tipoDeRecursoDe(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar un tipo de recurso.");
        }
        try {
            return TipoDeRecurso.valueOf(tipo);
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalida("El tipo de recurso '" + tipo + "' no existe.");
        }
    }

    private static EstadoDeRecurso estadoDeRecursoDe(String estado) {
        if (estado == null || estado.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar un estado.");
        }
        try {
            return EstadoDeRecurso.valueOf(estado);
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalida("El estado '" + estado + "' no existe.");
        }
    }

    @ExceptionHandler(SolicitudInvalida.class)
    ResponseEntity<ErrorResponse> solicitudInvalida(SolicitudInvalida e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    /** Un id de alerta que no existe (inventado o de otro municipio, ver ADR 0001) da 404 genérico. */
    @ExceptionHandler(AlertaNoEncontrada.class)
    ResponseEntity<ErrorResponse> alertaNoEncontrada(AlertaNoEncontrada e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("No encontramos esa alerta."));
    }

    /** Un id de recurso que no existe (inventado o de otro municipio, ver ADR 0001) da 404 genérico. */
    @ExceptionHandler(RecursoNoEncontrado.class)
    ResponseEntity<ErrorResponse> recursoNoEncontrado(RecursoNoEncontrado e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("No encontramos ese recurso."));
    }

    record PublicarAlertaRequest(
            String tipo, String nivel, String titulo, String descripcion, String recomendaciones,
            String zonaAfectada) {
    }

    record RegistrarRecursoRequest(
            String tipo, String nombre, String direccion, Integer capacidad, String telefonoContacto,
            String descripcion) {
    }

    record ActualizarEstadoRequest(String estadoNuevo) {
    }

    record AlertaResponse(
            Long id,
            String tipo,
            String nivel,
            String titulo,
            String descripcion,
            String recomendaciones,
            String zonaAfectada,
            String estado,
            String publicadoPorNombre,
            String publicadoPorEmail,
            Instant creadoEn,
            Instant actualizadoEn) {

        static AlertaResponse de(AlertaDeDefensaCivilEntity alerta) {
            return new AlertaResponse(
                    alerta.getId(),
                    alerta.getTipo().name(),
                    alerta.getNivel().name(),
                    alerta.getTitulo(),
                    alerta.getDescripcion(),
                    alerta.getRecomendaciones(),
                    alerta.getZonaAfectada(),
                    alerta.getEstado().name(),
                    alerta.getPublicadoPorNombre(),
                    alerta.getPublicadoPorEmail(),
                    alerta.getCreadoEn(),
                    alerta.getActualizadoEn());
        }
    }

    record RecursoResponse(
            Long id,
            String tipo,
            String nombre,
            String direccion,
            Integer capacidad,
            String telefonoContacto,
            String descripcion,
            String estado,
            String publicadoPorNombre,
            String publicadoPorEmail,
            Instant creadoEn,
            Instant actualizadoEn) {

        static RecursoResponse de(RecursoDeDefensaCivilEntity recurso) {
            return new RecursoResponse(
                    recurso.getId(),
                    recurso.getTipo().name(),
                    recurso.getNombre(),
                    recurso.getDireccion(),
                    recurso.getCapacidad(),
                    recurso.getTelefonoContacto(),
                    recurso.getDescripcion(),
                    recurso.getEstado().name(),
                    recurso.getPublicadoPorNombre(),
                    recurso.getPublicadoPorEmail(),
                    recurso.getCreadoEn(),
                    recurso.getActualizadoEn());
        }
    }

    record ErrorResponse(String error) {
    }
}
