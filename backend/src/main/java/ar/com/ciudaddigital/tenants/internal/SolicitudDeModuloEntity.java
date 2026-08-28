package ar.com.ciudaddigital.tenants.internal;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Fila de la tabla {@code solicitud_modulo}, en la base de control (ADR
 * 0022 §2): un pedido de un municipio de alta o baja de un módulo, dato
 * contractual, no operativo, mismo criterio que {@link TenantEntity#getTramoPoblacional()}.
 *
 * <p>Crear o atender una solicitud nunca prende ni apaga el módulo: eso
 * sigue siendo, exclusivamente, {@link AdministracionDeModulos} (ADR 0012
 * §8). Esta entidad solo deja constancia del pedido y de si alguien ya lo
 * atendió.
 */
@Entity
@Table(name = "solicitud_modulo")
class SolicitudDeModuloEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "modulo_codigo")
    private String moduloCodigo;

    @Enumerated(EnumType.STRING)
    private TipoDeSolicitudDeModulo tipo;

    private String justificacion;

    @Enumerated(EnumType.STRING)
    private EstadoDeSolicitudDeModulo estado;

    @Column(name = "solicitada_por_nombre")
    private String solicitadaPorNombre;

    @Column(name = "solicitada_por_email")
    private String solicitadaPorEmail;

    @Column(name = "creada_en")
    private OffsetDateTime creadaEn;

    @Column(name = "atendida_en")
    private OffsetDateTime atendidaEn;

    protected SolicitudDeModuloEntity() {
        // Requerido por JPA.
    }

    /** Solicitud recién creada por el municipio: arranca {@code PENDIENTE}, sin atender. */
    static SolicitudDeModuloEntity nueva(UUID tenantId, String moduloCodigo,
            TipoDeSolicitudDeModulo tipo, String justificacion, String solicitadaPorNombre,
            String solicitadaPorEmail) {

        SolicitudDeModuloEntity solicitud = new SolicitudDeModuloEntity();
        solicitud.tenantId = tenantId;
        solicitud.moduloCodigo = moduloCodigo;
        solicitud.tipo = tipo;
        solicitud.justificacion = justificacion;
        solicitud.estado = EstadoDeSolicitudDeModulo.PENDIENTE;
        solicitud.solicitadaPorNombre = solicitadaPorNombre;
        solicitud.solicitadaPorEmail = solicitadaPorEmail;
        solicitud.creadaEn = OffsetDateTime.now();
        return solicitud;
    }

    /**
     * Marca la solicitud como resuelta por la plataforma. No cambia el
     * entitlement del municipio (ADR 0022 §3): eso, si corresponde, ya lo
     * hizo la plataforma por fuera, con el mecanismo existente.
     *
     * @throws IllegalStateException si la solicitud no está {@code PENDIENTE}: la pantalla que
     *         consume esto no ofrece "atender" dos veces, así que no hace falta una excepción de
     *         negocio propia para un caso que el frontend no puede disparar.
     */
    void marcarAtendida() {
        if (estado != EstadoDeSolicitudDeModulo.PENDIENTE) {
            throw new IllegalStateException("La solicitud " + id + " ya fue atendida.");
        }
        this.estado = EstadoDeSolicitudDeModulo.ATENDIDA;
        this.atendidaEn = OffsetDateTime.now();
    }

    Long getId() {
        return id;
    }

    UUID getTenantId() {
        return tenantId;
    }

    String getModuloCodigo() {
        return moduloCodigo;
    }

    TipoDeSolicitudDeModulo getTipo() {
        return tipo;
    }

    String getJustificacion() {
        return justificacion;
    }

    EstadoDeSolicitudDeModulo getEstado() {
        return estado;
    }

    String getSolicitadaPorNombre() {
        return solicitadaPorNombre;
    }

    String getSolicitadaPorEmail() {
        return solicitadaPorEmail;
    }

    OffsetDateTime getCreadaEn() {
        return creadaEn;
    }

    OffsetDateTime getAtendidaEn() {
        return atendidaEn;
    }
}
