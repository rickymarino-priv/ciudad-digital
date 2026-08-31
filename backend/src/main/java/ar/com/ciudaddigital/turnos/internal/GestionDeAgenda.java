package ar.com.ciudaddigital.turnos.internal;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta protegida, búsqueda pública y actualización de estado del catálogo
 * de actividades municipales del municipio del request en curso, más el
 * alta protegida y la búsqueda pública de sus franjas horarias (ADR 0026
 * §2/§3): publicar la agenda de una actividad es una única unidad de
 * trabajo administrativo, no dos servicios separados.
 */
@Service
class GestionDeAgenda {

    private static final int LARGO_MAXIMO_NOMBRE = 150;
    private static final int LARGO_MAXIMO_UBICACION = 300;
    private static final int LARGO_MAXIMO_PUBLICADO_POR_NOMBRE = 150;
    private static final int LARGO_MAXIMO_PUBLICADO_POR_EMAIL = 200;

    private final ActividadRepository actividades;
    private final FranjaHorariaRepository franjas;

    GestionDeAgenda(ActividadRepository actividades, FranjaHorariaRepository franjas) {
        this.actividades = actividades;
        this.franjas = franjas;
    }

    @Transactional("tenantTransactionManager")
    ActividadEntity publicarActividad(String nombre, TipoDeActividad tipo, String descripcion, String ubicacion,
            String publicadoPorNombre, String publicadoPorEmail) {

        if (nombre == null || nombre.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar el nombre de la actividad.");
        }
        if (nombre.length() > LARGO_MAXIMO_NOMBRE) {
            throw new SolicitudInvalida("El nombre no puede superar los " + LARGO_MAXIMO_NOMBRE + " caracteres.");
        }
        if (tipo == null) {
            throw new SolicitudInvalida("Hay que indicar el tipo de actividad.");
        }
        if (ubicacion == null || ubicacion.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar la ubicación de la actividad.");
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
                    "El nombre de quien publica no puede superar los "
                            + LARGO_MAXIMO_PUBLICADO_POR_NOMBRE + " caracteres.");
        }
        if (publicadoPorEmail != null && publicadoPorEmail.length() > LARGO_MAXIMO_PUBLICADO_POR_EMAIL) {
            throw new SolicitudInvalida(
                    "El correo de quien publica no puede superar los "
                            + LARGO_MAXIMO_PUBLICADO_POR_EMAIL + " caracteres.");
        }

        ActividadEntity actividad = ActividadEntity.publicar(
                nombre, tipo, descripcion, ubicacion, publicadoPorNombre, publicadoPorEmail);
        return actividades.save(actividad);
    }

    /**
     * {@code tipo} y {@code estado} ya vienen resueltos a su enum (o
     * {@code null} si no se pidió el filtro): un valor que no matchea
     * ningún literal del enum ya fue rechazado con 400 antes de llegar
     * acá, en el controller (mismo criterio que
     * {@code GestionDeObras#buscar}). {@code q} vacío o en blanco se
     * trata como "sin filtro de texto", no como una búsqueda del string
     * vacío.
     */
    List<ActividadEntity> buscarActividades(TipoDeActividad tipo, EstadoDeActividad estado, String q) {
        String patron = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase() + "%";
        return actividades.buscar(tipo, estado, patron);
    }

    @Transactional("tenantTransactionManager")
    ActividadEntity cambiarEstadoDeActividad(Long id, EstadoDeActividad estadoNuevo) {
        ActividadEntity actividad = actividades.findById(id)
                .orElseThrow(() -> new ActividadNoEncontrada("No existe la actividad " + id + "."));

        if (estadoNuevo == null) {
            throw new SolicitudInvalida("Hay que indicar el estado nuevo.");
        }

        // Ambos sentidos son válidos entre ACTIVA y INACTIVA (ADR 0026 §2): no
        // hace falta una tabla de transiciones, mismo criterio que
        // GestionDeProgramasSociales#cambiarEstado.
        actividad.actualizarEstado(estadoNuevo);
        return actividades.save(actividad);
    }

    @Transactional("tenantTransactionManager")
    FranjaHorariaEntity crearFranja(
            Long actividadId, LocalDate fecha, LocalTime horaInicio, LocalTime horaFin, Integer cupoTotal) {

        ActividadEntity actividad = actividades.findById(actividadId)
                .orElseThrow(() -> new ActividadNoEncontrada("No existe la actividad " + actividadId + "."));

        if (fecha == null) {
            throw new SolicitudInvalida("Hay que indicar la fecha de la franja.");
        }
        if (horaInicio == null) {
            throw new SolicitudInvalida("Hay que indicar la hora de inicio.");
        }
        if (horaFin == null) {
            throw new SolicitudInvalida("Hay que indicar la hora de fin.");
        }
        if (!horaFin.isAfter(horaInicio)) {
            throw new SolicitudInvalida("La hora de fin tiene que ser posterior a la hora de inicio.");
        }
        if (cupoTotal == null || cupoTotal <= 0) {
            throw new SolicitudInvalida("El cupo total tiene que ser mayor a cero.");
        }

        // No se valida acá que la actividad esté ACTIVA: eso se valida recién
        // al reservar (GestionDeReservas#reservar), no al publicar la franja
        // (ADR 0026 §3).
        FranjaHorariaEntity franja = FranjaHorariaEntity.crear(actividad.getId(), fecha, horaInicio, horaFin, cupoTotal);
        return franjas.save(franja);
    }

    List<FranjaHorariaEntity> buscarFranjas(Long actividadId) {
        if (actividadId == null) {
            throw new SolicitudInvalida("Hay que indicar la actividad.");
        }
        return franjas.findByActividadIdOrderByFechaAscHoraInicioAsc(actividadId);
    }
}
