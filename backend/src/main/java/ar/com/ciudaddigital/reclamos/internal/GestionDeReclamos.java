package ar.com.ciudaddigital.reclamos.internal;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.com.ciudaddigital.seguimientoanonimo.TokenDeSeguimiento;

/**
 * Alta, listado, cambio de estado y consulta anónima por token de los
 * reclamos del municipio del request en curso (ADR 0014, ADR 0017).
 */
@Service
class GestionDeReclamos {

    /** Recorte defensivo: es la denuncia de un vecino, no se trunca en silencio, se rechaza. */
    private static final int LARGO_MAXIMO_DESCRIPCION = 2000;
    private static final int LARGO_MAXIMO_DIRECCION = 300;
    private static final int LARGO_MAXIMO_NOMBRE_CONTACTO = 150;
    private static final int LARGO_MAXIMO_CONTACTO = 200;

    /**
     * Transiciones válidas del ciclo de vida fijo del reclamo (ADR 0014
     * §3): tabla codificada acá, no en la entidad ni en un motor genérico
     * de workflow, porque hoy es el mismo circuito para todos los
     * municipios.
     */
    private static final Map<EstadoReclamo, Set<EstadoReclamo>> TRANSICIONES_VALIDAS =
            new EnumMap<>(Map.of(
                    EstadoReclamo.NUEVO, EnumSet.of(EstadoReclamo.EN_PROCESO, EstadoReclamo.RECHAZADO),
                    EstadoReclamo.EN_PROCESO, EnumSet.of(EstadoReclamo.RESUELTO, EstadoReclamo.RECHAZADO),
                    EstadoReclamo.RESUELTO, EnumSet.noneOf(EstadoReclamo.class),
                    EstadoReclamo.RECHAZADO, EnumSet.noneOf(EstadoReclamo.class)));

    private final ReclamoRepository reclamos;

    GestionDeReclamos(ReclamoRepository reclamos) {
        this.reclamos = reclamos;
    }

    @Transactional("tenantTransactionManager")
    ReclamoCreado cargar(CategoriaReclamo categoria, String descripcion, String direccion,
            String nombreContacto, String contacto) {

        if (categoria == null) {
            throw new SolicitudInvalida("Hay que indicar una categoría.");
        }
        if (descripcion == null || descripcion.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar una descripción.");
        }
        if (descripcion.length() > LARGO_MAXIMO_DESCRIPCION) {
            throw new SolicitudInvalida(
                    "La descripción no puede superar los " + LARGO_MAXIMO_DESCRIPCION + " caracteres.");
        }
        if (direccion == null || direccion.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar una dirección.");
        }
        if (direccion.length() > LARGO_MAXIMO_DIRECCION) {
            throw new SolicitudInvalida(
                    "La dirección no puede superar los " + LARGO_MAXIMO_DIRECCION + " caracteres.");
        }
        if (nombreContacto != null && !nombreContacto.isBlank()
                && nombreContacto.length() > LARGO_MAXIMO_NOMBRE_CONTACTO) {
            throw new SolicitudInvalida(
                    "El nombre de contacto no puede superar los "
                            + LARGO_MAXIMO_NOMBRE_CONTACTO + " caracteres.");
        }
        if (contacto != null && !contacto.isBlank() && contacto.length() > LARGO_MAXIMO_CONTACTO) {
            throw new SolicitudInvalida(
                    "El contacto no puede superar los " + LARGO_MAXIMO_CONTACTO + " caracteres.");
        }

        // El token en claro solo existe acá, entre que se genera y que el
        // record de retorno lo lleva hasta el controller (ADR 0017 §4): ni
        // la entidad ni el repositorio lo vuelven a ver.
        String tokenDeSeguimiento = TokenDeSeguimiento.generar();
        ReclamoEntity reclamo = ReclamoEntity.nuevo(categoria, descripcion, direccion,
                nombreContacto, contacto, TokenDeSeguimiento.hash(tokenDeSeguimiento));
        return new ReclamoCreado(reclamos.save(reclamo), tokenDeSeguimiento);
    }

    List<ReclamoEntity> listar() {
        return reclamos.findAllByOrderByCreadoEnDesc();
    }

    /**
     * Consulta anónima por posesión del token (ADR 0017 §4): un
     * {@code token} vacío se trata igual que "no encontrado", nunca como
     * {@link SolicitudInvalida}, para no distinguirle a quien prueba
     * tokens al azar un formato inválido de un token que no existe.
     */
    ReclamoEntity consultarPorToken(String token) {
        if (token == null || token.isBlank()) {
            throw new TokenNoEncontrado("No encontramos un reclamo con ese código.");
        }

        return reclamos.findByTokenHash(TokenDeSeguimiento.hash(token))
                .orElseThrow(() -> new TokenNoEncontrado("No encontramos un reclamo con ese código."));
    }

    @Transactional("tenantTransactionManager")
    ReclamoEntity cambiarEstado(Long id, EstadoReclamo nuevoEstado, String comentario) {
        ReclamoEntity reclamo = reclamos.findById(id)
                .orElseThrow(() -> new SolicitudInvalida("No existe el reclamo " + id + "."));

        if (nuevoEstado == null) {
            throw new SolicitudInvalida("Hay que indicar el estado nuevo.");
        }

        EstadoReclamo estadoActual = reclamo.getEstado();
        if (!TRANSICIONES_VALIDAS.get(estadoActual).contains(nuevoEstado)) {
            throw new SolicitudInvalida("No se puede pasar de " + estadoActual + " a " + nuevoEstado + ".");
        }

        reclamo.cambiarEstado(nuevoEstado, comentario);
        return reclamos.save(reclamo);
    }

    /**
     * Resultado del alta: además del reclamo, el token en claro para que el
     * controller lo devuelva en la respuesta HTTP —la única vez que existe
     * fuera de este método— sin forzarlo a volver a tocar el servicio
     * (ADR 0017 §4).
     */
    record ReclamoCreado(ReclamoEntity reclamo, String tokenDeSeguimiento) {
    }
}
