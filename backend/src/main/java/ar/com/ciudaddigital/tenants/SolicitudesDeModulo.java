package ar.com.ciudaddigital.tenants;

import java.time.Instant;
import java.util.List;

/**
 * Crea y lista las solicitudes de alta/baja de módulo del municipio del
 * request en curso (ADR 0022 §2). Nunca recibe el tenant como parámetro:
 * ambas operaciones lo toman de {@link TenantContext#requerido()}, para que
 * sea estructuralmente imposible que el request de un municipio cree o lea
 * la solicitud de otro.
 *
 * <p>La usa {@code municipio.internal.ConsolaDelMunicipioController}: crear
 * o listar una solicitud no cambia el entitlement del municipio, sigue
 * siendo la plataforma quien prende o apaga módulos (ADR 0012 §8).
 */
public interface SolicitudesDeModulo {

    /**
     * @throws SolicitudDeModuloInvalida si el código de módulo no existe en el catálogo, el tipo
     *         no es {@code ALTA}/{@code BAJA}, o la justificación está vacía.
     */
    SolicitudDeModuloInfo crear(String moduloCodigo, String tipo, String justificacion,
            String nombreSolicitante, String emailSolicitante);

    List<SolicitudDeModuloInfo> delTenantActual();

    record SolicitudDeModuloInfo(
            Long id, String moduloCodigo, String tipo, String justificacion, String estado,
            Instant creadaEn, Instant atendidaEn) {
    }
}
