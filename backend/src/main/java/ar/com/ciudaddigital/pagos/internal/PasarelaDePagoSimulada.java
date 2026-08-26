package ar.com.ciudaddigital.pagos.internal;

import java.util.UUID;

import org.springframework.stereotype.Component;

import ar.com.ciudaddigital.pagos.PasarelaDePago;
import ar.com.ciudaddigital.pagos.ResultadoDeInicioDePago;
import ar.com.ciudaddigital.pagos.SolicitudDePago;

/**
 * Único adaptador de {@link PasarelaDePago} que existe hoy, en todos los
 * ambientes —dev, test y el único deploy que existe hoy— (ADR 0018 §2): no
 * hay flag de configuración ni perfil de Spring que elija entre "simulado"
 * y "real" porque todavía no existe una segunda implementación.
 *
 * <p>No hace ninguna llamada de red ni valida el monto —eso es
 * responsabilidad de quien arma la {@link SolicitudDePago}, antes de
 * llamar acá—: solo genera una referencia externa no adivinable. Si el
 * pago se aprueba o se rechaza lo decide después el propio flujo simulado
 * (en la práctica, el frontend actuando como si fuera la pasarela), nunca
 * este método.
 */
@Component
class PasarelaDePagoSimulada implements PasarelaDePago {

    private static final String PREFIJO_REFERENCIA_EXTERNA = "SIM-";

    @Override
    public ResultadoDeInicioDePago iniciarPago(SolicitudDePago solicitud) {
        String referenciaExterna = PREFIJO_REFERENCIA_EXTERNA + UUID.randomUUID();
        return new ResultadoDeInicioDePago(referenciaExterna, null);
    }
}
