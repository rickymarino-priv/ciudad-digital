package ar.com.ciudaddigital.transparencia.internal;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publicación y búsqueda de partidas presupuestarias y entradas de escala
 * salarial del municipio del request en curso (backlog R11).
 *
 * <p>Son dos recursos independientes que conviven en el mismo servicio
 * porque comparten el mismo módulo comercial ({@code transparencia}) y el
 * mismo permiso de escritura ({@code transparencia.publicar}), no porque
 * tengan un modelo de datos en común: no hay relación entre una partida y
 * una entrada de escala salarial.
 */
@Service
class GestionDeTransparencia {

    private static final int ANIO_MINIMO = 2000;
    private static final int ANIO_MAXIMO = 2100;

    private static final int LARGO_MAXIMO_AREA = 150;
    private static final int LARGO_MAXIMO_NUMERO_PARTIDA = 50;
    private static final int LARGO_MAXIMO_CONCEPTO = 300;
    private static final int LARGO_MAXIMO_CARGO = 200;
    private static final int LARGO_MAXIMO_PUBLICADO_POR_NOMBRE = 150;
    private static final int LARGO_MAXIMO_PUBLICADO_POR_EMAIL = 200;

    private final PartidaPresupuestariaRepository partidas;
    private final EscalaSalarialRepository escalas;

    GestionDeTransparencia(PartidaPresupuestariaRepository partidas, EscalaSalarialRepository escalas) {
        this.partidas = partidas;
        this.escalas = escalas;
    }

    @Transactional("tenantTransactionManager")
    PartidaPresupuestariaEntity publicarPartida(Integer anio, String area, String numeroPartida, String concepto,
            BigDecimal montoAsignado, BigDecimal montoEjecutado, String publicadoPorNombre,
            String publicadoPorEmail) {

        validarAnio(anio);
        if (area == null || area.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar el área.");
        }
        if (area.length() > LARGO_MAXIMO_AREA) {
            throw new SolicitudInvalida("El área no puede superar los " + LARGO_MAXIMO_AREA + " caracteres.");
        }
        if (numeroPartida == null || numeroPartida.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar el número de partida.");
        }
        if (numeroPartida.length() > LARGO_MAXIMO_NUMERO_PARTIDA) {
            throw new SolicitudInvalida(
                    "El número de partida no puede superar los " + LARGO_MAXIMO_NUMERO_PARTIDA + " caracteres.");
        }
        if (concepto == null || concepto.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar el concepto.");
        }
        if (concepto.length() > LARGO_MAXIMO_CONCEPTO) {
            throw new SolicitudInvalida("El concepto no puede superar los " + LARGO_MAXIMO_CONCEPTO + " caracteres.");
        }
        if (montoAsignado == null) {
            throw new SolicitudInvalida("Hay que indicar el monto asignado.");
        }
        if (montoAsignado.signum() < 0) {
            throw new SolicitudInvalida("El monto asignado no puede ser negativo.");
        }
        // montoEjecutado es opcional y no se valida contra montoAsignado: un
        // municipio puede ejecutar por encima de lo asignado con una
        // modificación presupuestaria; no es esta rebanada la que arbitra esa
        // regla.
        if (montoEjecutado != null && montoEjecutado.signum() < 0) {
            throw new SolicitudInvalida("El monto ejecutado no puede ser negativo.");
        }
        validarActor(publicadoPorNombre, publicadoPorEmail);

        PartidaPresupuestariaEntity partida = PartidaPresupuestariaEntity.nueva(anio, area, numeroPartida, concepto,
                montoAsignado, montoEjecutado, publicadoPorNombre, publicadoPorEmail);
        return partidas.save(partida);
    }

    /**
     * {@code texto} vacío o en blanco se trata como "sin filtro de texto",
     * no como una búsqueda del string vacío. El patrón de {@code LIKE} se
     * arma acá, no en el repositorio (ver el Javadoc de
     * {@link PartidaPresupuestariaRepository#buscar}).
     */
    List<PartidaPresupuestariaEntity> buscarPartidas(Integer anio, String texto) {
        return partidas.buscar(anio, patronDe(texto));
    }

    @Transactional("tenantTransactionManager")
    EscalaSalarialEntity publicarCargo(Integer anio, String area, String cargo, Integer cantidadCargos,
            BigDecimal montoBrutoMensual, String publicadoPorNombre, String publicadoPorEmail) {

        validarAnio(anio);
        if (area == null || area.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar el área.");
        }
        if (area.length() > LARGO_MAXIMO_AREA) {
            throw new SolicitudInvalida("El área no puede superar los " + LARGO_MAXIMO_AREA + " caracteres.");
        }
        if (cargo == null || cargo.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar el cargo.");
        }
        if (cargo.length() > LARGO_MAXIMO_CARGO) {
            throw new SolicitudInvalida("El cargo no puede superar los " + LARGO_MAXIMO_CARGO + " caracteres.");
        }
        // cantidadCargos es opcional en el request: sin valor, default 1 (un
        // único cargo de ese tipo/función).
        Integer cantidad = cantidadCargos == null ? 1 : cantidadCargos;
        if (cantidad <= 0) {
            throw new SolicitudInvalida("La cantidad de cargos tiene que ser mayor a cero.");
        }
        if (montoBrutoMensual == null) {
            throw new SolicitudInvalida("Hay que indicar el monto bruto mensual.");
        }
        if (montoBrutoMensual.signum() < 0) {
            throw new SolicitudInvalida("El monto bruto mensual no puede ser negativo.");
        }
        validarActor(publicadoPorNombre, publicadoPorEmail);

        EscalaSalarialEntity escala = EscalaSalarialEntity.nueva(anio, area, cargo, cantidad, montoBrutoMensual,
                publicadoPorNombre, publicadoPorEmail);
        return escalas.save(escala);
    }

    /**
     * Mismo criterio que {@link #buscarPartidas}: {@code texto} vacío o en
     * blanco desactiva el filtro de texto.
     */
    List<EscalaSalarialEntity> buscarCargos(Integer anio, String texto) {
        return escalas.buscar(anio, patronDe(texto));
    }

    /** Sanity check de dominio: sin un rango razonable, un año mal tipeado (p. ej. "202") pasaría sin aviso. */
    private static void validarAnio(Integer anio) {
        if (anio == null) {
            throw new SolicitudInvalida("Hay que indicar el año.");
        }
        if (anio < ANIO_MINIMO || anio > ANIO_MAXIMO) {
            throw new SolicitudInvalida("El año tiene que estar entre " + ANIO_MINIMO + " y " + ANIO_MAXIMO + ".");
        }
    }

    /**
     * publicadoPorNombre/publicadoPorEmail salen del actor autenticado, no de
     * la solicitud: si alguno faltara sería un problema del mecanismo de
     * autenticación, no una solicitud inválida del agente (por eso no llevan
     * acá los mismos mensajes de SolicitudInvalida que el resto de los
     * campos).
     */
    private static void validarActor(String publicadoPorNombre, String publicadoPorEmail) {
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
    }

    private static String patronDe(String texto) {
        return texto == null || texto.isBlank() ? null : "%" + texto.trim().toLowerCase() + "%";
    }
}
