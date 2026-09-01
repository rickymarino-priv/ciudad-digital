package ar.com.ciudaddigital.defensacivil.internal;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta protegida, búsqueda pública y finalización de las alertas de
 * Defensa Civil del municipio del request en curso (ADR 0031 §4).
 */
@Service
class GestionDeAlertas {

    private static final int LARGO_MAXIMO_TITULO = 300;
    private static final int LARGO_MAXIMO_ZONA_AFECTADA = 300;
    private static final int LARGO_MAXIMO_PUBLICADO_POR_NOMBRE = 150;
    private static final int LARGO_MAXIMO_PUBLICADO_POR_EMAIL = 200;

    private final AlertaDeDefensaCivilRepository alertas;

    GestionDeAlertas(AlertaDeDefensaCivilRepository alertas) {
        this.alertas = alertas;
    }

    @Transactional("tenantTransactionManager")
    AlertaDeDefensaCivilEntity publicar(TipoDeAlerta tipo, NivelDeAlerta nivel, String titulo, String descripcion,
            String recomendaciones, String zonaAfectada, String publicadoPorNombre, String publicadoPorEmail) {

        if (tipo == null) {
            throw new SolicitudInvalida("Hay que indicar el tipo de alerta.");
        }
        if (nivel == null) {
            throw new SolicitudInvalida("Hay que indicar el nivel de la alerta.");
        }
        if (titulo == null || titulo.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar el título de la alerta.");
        }
        if (titulo.length() > LARGO_MAXIMO_TITULO) {
            throw new SolicitudInvalida("El título no puede superar los " + LARGO_MAXIMO_TITULO + " caracteres.");
        }
        if (descripcion == null || descripcion.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar la descripción de la alerta.");
        }
        if (recomendaciones == null || recomendaciones.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar las recomendaciones de la alerta.");
        }
        if (zonaAfectada != null && zonaAfectada.length() > LARGO_MAXIMO_ZONA_AFECTADA) {
            throw new SolicitudInvalida(
                    "La zona afectada no puede superar los " + LARGO_MAXIMO_ZONA_AFECTADA + " caracteres.");
        }
        // publicadoPorNombre/publicadoPorEmail salen del actor autenticado, no de la
        // solicitud: si alguno faltara sería un problema del mecanismo de
        // autenticación, no una solicitud inválida del agente (mismo criterio que
        // GestionDeArbolado#registrar/GestionDeEventos#publicar).
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

        AlertaDeDefensaCivilEntity alerta = AlertaDeDefensaCivilEntity.publicar(
                tipo, nivel, titulo, descripcion, recomendaciones, zonaAfectada,
                publicadoPorNombre, publicadoPorEmail);
        return alertas.save(alerta);
    }

    /**
     * {@code tipo}/{@code nivel}/{@code estado} ya vienen resueltos a su
     * enum (o {@code null} si no se pidió el filtro): un valor que no
     * matchea ningún literal del enum ya fue rechazado con 400 antes de
     * llegar acá, en el controller (ADR 0031 §6) — no se trata como "sin
     * filtro". {@code q} vacío o en blanco se trata como "sin filtro de
     * texto", no como una búsqueda del string vacío (mismo criterio que
     * {@code GestionDeEventos#buscar}).
     */
    List<AlertaDeDefensaCivilEntity> buscar(TipoDeAlerta tipo, NivelDeAlerta nivel, EstadoDeAlerta estado, String q) {
        String patron = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase() + "%";
        return alertas.buscar(tipo, nivel, estado, patron);
    }

    /**
     * La única transición válida es {@code VIGENTE → FINALIZADA} (ADR
     * 0031 §4): un solo salto sin retorno, así que alcanza con este
     * chequeo directo, sin una tabla de transiciones genérica como en
     * {@code GestionDeObras}/{@code GestionDeArbolado} (mismo criterio que
     * {@code GestionDeEventos#cancelar}).
     */
    @Transactional("tenantTransactionManager")
    AlertaDeDefensaCivilEntity finalizar(Long id) {
        AlertaDeDefensaCivilEntity alerta = alertas.findById(id)
                .orElseThrow(() -> new AlertaNoEncontrada("No existe la alerta " + id + "."));

        if (alerta.getEstado() != EstadoDeAlerta.VIGENTE) {
            throw new SolicitudInvalida(
                    "No se puede finalizar una alerta en estado " + alerta.getEstado() + ".");
        }

        alerta.finalizar();
        return alertas.save(alerta);
    }
}
