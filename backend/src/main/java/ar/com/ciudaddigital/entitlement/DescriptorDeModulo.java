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
}
