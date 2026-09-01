package ar.com.ciudaddigital.eventos.internal;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta protegida, búsqueda pública y cancelación de los eventos de la
 * agenda del municipio del request en curso (ADR 0030).
 */
@Service
class GestionDeEventos {

    private static final int LARGO_MAXIMO_NOMBRE = 200;
    private static final int LARGO_MAXIMO_UBICACION = 300;
    private static final int LARGO_MAXIMO_PUBLICADO_POR_NOMBRE = 150;
    private static final int LARGO_MAXIMO_PUBLICADO_POR_EMAIL = 200;

    private final EventoRepository eventos;

    GestionDeEventos(EventoRepository eventos) {
        this.eventos = eventos;
    }

    @Transactional("tenantTransactionManager")
    EventoEntity publicar(String nombre, CategoriaDeEvento categoria, String ubicacion, String descripcion,
            LocalDate fechaInicio, LocalDate fechaFin, LocalTime horaInicio,
            String publicadoPorNombre, String publicadoPorEmail) {

        if (nombre == null || nombre.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar el nombre del evento.");
        }
        if (nombre.length() > LARGO_MAXIMO_NOMBRE) {
            throw new SolicitudInvalida("El nombre no puede superar los " + LARGO_MAXIMO_NOMBRE + " caracteres.");
        }
        if (categoria == null) {
            throw new SolicitudInvalida("Hay que indicar una categoría de evento.");
        }
        if (ubicacion == null || ubicacion.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar la ubicación del evento.");
        }
        if (ubicacion.length() > LARGO_MAXIMO_UBICACION) {
            throw new SolicitudInvalida(
                    "La ubicación no puede superar los " + LARGO_MAXIMO_UBICACION + " caracteres.");
        }
        if (fechaInicio == null) {
            throw new SolicitudInvalida("Hay que indicar la fecha de inicio del evento.");
        }
        if (fechaFin != null && fechaFin.isBefore(fechaInicio)) {
            throw new SolicitudInvalida("La fecha de fin no puede ser anterior a la fecha de inicio.");
        }
        // publicadoPorNombre/publicadoPorEmail salen del actor autenticado, no de la
        // solicitud: si alguno faltara sería un problema del mecanismo de
        // autenticación, no una solicitud inválida del agente (mismo criterio que
        // GestionDeEspaciosVerdes#registrar/GestionDeObras#registrar).
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

        EventoEntity evento = EventoEntity.publicar(
                nombre, categoria, ubicacion, descripcion, fechaInicio, fechaFin, horaInicio,
                publicadoPorNombre, publicadoPorEmail);
        return eventos.save(evento);
    }

    /**
     * {@code categoria}/{@code estado} ya vienen resueltos a su enum (o
     * {@code null} si no se pidió el filtro): un valor que no matchea
     * ningún literal del enum ya fue rechazado con 400 antes de llegar
     * acá, en el controller (ADR 0030 §5) — no se trata como "sin filtro".
     * {@code q} vacío o en blanco se trata como "sin filtro de texto", no
     * como una búsqueda del string vacío (mismo criterio que
     * {@code GestionDeEspaciosVerdes#buscar}).
     */
    List<EventoEntity> buscar(CategoriaDeEvento categoria, EstadoDeEvento estado, String q) {
        String patron = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase() + "%";
        return eventos.buscar(categoria, estado, patron);
    }

    /**
     * La única transición válida es {@code PROGRAMADO → CANCELADO} (ADR
     * 0030 §3): un solo salto sin retorno, así que alcanza con este chequeo
     * directo, sin una tabla de transiciones genérica como en
     * {@code GestionDeObras}/{@code GestionDeArbolado}/
     * {@code GestionDeEspaciosVerdes} (ADR 0030 §7).
     */
    @Transactional("tenantTransactionManager")
    EventoEntity cancelar(Long id) {
        EventoEntity evento = eventos.findById(id)
                .orElseThrow(() -> new EventoNoEncontrado("No existe el evento " + id + "."));

        if (evento.getEstado() != EstadoDeEvento.PROGRAMADO) {
            throw new SolicitudInvalida(
                    "No se puede cancelar un evento en estado " + evento.getEstado() + ".");
        }

        evento.cancelar();
        return eventos.save(evento);
    }
}
