package ar.com.ciudaddigital.boletin.internal;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publicación y búsqueda de normas del Boletín Oficial del municipio del
 * request en curso (backlog R7).
 */
@Service
class GestionDelBoletin {

    /** Recorte defensivo: es un acto legal del municipio, no se trunca en silencio, se rechaza. */
    private static final int LARGO_MAXIMO_NUMERO = 50;
    private static final int LARGO_MAXIMO_TITULO = 300;
    private static final int LARGO_MAXIMO_PUBLICADO_POR_NOMBRE = 150;
    private static final int LARGO_MAXIMO_PUBLICADO_POR_EMAIL = 200;

    private final NormaRepository normas;

    GestionDelBoletin(NormaRepository normas) {
        this.normas = normas;
    }

    @Transactional("tenantTransactionManager")
    NormaEntity publicar(TipoDeNorma tipo, String numero, String titulo, String texto,
            LocalDate fechaPublicacion, String publicadoPorNombre, String publicadoPorEmail) {

        if (tipo == null) {
            throw new SolicitudInvalida("Hay que indicar un tipo de norma.");
        }
        if (numero == null || numero.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar un número.");
        }
        if (numero.length() > LARGO_MAXIMO_NUMERO) {
            throw new SolicitudInvalida("El número no puede superar los " + LARGO_MAXIMO_NUMERO + " caracteres.");
        }
        if (titulo == null || titulo.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar un título.");
        }
        if (titulo.length() > LARGO_MAXIMO_TITULO) {
            throw new SolicitudInvalida("El título no puede superar los " + LARGO_MAXIMO_TITULO + " caracteres.");
        }
        if (texto == null || texto.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar el texto de la norma.");
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

        NormaEntity norma = NormaEntity.nueva(tipo, numero, titulo, texto, fechaPublicacion,
                publicadoPorNombre, publicadoPorEmail);
        return normas.save(norma);
    }

    /**
     * {@code textoEnTitulo} vacío o en blanco se trata como "sin filtro de
     * texto", no como una búsqueda del string vacío. El patrón de
     * {@code LIKE} se arma acá, no en el repositorio (ver el Javadoc de
     * {@link NormaRepository#buscar}).
     */
    List<NormaEntity> buscar(TipoDeNorma tipo, String textoEnTitulo) {
        String patron = textoEnTitulo == null || textoEnTitulo.isBlank()
                ? null
                : "%" + textoEnTitulo.trim().toLowerCase() + "%";
        return normas.buscar(tipo, patron);
    }
}
