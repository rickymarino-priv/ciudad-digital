package ar.com.ciudaddigital.cementerio.internal;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro y búsqueda de sepulturas del cementerio municipal del municipio
 * del request en curso (backlog R8).
 */
@Service
class GestionDelCementerio {

    private static final int LARGO_MAXIMO_SECTOR = 100;
    private static final int LARGO_MAXIMO_FILA = 50;
    private static final int LARGO_MAXIMO_NUMERO = 50;
    private static final int LARGO_MAXIMO_NOMBRE_DIFUNTO = 200;
    private static final int LARGO_MAXIMO_NOMBRE_TITULAR = 200;
    private static final int LARGO_MAXIMO_CONTACTO_TITULAR = 200;
    private static final int LARGO_MAXIMO_REGISTRADO_POR_NOMBRE = 150;
    private static final int LARGO_MAXIMO_REGISTRADO_POR_EMAIL = 200;

    private final SepulturaRepository sepulturas;

    GestionDelCementerio(SepulturaRepository sepulturas) {
        this.sepulturas = sepulturas;
    }

    @Transactional("tenantTransactionManager")
    SepulturaEntity registrar(TipoDeParcela tipo, String sector, String fila, String numero,
            String nombreDifunto, LocalDate fechaFallecimiento, LocalDate fechaInhumacion,
            String nombreTitular, String contactoTitular, String observaciones,
            String registradoPorNombre, String registradoPorEmail) {

        if (tipo == null) {
            throw new SolicitudInvalida("Hay que indicar un tipo de parcela.");
        }
        if (sector == null || sector.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar el sector.");
        }
        if (sector.length() > LARGO_MAXIMO_SECTOR) {
            throw new SolicitudInvalida("El sector no puede superar los " + LARGO_MAXIMO_SECTOR + " caracteres.");
        }
        if (fila != null && fila.length() > LARGO_MAXIMO_FILA) {
            throw new SolicitudInvalida("La fila no puede superar los " + LARGO_MAXIMO_FILA + " caracteres.");
        }
        if (numero == null || numero.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar el número.");
        }
        if (numero.length() > LARGO_MAXIMO_NUMERO) {
            throw new SolicitudInvalida("El número no puede superar los " + LARGO_MAXIMO_NUMERO + " caracteres.");
        }
        if (nombreDifunto == null || nombreDifunto.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar el nombre del difunto.");
        }
        if (nombreDifunto.length() > LARGO_MAXIMO_NOMBRE_DIFUNTO) {
            throw new SolicitudInvalida(
                    "El nombre del difunto no puede superar los " + LARGO_MAXIMO_NOMBRE_DIFUNTO + " caracteres.");
        }
        if (fechaFallecimiento == null) {
            throw new SolicitudInvalida("Hay que indicar la fecha de fallecimiento.");
        }
        if (fechaInhumacion == null) {
            throw new SolicitudInvalida("Hay que indicar la fecha de inhumación.");
        }
        // Validación de dominio: no se puede sepultar antes de fallecer.
        if (fechaInhumacion.isBefore(fechaFallecimiento)) {
            throw new SolicitudInvalida(
                    "La fecha de inhumación no puede ser anterior a la fecha de fallecimiento.");
        }
        if (nombreTitular != null && nombreTitular.length() > LARGO_MAXIMO_NOMBRE_TITULAR) {
            throw new SolicitudInvalida(
                    "El nombre del titular no puede superar los " + LARGO_MAXIMO_NOMBRE_TITULAR + " caracteres.");
        }
        if (contactoTitular != null && contactoTitular.length() > LARGO_MAXIMO_CONTACTO_TITULAR) {
            throw new SolicitudInvalida(
                    "El contacto del titular no puede superar los "
                            + LARGO_MAXIMO_CONTACTO_TITULAR + " caracteres.");
        }
        // observaciones: columna text, sin cap adicional de servicio, mismo
        // criterio que NormaEntity#texto.

        // registradoPorNombre/registradoPorEmail salen del actor autenticado, no de
        // la solicitud: si alguno faltara sería un problema del mecanismo de
        // autenticación, no una solicitud inválida del agente (por eso no llevan
        // acá los mismos mensajes de SolicitudInvalida que el resto de los campos).
        if (registradoPorNombre != null && registradoPorNombre.length() > LARGO_MAXIMO_REGISTRADO_POR_NOMBRE) {
            throw new SolicitudInvalida(
                    "El nombre de quien registra no puede superar los "
                            + LARGO_MAXIMO_REGISTRADO_POR_NOMBRE + " caracteres.");
        }
        if (registradoPorEmail != null && registradoPorEmail.length() > LARGO_MAXIMO_REGISTRADO_POR_EMAIL) {
            throw new SolicitudInvalida(
                    "El correo de quien registra no puede superar los "
                            + LARGO_MAXIMO_REGISTRADO_POR_EMAIL + " caracteres.");
        }

        SepulturaEntity sepultura = SepulturaEntity.nueva(tipo, sector, fila, numero, nombreDifunto,
                fechaFallecimiento, fechaInhumacion, nombreTitular, contactoTitular, observaciones,
                registradoPorNombre, registradoPorEmail);
        return sepulturas.save(sepultura);
    }

    /**
     * {@code textoEnNombreDifunto} vacío o en blanco se trata como "sin
     * filtro de texto", no como una búsqueda del string vacío. El patrón
     * de {@code LIKE} se arma acá, no en el repositorio (ver el Javadoc de
     * {@link SepulturaRepository#buscar}).
     */
    List<SepulturaEntity> buscar(TipoDeParcela tipo, String textoEnNombreDifunto) {
        String patron = textoEnNombreDifunto == null || textoEnNombreDifunto.isBlank()
                ? null
                : "%" + textoEnNombreDifunto.trim().toLowerCase() + "%";
        return sepulturas.buscar(tipo, patron);
    }
}
