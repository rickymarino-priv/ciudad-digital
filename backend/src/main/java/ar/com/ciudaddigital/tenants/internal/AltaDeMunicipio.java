package ar.com.ciudaddigital.tenants.internal;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ar.com.ciudaddigital.tenants.internal.TenantConfig.Tema;

/**
 * Da de alta un municipio nuevo de punta a punta (ADR 0005).
 *
 * <p>El alta avanza por estados explícitos —{@code PENDIENTE →
 * APROVISIONANDO → ACTIVO}, o {@code ERROR}— y cada cambio se persiste
 * apenas ocurre. Esa es la diferencia con un script: si el alta falla a
 * mitad de camino, queda registrado en qué estado quedó el municipio en
 * vez de perderse.
 */
@Service
class AltaDeMunicipio {

    private static final Logger log = LoggerFactory.getLogger(AltaDeMunicipio.class);

    /**
     * Slug admitido: es la base del nombre de la base de datos y del
     * subdominio, así que no puede tener nada que no sea seguro en ninguno
     * de los dos lugares.
     */
    private static final Pattern SLUG_VALIDO = Pattern.compile("^[a-z][a-z0-9]{2,40}$");

    private static final String PREFIJO_BASE = "tenant_";

    private final TenantRepository repositorio;
    private final CreadorDeBaseDeTenant creador;
    private final MigradorDeTenant migrador;
    private final SembradorDeTenant sembrador;

    AltaDeMunicipio(TenantRepository repositorio, CreadorDeBaseDeTenant creador,
            MigradorDeTenant migrador, SembradorDeTenant sembrador) {
        this.repositorio = repositorio;
        this.creador = creador;
        this.migrador = migrador;
        this.sembrador = sembrador;
    }

    TenantEntity darDeAlta(SolicitudDeAlta solicitud) {
        validar(solicitud);

        String slug = solicitud.slug().toLowerCase(Locale.ROOT);
        String nombreBaseDatos = PREFIJO_BASE + slug;

        TenantEntity tenant = repositorio.save(TenantEntity.nueva(
                slug,
                solicitud.nombreMunicipio(),
                slug,
                nombreBaseDatos,
                new TenantConfig(solicitud.tema(), List.of())));

        tenant.cambiarEstado(EstadoTenant.APROVISIONANDO);
        repositorio.save(tenant);

        try {
            creador.crear(nombreBaseDatos);
            migrador.migrar(nombreBaseDatos);
            sembrador.sembrarDatosDeContacto(nombreBaseDatos, solicitud.direccion(),
                    solicitud.telefono(), solicitud.email());

            tenant.cambiarEstado(EstadoTenant.ACTIVO);
            log.info("Municipio {} dado de alta en la base {}", slug, nombreBaseDatos);

        } catch (RuntimeException e) {
            // El municipio queda registrado en ERROR, no se borra: hace
            // falta saber que el alta se intentó y falló, y por qué.
            tenant.cambiarEstado(EstadoTenant.ERROR);
            repositorio.save(tenant);
            log.error("Falló el alta del municipio {}", slug, e);
            throw e;
        }

        return repositorio.save(tenant);
    }

    private void validar(SolicitudDeAlta solicitud) {
        String slug = solicitud.slug() == null ? "" : solicitud.slug().toLowerCase(Locale.ROOT);

        if (!SLUG_VALIDO.matcher(slug).matches()) {
            throw new SolicitudInvalida(
                    "El identificador del municipio debe tener entre 3 y 41 caracteres, "
                            + "empezar con una letra y contener solo letras y números "
                            + "minúsculos.");
        }
        if (repositorio.existsBySlugIgnoreCase(slug)) {
            throw new SolicitudInvalida("Ya existe un municipio con el identificador " + slug + ".");
        }
        if (repositorio.existsBySubdominioIgnoreCase(slug)) {
            throw new SolicitudInvalida("Ya hay un municipio publicado en el subdominio " + slug + ".");
        }
        if (creador.existe(PREFIJO_BASE + slug)) {
            // Puede pasar si un alta anterior falló después de crear la
            // base: mejor avisar que pisar datos de alguien.
            throw new SolicitudInvalida(
                    "Ya existe una base de datos para el municipio " + slug
                            + ". Hay que revisarla antes de volver a intentar el alta.");
        }
    }

    record SolicitudDeAlta(
            String slug,
            String nombreMunicipio,
            String direccion,
            String telefono,
            String email,
            Tema tema) {
    }

    static class SolicitudInvalida extends RuntimeException {
        SolicitudInvalida(String mensaje) {
            super(mensaje);
        }
    }
}
