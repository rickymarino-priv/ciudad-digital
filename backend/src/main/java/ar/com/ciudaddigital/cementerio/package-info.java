/**
 * Registro de sepulturas del cementerio municipal: parcelas, nichos y
 * panteones, con búsqueda pública por nombre del difunto (backlog R8).
 *
 * <p>Es, otra vez, el mismo patrón que {@code boletin} (R7): lectura
 * pública ({@code GET /api/cementerio}) sin sesión y escritura protegida
 * ({@code POST /api/cementerio}) con sesión y el permiso
 * {@code cementerio.registrar}. A diferencia de {@code boletin}, el
 * permiso se asigna a ambos roles de sistema (administrador y agente):
 * registrar una inhumación es operación diaria del personal del
 * cementerio, no un acto legal reservado a administrador. No hay ADR
 * nuevo: se reutilizan tal cual {@code rutasDeLecturaPublica()}
 * (ADR 0012 §1) y los permisos granulares (ADR 0011).
 */
package ar.com.ciudaddigital.cementerio;
