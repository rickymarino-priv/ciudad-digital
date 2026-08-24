package ar.com.ciudaddigital.acceso.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Una capacidad concreta del sistema, identificada por {@code modulo.accion}
 * (ADR 0011).
 *
 * <p>El catálogo lo define el sistema y se siembra por migración: un
 * permiso existe porque hay código que lo verifica, así que el municipio no
 * los crea ni los edita.
 */
@Entity
@Table(name = "permiso")
class PermisoEntity {

    @Id
    @Column(length = 100)
    private String codigo;

    @Column(nullable = false, length = 60)
    private String area;

    @Column(nullable = false, length = 60)
    private String modulo;

    @Column(nullable = false, length = 60)
    private String accion;

    @Column(nullable = false, length = 300)
    private String descripcion;

    protected PermisoEntity() {
    }

    String getCodigo() {
        return codigo;
    }

    String getArea() {
        return area;
    }

    String getModulo() {
        return modulo;
    }

    String getAccion() {
        return accion;
    }

    String getDescripcion() {
        return descripcion;
    }
}
