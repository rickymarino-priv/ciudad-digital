package ar.com.ciudaddigital.municipio.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Datos de contacto del municipio.
 *
 * <p>Vive en la base propia del tenant, no en la base de control: es
 * información del municipio, no del registro de municipios.
 */
@Entity
@Table(name = "datos_de_contacto")
class DatosDeContactoEntity {

    /** Fila única: son los datos del municipio dueño de esta base. */
    static final int ID_UNICO = 1;

    @Id
    private Integer id;

    @Column(name = "direccion")
    private String direccion;

    @Column(name = "telefono")
    private String telefono;

    @Column(name = "email")
    private String email;

    protected DatosDeContactoEntity() {
        // Requerido por JPA.
    }

    String getDireccion() {
        return direccion;
    }

    String getTelefono() {
        return telefono;
    }

    String getEmail() {
        return email;
    }
}
