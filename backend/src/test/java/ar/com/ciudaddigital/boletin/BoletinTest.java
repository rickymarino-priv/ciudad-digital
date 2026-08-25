package ar.com.ciudaddigital.boletin;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import ar.com.ciudaddigital.SoporteDeIntegracion;

/**
 * Publicación protegida y búsqueda pública de normas del Boletín Oficial
 * (backlog R7), complemento de {@code ReclamosTest}: acá la escritura
 * requiere sesión y permiso, y la lectura es pública sin sesión.
 *
 * <p>Cada test fija explícitamente qué módulos tiene contratado cada
 * municipio antes de verificar nada, mismo criterio que
 * {@code EntitlementDeModulosTest} y {@code ReclamosTest}: el contenedor
 * de Postgres se comparte entre clases de test.
 */
class BoletinTest extends SoporteDeIntegracion {

    private static final String A = "tandil";
    private static final String B = "olavarria";

    @BeforeEach
    void municipiosDePrueba() throws Exception {
        asegurarMunicipio(A, "Tandil", "#00695C");
        asegurarMunicipio(B, "Olavarría", "#4527A0");
    }

    @Test
    @DisplayName("publicación con el módulo contratado y el permiso responde 201; sin el módulo, "
            + "403 MODULO_NO_CONTRATADO aunque haya sesión y permiso")
    void publicacionSoloConElModuloContratado() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "boletin");
        fijarModulos(B, plataforma);
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);
        MockHttpSession administradorDeB = iniciarSesionDeAdministrador(B);

        mvc.perform(publicar(A, administradorDeA, """
                {"tipo":"ORDENANZA","numero":"123/2026","titulo":"Ordenanza de prueba",
                 "texto":"Texto completo de la ordenanza.","fechaPublicacion":"2026-01-15"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.tipo").value("ORDENANZA"))
                .andExpect(jsonPath("$.publicadoPorNombre").value("Administrador de Tandil"))
                .andExpect(jsonPath("$.publicadoPorEmail").value(emailDelAdministrador(A)));

        mvc.perform(publicar(B, administradorDeB, """
                {"tipo":"ORDENANZA","numero":"1/2026","titulo":"Otra",
                 "texto":"Texto.","fechaPublicacion":"2026-01-15"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("boletin"));
    }

    @Test
    @DisplayName("un agente (sin boletin.publicar) recibe 403 sin código")
    void publicacionSinElPermisoSeRechaza() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "boletin");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        MockHttpSession agenteDeA =
                crearAgenteYLoguear(A, administradorDeA, "agente-boletin@tandil.gob.ar");

        mvc.perform(publicar(A, agenteDeA, """
                {"tipo":"DECRETO","numero":"1/2026","titulo":"Decreto de prueba",
                 "texto":"Texto.","fechaPublicacion":"2026-01-15"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").doesNotExist());
    }

    @Test
    @DisplayName("sin título, o con un tipo inexistente, se rechaza con 400")
    void publicacionInvalidaSeRechaza() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "boletin");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        mvc.perform(publicar(A, administradorDeA, """
                {"tipo":"ORDENANZA","numero":"1/2026",
                 "texto":"Texto.","fechaPublicacion":"2026-01-15"}"""))
                .andExpect(status().isBadRequest());

        mvc.perform(publicar(A, administradorDeA, """
                {"tipo":"INEXISTENTE","numero":"1/2026","titulo":"Algo",
                 "texto":"Texto.","fechaPublicacion":"2026-01-15"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("lectura pública: sin sesión, con el módulo contratado, devuelve lo publicado; "
            + "sin el módulo, 403 MODULO_NO_CONTRATADO aun sin sesión")
    void lecturaPublicaSoloConElModuloContratado() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "boletin");
        fijarModulos(B, plataforma);
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        String titulo = "Norma pública " + UUID.randomUUID();
        mvc.perform(publicar(A, administradorDeA, """
                {"tipo":"COMUNICADO","numero":"1/2026","titulo":"%s",
                 "texto":"Texto.","fechaPublicacion":"2026-01-15"}"""
                .formatted(titulo)))
                .andExpect(status().isCreated());

        mvc.perform(get(portalDe(A, "/api/boletin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.titulo == '" + titulo + "')]").isNotEmpty());

        mvc.perform(get(portalDe(B, "/api/boletin")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("boletin"));
    }

    @Test
    @DisplayName("filtros: por tipo y por texto en el título")
    void filtrosDeTipoYTexto() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "boletin");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        String sufijo = UUID.randomUUID().toString();
        String tituloOrdenanza = "Ordenanza de arbolado " + sufijo;
        String tituloDecreto = "Decreto de emergencia " + sufijo;

        mvc.perform(publicar(A, administradorDeA, """
                {"tipo":"ORDENANZA","numero":"10/2026","titulo":"%s",
                 "texto":"Texto.","fechaPublicacion":"2026-01-10"}"""
                .formatted(tituloOrdenanza)))
                .andExpect(status().isCreated());
        mvc.perform(publicar(A, administradorDeA, """
                {"tipo":"DECRETO","numero":"11/2026","titulo":"%s",
                 "texto":"Texto.","fechaPublicacion":"2026-01-11"}"""
                .formatted(tituloDecreto)))
                .andExpect(status().isCreated());

        mvc.perform(get(portalDe(A, "/api/boletin?tipo=DECRETO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.titulo == '" + tituloDecreto + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.titulo == '" + tituloOrdenanza + "')]").isEmpty());

        mvc.perform(get(portalDe(A, "/api/boletin?q=arbolado")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.titulo == '" + tituloOrdenanza + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.titulo == '" + tituloDecreto + "')]").isEmpty());
    }

    @Test
    @DisplayName("aislamiento: una norma publicada en un municipio no aparece en el listado del otro")
    void aislamientoEntreTenants() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "boletin");
        fijarModulos(B, plataforma, "boletin");

        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);
        MockHttpSession administradorDeB = iniciarSesionDeAdministrador(B);

        // Títulos únicos (con un sufijo aleatorio) para no confundirse con
        // filas que hayan quedado de otro test de esta misma clase, que
        // reutiliza estos dos municipios (mismo criterio que ReclamosTest).
        String tituloDeA = "Norma de Tandil " + UUID.randomUUID();
        String tituloDeB = "Norma de Olavarría " + UUID.randomUUID();

        mvc.perform(publicar(A, administradorDeA, """
                {"tipo":"RESOLUCION","numero":"1/2026","titulo":"%s",
                 "texto":"Texto.","fechaPublicacion":"2026-01-15"}"""
                .formatted(tituloDeA)))
                .andExpect(status().isCreated());
        mvc.perform(publicar(B, administradorDeB, """
                {"tipo":"RESOLUCION","numero":"1/2026","titulo":"%s",
                 "texto":"Texto.","fechaPublicacion":"2026-01-15"}"""
                .formatted(tituloDeB)))
                .andExpect(status().isCreated());

        // El id por sí solo no sirve para comparar entre municipios: cada
        // base tiene su propia secuencia. Lo que prueba el aislamiento es
        // el título, único por el sufijo aleatorio.
        mvc.perform(get(portalDe(A, "/api/boletin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.titulo == '" + tituloDeA + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.titulo == '" + tituloDeB + "')]").isEmpty());

        mvc.perform(get(portalDe(B, "/api/boletin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.titulo == '" + tituloDeB + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.titulo == '" + tituloDeA + "')]").isEmpty());
    }

    private MockHttpServletRequestBuilder publicar(String subdominio, MockHttpSession sesion, String cuerpo) {
        return post(portalDe(subdominio, "/api/boletin"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private void fijarModulos(String slug, MockHttpSession sesionDePlataforma, String... modulos)
            throws Exception {

        String lista = String.join(",", Arrays.stream(modulos)
                .map(codigo -> "\"" + codigo + "\"").toList());

        mvc.perform(put("/api/admin/municipios/" + slug + "/modulos")
                .session(sesionDePlataforma)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"modulos\":[" + lista + "]}"))
                .andExpect(status().isOk());
    }

    /** Crea un usuario con el rol de sistema 'agente' (sin {@code boletin.publicar}) y abre su sesión. */
    private MockHttpSession crearAgenteYLoguear(String subdominio, MockHttpSession sesionAdmin, String email)
            throws Exception {

        String cuerpoDeRoles = mvc.perform(get(portalDe(subdominio, "/api/roles")).session(sesionAdmin))
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> roles = JsonPath.read(cuerpoDeRoles, "$[?(@.codigo=='agente')]");
        Long idDelRolAgente = ((Number) roles.get(0).get("id")).longValue();

        String password = "otra-contrasena-larga";
        mvc.perform(post(portalDe(subdominio, "/api/usuarios"))
                .session(sesionAdmin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombre":"Agente de prueba","email":"%s","password":"%s","roles":[%d]}
                        """.formatted(email, password, idDelRolAgente)))
                .andExpect(status().isCreated());

        return iniciarSesion(subdominio, email, password);
    }
}
