package ar.com.ciudaddigital.mesaentradas.internal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * Un trámite iniciado en Mesa de Entradas de este municipio (backlog R9,
 * ADR 0015).
 *
 * <p>Vive en la base del municipio: sin columna de tenant, igual que
 * {@code reclamo}/{@code norma}/{@code sepultura}. {@code solicitanteNombre}
 * es obligatorio —a diferencia de {@code reclamos.nombreContacto}, un
 * certificado se emite a nombre de alguien—, {@code solicitanteContacto} es
 * opcional, mismo criterio que {@code reclamos.contacto} (ADR 0014 §4).
 * Los cinco campos propios de los tres tipos de trámite
 * ({@code domicilioACertificar}, {@code rubroComercial},
 * {@code direccionLocal}, {@code direccionObra}, {@code descripcionObra})
 * son columnas explícitas nullable, cada una obligatoria solo para su
 * tipo —garantizado por el {@code check} de la migración, no por la
 * columna sola— en vez de un JSON de datos variables por tipo (ADR 0016).
 *
 * <p>El historial de movimientos vive siempre junto al expediente (ADR 0015
 * §2): no hace falta un repositorio propio para
 * {@link MovimientoDeExpedienteEntity}, se persiste en cascada acá.
 */
@Entity
@Table(name = "expediente")
class ExpedienteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoDeTramite tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoDeExpediente estado;

    @Column(name = "solicitante_nombre", nullable = false, length = 200)
    private String solicitanteNombre;

    @Column(name = "solicitante_contacto", length = 200)
    private String solicitanteContacto;

    @Column(name = "domicilio_a_certificar", length = 300)
    private String domicilioACertificar;

    @Column(name = "rubro_comercial", length = 200)
    private String rubroComercial;

    @Column(name = "direccion_local", length = 300)
    private String direccionLocal;

    @Column(name = "direccion_obra", length = 300)
    private String direccionObra;

    @Column(name = "descripcion_obra", length = 500)
    private String descripcionObra;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    // EAGER, no el default LAZY de @OneToMany: la aplicación corre con
    // spring.jpa.open-in-view=false (sin sesión abierta más allá de la
    // transacción del service), y GestionDeExpedientes.listar()/avanzar()
    // siempre devuelven el expediente con su historial completo — es la
    // propia respuesta pública del módulo (spec CD-17), no un dato que se
    // acceda a veces sí, a veces no. Con ese patrón de acceso, EAGER es más
    // simple que forzar la inicialización a mano en cada método del
    // servicio o sumar un JOIN FETCH al repositorio.
    @OneToMany(mappedBy = "expediente", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("fecha asc")
    private List<MovimientoDeExpedienteEntity> movimientos = new ArrayList<>();

    protected ExpedienteEntity() {
    }

    static ExpedienteEntity nuevo(TipoDeTramite tipo, String solicitanteNombre, String solicitanteContacto,
            DatosPropiosDelTramite datos) {

        ExpedienteEntity expediente = new ExpedienteEntity();
        expediente.tipo = tipo;
        expediente.estado = CircuitosDeTramite.de(tipo).estadoInicial();
        expediente.solicitanteNombre = solicitanteNombre;
        expediente.solicitanteContacto = solicitanteContacto;
        expediente.domicilioACertificar = datos.domicilioACertificar();
        expediente.rubroComercial = datos.rubroComercial();
        expediente.direccionLocal = datos.direccionLocal();
        expediente.direccionObra = datos.direccionObra();
        expediente.descripcionObra = datos.descripcionObra();
        expediente.creadoEn = Instant.now();
        expediente.actualizadoEn = expediente.creadoEn;
        expediente.agregarMovimiento(MovimientoDeExpedienteEntity.deAlta(expediente.estado));
        return expediente;
    }

    Long getId() {
        return id;
    }

    TipoDeTramite getTipo() {
        return tipo;
    }

    EstadoDeExpediente getEstado() {
        return estado;
    }

    String getSolicitanteNombre() {
        return solicitanteNombre;
    }

    String getSolicitanteContacto() {
        return solicitanteContacto;
    }

    String getDomicilioACertificar() {
        return domicilioACertificar;
    }

    String getRubroComercial() {
        return rubroComercial;
    }

    String getDireccionLocal() {
        return direccionLocal;
    }

    String getDireccionObra() {
        return direccionObra;
    }

    String getDescripcionObra() {
        return descripcionObra;
    }

    Instant getCreadoEn() {
        return creadoEn;
    }

    Instant getActualizadoEn() {
        return actualizadoEn;
    }

    List<MovimientoDeExpedienteEntity> getMovimientos() {
        return movimientos;
    }

    /**
     * Fija el nuevo estado y agrega el movimiento correspondiente al
     * historial. <strong>No valida</strong> la transición: esa tabla vive
     * en {@code GestionDeExpedientes}/{@code CircuitosDeTramite}, mismo
     * criterio que {@code ReclamoEntity.cambiarEstado} (ADR 0014 §3) — esta
     * entidad no sabe qué transiciones son válidas, solo aplica la que ya
     * fue validada.
     */
    void avanzar(EstadoDeExpediente nuevoEstado, String actorNombre, String actorEmail, String comentario) {
        EstadoDeExpediente estadoAnterior = this.estado;
        this.estado = nuevoEstado;
        this.actualizadoEn = Instant.now();
        agregarMovimiento(
                MovimientoDeExpedienteEntity.deAvance(estadoAnterior, nuevoEstado, actorNombre, actorEmail,
                        comentario));
    }

    private void agregarMovimiento(MovimientoDeExpedienteEntity movimiento) {
        movimiento.fijarExpediente(this);
        movimientos.add(movimiento);
    }
}
