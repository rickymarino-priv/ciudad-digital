package ar.com.ciudaddigital.arbolado.internal;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta protegida, búsqueda pública y actualización de estado sanitario de
 * los árboles urbanos del municipio del request en curso (ADR 0024).
 */
@Service
class GestionDeArbolado {

    private static final int LARGO_MAXIMO_ESPECIE = 150;
    private static final int LARGO_MAXIMO_UBICACION = 300;
    private static final int LARGO_MAXIMO_PUBLICADO_POR_NOMBRE = 150;
    private static final int LARGO_MAXIMO_PUBLICADO_POR_EMAIL = 200;

    /**
     * Transiciones válidas del ciclo de vida fijo del estado sanitario de
     * un árbol (ADR 0024 §4): tabla codificada acá, no en la entidad ni en
     * un motor genérico de workflow, mismo criterio que
     * {@code GestionDeObras}. No se reutiliza esa tabla ni el código de
     * {@code obras}: las reglas de negocio no son las mismas (ADR 0024
     * §1/§7).
     */
    private static final Map<EstadoDeArbol, Set<EstadoDeArbol>> TRANSICIONES_VALIDAS =
            new EnumMap<>(Map.of(
                    EstadoDeArbol.PLANTADO, EnumSet.of(EstadoDeArbol.SANO),
                    EstadoDeArbol.SANO, EnumSet.of(EstadoDeArbol.REQUIERE_INTERVENCION),
                    EstadoDeArbol.REQUIERE_INTERVENCION,
                    EnumSet.of(EstadoDeArbol.SANO, EstadoDeArbol.RETIRADO),
                    EstadoDeArbol.RETIRADO, EnumSet.noneOf(EstadoDeArbol.class)));

    private final ArbolUrbanoRepository arboles;

    GestionDeArbolado(ArbolUrbanoRepository arboles) {
        this.arboles = arboles;
    }

    @Transactional("tenantTransactionManager")
    ArbolUrbanoEntity registrar(String especie, String ubicacion, String descripcion,
            LocalDate fechaDePlantacion, String publicadoPorNombre, String publicadoPorEmail) {

        if (especie == null || especie.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar la especie del árbol.");
        }
        if (especie.length() > LARGO_MAXIMO_ESPECIE) {
            throw new SolicitudInvalida("La especie no puede superar los " + LARGO_MAXIMO_ESPECIE + " caracteres.");
        }
        if (ubicacion == null || ubicacion.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar la ubicación del árbol.");
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
                    "El nombre de quien registra no puede superar los "
                            + LARGO_MAXIMO_PUBLICADO_POR_NOMBRE + " caracteres.");
        }
        if (publicadoPorEmail != null && publicadoPorEmail.length() > LARGO_MAXIMO_PUBLICADO_POR_EMAIL) {
            throw new SolicitudInvalida(
                    "El correo de quien registra no puede superar los "
                            + LARGO_MAXIMO_PUBLICADO_POR_EMAIL + " caracteres.");
        }

        ArbolUrbanoEntity arbol = ArbolUrbanoEntity.registrar(
                especie, ubicacion, descripcion, fechaDePlantacion, publicadoPorNombre, publicadoPorEmail);
        return arboles.save(arbol);
    }

    /**
     * {@code estado} ya viene resuelto a su enum (o {@code null} si no se
     * pidió el filtro): un valor que no matchea ningún literal del enum ya
     * fue rechazado con 400 antes de llegar acá, en el controller (ADR
     * 0024, Tarea 1) — no se trata como "sin filtro". {@code q} vacío o en
     * blanco se trata como "sin filtro de texto", no como una búsqueda del
     * string vacío (mismo criterio que {@code GestionDeObras#buscar}).
     */
    List<ArbolUrbanoEntity> buscar(EstadoDeArbol estado, String q) {
        String patron = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase() + "%";
        return arboles.buscar(estado, patron);
    }

    @Transactional("tenantTransactionManager")
    ArbolUrbanoEntity actualizarEstado(Long id, EstadoDeArbol estadoNuevo) {
        ArbolUrbanoEntity arbol = arboles.findById(id)
                .orElseThrow(() -> new ArbolNoEncontrado("No existe el árbol " + id + "."));

        if (estadoNuevo == null) {
            throw new SolicitudInvalida("Hay que indicar el estado nuevo.");
        }

        EstadoDeArbol estadoActual = arbol.getEstado();
        if (!TRANSICIONES_VALIDAS.get(estadoActual).contains(estadoNuevo)) {
            throw new SolicitudInvalida("No se puede pasar de " + estadoActual + " a " + estadoNuevo + ".");
        }

        arbol.actualizarEstado(estadoNuevo);
        return arboles.save(arbol);
    }
}
