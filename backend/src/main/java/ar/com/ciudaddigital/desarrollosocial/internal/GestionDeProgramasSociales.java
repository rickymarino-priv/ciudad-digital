package ar.com.ciudaddigital.desarrollosocial.internal;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta protegida, búsqueda pública y actualización de estado del catálogo
 * de programas sociales del municipio del request en curso (ADR 0025
 * §3).
 */
@Service
class GestionDeProgramasSociales {

    private static final int LARGO_MAXIMO_NOMBRE = 150;
    private static final int LARGO_MAXIMO_PUBLICADO_POR_NOMBRE = 150;
    private static final int LARGO_MAXIMO_PUBLICADO_POR_EMAIL = 200;

    private final ProgramaSocialRepository programas;

    GestionDeProgramasSociales(ProgramaSocialRepository programas) {
        this.programas = programas;
    }

    @Transactional("tenantTransactionManager")
    ProgramaSocialEntity publicar(String nombre, String descripcion, String criteriosDeElegibilidad,
            String publicadoPorNombre, String publicadoPorEmail) {

        if (nombre == null || nombre.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar el nombre del programa.");
        }
        if (nombre.length() > LARGO_MAXIMO_NOMBRE) {
            throw new SolicitudInvalida("El nombre no puede superar los " + LARGO_MAXIMO_NOMBRE + " caracteres.");
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

        ProgramaSocialEntity programa = ProgramaSocialEntity.publicar(
                nombre, descripcion, criteriosDeElegibilidad, publicadoPorNombre, publicadoPorEmail);
        return programas.save(programa);
    }

    /**
     * {@code estado} ya viene resuelto a su enum (o {@code null} si no se
     * pidió el filtro): un valor que no matchea ningún literal del enum ya
     * fue rechazado con 400 antes de llegar acá, en el controller (mismo
     * criterio que {@code GestionDeObras#buscar}). {@code q} vacío o en
     * blanco se trata como "sin filtro de texto", no como una búsqueda del
     * string vacío.
     */
    List<ProgramaSocialEntity> buscar(EstadoDePrograma estado, String q) {
        String patron = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase() + "%";
        return programas.buscar(estado, patron);
    }

    @Transactional("tenantTransactionManager")
    ProgramaSocialEntity cambiarEstado(Long id, EstadoDePrograma estadoNuevo) {
        ProgramaSocialEntity programa = programas.findById(id)
                .orElseThrow(() -> new ProgramaNoEncontrado("No existe el programa " + id + "."));

        if (estadoNuevo == null) {
            throw new SolicitudInvalida("Hay que indicar el estado nuevo.");
        }

        // Ambos sentidos son válidos entre ABIERTO y CERRADO (ADR 0025 §3): no
        // hace falta una tabla de transiciones, a diferencia de Obras/Arbolado.
        programa.actualizarEstado(estadoNuevo);
        return programas.save(programa);
    }
}
