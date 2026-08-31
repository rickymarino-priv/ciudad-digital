package ar.com.ciudaddigital.educacion.internal;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta protegida, búsqueda pública y actualización de estado de las
 * instituciones educativas municipales del municipio del request en curso
 * (ADR 0028).
 */
@Service
class GestionDeEducacion {

    private static final int LARGO_MAXIMO_NOMBRE = 200;
    private static final int LARGO_MAXIMO_UBICACION = 300;
    private static final int LARGO_MAXIMO_PUBLICADO_POR_NOMBRE = 150;
    private static final int LARGO_MAXIMO_PUBLICADO_POR_EMAIL = 200;

    /**
     * Transiciones válidas del ciclo de vida fijo de una institución (ADR
     * 0028 §4): tabla codificada acá, no en la entidad ni en un motor
     * genérico de workflow, mismo patrón que
     * {@code GestionDeObras}/{@code GestionDeArbolado} — sin reutilizar
     * código de ninguno de los dos, `educacion` no depende de esos módulos
     * (ADR 0028 §1/Contexto).
     */
    private static final Map<EstadoDeInstitucion, Set<EstadoDeInstitucion>> TRANSICIONES_VALIDAS =
            new EnumMap<>(Map.of(
                    EstadoDeInstitucion.ACTIVA, EnumSet.of(EstadoDeInstitucion.CERRADA_TEMPORALMENTE),
                    EstadoDeInstitucion.CERRADA_TEMPORALMENTE,
                    EnumSet.of(EstadoDeInstitucion.ACTIVA, EstadoDeInstitucion.CERRADA_DEFINITIVAMENTE),
                    EstadoDeInstitucion.CERRADA_DEFINITIVAMENTE, EnumSet.noneOf(EstadoDeInstitucion.class)));

    private final InstitucionEducativaRepository instituciones;

    GestionDeEducacion(InstitucionEducativaRepository instituciones) {
        this.instituciones = instituciones;
    }

    @Transactional("tenantTransactionManager")
    InstitucionEducativaEntity registrar(String nombre, TipoDeInstitucionEducativa tipo, String ubicacion,
            String descripcion, String publicadoPorNombre, String publicadoPorEmail) {

        if (nombre == null || nombre.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar el nombre de la institución.");
        }
        if (nombre.length() > LARGO_MAXIMO_NOMBRE) {
            throw new SolicitudInvalida("El nombre no puede superar los " + LARGO_MAXIMO_NOMBRE + " caracteres.");
        }
        if (tipo == null) {
            throw new SolicitudInvalida("Hay que indicar un tipo de institución.");
        }
        if (ubicacion == null || ubicacion.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar la ubicación de la institución.");
        }
        if (ubicacion.length() > LARGO_MAXIMO_UBICACION) {
            throw new SolicitudInvalida(
                    "La ubicación no puede superar los " + LARGO_MAXIMO_UBICACION + " caracteres.");
        }
        // publicadoPorNombre/publicadoPorEmail salen del actor autenticado, no de la
        // solicitud: si alguno faltara sería un problema del mecanismo de
        // autenticación, no una solicitud inválida del agente (mismo criterio que
        // GestionDeObras#registrar).
        if (publicadoPorNombre != null && publicadoPorNombre.length() > LARGO_MAXIMO_PUBLICADO_POR_NOMBRE) {
            throw new SolicitudInvalida(
                    "El nombre de quien registra no puede superar los "
                            + LARGO_MAXIMO_PUBLICADO_POR_NOMBRE + " caracteres.");
        }
        if (publicadoPorEmail != null && publicadoPorEmail.length() > LARGO_MAXIMO_PUBLICADO_POR_EMAIL) {
            throw new SolicitudInvalida(
                    "El correo de quien registra no puede superar los "
                            + LARGO_MAXIMO_PUBLICADO_POR_EMAIL + " caracteres.");
        }

        InstitucionEducativaEntity institucion = InstitucionEducativaEntity.registrar(
                nombre, tipo, ubicacion, descripcion, publicadoPorNombre, publicadoPorEmail);
        return instituciones.save(institucion);
    }

    /**
     * {@code estado}/{@code tipo} ya vienen resueltos a su enum (o
     * {@code null} si no se pidió el filtro): un valor que no matchea
     * ningún literal del enum ya fue rechazado con 400 antes de llegar
     * acá, en el controller — no se trata como "sin filtro". {@code q}
     * vacío o en blanco se trata como "sin filtro de texto", no como una
     * búsqueda del string vacío (mismo criterio que
     * {@code GestionDeObras#buscar}).
     */
    List<InstitucionEducativaEntity> buscar(EstadoDeInstitucion estado, TipoDeInstitucionEducativa tipo, String q) {
        String patron = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase() + "%";
        return instituciones.buscar(estado, tipo, patron);
    }

    @Transactional("tenantTransactionManager")
    InstitucionEducativaEntity actualizarEstado(Long id, EstadoDeInstitucion estadoNuevo) {
        InstitucionEducativaEntity institucion = instituciones.findById(id)
                .orElseThrow(() -> new InstitucionEducativaNoEncontrada("No existe la institución " + id + "."));

        if (estadoNuevo == null) {
            throw new SolicitudInvalida("Hay que indicar el estado nuevo.");
        }

        EstadoDeInstitucion estadoActual = institucion.getEstado();
        if (!TRANSICIONES_VALIDAS.get(estadoActual).contains(estadoNuevo)) {
            throw new SolicitudInvalida("No se puede pasar de " + estadoActual + " a " + estadoNuevo + ".");
        }

        institucion.actualizarEstado(estadoNuevo);
        return instituciones.save(institucion);
    }
}
