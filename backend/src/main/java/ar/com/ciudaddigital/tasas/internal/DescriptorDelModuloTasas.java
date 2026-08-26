package ar.com.ciudaddigital.tasas.internal;

import java.util.List;

import org.springframework.stereotype.Component;

import ar.com.ciudaddigital.entitlement.DescriptorDeModulo;

/**
 * Declara el módulo {@code tasas} ante el catálogo de entitlement (ADR
 * 0012 §1). Mismo patrón que {@code DescriptorDelModuloReclamos}: lectura
 * y una parte de la escritura son públicas, publicar una tasa requiere
 * sesión y permiso.
 */
@Component
class DescriptorDelModuloTasas implements DescriptorDeModulo {

    static final String CODIGO = "tasas";

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public String nombre() {
        return "Tasas municipales";
    }

    @Override
    public String descripcion() {
        return "Alta de tasas municipales por número de cuenta y su pago online, con un "
                + "adaptador de pago simulado para desarrollo y demo (ADR 0018).";
    }

    @Override
    public List<String> prefijosDeApi() {
        return List.of("/api/tasas");
    }

    /**
     * Solo la búsqueda por número de cuenta es pública para lectura: no
     * hay ningún listado abierto de todas las tasas del municipio (ver el
     * porqué en {@code GestionDeTasas#buscarPorCuenta}).
     */
    @Override
    public List<String> rutasDeLecturaPublica() {
        return List.of("/api/tasas");
    }

    /**
     * Iniciar y confirmar un pago son escrituras anónimas legítimas (ADR
     * 0018 §4): un vecino sin cuenta paga su tasa, y —en el adaptador
     * simulado— el propio frontend confirma el resultado haciendo de
     * pasarela. Publicar una tasa no está acá: requiere sesión y el
     * permiso {@code tasas.publicar}.
     */
    @Override
    public List<String> rutasDeEscrituraPublica() {
        return List.of("/api/tasas/{id}/pagos", "/api/tasas/pagos/confirmar");
    }
}
