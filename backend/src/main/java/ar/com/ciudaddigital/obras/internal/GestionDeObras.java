package ar.com.ciudaddigital.obras.internal;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta protegida, búsqueda pública y actualización de estado de las obras
 * públicas del municipio del request en curso (ADR 0023).
 */
@Service
class GestionDeObras {

    private static final int LARGO_MAXIMO_NOMBRE = 200;
    private static final int LARGO_MAXIMO_UBICACION = 300;
    private static final int LARGO_MAXIMO_PUBLICADO_POR_NOMBRE = 150;
    private static final int LARGO_MAXIMO_PUBLICADO_POR_EMAIL = 200;

    /**
     * Transiciones válidas del ciclo de vida fijo de una obra (ADR 0023
     * §3): tabla codificada acá, no en la entidad ni en un motor genérico
     * de workflow, mismo criterio que {@code GestionDeReclamos}.
     */
    private static final Map<EstadoDeObra, Set<EstadoDeObra>> TRANSICIONES_VALIDAS =
            new EnumMap<>(Map.of(
                    EstadoDeObra.PLANIFICADA, EnumSet.of(EstadoDeObra.EN_EJECUCION),
                    EstadoDeObra.EN_EJECUCION, EnumSet.of(EstadoDeObra.PARALIZADA, EstadoDeObra.FINALIZADA),
                    EstadoDeObra.PARALIZADA, EnumSet.of(EstadoDeObra.EN_EJECUCION),
                    EstadoDeObra.FINALIZADA, EnumSet.noneOf(EstadoDeObra.class)));

    private final ObraPublicaRepository obras;

    GestionDeObras(ObraPublicaRepository obras) {
        this.obras = obras;
    }

    @Transactional("tenantTransactionManager")
    ObraPublicaEntity registrar(String nombre, TipoDeObra tipo, String ubicacion, String descripcion,
            LocalDate fechaInicioEstimada, LocalDate fechaFinEstimada,
            String publicadoPorNombre, String publicadoPorEmail) {

        if (nombre == null || nombre.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar el nombre de la obra.");
        }
        if (nombre.length() > LARGO_MAXIMO_NOMBRE) {
            throw new SolicitudInvalida("El nombre no puede superar los " + LARGO_MAXIMO_NOMBRE + " caracteres.");
        }
        if (tipo == null) {
            throw new SolicitudInvalida("Hay que indicar un tipo de obra.");
        }
        if (ubicacion == null || ubicacion.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar la ubicación de la obra.");
        }
        if (ubicacion.length() > LARGO_MAXIMO_UBICACION) {
            throw new SolicitudInvalida(
                    "La ubicación no puede superar los " + LARGO_MAXIMO_UBICACION + " caracteres.");
        }
        if (fechaInicioEstimada != null && fechaFinEstimada != null
                && fechaFinEstimada.isBefore(fechaInicioEstimada)) {
            throw new SolicitudInvalida(
                    "La fecha estimada de fin no puede ser anterior a la fecha estimada de inicio.");
        }
        // publicadoPorNombre/publicadoPorEmail salen del actor autenticado, no de la
        // solicitud: si alguno faltara sería un problema del mecanismo de
        // autenticación, no una solicitud inválida del agente (mismo criterio que
        // GestionDelBoletin#publicar).
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

        ObraPublicaEntity obra = ObraPublicaEntity.registrar(nombre, tipo, ubicacion, descripcion,
                fechaInicioEstimada, fechaFinEstimada, publicadoPorNombre, publicadoPorEmail);
        return obras.save(obra);
    }

    /**
     * {@code estado}/{@code tipo} ya vienen resueltos a su enum (o
     * {@code null} si no se pidió el filtro): un valor que no matchea
     * ningún literal del enum ya fue rechazado con 400 antes de llegar
     * acá, en el controller (ADR 0023, Tarea 1) — no se trata como "sin
     * filtro". {@code q} vacío o en blanco se trata como "sin filtro de
     * texto", no como una búsqueda del string vacío (mismo criterio que
     * {@code GestionDelBoletin#buscar}).
     */
    List<ObraPublicaEntity> buscar(EstadoDeObra estado, TipoDeObra tipo, String q) {
        String patron = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase() + "%";
        return obras.buscar(estado, tipo, patron);
    }

    @Transactional("tenantTransactionManager")
    ObraPublicaEntity actualizarEstado(Long id, EstadoDeObra estadoNuevo) {
        ObraPublicaEntity obra = obras.findById(id)
                .orElseThrow(() -> new ObraNoEncontrada("No existe la obra " + id + "."));

        if (estadoNuevo == null) {
            throw new SolicitudInvalida("Hay que indicar el estado nuevo.");
        }

        EstadoDeObra estadoActual = obra.getEstado();
        if (!TRANSICIONES_VALIDAS.get(estadoActual).contains(estadoNuevo)) {
            throw new SolicitudInvalida("No se puede pasar de " + estadoActual + " a " + estadoNuevo + ".");
        }

        obra.actualizarEstado(estadoNuevo);
        return obras.save(obra);
    }
}
