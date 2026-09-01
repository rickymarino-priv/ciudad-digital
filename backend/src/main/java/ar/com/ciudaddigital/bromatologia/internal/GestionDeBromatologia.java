package ar.com.ciudaddigital.bromatologia.internal;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta protegida y búsqueda pública de comercios, y alta protegida y
 * búsqueda protegida de inspecciones, del municipio del request en curso
 * (ADR 0032 §2/§3/§4).
 *
 * <p>Un único componente para las dos entidades, a diferencia de
 * {@code defensacivil} ({@code GestionDeAlertas}/{@code GestionDeRecursos}
 * separados): acá {@code registrarInspeccion} necesita, en una única
 * transacción, leer el comercio, crear la inspección y actualizar el
 * estado del comercio, así que las dos entidades comparten un límite
 * transaccional real, no solo una pantalla (ADR 0032 §3).
 */
@Service
class GestionDeBromatologia {

    private static final int LARGO_MAXIMO_NOMBRE = 200;
    private static final int LARGO_MAXIMO_DIRECCION = 300;
    private static final int LARGO_MAXIMO_PUBLICADO_POR_NOMBRE = 150;
    private static final int LARGO_MAXIMO_PUBLICADO_POR_EMAIL = 200;

    private final ComercioBromatologicoRepository comercios;
    private final InspeccionBromatologicaRepository inspecciones;

    GestionDeBromatologia(ComercioBromatologicoRepository comercios, InspeccionBromatologicaRepository inspecciones) {
        this.comercios = comercios;
        this.inspecciones = inspecciones;
    }

    @Transactional("tenantTransactionManager")
    ComercioBromatologicoEntity registrarComercio(String nombre, RubroBromatologico rubro, String direccion,
            LocalDate fechaHabilitacion, LocalDate fechaVencimientoHabilitacion,
            String publicadoPorNombre, String publicadoPorEmail) {

        if (nombre == null || nombre.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar el nombre del comercio.");
        }
        if (nombre.length() > LARGO_MAXIMO_NOMBRE) {
            throw new SolicitudInvalida("El nombre no puede superar los " + LARGO_MAXIMO_NOMBRE + " caracteres.");
        }
        if (rubro == null) {
            throw new SolicitudInvalida("Hay que indicar el rubro del comercio.");
        }
        if (direccion == null || direccion.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar la dirección del comercio.");
        }
        if (direccion.length() > LARGO_MAXIMO_DIRECCION) {
            throw new SolicitudInvalida(
                    "La dirección no puede superar los " + LARGO_MAXIMO_DIRECCION + " caracteres.");
        }
        if (fechaHabilitacion == null) {
            throw new SolicitudInvalida("Hay que indicar la fecha de habilitación.");
        }
        if (fechaVencimientoHabilitacion == null) {
            throw new SolicitudInvalida("Hay que indicar la fecha de vencimiento de la habilitación.");
        }
        if (!fechaVencimientoHabilitacion.isAfter(fechaHabilitacion)) {
            throw new SolicitudInvalida(
                    "La fecha de vencimiento de la habilitación tiene que ser posterior a la fecha de habilitación.");
        }
        // publicadoPorNombre/publicadoPorEmail salen del actor autenticado, no de la
        // solicitud: si alguno faltara sería un problema del mecanismo de
        // autenticación, no una solicitud inválida del agente (mismo criterio que
        // GestionDeRecursos#registrar).
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

        ComercioBromatologicoEntity comercio = ComercioBromatologicoEntity.registrar(
                nombre, rubro, direccion, fechaHabilitacion, fechaVencimientoHabilitacion,
                publicadoPorNombre, publicadoPorEmail);
        return comercios.save(comercio);
    }

    /**
     * {@code rubro}/{@code estado} ya vienen resueltos a su enum (o
     * {@code null} si no se pidió el filtro): un valor que no matchea
     * ningún literal del enum ya fue rechazado con 400 antes de llegar
     * acá, en el controller — no se trata como "sin filtro". {@code q}
     * vacío o en blanco se trata como "sin filtro de texto", no como una
     * búsqueda del string vacío (mismo criterio que
     * {@code GestionDeRecursos#buscar}).
     */
    List<ComercioBromatologicoEntity> buscarComercios(RubroBromatologico rubro, EstadoBromatologico estado, String q) {
        String patron = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase() + "%";
        return comercios.buscar(rubro, estado, patron);
    }

    /**
     * Crea la inspección y actualiza {@code comercio.estado} al valor de
     * {@code resultado} en una única transacción (ADR 0032 §3). A
     * diferencia de {@code GestionDeRecursos#actualizarEstado}, no
     * rechaza un {@code resultado} igual al {@code estado} actual: una
     * reinspección de rutina que confirma que todo sigue igual es, en sí
     * misma, información de historial válida.
     */
    @Transactional("tenantTransactionManager")
    InspeccionBromatologicaEntity registrarInspeccion(Long comercioId, LocalDate fecha, EstadoBromatologico resultado,
            String observaciones, String inspeccionadoPorNombre, String inspeccionadoPorEmail) {

        ComercioBromatologicoEntity comercio = comercios.findById(comercioId)
                .orElseThrow(() -> new ComercioNoEncontrado("No existe el comercio " + comercioId + "."));

        if (fecha == null) {
            throw new SolicitudInvalida("Hay que indicar la fecha de la inspección.");
        }
        if (resultado == null) {
            throw new SolicitudInvalida("Hay que indicar el resultado de la inspección.");
        }

        InspeccionBromatologicaEntity inspeccion = InspeccionBromatologicaEntity.registrar(
                comercioId, fecha, resultado, observaciones, inspeccionadoPorNombre, inspeccionadoPorEmail);
        InspeccionBromatologicaEntity guardada = inspecciones.save(inspeccion);

        comercio.actualizarEstado(resultado);
        comercios.save(comercio);

        return guardada;
    }

    /** Ordenadas por fecha descendente (ADR 0032 §4). Valida que el comercio exista en este tenant. */
    List<InspeccionBromatologicaEntity> buscarInspecciones(Long comercioId) {
        if (!comercios.existsById(comercioId)) {
            throw new ComercioNoEncontrado("No existe el comercio " + comercioId + ".");
        }
        return inspecciones.findByComercioIdOrderByFechaDesc(comercioId);
    }
}
