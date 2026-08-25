package ar.com.ciudaddigital.mesaentradas.internal;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta, listado y avance de estado de los expedientes del municipio del
 * request en curso (ADR 0015).
 */
@Service
class GestionDeExpedientes {

    /** Recorte defensivo: es un trámite formal, no se trunca en silencio, se rechaza. */
    private static final int LARGO_MAXIMO_SOLICITANTE_NOMBRE = 200;
    private static final int LARGO_MAXIMO_SOLICITANTE_CONTACTO = 200;
    private static final int LARGO_MAXIMO_DOMICILIO_A_CERTIFICAR = 300;

    private final ExpedienteRepository expedientes;

    GestionDeExpedientes(ExpedienteRepository expedientes) {
        this.expedientes = expedientes;
    }

    @Transactional("tenantTransactionManager")
    ExpedienteEntity iniciar(TipoDeTramite tipo, String solicitanteNombre, String solicitanteContacto,
            String domicilioACertificar) {

        // El controller ya resuelve el tipo desde el string del request
        // (SolicitudInvalida si falta o no existe); acá solo se guarda
        // contra un null defensivo.
        if (tipo == null) {
            throw new SolicitudInvalida("Hay que indicar un tipo de trámite.");
        }
        if (solicitanteNombre == null || solicitanteNombre.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar el nombre del solicitante.");
        }
        if (solicitanteNombre.length() > LARGO_MAXIMO_SOLICITANTE_NOMBRE) {
            throw new SolicitudInvalida(
                    "El nombre del solicitante no puede superar los "
                            + LARGO_MAXIMO_SOLICITANTE_NOMBRE + " caracteres.");
        }
        if (solicitanteContacto != null && !solicitanteContacto.isBlank()
                && solicitanteContacto.length() > LARGO_MAXIMO_SOLICITANTE_CONTACTO) {
            throw new SolicitudInvalida(
                    "El contacto del solicitante no puede superar los "
                            + LARGO_MAXIMO_SOLICITANTE_CONTACTO + " caracteres.");
        }
        if (domicilioACertificar == null || domicilioACertificar.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar el domicilio a certificar.");
        }
        if (domicilioACertificar.length() > LARGO_MAXIMO_DOMICILIO_A_CERTIFICAR) {
            throw new SolicitudInvalida(
                    "El domicilio a certificar no puede superar los "
                            + LARGO_MAXIMO_DOMICILIO_A_CERTIFICAR + " caracteres.");
        }

        ExpedienteEntity expediente =
                ExpedienteEntity.nuevo(tipo, solicitanteNombre, solicitanteContacto, domicilioACertificar);
        return expedientes.save(expediente);
    }

    List<ExpedienteEntity> listar() {
        return expedientes.findAllByOrderByCreadoEnDesc();
    }

    @Transactional("tenantTransactionManager")
    ExpedienteEntity avanzar(Long id, EstadoDeExpediente nuevoEstado, String comentario, String actorNombre,
            String actorEmail) {

        ExpedienteEntity expediente = expedientes.findById(id)
                .orElseThrow(() -> new SolicitudInvalida("No existe el expediente " + id + "."));

        if (nuevoEstado == null) {
            throw new SolicitudInvalida("Hay que indicar el estado nuevo.");
        }

        EstadoDeExpediente estadoActual = expediente.getEstado();
        if (!CircuitosDeTramite.de(expediente.getTipo()).transicionesValidas().get(estadoActual)
                .contains(nuevoEstado)) {
            throw new SolicitudInvalida("No se puede pasar de " + estadoActual + " a " + nuevoEstado + ".");
        }

        expediente.avanzar(nuevoEstado, actorNombre, actorEmail, comentario);
        return expedientes.save(expediente);
    }
}
