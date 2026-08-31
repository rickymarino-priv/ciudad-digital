package ar.com.ciudaddigital.turnos.internal;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reserva pública anónima de un turno, y bandeja de gestión (listado) de
 * las reservas de una franja, del municipio del request en curso (ADR
 * 0026 §4/§5).
 *
 * <p>Servicio propio, separado de {@code GestionDeAgenda}: este atiende
 * escritura pública anónima (un vecino sin sesión reservando un lugar),
 * no gestión administrativa del municipio.
 */
@Service
class GestionDeReservas {

    private static final int LARGO_MAXIMO_NOMBRE_SOLICITANTE = 150;
    private static final int LARGO_MAXIMO_DNI_SOLICITANTE = 20;
    private static final int LARGO_MAXIMO_CONTACTO = 200;

    private final FranjaHorariaRepository franjas;
    private final ActividadRepository actividades;
    private final TurnoRepository turnos;

    GestionDeReservas(FranjaHorariaRepository franjas, ActividadRepository actividades, TurnoRepository turnos) {
        this.franjas = franjas;
        this.actividades = actividades;
        this.turnos = turnos;
    }

    /**
     * Decisión central de la rebanada (ADR 0026 §4): el cupo se
     * decrementa con un único {@code UPDATE} condicional atómico
     * ({@link FranjaHorariaRepository#reservarUnLugar}), nunca con una
     * lectura de {@code cupoDisponible} seguida de una escritura en Java
     * — esa secuencia tendría una ventana de carrera entre dos
     * solicitudes públicas anónimas concurrentes.
     *
     * <p>El chequeo de {@link TurnoRepository#existsByFranjaIdAndDniSolicitante}
     * (paso 4) es solo una salida rápida para el caso común, sin carrera:
     * la barrera real contra la reserva duplicada bajo concurrencia es la
     * restricción {@code unique (franja_id, dni_solicitante)} de la base,
     * traducida acá a {@link ReservaDuplicada} (paso 6). Esa excepción se
     * relanza sin atrapar el fallo silenciosamente: tiene que llegar
     * hasta el proxy transaccional para que TODA la transacción —incluido
     * el decremento de cupo del paso 5, que ya se aplicó— haga rollback.
     * Sin ese rollback quedaría un cupo "fantasma" consumido sin ningún
     * turno guardado.
     */
    @Transactional("tenantTransactionManager")
    ReservaPublicaResponse reservar(Long franjaId, String nombreSolicitante, String dniSolicitante, String contacto) {
        if (franjaId == null) {
            throw new SolicitudInvalida("Hay que indicar la franja horaria.");
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

        // 1/2. La franja tiene que existir.
        FranjaHorariaEntity franja = franjas.findById(franjaId)
                .orElseThrow(() -> new FranjaNoEncontrada("No existe la franja " + franjaId + "."));

        // 3. Y su actividad tiene que estar ACTIVA. La actividad siempre existe:
        // una franja nunca se crea sin una (GestionDeAgenda#crearFranja ya lo
        // valida), y ninguna de las dos tiene borrado en esta rebanada.
        ActividadEntity actividad = actividades.findById(franja.getActividadId())
                .orElseThrow(() -> new IllegalStateException(
                        "La franja " + franjaId + " referencia una actividad inexistente."));
        if (actividad.getEstado() != EstadoDeActividad.ACTIVA) {
            throw new SolicitudInvalida("La actividad no admite reservas en este momento.");
        }

        // 4. Chequeo temprano de duplicado, antes de tocar el cupo.
        if (turnos.existsByFranjaIdAndDniSolicitante(franjaId, dniSolicitante)) {
            throw new ReservaDuplicada("Ya existe una reserva con ese DNI para esta franja.");
        }

        // 5. Recién acá se consume un lugar, con el UPDATE condicional atómico.
        int filasAfectadas = franjas.reservarUnLugar(franjaId);
        if (filasAfectadas == 0) {
            throw new CupoAgotado("No queda cupo disponible para esta franja.");
        }

        // 6. Si dos solicitudes con el mismo DNI ganaron la carrera del chequeo
        // temprano casi al mismo tiempo, la restricción unique de la base es la
        // que realmente lo evita.
        TurnoEntity turno = TurnoEntity.reservar(franjaId, nombreSolicitante, dniSolicitante, contacto);
        try {
            turno = turnos.save(turno);
        } catch (DataIntegrityViolationException e) {
            throw new ReservaDuplicada("Ya existe una reserva con ese DNI para esta franja.");
        }

        Integer cupoDisponibleRestante = franjas.cupoDisponibleDe(franjaId);
        return new ReservaPublicaResponse(
                turno.getId(), actividad.getNombre(), franja.getFecha(), franja.getHoraInicio(),
                franja.getHoraFin(), cupoDisponibleRestante);
    }

    /**
     * Todas las reservas de una franja, para la agenda de gestión (ADR
     * 0026 §5): sin lectura pública equivalente, solo la usa quien tiene
     * {@code turnos.gestionar}.
     */
    List<TurnoEntity> listarParaGestion(Long franjaId) {
        if (franjaId == null) {
            throw new SolicitudInvalida("Hay que indicar la franja horaria.");
        }
        return turnos.findByFranjaIdOrderByCreadoEnAsc(franjaId);
    }

    /**
     * Confirmación que recibe el vecino que acaba de reservar (ADR 0026
     * §4/§5): actividad, franja y cupo resultante, deliberadamente sin
     * {@code nombreSolicitante}/{@code dniSolicitante}/{@code contacto} —
     * el propio vecino ya los tiene, mismo criterio que
     * {@code InscripcionPublicaResponse} en Desarrollo Social. Se arma
     * acá, con el join contra {@code ActividadRepository}/
     * {@code FranjaHorariaRepository} ya resuelto, para que el controller
     * no tenga que volver a tocar esos repositorios.
     */
    record ReservaPublicaResponse(
            Long id, String nombreActividad, LocalDate fecha, LocalTime horaInicio, LocalTime horaFin,
            Integer cupoDisponibleRestante) {
    }
}
