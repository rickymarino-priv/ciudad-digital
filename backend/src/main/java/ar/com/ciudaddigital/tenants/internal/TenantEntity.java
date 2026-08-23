package ar.com.ciudaddigital.tenants.internal;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import ar.com.ciudaddigital.tenants.TenantInfo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Fila de la tabla {@code tenant} en la base de control.
 *
 * <p>Vive solo dentro de este módulo: hacia afuera se expone
 * {@link TenantInfo}, que no lleva configuración visual ni comercial.
 */
@Entity
@Table(name = "tenant")
class TenantEntity {

    @Id
    private UUID id;

    private String slug;

    @Column(name = "nombre_municipio")
    private String nombreMunicipio;

    private String subdominio;

    @Column(name = "dominio_personalizado")
    private String dominioPersonalizado;

    @Enumerated(EnumType.STRING)
    private EstadoTenant estado;

    @Column(name = "nombre_base_datos")
    private String nombreBaseDatos;

    @Column(name = "fecha_alta")
    private OffsetDateTime fechaAlta;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private TenantConfig config;

    protected TenantEntity() {
        // Requerido por JPA.
    }

    /**
     * Municipio recién solicitado, todavía sin base propia.
     *
     * <p>Nace {@link EstadoTenant#PENDIENTE}: no atiende requests hasta que
     * el aprovisionamiento termine bien.
     */
    static TenantEntity nueva(String slug, String nombreMunicipio, String subdominio,
            String nombreBaseDatos, TenantConfig config) {
        TenantEntity tenant = new TenantEntity();
        tenant.id = UUID.randomUUID();
        tenant.slug = slug;
        tenant.nombreMunicipio = nombreMunicipio;
        tenant.subdominio = subdominio;
        tenant.nombreBaseDatos = nombreBaseDatos;
        tenant.estado = EstadoTenant.PENDIENTE;
        tenant.fechaAlta = OffsetDateTime.now();
        tenant.config = config;
        return tenant;
    }

    void cambiarEstado(EstadoTenant nuevo) {
        this.estado = nuevo;
    }

    TenantInfo aTenantInfo() {
        return new TenantInfo(id, slug, nombreMunicipio, nombreBaseDatos);
    }

    UUID getId() {
        return id;
    }

    String getSlug() {
        return slug;
    }

    String getNombreMunicipio() {
        return nombreMunicipio;
    }

    String getSubdominio() {
        return subdominio;
    }

    String getDominioPersonalizado() {
        return dominioPersonalizado;
    }

    EstadoTenant getEstado() {
        return estado;
    }

    String getNombreBaseDatos() {
        return nombreBaseDatos;
    }

    OffsetDateTime getFechaAlta() {
        return fechaAlta;
    }

    TenantConfig getConfig() {
        return config;
    }
}
