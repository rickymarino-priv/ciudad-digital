package ar.com.ciudaddigital.proveedores.internal;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import ar.com.ciudaddigital.padronfiscal.SituacionFiscal;

/**
 * Un proveedor registrado por una empresa ante este municipio, sin cuenta
 * (ADR 0014 §1), con la documentación que declara tener y su estado de
 * aprobación.
 *
 * <p>Vive en la base del municipio: no hay columna de tenant porque no
 * hace falta, la base ya es la del municipio. {@code cuit} llega ya
 * normalizado y formateado por {@code GestionDeProveedores}, así que acá
 * no hay ninguna lógica de formato. {@code tokenHash} es el hash SHA-256
 * del token de seguimiento anónimo (ADR 0017): el token en claro nunca
 * llega a esta entidad, ni tiene getter que lo exponga.
 */
@Entity
@Table(name = "proveedor")
class ProveedorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "razon_social", nullable = false, length = 200)
    private String razonSocial;

    @Column(nullable = false, length = 13)
    private String cuit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RubroProveedor rubro;

    @Column(name = "email_contacto", nullable = false, length = 200)
    private String emailContacto;

    @Column(name = "telefono_contacto", nullable = false, length = 50)
    private String telefonoContacto;

    @Column(nullable = false, length = 300)
    private String domicilio;

    @Column(name = "declara_constancia_afip", nullable = false)
    private boolean declaraConstanciaAfip;

    @Column(name = "declara_seguro_responsabilidad_civil", nullable = false)
    private boolean declaraSeguroResponsabilidadCivil;

    @Column(name = "declara_certificado_antecedentes", nullable = false)
    private boolean declaraCertificadoAntecedentes;

    @Column(name = "documentacion_adicional", length = 500)
    private String documentacionAdicional;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoDeProveedor estado;

    @Enumerated(EnumType.STRING)
    @Column(name = "situacion_fiscal", nullable = false, length = 20)
    private SituacionFiscal situacionFiscal;

    @Column(name = "comentario_gestion", length = 1000)
    private String comentarioGestion;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected ProveedorEntity() {
    }

    /**
     * {@code tokenHash} llega ya calculado: esta entidad no depende de
     * {@code seguimientoanonimo}, es {@code GestionDeProveedores} quien
     * genera el token y calcula su hash (ADR 0017 §4). El token en claro
     * nunca llega hasta acá.
     */
    static ProveedorEntity nuevo(String razonSocial, String cuit, RubroProveedor rubro, String emailContacto,
            String telefonoContacto, String domicilio, boolean declaraConstanciaAfip,
            boolean declaraSeguroResponsabilidadCivil, boolean declaraCertificadoAntecedentes,
            String documentacionAdicional, String tokenHash, SituacionFiscal situacionFiscal) {

        ProveedorEntity proveedor = new ProveedorEntity();
        proveedor.razonSocial = razonSocial;
        proveedor.cuit = cuit;
        proveedor.rubro = rubro;
        proveedor.emailContacto = emailContacto;
        proveedor.telefonoContacto = telefonoContacto;
        proveedor.domicilio = domicilio;
        proveedor.declaraConstanciaAfip = declaraConstanciaAfip;
        proveedor.declaraSeguroResponsabilidadCivil = declaraSeguroResponsabilidadCivil;
        proveedor.declaraCertificadoAntecedentes = declaraCertificadoAntecedentes;
        proveedor.documentacionAdicional = documentacionAdicional;
        proveedor.estado = EstadoDeProveedor.PENDIENTE;
        proveedor.tokenHash = tokenHash;
        proveedor.situacionFiscal = situacionFiscal;
        proveedor.creadoEn = Instant.now();
        proveedor.actualizadoEn = proveedor.creadoEn;
        return proveedor;
    }

    Long getId() {
        return id;
    }

    String getRazonSocial() {
        return razonSocial;
    }

    String getCuit() {
        return cuit;
    }

    RubroProveedor getRubro() {
        return rubro;
    }

    String getEmailContacto() {
        return emailContacto;
    }

    String getTelefonoContacto() {
        return telefonoContacto;
    }

    String getDomicilio() {
        return domicilio;
    }

    boolean isDeclaraConstanciaAfip() {
        return declaraConstanciaAfip;
    }

    boolean isDeclaraSeguroResponsabilidadCivil() {
        return declaraSeguroResponsabilidadCivil;
    }

    boolean isDeclaraCertificadoAntecedentes() {
        return declaraCertificadoAntecedentes;
    }

    String getDocumentacionAdicional() {
        return documentacionAdicional;
    }

    EstadoDeProveedor getEstado() {
        return estado;
    }

    SituacionFiscal getSituacionFiscal() {
        return situacionFiscal;
    }

    String getComentarioGestion() {
        return comentarioGestion;
    }

    Instant getCreadoEn() {
        return creadoEn;
    }

    Instant getActualizadoEn() {
        return actualizadoEn;
    }

    /**
     * Fija el estado nuevo y, si viene, el comentario de gestión. No valida
     * la transición: esa tabla vive en {@link GestionDeProveedores}, no acá
     * (ADR 0014 §3) — esta entidad no sabe qué transiciones son válidas, solo
     * aplica la que ya fue validada.
     */
    void cambiarEstado(EstadoDeProveedor nuevoEstado, String comentario) {
        this.estado = nuevoEstado;
        this.actualizadoEn = Instant.now();
        if (comentario != null && !comentario.isBlank()) {
            this.comentarioGestion = comentario;
        }
    }
}
