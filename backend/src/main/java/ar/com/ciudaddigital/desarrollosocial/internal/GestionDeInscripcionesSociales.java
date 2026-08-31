package ar.com.ciudaddigital.desarrollosocial.internal;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.com.ciudaddigital.seguimientoanonimo.TokenDeSeguimiento;

/**
 * Alta pública, consulta anónima por token, y bandeja de gestión
 * (listado y cambio de estado) de las inscripciones a programas sociales
 * del municipio del request en curso (ADR 0025 §4/§5/§6/§8).
 */
@Service
class GestionDeInscripcionesSociales {

    private static final int LARGO_MAXIMO_NOMBRE_SOLICITANTE = 150;
    private static final int LARGO_MAXIMO_DNI_SOLICITANTE = 20;
    private static final int LARGO_MAXIMO_CONTACTO = 200;
    private static final int LARGO_MAXIMO_COMENTARIO_ADICIONAL = 2000;
    private static final int LARGO_MAXIMO_COMENTARIO_DE_RESOLUCION = 2000;
    private static final int LARGO_MAXIMO_RESUELTO_POR_NOMBRE = 150;
    private static final int LARGO_MAXIMO_RESUELTO_POR_EMAIL = 200;

    /**
     * Mensaje único para "no existe" y "existe pero está cerrado" (ADR
     * 0025 §5): no le da a quien prueba ids al azar más información de la
     * necesaria, mismo espíritu que ADR 0017 §4 aplica al token.
     */
    private static final String MENSAJE_PROGRAMA_NO_DISPONIBLE =
            "El programa no existe o no admite inscripciones en este momento.";

    /**
     * Transiciones válidas del ciclo de vida fijo de una inscripción (ADR
     * 0025 §8): tabla codificada acá, no en la entidad ni en un motor
     * genérico de workflow, mismo criterio que {@code GestionDeObras}/
     * {@code GestionDeArbolado}.
     */
    private static final Map<EstadoDeInscripcion, Set<EstadoDeInscripcion>> TRANSICIONES_VALIDAS =
            new EnumMap<>(Map.of(
                    EstadoDeInscripcion.RECIBIDA, EnumSet.of(EstadoDeInscripcion.EN_EVALUACION),
                    EstadoDeInscripcion.EN_EVALUACION,
                    EnumSet.of(EstadoDeInscripcion.APROBADA, EstadoDeInscripcion.RECHAZADA),
                    EstadoDeInscripcion.APROBADA, EnumSet.noneOf(EstadoDeInscripcion.class),
                    EstadoDeInscripcion.RECHAZADA, EnumSet.noneOf(EstadoDeInscripcion.class)));

    private final InscripcionSocialRepository inscripciones;
    private final ProgramaSocialRepository programas;

    GestionDeInscripcionesSociales(InscripcionSocialRepository inscripciones, ProgramaSocialRepository programas) {
        this.inscripciones = inscripciones;
        this.programas = programas;
    }

    @Transactional("tenantTransactionManager")
    InscripcionCreada inscribir(Long programaId, String nombreSolicitante, String dniSolicitante, String contacto,
            Integer cantidadIntegrantesGrupoFamiliar, SituacionDeclarada situacionDeclarada,
            String comentarioAdicional) {

        if (programaId == null) {
            throw new SolicitudInvalida("Hay que indicar el programa.");
        }
        if (nombreSolicitante == null || nombreSolicitante.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar el nombre y apellido del solicitante.");
        }
        if (nombreSolicitante.length() > LARGO_MAXIMO_NOMBRE_SOLICITANTE) {
            throw new SolicitudInvalida(
                    "El nombre no puede superar los " + LARGO_MAXIMO_NOMBRE_SOLICITANTE + " caracteres.");
        }
        if (dniSolicitante == null || dniSolicitante.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar el DNI del solicitante.");
        }
        if (dniSolicitante.length() > LARGO_MAXIMO_DNI_SOLICITANTE) {
            throw new SolicitudInvalida(
                    "El DNI no puede superar los " + LARGO_MAXIMO_DNI_SOLICITANTE + " caracteres.");
        }
        if (contacto == null || contacto.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar un contacto.");
        }
        if (contacto.length() > LARGO_MAXIMO_CONTACTO) {
            throw new SolicitudInvalida("El contacto no puede superar los " + LARGO_MAXIMO_CONTACTO + " caracteres.");
        }
        if (cantidadIntegrantesGrupoFamiliar == null || cantidadIntegrantesGrupoFamiliar <= 0) {
            throw new SolicitudInvalida(
                    "La cantidad de integrantes del grupo familiar tiene que ser mayor a cero.");
        }
        if (situacionDeclarada == null) {
            throw new SolicitudInvalida("Hay que indicar la situación declarada.");
        }
        if (comentarioAdicional != null && comentarioAdicional.length() > LARGO_MAXIMO_COMENTARIO_ADICIONAL) {
            throw new SolicitudInvalida(
                    "El comentario adicional no puede superar los "
                            + LARGO_MAXIMO_COMENTARIO_ADICIONAL + " caracteres.");
        }

        ProgramaSocialEntity programa = programas.findById(programaId).orElse(null);
        if (programa == null || programa.getEstado() != EstadoDePrograma.ABIERTO) {
            throw new SolicitudInvalida(MENSAJE_PROGRAMA_NO_DISPONIBLE);
        }

        // El token en claro solo existe acá, entre que se genera y que el record
        // de retorno lo lleva hasta el controller (ADR 0017 §4): ni la entidad
        // ni el repositorio lo vuelven a ver.
        String tokenDeSeguimiento = TokenDeSeguimiento.generar();
        InscripcionSocialEntity inscripcion = InscripcionSocialEntity.inscribir(
                programaId, nombreSolicitante, dniSolicitante, contacto, cantidadIntegrantesGrupoFamiliar,
                situacionDeclarada, comentarioAdicional, TokenDeSeguimiento.hash(tokenDeSeguimiento));
        return new InscripcionCreada(inscripciones.save(inscripcion), tokenDeSeguimiento);
    }

    /**
     * Consulta anónima por posesión del token (ADR 0017 §4, ADR 0025 §6):
     * un {@code token} vacío se trata igual que "no encontrado", nunca
     * como {@link SolicitudInvalida}. El nombre del programa se resuelve
     * acá, no en el controller, para que el controller no tenga que tocar
     * {@link ProgramaSocialRepository} por su cuenta.
     */
    InscripcionConPrograma consultarPorToken(String token) {
        if (token == null || token.isBlank()) {
            throw new TokenNoEncontrado("No encontramos una inscripción con ese código.");
        }

        InscripcionSocialEntity inscripcion = inscripciones.findByTokenHash(TokenDeSeguimiento.hash(token))
                .orElseThrow(() -> new TokenNoEncontrado("No encontramos una inscripción con ese código."));

        // El programa siempre existe: una inscripción nunca se crea sin uno
        // (inscribir() ya lo valida), y ni Programa ni Inscripción tienen
        // borrado en esta rebanada.
        String nombrePrograma = programas.findById(inscripcion.getProgramaId())
                .map(ProgramaSocialEntity::getNombre)
                .orElse(null);
        return new InscripcionConPrograma(inscripcion, nombrePrograma);
    }

    /**
     * Todas las inscripciones del municipio, para la bandeja de gestión
     * (ADR 0025 §6/§7): sin lectura pública equivalente, solo la usa quien
     * tiene {@code desarrollosocial.revisarInscripciones}.
     */
    List<InscripcionSocialEntity> listarParaGestion(Long programaId, EstadoDeInscripcion estado) {
        return inscripciones.listarParaGestion(programaId, estado);
    }

    @Transactional("tenantTransactionManager")
    InscripcionSocialEntity actualizarEstado(Long id, EstadoDeInscripcion estadoNuevo, String comentarioDeResolucion,
            String resueltoPorNombre, String resueltoPorEmail) {

        InscripcionSocialEntity inscripcion = inscripciones.findById(id)
                .orElseThrow(() -> new InscripcionNoEncontrada("No existe la inscripción " + id + "."));

        if (estadoNuevo == null) {
            throw new SolicitudInvalida("Hay que indicar el estado nuevo.");
        }

        EstadoDeInscripcion estadoActual = inscripcion.getEstado();
        if (!TRANSICIONES_VALIDAS.get(estadoActual).contains(estadoNuevo)) {
            throw new SolicitudInvalida("No se puede pasar de " + estadoActual + " a " + estadoNuevo + ".");
        }

        // Aprobar o rechazar exige dejar constancia de por qué (ADR 0025 §8,
        // mismo criterio que GestionDeMultas#resolverDescargo); marcar
        // EN_EVALUACION no exige comentario, solo deja constancia de que
        // alguien empezó a revisar.
        boolean esResolucionTerminal =
                estadoNuevo == EstadoDeInscripcion.APROBADA || estadoNuevo == EstadoDeInscripcion.RECHAZADA;
        if (esResolucionTerminal && (comentarioDeResolucion == null || comentarioDeResolucion.isBlank())) {
            throw new SolicitudInvalida("Hay que indicar un comentario de la resolución.");
        }
        if (comentarioDeResolucion != null && comentarioDeResolucion.length() > LARGO_MAXIMO_COMENTARIO_DE_RESOLUCION) {
            throw new SolicitudInvalida(
                    "El comentario no puede superar los " + LARGO_MAXIMO_COMENTARIO_DE_RESOLUCION + " caracteres.");
        }
        if (resueltoPorNombre != null && resueltoPorNombre.length() > LARGO_MAXIMO_RESUELTO_POR_NOMBRE) {
            throw new SolicitudInvalida(
                    "El nombre de quien resuelve no puede superar los "
                            + LARGO_MAXIMO_RESUELTO_POR_NOMBRE + " caracteres.");
        }
        if (resueltoPorEmail != null && resueltoPorEmail.length() > LARGO_MAXIMO_RESUELTO_POR_EMAIL) {
            throw new SolicitudInvalida(
                    "El correo de quien resuelve no puede superar los "
                            + LARGO_MAXIMO_RESUELTO_POR_EMAIL + " caracteres.");
        }

        inscripcion.actualizarEstado(estadoNuevo, comentarioDeResolucion, resueltoPorNombre, resueltoPorEmail);
        return inscripciones.save(inscripcion);
    }

    /**
     * Resultado del alta: además de la inscripción, el token en claro
     * para que el controller lo devuelva en la respuesta HTTP —la única
     * vez que existe fuera de este método— sin forzarlo a volver a tocar
     * el servicio (mismo patrón que {@code GestionDeReclamos.ReclamoCreado}).
     */
    record InscripcionCreada(InscripcionSocialEntity inscripcion, String tokenDeSeguimiento) {
    }

    /**
     * Resultado de la consulta por token: la inscripción más el nombre de
     * su programa, ya resuelto acá (ADR 0025 §6) para que
     * {@code SeguimientoDeInscripcionResponse} no necesite volver a tocar
     * el repositorio de programas.
     */
    record InscripcionConPrograma(InscripcionSocialEntity inscripcion, String nombrePrograma) {
    }
}
