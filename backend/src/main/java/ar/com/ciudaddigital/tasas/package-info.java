/**
 * Tasas municipales y su pago online (backlog R13, ADR 0018).
 *
 * <p>Primer módulo funcional que cobra algo del producto: un municipio con
 * el permiso {@code tasas.publicar} da de alta una tasa para un número de
 * cuenta (sin padrón de contribuyentes real todavía, ver ADR 0018), y un
 * vecino sin sesión la busca y la paga desde el portal público contra
 * {@code pagos.PasarelaDePago} —hoy, el único adaptador simulado que
 * existe (ADR 0018 §2)—.
 *
 * <p>La confirmación del pago no pasa por {@code pagos}: este módulo
 * declara su propio endpoint de confirmación como escritura pública (ADR
 * 0018 §4, reutilizando {@code rutasDeEscrituraPublica()} de ADR 0014 §1) y
 * es dueño de su propio estado ({@code PENDIENTE}/{@code PAGADA}).
 */
package ar.com.ciudaddigital.tasas;
