package ar.com.ciudaddigital.tenants.internal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.com.ciudaddigital.tenants.TenantContext;
import ar.com.ciudaddigital.tenants.TenantInfo;

/**
 * Identidad del municipio del request: nombre y tokens de tema.
 *
 * <p>Es lo primero que pide el frontend al arrancar, para pintarse con la
 * marca correcta (ADR 0006). Devuelve únicamente datos del tenant resuelto
 * por el host — nunca recibe un identificador de municipio por parámetro,
 * justamente para que no exista forma de pedir la marca de otro.
 */
@RestController
@RequestMapping("/api/tenant")
class TenantThemeController {

    private final TenantRepository repositorio;

    TenantThemeController(TenantRepository repositorio) {
        this.repositorio = repositorio;
    }

    @GetMapping("/tema")
    TemaResponse tema() {
        TenantInfo actual = TenantContext.requerido();

        // Por id y no por slug: el modelo permite que slug y subdominio
        // difieran, y el id es el único identificador siempre exacto.
        TenantEntity entidad = repositorio.findById(actual.id())
                .orElseThrow(() -> new IllegalStateException(
                        "El tenant resuelto ya no está en la base de control: " + actual.slug()));

        TenantConfig config = entidad.getConfig();
        return new TemaResponse(
                entidad.getSlug(),
                entidad.getNombreMunicipio(),
                config == null ? null : config.tema());
    }

    record TemaResponse(String slug, String nombreMunicipio, TenantConfig.Tema tema) {
    }
}
