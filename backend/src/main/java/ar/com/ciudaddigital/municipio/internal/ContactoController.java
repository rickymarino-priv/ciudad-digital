package ar.com.ciudaddigital.municipio.internal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Datos de contacto del municipio del request, leídos de su propia base.
 *
 * <p>Es la prueba visible de que cada municipio guarda sus datos aparte:
 * el mismo endpoint, en dos subdominios distintos, lee de dos bases
 * distintas.
 */
@RestController
@RequestMapping("/api/municipio")
class ContactoController {

    private final DatosDeContactoRepository repositorio;

    ContactoController(DatosDeContactoRepository repositorio) {
        this.repositorio = repositorio;
    }

    @GetMapping("/contacto")
    ResponseEntity<ContactoResponse> contacto() {
        return repositorio.findById(DatosDeContactoEntity.ID_UNICO)
                .map(datos -> new ContactoResponse(
                        datos.getDireccion(), datos.getTelefono(), datos.getEmail()))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    record ContactoResponse(String direccion, String telefono, String email) {
    }
}
