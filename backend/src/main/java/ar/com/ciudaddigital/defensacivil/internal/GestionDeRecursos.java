package ar.com.ciudaddigital.defensacivil.internal;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta protegida, búsqueda pública y actualización de estado de los
 * recursos de Defensa Civil del municipio del request en curso (ADR 0031
 * §5).
 */
@Service
class GestionDeRecursos {

    private static final int LARGO_MAXIMO_NOMBRE = 200;
    private static final int LARGO_MAXIMO_DIRECCION = 300;
    private static final int LARGO_MAXIMO_TELEFONO_CONTACTO = 50;
    private static final int LARGO_MAXIMO_PUBLICADO_POR_NOMBRE = 150;
    private static final int LARGO_MAXIMO_PUBLICADO_POR_EMAIL = 200;

    private final RecursoDeDefensaCivilRepository recursos;

    GestionDeRecursos(RecursoDeDefensaCivilRepository recursos) {
        this.recursos = recursos;
    }

    @Transactional("tenantTransactionManager")
    RecursoDeDefensaCivilEntity registrar(TipoDeRecurso tipo, String nombre, String direccion, Integer capacidad,
            String telefonoContacto, String descripcion, String publicadoPorNombre, String publicadoPorEmail) {

        if (tipo == null) {
            throw new SolicitudInvalida("Hay que indicar el tipo de recurso.");
        }
        if (nombre == null || nombre.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar el nombre del recurso.");
        }
        if (nombre.length() > LARGO_MAXIMO_NOMBRE) {
            throw new SolicitudInvalida("El nombre no puede superar los " + LARGO_MAXIMO_NOMBRE + " caracteres.");
        }
        if (direccion == null || direccion.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar la dirección del recurso.");
        }
        if (direccion.length() > LARGO_MAXIMO_DIRECCION) {
            throw new SolicitudInvalida(
                    "La dirección no puede superar los " + LARGO_MAXIMO_DIRECCION + " caracteres.");
        }
        if (capacidad != null && capacidad < 0) {
            throw new SolicitudInvalida("La capacidad no puede ser negativa.");
        }
        if (telefonoContacto != null && telefonoContacto.length() > LARGO_MAXIMO_TELEFONO_CONTACTO) {
            throw new SolicitudInvalida(
                    "El teléfono de contacto no puede superar los "
                            + LARGO_MAXIMO_TELEFONO_CONTACTO + " caracteres.");
        }
        // publicadoPorNombre/publicadoPorEmail salen del actor autenticado, no de la
        // solicitud: si alguno faltara sería un problema del mecanismo de
        // autenticación, no una solicitud inválida del agente (mismo criterio que
        // GestionDeAlertas#publicar).
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

        RecursoDeDefensaCivilEntity recurso = RecursoDeDefensaCivilEntity.registrar(
                tipo, nombre, direccion, capacidad, telefonoContacto, descripcion,
                publicadoPorNombre, publicadoPorEmail);
        return recursos.save(recurso);
    }

    /**
     * {@code tipo}/{@code estado} ya vienen resueltos a su enum (o
     * {@code null} si no se pidió el filtro): un valor que no matchea
     * ningún literal del enum ya fue rechazado con 400 antes de llegar
     * acá, en el controller — no se trata como "sin filtro". {@code q}
     * vacío o en blanco se trata como "sin filtro de texto", no como una
     * búsqueda del string vacío (mismo criterio que
     * {@code GestionDeAlertas#buscar}).
     */
    List<RecursoDeDefensaCivilEntity> buscar(TipoDeRecurso tipo, EstadoDeRecurso estado, String q) {
        String patron = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase() + "%";
        return recursos.buscar(tipo, estado, patron);
    }

    /**
     * Sin tabla de transiciones: con solo dos valores y transición libre
     * en ambos sentidos, el chequeo directo alcanza (ADR 0031 §5). Pedir
     * el mismo estado en el que ya está se rechaza: no hay ninguna
     * transición "a sí mismo" en ningún módulo previo del proyecto.
     */
    @Transactional("tenantTransactionManager")
    RecursoDeDefensaCivilEntity actualizarEstado(Long id, EstadoDeRecurso estadoNuevo) {
        RecursoDeDefensaCivilEntity recurso = recursos.findById(id)
                .orElseThrow(() -> new RecursoNoEncontrado("No existe el recurso " + id + "."));

        if (estadoNuevo == null) {
            throw new SolicitudInvalida("Hay que indicar el estado nuevo.");
        }
        if (estadoNuevo == recurso.getEstado()) {
            throw new SolicitudInvalida("El recurso ya está en ese estado.");
        }

        recurso.actualizarEstado(estadoNuevo);
        return recursos.save(recurso);
    }
}
