package ar.com.ciudaddigital.entitlement;

import java.util.List;

/**
 * Lo que un módulo funcional publica de sí mismo para poder ser
 * contratado y gateado (ADR 0012 §1). Cada módulo funcional registra un
 * bean que implementa esta interfaz; el catálogo de módulos del producto
 * es la suma de los descriptores presentes en el contexto, no una tabla
 * que alguien mantiene aparte.
 */
public interface DescriptorDeModulo {

    /**
     * Identificador comercial estable del módulo, y prefijo de sus
     * permisos: {@code ejemplo} ↔ {@code ejemplo.usar} (ADR 0012 §6).
     */
    String codigo();

    String nombre();

    String descripcion();

    /**
     * Prefijos de ruta de API que pertenecen a este módulo (p. ej.
     * {@code /api/ejemplo}). El gating atribuye un request a un módulo por
     * coincidencia de segmento contra esta lista: {@code /api/ejemplo} y
     * {@code /api/ejemplo/...} coinciden, {@code /api/ejemplote} no.
     */
    List<String> prefijosDeApi();

    /**
     * Rutas de este módulo que se pueden leer con {@code GET} sin sesión
     * (p. ej. {@code /api/ejemplo/ping}): lo que un vecino anónimo puede ver
     * en el portal (ADR 0012 §1).
     *
     * <p>La cadena de seguridad arma sus reglas de {@code permitAll}
     * iterando el catálogo, así que declarar acá una ruta pública es lo
     * único que un módulo con lectura anónima necesita hacer; no implica
     * tocar {@code acceso.internal.ConfiguracionDeSeguridad}. Solo cubre
     * lectura: un módulo que necesite exponer escritura anónima requiere su
     * propia decisión, no está contemplado por este método (ADR 0012 §1).
     *
     * <p>Default vacío para que un módulo sin rutas públicas no tenga que
     * declarar nada.
     */
    default List<String> rutasDeLecturaPublica() {
        return List.of();
    }

    /**
     * Rutas de este módulo que aceptan {@code POST} sin sesión (p. ej.
     * {@code /api/reclamos}): un alta que un vecino anónimo puede hacer,
     * como cargar un reclamo sin cuenta (ADR 0014 §1).
     *
     * <p>La cadena de seguridad arma sus reglas de {@code permitAll}
     * iterando el catálogo, igual que ya hace con
     * {@link #rutasDeLecturaPublica()} para {@code GET}; declarar acá una
     * ruta de alta pública es lo único que un módulo con escritura anónima
     * necesita hacer, sin tocar {@code acceso.internal.ConfiguracionDeSeguridad}.
     *
     * <p><strong>Solo cubre {@code POST}.</strong> Nunca {@code PUT},
     * {@code PATCH} ni {@code DELETE} públicos: sin una cuenta detrás no
     * hay forma de verificar que quien edita o borra es quien creó. Alta
     * sí, todo lo demás no (ADR 0014 §1).
     *
     * <p>Default vacío para que un módulo sin escritura pública no tenga
     * que declarar nada.
     */
    default List<String> rutasDeEscrituraPublica() {
        return List.of();
    }
}
