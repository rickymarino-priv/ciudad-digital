package ar.com.ciudaddigital.desarrollosocial.internal;

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
import ar.com.ciudaddigital.desarrollosocial.internal.GestionDeInscripcionesSociales.InscripcionConPrograma;
import ar.com.ciudaddigital.desarrollosocial.internal.GestionDeInscripcionesSociales.InscripcionCreada;

/**
 * Catálogo protegido/público de programas sociales, alta pública de
 * inscripciones, seguimiento anónimo por token, y bandeja de gestión de
 * inscripciones (ADR 0025).
 *
 * <p>Publicar un programa y cambiar su estado requieren sesión y el
 * permiso {@code desarrollosocial.gestionarProgramas}. El listado de
 * programas no lleva {@code @PreAuthorize}: es la ruta que
 * {@code DescriptorDelModuloDesarrolloSocial} declara como
 * {@code rutasDeLecturaPublica()} (ADR 0012 §1). El alta de inscripciones
 * tampoco lleva {@code @PreAuthorize}: es la ruta que se declara como
 * {@code rutasDeEscrituraPublica()} (ADR 0025 §5), igual que la consulta
 * por token, declarada como lectura pública (ADR 0017 §4). Listar
 * inscripciones y cambiar su estado requieren sesión y el permiso
 * {@code desarrollosocial.revisarInscripciones} —deliberadamente
 * distinto de {@code gestionarProgramas} (ADR 0025 §7), no
 * {@code hasAnyAuthority} con ese otro permiso.
 */
@RestController
@RequestMapping("/api/desarrollosocial")
class DesarrolloSocialController {

    private final GestionDeProgramasSociales gestionDeProgramas;
    private final GestionDeInscripcionesSociales gestionDeInscripciones;

    DesarrolloSocialController(
            GestionDeProgramasSociales gestionDeProgramas, GestionDeInscripcionesSociales gestionDeInscripciones) {
        this.gestionDeProgramas = gestionDeProgramas;
        this.gestionDeInscripciones = gestionDeInscripciones;
    }

    @PostMapping("/programas")
    @PreAuthorize("hasAuthority('desarrollosocial.gestionarProgramas')")
    ResponseEntity<ProgramaSocialResponse> publicarPrograma(
            @RequestBody RegistrarProgramaRequest request, Authentication autenticacion) {

        ActorAutenticado actor = actorDe(autenticacion);
        ProgramaSocialEntity programa = gestionDeProgramas.publicar(
                request.nombre(), request.descripcion(), request.criteriosDeElegibilidad(),
                actor.nombre(), actor.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(ProgramaSocialResponse.de(programa));
    }

    @GetMapping("/programas")
    List<ProgramaSocialResponse> buscarProgramas(
            @RequestParam(required = false) String estado, @RequestParam(required = false) String q) {

        EstadoDePrograma estadoDePrograma = estado == null || estado.isBlank() ? null : estadoDePrograma(estado);
        return gestionDeProgramas.buscar(estadoDePrograma, q).stream().map(ProgramaSocialResponse::de).toList();
    }

    @PatchMapping("/programas/{id}/estado")
    @PreAuthorize("hasAuthority('desarrollosocial.gestionarProgramas')")
    ProgramaSocialResponse cambiarEstadoDePrograma(
            @PathVariable Long id, @RequestBody ActualizarEstadoDeProgramaRequest request) {

        EstadoDePrograma estadoNuevo = estadoDePrograma(request.estadoNuevo());
        ProgramaSocialEntity programa = gestionDeProgramas.cambiarEstado(id, estadoNuevo);
        return ProgramaSocialResponse.de(programa);
    }

    @PostMapping("/inscripciones")
    ResponseEntity<InscripcionPublicaResponse> inscribir(@RequestBody CrearInscripcionRequest request) {
        SituacionDeclarada situacionDeclarada = situacionDeclaradaDe(request.situacionDeclarada());
        InscripcionCreada creada = gestionDeInscripciones.inscribir(
                request.programaId(), request.nombreSolicitante(), request.dniSolicitante(), request.contacto(),
                request.cantidadIntegrantesGrupoFamiliar(), situacionDeclarada, request.comentarioAdicional());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(InscripcionPublicaResponse.de(creada.inscripcion(), creada.tokenDeSeguimiento()));
    }

    @GetMapping("/inscripciones/seguimiento/{token}")
    SeguimientoDeInscripcionResponse consultarPorToken(@PathVariable String token) {
        InscripcionConPrograma resultado = gestionDeInscripciones.consultarPorToken(token);
        return SeguimientoDeInscripcionResponse.de(resultado.inscripcion(), resultado.nombrePrograma());
    }

    @GetMapping("/inscripciones")
    @PreAuthorize("hasAuthority('desarrollosocial.revisarInscripciones')")
    List<InscripcionResponse> listarInscripcionesParaGestion(
            @RequestParam(required = false) Long programaId, @RequestParam(required = false) String estado) {

        EstadoDeInscripcion estadoDeInscripcion = estado == null || estado.isBlank() ? null : estadoDeInscripcion(estado);
        return gestionDeInscripciones.listarParaGestion(programaId, estadoDeInscripcion)
                .stream().map(InscripcionResponse::de).toList();
    }

    @PatchMapping("/inscripciones/{id}/estado")
    @PreAuthorize("hasAuthority('desarrollosocial.revisarInscripciones')")
    InscripcionResponse cambiarEstadoDeInscripcion(
            @PathVariable Long id, @RequestBody ActualizarEstadoDeInscripcionRequest request,
            Authentication autenticacion) {

        ActorAutenticado actor = actorDe(autenticacion);
        EstadoDeInscripcion estadoNuevo = estadoDeInscripcion(request.estadoNuevo());
        InscripcionSocialEntity inscripcion = gestionDeInscripciones.actualizarEstado(
                id, estadoNuevo, request.comentarioDeResolucion(), actor.nombre(), actor.email());
        return InscripcionResponse.de(inscripcion);
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

    private static EstadoDePrograma estadoDePrograma(String estado) {
        if (estado == null || estado.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar un estado.");
        }
        try {
            return EstadoDePrograma.valueOf(estado);
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalida("El estado '" + estado + "' no existe.");
        }
    }

    private static EstadoDeInscripcion estadoDeInscripcion(String estado) {
        if (estado == null || estado.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar un estado.");
        }
        try {
            return EstadoDeInscripcion.valueOf(estado);
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalida("El estado '" + estado + "' no existe.");
        }
    }

    private static SituacionDeclarada situacionDeclaradaDe(String situacionDeclarada) {
        if (situacionDeclarada == null || situacionDeclarada.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar la situación declarada.");
        }
        try {
            return SituacionDeclarada.valueOf(situacionDeclarada);
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalida("La situación declarada '" + situacionDeclarada + "' no existe.");
        }
    }

    @ExceptionHandler(SolicitudInvalida.class)
    ResponseEntity<ErrorResponse> solicitudInvalida(SolicitudInvalida e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    /** Un id de programa que no existe (inventado o de otro municipio, ver ADR 0001) da 404 genérico. */
    @ExceptionHandler(ProgramaNoEncontrado.class)
    ResponseEntity<ErrorResponse> programaNoEncontrado(ProgramaNoEncontrado e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("No encontramos ese programa."));
    }

    /** Un id de inscripción que no existe (inventado o de otro municipio, ver ADR 0001) da 404 genérico. */
    @ExceptionHandler(InscripcionNoEncontrada.class)
    ResponseEntity<ErrorResponse> inscripcionNoEncontrada(InscripcionNoEncontrada e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("No encontramos esa inscripción."));
    }

    /**
     * Mensaje genérico, siempre el mismo, sin importar si el token no
     * matchea ninguna fila o el string ni siquiera tiene forma de token
     * (ADR 0017 §4).
     */
    @ExceptionHandler(TokenNoEncontrado.class)
    ResponseEntity<ErrorResponse> tokenNoEncontrado(TokenNoEncontrado e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("No encontramos una inscripción con ese código."));
    }

    record RegistrarProgramaRequest(String nombre, String descripcion, String criteriosDeElegibilidad) {
    }

    record ActualizarEstadoDeProgramaRequest(String estadoNuevo) {
    }

    record CrearInscripcionRequest(
            Long programaId, String nombreSolicitante, String dniSolicitante, String contacto,
            Integer cantidadIntegrantesGrupoFamiliar, String situacionDeclarada, String comentarioAdicional) {
    }

    record ActualizarEstadoDeInscripcionRequest(String estadoNuevo, String comentarioDeResolucion) {
    }

    record ProgramaSocialResponse(
            Long id,
            String nombre,
            String descripcion,
            String criteriosDeElegibilidad,
            String estado,
            String publicadoPorNombre,
            String publicadoPorEmail,
            Instant creadoEn,
            Instant actualizadoEn) {

        static ProgramaSocialResponse de(ProgramaSocialEntity programa) {
            return new ProgramaSocialResponse(
                    programa.getId(),
                    programa.getNombre(),
                    programa.getDescripcion(),
                    programa.getCriteriosDeElegibilidad(),
                    programa.getEstado().name(),
                    programa.getPublicadoPorNombre(),
                    programa.getPublicadoPorEmail(),
                    programa.getCreadoEn(),
                    programa.getActualizadoEn());
        }
    }

    /**
     * Confirmación al vecino que se inscribió: deliberadamente sin
     * {@code nombreSolicitante}/{@code dniSolicitante}/{@code contacto}/
     * {@code cantidadIntegrantesGrupoFamiliar}/{@code situacionDeclarada}/
     * {@code comentarioAdicional} — no es una vista de gestión (ADR 0025
     * §6). {@code tokenDeSeguimiento} es la única vez en toda la vida de
     * la inscripción que ese valor viaja en claro (ADR 0017 §4): ni la
     * entidad ni ningún otro endpoint lo vuelven a exponer.
     */
    record InscripcionPublicaResponse(Long id, String estado, String tokenDeSeguimiento) {

        static InscripcionPublicaResponse de(InscripcionSocialEntity inscripcion, String tokenDeSeguimiento) {
            return new InscripcionPublicaResponse(inscripcion.getId(), inscripcion.getEstado().name(),
                    tokenDeSeguimiento);
        }
    }

    /**
     * Lo que ve el vecino que consulta con su token de seguimiento (ADR
     * 0025 §6): nombre del programa, estado, comentario de resolución si
     * ya se evaluó, y las marcas de tiempo. Deliberadamente sin
     * {@code nombreSolicitante}/{@code dniSolicitante}/{@code contacto}/
     * {@code cantidadIntegrantesGrupoFamiliar}/{@code situacionDeclarada}/
     * {@code comentarioAdicional}: son datos que el propio vecino ya
     * tiene, mismo criterio exacto que
     * {@code ReclamosController.SeguimientoDeReclamoResponse}.
     */
    record SeguimientoDeInscripcionResponse(
            Long id, String nombrePrograma, String estado, String comentarioDeResolucion,
            Instant creadoEn, Instant actualizadoEn) {

        static SeguimientoDeInscripcionResponse de(InscripcionSocialEntity inscripcion, String nombrePrograma) {
            return new SeguimientoDeInscripcionResponse(
                    inscripcion.getId(),
                    nombrePrograma,
                    inscripcion.getEstado().name(),
                    inscripcion.getComentarioDeResolucion(),
                    inscripcion.getCreadoEn(),
                    inscripcion.getActualizadoEn());
        }
    }

    /**
     * Shape completo, con todos los datos personales: la única vista que
     * los expone, y ya está detrás de {@code desarrollosocial.revisarInscripciones}
     * (ADR 0025 §7).
     */
    record InscripcionResponse(
            Long id,
            Long programaId,
            String nombreSolicitante,
            String dniSolicitante,
            String contacto,
            Integer cantidadIntegrantesGrupoFamiliar,
            String situacionDeclarada,
            String comentarioAdicional,
            String estado,
            String comentarioDeResolucion,
            String resueltoPorNombre,
            String resueltoPorEmail,
            Instant resueltoEn,
            Instant creadoEn,
            Instant actualizadoEn) {

        static InscripcionResponse de(InscripcionSocialEntity inscripcion) {
            return new InscripcionResponse(
                    inscripcion.getId(),
                    inscripcion.getProgramaId(),
                    inscripcion.getNombreSolicitante(),
                    inscripcion.getDniSolicitante(),
                    inscripcion.getContacto(),
                    inscripcion.getCantidadIntegrantesGrupoFamiliar(),
                    inscripcion.getSituacionDeclarada().name(),
                    inscripcion.getComentarioAdicional(),
                    inscripcion.getEstado().name(),
                    inscripcion.getComentarioDeResolucion(),
                    inscripcion.getResueltoPorNombre(),
                    inscripcion.getResueltoPorEmail(),
                    inscripcion.getResueltoEn(),
                    inscripcion.getCreadoEn(),
                    inscripcion.getActualizadoEn());
        }
    }

    record ErrorResponse(String error) {
    }
}
