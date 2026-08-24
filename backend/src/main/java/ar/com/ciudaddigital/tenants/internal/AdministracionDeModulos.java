package ar.com.ciudaddigital.tenants.internal;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import ar.com.ciudaddigital.entitlement.CatalogoDeModulos;
import ar.com.ciudaddigital.entitlement.DescriptorDeModulo;
import ar.com.ciudaddigital.tenants.internal.AltaDeMunicipio.SolicitudInvalida;
import ar.com.ciudaddigital.tenants.internal.TenantConfig.Tema;

/**
 * Módulos contratados de un municipio: catálogo, validación de códigos y
 * el cambio de la lista habilitada (ADR 0012 §8).
 *
 * <p>Prender y apagar módulos es una operación de plataforma, no del
 * municipio: la usan {@link AdministracionDeMunicipiosController} —la API
 * cross-tenant, con sesión de usuario de plataforma— y {@link
 * AltaDeMunicipio}, para validar los módulos que vienen en la solicitud de
 * alta. No hay ninguna superficie de esto en el portal de municipio.
 */
@Service
class AdministracionDeModulos {

    private final TenantRepository repositorio;
    private final CatalogoDeModulos catalogo;

    AdministracionDeModulos(TenantRepository repositorio, CatalogoDeModulos catalogo) {
        this.repositorio = repositorio;
        this.catalogo = catalogo;
    }

    List<DescriptorDeModulo> catalogoCompleto() {
        return catalogo.catalogo();
    }

    TenantEntity municipio(String slug) {
        return repositorio.findBySlugIgnoreCase(slug == null ? "" : slug.trim())
                .orElseThrow(() -> new SolicitudInvalida(
                        "No hay ningún municipio con el identificador " + slug + "."));
    }

    /**
     * Valida una lista de códigos de módulo contra el catálogo y colapsa
     * duplicados. Un código desconocido rechaza la solicitud entera antes
     * de tocar nada: un código habilitado que no corresponde a ningún
     * descriptor no se acepta al escribir (ADR 0012 §1). {@code null} se
     * trata como lista vacía —lo usa el alta de municipio, donde "no
     * mandé módulos" es válido—; la API de administración exige la lista
     * explícita antes de llegar acá.
     */
    List<String> validarCatalogoDeModulos(List<String> codigos) {
        List<String> normalizados = codigos == null ? List.of() : codigos.stream().distinct().toList();

        Set<String> validos = catalogo.catalogo().stream()
                .map(DescriptorDeModulo::codigo)
                .collect(Collectors.toSet());

        for (String codigo : normalizados) {
            if (!validos.contains(codigo)) {
                throw new SolicitudInvalida("No existe el módulo " + codigo + ".");
            }
        }
        return normalizados;
    }

    /**
     * Reemplaza la lista completa de módulos habilitados de un municipio.
     * Conserva el tema: {@code config} guarda tema y módulos juntos
     * (ADR 0007), así que reemplazarla entera pisaría la identidad visual
     * si no se preservara explícitamente.
     */
    TenantEntity reemplazarModulos(String slug, List<String> codigosSolicitados) {
        List<String> normalizados = validarCatalogoDeModulos(codigosSolicitados);

        TenantEntity tenant = municipio(slug);
        Tema tema = tenant.getConfig() == null ? null : tenant.getConfig().tema();
        tenant.cambiarConfig(new TenantConfig(tema, normalizados));
        return repositorio.save(tenant);
    }

    /** Estado de cada módulo del catálogo para un municipio puntual. */
    List<ModuloDeMunicipio> describir(TenantEntity tenant) {
        Set<String> habilitados = tenant.getConfig() == null
                ? Set.of()
                : Set.copyOf(tenant.getConfig().modulosHabilitados());

        return catalogo.catalogo().stream()
                .map(descriptor -> new ModuloDeMunicipio(
                        descriptor.codigo(), descriptor.nombre(), descriptor.descripcion(),
                        habilitados.contains(descriptor.codigo())))
                .toList();
    }

    record ModuloDeMunicipio(String codigo, String nombre, String descripcion, boolean habilitado) {
    }
}
