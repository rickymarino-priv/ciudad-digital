package ar.com.ciudaddigital.desarrollosocial.internal;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Una inscripción de un vecino a un programa social de este municipio
 * (ADR 0025 §4). Vive en la base del municipio, sin columna de tenant.
 *
 * <p>A diferencia de {@code ReclamoEntity}, {@code contacto} es
 * obligatorio: el municipio necesita poder contactar a la familia para
 * gestionar la ayuda, no es un dato informativo opcional (ADR 0025 §4).
 * {@code cantidadIntegrantesGrupoFamiliar} y {@code situacionDeclarada}
 * son los únicos datos de elegibilidad, deliberadamente pobres en
 * detalle: nunca un monto de ingreso, un comprobante ni la composición
 * nominal del grupo familiar.
 *
 * <p>{@code tokenHash} es el hash SHA-256 del token de seguimiento
 * anónimo (ADR 0017): el token en claro nunca llega a esta entidad, ni
 * tiene getter que lo exponga, mismo criterio que {@code ReclamoEntity}.
 *
 * <p>Esta entidad sí muta después de creada, pero solo su {@code estado}
 * y los campos de resolución (ADR 0025 §8): el resto de los datos del
 * alta no tiene ningún método de edición a propósito. La validación de
 * qué transición es válida, y de qué transiciones exigen comentario, vive
 * en {@code GestionDeInscripcionesSociales}, no acá — esta entidad no
 * sabe qué transiciones son válidas, solo aplica la que ya fue validada
 * (mismo criterio que {@code ObraPublicaEntity#actualizarEstado}).
 */
@Entity
@Table(name = "inscripcion_social")
class InscripcionSocialEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "programa_id", nullable = false)
    private Long programaId;

    @Column(name = "nombre_solicitante", nullable = false, length = 150)
    private String nombreSolicitante;

    @Column(name = "dni_solicitante", nullable = false, length = 20)
    private String dniSolicitante;

    @Column(nullable = false, length = 200)
    private String contacto;

    @Column(name = "cantidad_integrantes_grupo_familiar", nullable = false)
    private Integer cantidadIntegrantesGrupoFamiliar;

    @Enumerated(EnumType.STRING)
    @Column(name = "situacion_declarada", nullable = false, length = 30)
    private SituacionDeclarada situacionDeclarada;

    @Column(name = "comentario_adicional")
    private String comentarioAdicional;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private EstadoDeInscripcion estado;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "comentario_de_resolucion")
    private String comentarioDeResolucion;

    @Column(name = "resuelto_por_nombre", length = 150)
    private String resueltoPorNombre;

    @Column(name = "resuelto_por_email", length = 200)
    private String resueltoPorEmail;

    @Column(name = "resuelto_en")
    private Instant resueltoEn;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected InscripcionSocialEntity() {
    }

    /**
     * {@code tokenHash} llega ya calculado: esta entidad no depende de
     * {@code seguimientoanonimo}, es {@code GestionDeInscripcionesSociales}
     * quien genera el token y calcula su hash (ADR 0017 §4). El token en
     * claro nunca llega hasta acá. El estado inicial no es un parámetro:
     * siempre nace {@code RECIBIDA}.
     */
    static InscripcionSocialEntity inscribir(Long programaId, String nombreSolicitante, String dniSolicitante,
            String contacto, Integer cantidadIntegrantesGrupoFamiliar, SituacionDeclarada situacionDeclarada,
            String comentarioAdicional, String tokenHash) {

        InscripcionSocialEntity inscripcion = new InscripcionSocialEntity();
        inscripcion.programaId = programaId;
        inscripcion.nombreSolicitante = nombreSolicitante;
        inscripcion.dniSolicitante = dniSolicitante;
        inscripcion.contacto = contacto;
        inscripcion.cantidadIntegrantesGrupoFamiliar = cantidadIntegrantesGrupoFamiliar;
        inscripcion.situacionDeclarada = situacionDeclarada;
        inscripcion.comentarioAdicional = comentarioAdicional;
        inscripcion.estado = EstadoDeInscripcion.RECIBIDA;
        inscripcion.tokenHash = tokenHash;
        inscripcion.creadoEn = Instant.now();
        inscripcion.actualizadoEn = inscripcion.creadoEn;
        return inscripcion;
    }

    Long getId() {
        return id;
    }

    Long getProgramaId() {
        return programaId;
    }

    String getNombreSolicitante() {
        return nombreSolicitante;
    }

    String getDniSolicitante() {
        return dniSolicitante;
    }

    String getContacto() {
        return contacto;
    }

    Integer getCantidadIntegrantesGrupoFamiliar() {
        return cantidadIntegrantesGrupoFamiliar;
    }

    SituacionDeclarada getSituacionDeclarada() {
        return situacionDeclarada;
    }

    String getComentarioAdicional() {
        return comentarioAdicional;
    }

    EstadoDeInscripcion getEstado() {
        return estado;
    }

    String getComentarioDeResolucion() {
        return comentarioDeResolucion;
    }

    String getResueltoPorNombre() {
        return resueltoPorNombre;
    }

    String getResueltoPorEmail() {
        return resueltoPorEmail;
    }

    Instant getResueltoEn() {
        return resueltoEn;
    }

    Instant getCreadoEn() {
        return creadoEn;
    }

    Instant getActualizadoEn() {
        return actualizadoEn;
    }

    /**
     * Aplica el estado nuevo, ya validado por {@code
     * GestionDeInscripcionesSociales} contra la tabla de transiciones (ADR
     * 0025 §8). Los campos de resolución solo se completan cuando el
     * destino es terminal ({@code APROBADA}/{@code RECHAZADA}): pasar a
     * {@code EN_EVALUACION} no los pisa, mismo criterio con el que el
     * servicio ya decide si el comentario es obligatorio u opcional.
     */
    void actualizarEstado(EstadoDeInscripcion estadoNuevo, String comentarioDeResolucion,
            String resueltoPorNombre, String resueltoPorEmail) {

        this.estado = estadoNuevo;
        this.actualizadoEn = Instant.now();
        if (estadoNuevo == EstadoDeInscripcion.APROBADA || estadoNuevo == EstadoDeInscripcion.RECHAZADA) {
            this.comentarioDeResolucion = comentarioDeResolucion;
            this.resueltoPorNombre = resueltoPorNombre;
            this.resueltoPorEmail = resueltoPorEmail;
            this.resueltoEn = Instant.now();
        }
    }
}
