package ar.com.ciudaddigital.entitlement.internal;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.com.ciudaddigital.entitlement.CatalogoDeModulos;

/**
 * Catálogo de módulos contratables, con el estado de cada uno para el
 * municipio del request en curso (ADR 0012 §7).
 *
 * <p>Es público: el portal lo necesita para pintarse antes de que haya
 * sesión, y qué módulos ofrece el producto —y cuáles tiene contratados un
 * municipio, que se ve en su propio portal de todos modos— no es
 * información protegida. El enforcement real es {@link
 * GatingDeModulosFilter}, no ocultar esto.
 */
@RestController
@RequestMapping("/api/modulos")
class ModulosController {

    private final CatalogoDeModulos catalogo;

    ModulosController(CatalogoDeModulos catalogo) {
        this.catalogo = catalogo;
    }

    @GetMapping
    List<ModuloResponse> listar() {
        return catalogo.catalogo().stream()
                .map(descriptor -> new ModuloResponse(
                        descriptor.codigo(), descriptor.nombre(), descriptor.descripcion(),
                        catalogo.habilitado(descriptor.codigo())))
                .toList();
    }

    record ModuloResponse(String codigo, String nombre, String descripcion, boolean habilitado) {
    }
}
