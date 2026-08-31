package ar.com.ciudaddigital.prensa.internal;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publicación y búsqueda de gacetillas de prensa del municipio del request
 * en curso (ADR 0027).
 */
@Service
class GestionDePrensa {

    private static final int LARGO_MAXIMO_TITULO = 300;
    private static final int LARGO_MAXIMO_PUBLICADO_POR_NOMBRE = 150;
    private static final int LARGO_MAXIMO_PUBLICADO_POR_EMAIL = 200;

    private final GacetillaRepository gacetillas;

    GestionDePrensa(GacetillaRepository gacetillas) {
        this.gacetillas = gacetillas;
    }

    @Transactional("tenantTransactionManager")
    GacetillaEntity publicar(CategoriaDeGacetilla categoria, String titulo, String texto,
            LocalDate fechaPublicacion, String publicadoPorNombre, String publicadoPorEmail) {

        if (categoria == null) {
            throw new SolicitudInvalida("Hay que indicar una categoría.");
        }
        if (titulo == null || titulo.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar un título.");
        }
        if (titulo.length() > LARGO_MAXIMO_TITULO) {
            throw new SolicitudInvalida("El título no puede superar los " + LARGO_MAXIMO_TITULO + " caracteres.");
        }
        if (texto == null || texto.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar el texto de la gacetilla.");
        }
        if (fechaPublicacion == null) {
            throw new SolicitudInvalida("Hay que indicar la fecha de publicación.");
        }
        // publicadoPorNombre/publicadoPorEmail salen del actor autenticado, no de la
        // solicitud: si alguno faltara sería un problema del mecanismo de
        // autenticación, no una solicitud inválida del usuario (por eso no lleva
        // acá los mismos mensajes de SolicitudInvalida que el resto de los campos).
        if (publicadoPorNombre != null && publicadoPorNombre.length() > LARGO_MAXIMO_PUBLICADO_POR_NOMBRE) {
            throw new SolicitudInvalida(
                    "El nombre de quien publica no puede superar los "
                            + LARGO_MAXIMO_PUBLICADO_POR_NOMBRE + " caracteres.");
        }
        if (publicadoPorEmail != null && publicadoPorEmail.length() > LARGO_MAXIMO_PUBLICADO_POR_EMAIL) {
            throw new SolicitudInvalida(
                    "El correo de quien publica no puede superar los "
                            + LARGO_MAXIMO_PUBLICADO_POR_EMAIL + " caracteres.");
        }

        GacetillaEntity gacetilla = GacetillaEntity.nueva(categoria, titulo, texto, fechaPublicacion,
                publicadoPorNombre, publicadoPorEmail);
        return gacetillas.save(gacetilla);
    }

    /**
     * {@code textoEnTitulo} vacío o en blanco se trata como "sin filtro de
     * texto", no como una búsqueda del string vacío. El patrón de
     * {@code LIKE} se arma acá, no en el repositorio (ver el Javadoc de
     * {@link GacetillaRepository#buscar}).
     */
    List<GacetillaEntity> buscar(CategoriaDeGacetilla categoria, String textoEnTitulo) {
        String patron = textoEnTitulo == null || textoEnTitulo.isBlank()
                ? null
                : "%" + textoEnTitulo.trim().toLowerCase() + "%";
        return gacetillas.buscar(categoria, patron);
    }
}
