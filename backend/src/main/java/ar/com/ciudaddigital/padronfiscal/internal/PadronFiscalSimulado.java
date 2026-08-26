package ar.com.ciudaddigital.padronfiscal.internal;

import org.springframework.stereotype.Component;

import ar.com.ciudaddigital.padronfiscal.PadronFiscal;
import ar.com.ciudaddigital.padronfiscal.SituacionFiscal;

/**
 * Único adaptador de {@link PadronFiscal} que existe hoy, en todos los
 * ambientes (ADR 0020 §2): no hay flag de configuración ni perfil de
 * Spring que elija entre "simulado" y "real" porque todavía no existe una
 * segunda implementación.
 *
 * <p>No hace ninguna llamada de red: responde de forma determinística
 * según el último dígito del CUIT normalizado, para que la demo sea
 * reproducible y los tests puedan fijar el resultado eligiendo el CUIT. La
 * regla es arbitraria a propósito, no imita ningún algoritmo real de
 * AFIP/ARBA. Un CUIT que ya falló la validación de formato de
 * {@code GestionDeProveedores} nunca llega hasta acá.
 */
@Component
class PadronFiscalSimulado implements PadronFiscal {

    @Override
    public SituacionFiscal consultar(String cuit) {
        String soloDigitos = cuit.replaceAll("\\D", "");
        char ultimoDigito = soloDigitos.charAt(soloDigitos.length() - 1);

        if (ultimoDigito == '0') {
            return SituacionFiscal.NO_ENCONTRADO;
        }
        int digito = Character.getNumericValue(ultimoDigito);
        return digito % 2 == 0 ? SituacionFiscal.ACTIVO : SituacionFiscal.INHABILITADO;
    }
}
