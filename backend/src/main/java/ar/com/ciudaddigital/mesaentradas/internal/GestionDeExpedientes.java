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
    private static final int LARGO_MAXIMO_RUBRO_COMERCIAL = 200;
    private static final int LARGO_MAXIMO_DIRECCION_LOCAL = 300;
    private static final int LARGO_MAXIMO_DIRECCION_OBRA = 300;
    private static final int LARGO_MAXIMO_DESCRIPCION_OBRA = 500;

    private final ExpedienteRepository expedientes;

    GestionDeExpedientes(ExpedienteRepository expedientes) {
        this.expedientes = expedientes;
    }

    @Transactional("tenantTransactionManager")
    ExpedienteEntity iniciar(TipoDeTramite tipo, String solicitanteNombre, String solicitanteContacto,
            DatosPropiosDelTramite datos) {

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
        DatosPropiosDelTramite datosSaneados = validarYSanearDatosPropios(tipo, datos);

        ExpedienteEntity expediente =
                ExpedienteEntity.nuevo(tipo, solicitanteNombre, solicitanteContacto, datosSaneados);
        return expedientes.save(expediente);
    }

    /**
     * Valida los campos propios del trámite según su tipo (ADR 0016) y
     * devuelve un {@link DatosPropiosDelTramite} saneado que solo contiene
     * los campos del tipo recibido, forzando a {@code null} los de los
     * otros dos: si el vecino manda campos de otro tipo (a propósito o por
     * un cliente desalineado), se descartan en silencio en vez de
     * persistirse — el {@code request} nunca llega tal cual a
     * {@code ExpedienteEntity.nuevo(...)}. Es la única rama del módulo que
     * necesariamente conoce los tres tipos a la vez —agregar un cuarto tipo
     * agrega un {@code case} acá—, el resto del módulo
     * ({@code listar}/{@code avanzar}) sigue siendo agnóstico vía
     * {@link CircuitosDeTramite}.
     */
    private DatosPropiosDelTramite validarYSanearDatosPropios(TipoDeTramite tipo, DatosPropiosDelTramite datos) {
        return switch (tipo) {
            case CERTIFICADO_DOMICILIO -> {
                if (datos.domicilioACertificar() == null || datos.domicilioACertificar().isBlank()) {
                    throw new SolicitudInvalida("Hay que indicar el domicilio a certificar.");
                }
                if (datos.domicilioACertificar().length() > LARGO_MAXIMO_DOMICILIO_A_CERTIFICAR) {
                    throw new SolicitudInvalida(
                            "El domicilio a certificar no puede superar los "
                                    + LARGO_MAXIMO_DOMICILIO_A_CERTIFICAR + " caracteres.");
                }
                yield new DatosPropiosDelTramite(datos.domicilioACertificar(), null, null, null, null);
            }
            case HABILITACION_COMERCIAL_SIMPLE -> {
                if (datos.rubroComercial() == null || datos.rubroComercial().isBlank()) {
                    throw new SolicitudInvalida("Hay que indicar el rubro comercial.");
                }
                if (datos.rubroComercial().length() > LARGO_MAXIMO_RUBRO_COMERCIAL) {
                    throw new SolicitudInvalida(
                            "El rubro comercial no puede superar los "
                                    + LARGO_MAXIMO_RUBRO_COMERCIAL + " caracteres.");
                }
                if (datos.direccionLocal() == null || datos.direccionLocal().isBlank()) {
                    throw new SolicitudInvalida("Hay que indicar la dirección del local.");
                }
                if (datos.direccionLocal().length() > LARGO_MAXIMO_DIRECCION_LOCAL) {
                    throw new SolicitudInvalida(
                            "La dirección del local no puede superar los "
                                    + LARGO_MAXIMO_DIRECCION_LOCAL + " caracteres.");
                }
                yield new DatosPropiosDelTramite(null, datos.rubroComercial(), datos.direccionLocal(), null, null);
            }
            case PERMISO_OBRA_MENOR -> {
                if (datos.direccionObra() == null || datos.direccionObra().isBlank()) {
                    throw new SolicitudInvalida("Hay que indicar la dirección de la obra.");
                }
                if (datos.direccionObra().length() > LARGO_MAXIMO_DIRECCION_OBRA) {
                    throw new SolicitudInvalida(
                            "La dirección de la obra no puede superar los "
                                    + LARGO_MAXIMO_DIRECCION_OBRA + " caracteres.");
                }
                if (datos.descripcionObra() == null || datos.descripcionObra().isBlank()) {
                    throw new SolicitudInvalida("Hay que indicar la descripción de la obra.");
                }
                if (datos.descripcionObra().length() > LARGO_MAXIMO_DESCRIPCION_OBRA) {
                    throw new SolicitudInvalida(
                            "La descripción de la obra no puede superar los "
                                    + LARGO_MAXIMO_DESCRIPCION_OBRA + " caracteres.");
                }
                yield new DatosPropiosDelTramite(null, null, null, datos.direccionObra(), datos.descripcionObra());
            }
        };
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
