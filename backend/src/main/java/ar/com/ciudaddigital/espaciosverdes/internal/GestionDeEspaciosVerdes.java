package ar.com.ciudaddigital.espaciosverdes.internal;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta protegida, búsqueda pública y actualización de estado de los
 * espacios verdes del municipio del request en curso (ADR 0029).
 */
@Service
class GestionDeEspaciosVerdes {

    private static final int LARGO_MAXIMO_NOMBRE = 150;
    private static final int LARGO_MAXIMO_UBICACION = 300;
    private static final int LARGO_MAXIMO_PUBLICADO_POR_NOMBRE = 150;
    private static final int LARGO_MAXIMO_PUBLICADO_POR_EMAIL = 200;

    /**
     * Transiciones válidas del ciclo de vida fijo de un espacio verde (ADR
     * 0029 §5): tabla codificada acá, no en la entidad ni en un motor
     * genérico de workflow, mismo criterio que {@code GestionDeObras}/
     * {@code GestionDeArbolado}. No se reutiliza esa tabla ni el código de
     * {@code obras}/{@code arbolado}/{@code educacion}: las reglas de
     * negocio no son las mismas (ADR 0029 §1/§8).
     */
    private static final Map<EstadoDeEspacioVerde, Set<EstadoDeEspacioVerde>> TRANSICIONES_VALIDAS =
            new EnumMap<>(Map.of(
                    EstadoDeEspacioVerde.DISPONIBLE, EnumSet.of(EstadoDeEspacioVerde.EN_MANTENIMIENTO),
                    EstadoDeEspacioVerde.EN_MANTENIMIENTO,
                    EnumSet.of(EstadoDeEspacioVerde.DISPONIBLE, EstadoDeEspacioVerde.CERRADO),
                    EstadoDeEspacioVerde.CERRADO, EnumSet.noneOf(EstadoDeEspacioVerde.class)));

    private final EspacioVerdeRepository espaciosVerdes;

    GestionDeEspaciosVerdes(EspacioVerdeRepository espaciosVerdes) {
        this.espaciosVerdes = espaciosVerdes;
    }

    @Transactional("tenantTransactionManager")
    EspacioVerdeEntity registrar(String nombre, TipoDeEspacioVerde tipo, String ubicacion, String descripcion,
            BigDecimal superficie, String publicadoPorNombre, String publicadoPorEmail) {

        if (nombre == null || nombre.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar el nombre del espacio verde.");
        }
        if (nombre.length() > LARGO_MAXIMO_NOMBRE) {
            throw new SolicitudInvalida("El nombre no puede superar los " + LARGO_MAXIMO_NOMBRE + " caracteres.");
        }
        if (tipo == null) {
            throw new SolicitudInvalida("Hay que indicar un tipo de espacio verde.");
        }
        if (ubicacion == null || ubicacion.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar la ubicación del espacio verde.");
        }
        if (ubicacion.length() > LARGO_MAXIMO_UBICACION) {
            throw new SolicitudInvalida(
                    "La ubicación no puede superar los " + LARGO_MAXIMO_UBICACION + " caracteres.");
        }
        if (superficie != null && superficie.compareTo(BigDecimal.ZERO) <= 0) {
            throw new SolicitudInvalida("La superficie tiene que ser mayor a cero.");
        }
        // publicadoPorNombre/publicadoPorEmail salen del actor autenticado, no de la
        // solicitud: si alguno faltara sería un problema del mecanismo de
        // autenticación, no una solicitud inválida del agente (mismo criterio que
        // GestionDeObras#registrar/GestionDeArbolado#registrar).
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

        EspacioVerdeEntity espacioVerde = EspacioVerdeEntity.registrar(
                nombre, tipo, ubicacion, descripcion, superficie, publicadoPorNombre, publicadoPorEmail);
        return espaciosVerdes.save(espacioVerde);
    }

    /**
     * {@code estado}/{@code tipo} ya vienen resueltos a su enum (o
     * {@code null} si no se pidió el filtro): un valor que no matchea
     * ningún literal del enum ya fue rechazado con 400 antes de llegar
     * acá, en el controller (ADR 0029, Tarea 1) — no se trata como "sin
     * filtro". {@code q} vacío o en blanco se trata como "sin filtro de
     * texto", no como una búsqueda del string vacío (mismo criterio que
     * {@code GestionDeObras#buscar}).
     */
    List<EspacioVerdeEntity> buscar(EstadoDeEspacioVerde estado, TipoDeEspacioVerde tipo, String q) {
        String patron = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase() + "%";
        return espaciosVerdes.buscar(estado, tipo, patron);
    }

    @Transactional("tenantTransactionManager")
    EspacioVerdeEntity actualizarEstado(Long id, EstadoDeEspacioVerde estadoNuevo) {
        EspacioVerdeEntity espacioVerde = espaciosVerdes.findById(id)
                .orElseThrow(() -> new EspacioVerdeNoEncontrado("No existe el espacio verde " + id + "."));

        if (estadoNuevo == null) {
            throw new SolicitudInvalida("Hay que indicar el estado nuevo.");
        }

        EstadoDeEspacioVerde estadoActual = espacioVerde.getEstado();
        if (!TRANSICIONES_VALIDAS.get(estadoActual).contains(estadoNuevo)) {
            throw new SolicitudInvalida("No se puede pasar de " + estadoActual + " a " + estadoNuevo + ".");
        }

        espacioVerde.actualizarEstado(estadoNuevo);
        return espaciosVerdes.save(espacioVerde);
    }
}
