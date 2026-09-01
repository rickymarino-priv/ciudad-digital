package ar.com.ciudaddigital.eventos.internal;

import java.util.List;

import org.springframework.stereotype.Component;

import ar.com.ciudaddigital.entitlement.DescriptorDeModulo;

/**
 * Declara el módulo {@code eventos} ante el catálogo de entitlement (ADR
 * 0012 §1). Publicar un evento y cancelarlo requieren sesión y el permiso
 * {@code eventos.gestionar}: la agenda la publica el municipio, nunca el
 * vecino (ADR 0030 §5), así que este módulo no declara ninguna
 * {@code rutaDeEscrituraPublica()} — no hay mutación pública/anónima de
 * ningún tipo acá.
 */
@Component
class DescriptorDelModuloEventos implements DescriptorDeModulo {

    static final String CODIGO = "eventos";

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public String nombre() {
        return "Cultura, Turismo y Deportes";
    }

    @Override
    public String descripcion() {
        return "Agenda pública de eventos culturales, turísticos y deportivos publicados por el municipio: "
                + "nombre, categoría, ubicación, fechas y estado, con alta protegida por el municipio y lectura "
                + "pública sin sesión.";
    }

    @Override
    public List<String> prefijosDeApi() {
        return List.of("/api/eventos");
    }

    /** Solo la agenda con filtros es pública (ADR 0030 §5). */
    @Override
    public List<String> rutasDeLecturaPublica() {
        return List.of("/api/eventos");
    }
}
