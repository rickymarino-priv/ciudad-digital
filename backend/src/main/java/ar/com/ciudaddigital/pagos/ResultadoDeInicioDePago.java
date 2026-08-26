package ar.com.ciudaddigital.pagos;

/**
 * Lo que la pasarela devuelve al iniciar un cobro (ADR 0018 §1).
 *
 * <p>{@code referenciaExterna} es el id que la pasarela le asigna a esa
 * transacción: un string opaco para quien llama, que después sirve para
 * correlacionar la confirmación del pago con el intento que la originó.
 * {@code urlDePago} es adonde redirigir al pagador; en el único adaptador
 * que existe hoy ({@code PasarelaDePagoSimulada}) es {@code null}, porque
 * no hay ningún sitio externo al que navegar (ADR 0018 §3) — el frontend
 * muestra en su lugar una vista in-app rotulada como simulador.
 */
public record ResultadoDeInicioDePago(String referenciaExterna, String urlDePago) {
}
