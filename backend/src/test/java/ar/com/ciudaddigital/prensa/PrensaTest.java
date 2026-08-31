package ar.com.ciudaddigital.prensa;

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
 * Publicación protegida y búsqueda pública de gacetillas de prensa
 * municipal (ADR 0027), calco estructural de {@code BoletinTest}.
 *
 * <p>Cada test fija explícitamente qué módulos tiene contratado cada
 * municipio antes de verificar nada, mismo criterio que
 * {@code EntitlementDeModulosTest} y {@code BoletinTest}: el contenedor
 * de Postgres se comparte entre clases de test.
 */
class PrensaTest extends SoporteDeIntegracion {

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
        fijarModulos(A, plataforma, "prensa");
        fijarModulos(B, plataforma);
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);
        MockHttpSession administradorDeB = iniciarSesionDeAdministrador(B);

        mvc.perform(publicar(A, administradorDeA, """
                {"categoria":"OBRAS","titulo":"Se inaugura la nueva plaza",
                 "texto":"Texto completo del comunicado.","fechaPublicacion":"2026-01-15"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.categoria").value("OBRAS"))
                .andExpect(jsonPath("$.publicadoPorNombre").value("Administrador de Tandil"))
                .andExpect(jsonPath("$.publicadoPorEmail").value(emailDelAdministrador(A)));

        mvc.perform(publicar(B, administradorDeB, """
                {"categoria":"OBRAS","titulo":"Otra",
                 "texto":"Texto.","fechaPublicacion":"2026-01-15"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("prensa"));
    }

    @Test
    @DisplayName("un agente con prensa.publicar puede publicar (201); sin sesión, 401/403 sin código")
    void publicacionConAgentePermitidaSinSesionRechazada() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "prensa");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        // A diferencia de BoletinTest (donde el agente análogo carece del
        // permiso de boletín), acá el agente sí tiene prensa.publicar por
        // default (V23): publicar una gacetilla es tarea operativa, no un
        // acto legal (ADR 0027 §3).
        MockHttpSession agenteDeA =
                crearAgenteYLoguear(A, administradorDeA, "agente-prensa@tandil.gob.ar");

        mvc.perform(publicar(A, agenteDeA, """
                {"categoria":"INSTITUCIONAL","titulo":"Comunicado de prueba",
                 "texto":"Texto.","fechaPublicacion":"2026-01-15"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.publicadoPorEmail").value("agente-prensa@tandil.gob.ar"));

        mvc.perform(post(portalDe(A, "/api/prensa"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"categoria":"INSTITUCIONAL","titulo":"Sin sesión",
                         "texto":"Texto.","fechaPublicacion":"2026-01-15"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").doesNotExist());
    }

    @Test
    @DisplayName("sin título, o con una categoría inexistente, se rechaza con 400")
    void publicacionInvalidaSeRechaza() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "prensa");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        mvc.perform(publicar(A, administradorDeA, """
                {"categoria":"OBRAS",
                 "texto":"Texto.","fechaPublicacion":"2026-01-15"}"""))
                .andExpect(status().isBadRequest());

        mvc.perform(publicar(A, administradorDeA, """
                {"categoria":"INEXISTENTE","titulo":"Algo",
                 "texto":"Texto.","fechaPublicacion":"2026-01-15"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("lectura pública: sin sesión, con el módulo contratado, devuelve lo publicado; "
            + "sin el módulo, 403 MODULO_NO_CONTRATADO aun sin sesión")
    void lecturaPublicaSoloConElModuloContratado() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "prensa");
        fijarModulos(B, plataforma);
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        String titulo = "Gacetilla pública " + UUID.randomUUID();
        mvc.perform(publicar(A, administradorDeA, """
                {"categoria":"CULTURA","titulo":"%s",
                 "texto":"Texto.","fechaPublicacion":"2026-01-15"}"""
                .formatted(titulo)))
                .andExpect(status().isCreated());

        mvc.perform(get(portalDe(A, "/api/prensa")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.titulo == '" + titulo + "')]").isNotEmpty());

        mvc.perform(get(portalDe(B, "/api/prensa")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("prensa"));
    }

    @Test
    @DisplayName("filtros: por categoría y por texto en el título")
    void filtrosDeCategoriaYTexto() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "prensa");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        String sufijo = UUID.randomUUID().toString();
        String tituloObras = "Se inaugura la plaza del barrio Centro " + sufijo;
        String tituloCultura = "Feria de cultura en el centro " + sufijo;

        mvc.perform(publicar(A, administradorDeA, """
                {"categoria":"OBRAS","titulo":"%s",
                 "texto":"Texto.","fechaPublicacion":"2026-01-10"}"""
                .formatted(tituloObras)))
                .andExpect(status().isCreated());
        mvc.perform(publicar(A, administradorDeA, """
                {"categoria":"CULTURA","titulo":"%s",
                 "texto":"Texto.","fechaPublicacion":"2026-01-11"}"""
                .formatted(tituloCultura)))
                .andExpect(status().isCreated());

        mvc.perform(get(portalDe(A, "/api/prensa?categoria=OBRAS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.titulo == '" + tituloObras + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.titulo == '" + tituloCultura + "')]").isEmpty());

        mvc.perform(get(portalDe(A, "/api/prensa?q=plaza")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.titulo == '" + tituloObras + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.titulo == '" + tituloCultura + "')]").isEmpty());
    }

    @Test
    @DisplayName("aislamiento: una gacetilla publicada en un municipio no aparece en el listado del otro")
    void aislamientoEntreTenants() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "prensa");
        fijarModulos(B, plataforma, "prensa");

        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);
        MockHttpSession administradorDeB = iniciarSesionDeAdministrador(B);

        // Títulos únicos (con un sufijo aleatorio) para no confundirse con
        // filas que hayan quedado de otro test de esta misma clase, que
        // reutiliza estos dos municipios (mismo criterio que BoletinTest).
        String tituloDeA = "Gacetilla de Tandil " + UUID.randomUUID();
        String tituloDeB = "Gacetilla de Olavarría " + UUID.randomUUID();

        mvc.perform(publicar(A, administradorDeA, """
                {"categoria":"INSTITUCIONAL","titulo":"%s",
                 "texto":"Texto.","fechaPublicacion":"2026-01-15"}"""
                .formatted(tituloDeA)))
                .andExpect(status().isCreated());
        mvc.perform(publicar(B, administradorDeB, """
                {"categoria":"INSTITUCIONAL","titulo":"%s",
                 "texto":"Texto.","fechaPublicacion":"2026-01-15"}"""
                .formatted(tituloDeB)))
                .andExpect(status().isCreated());

        // El id por sí solo no sirve para comparar entre municipios: cada
        // base tiene su propia secuencia. Lo que prueba el aislamiento es
        // el título, único por el sufijo aleatorio.
        mvc.perform(get(portalDe(A, "/api/prensa")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.titulo == '" + tituloDeA + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.titulo == '" + tituloDeB + "')]").isEmpty());

        mvc.perform(get(portalDe(B, "/api/prensa")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.titulo == '" + tituloDeB + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.titulo == '" + tituloDeA + "')]").isEmpty());
    }

    private MockHttpServletRequestBuilder publicar(String subdominio, MockHttpSession sesion, String cuerpo) {
        return post(portalDe(subdominio, "/api/prensa"))
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

    /** Crea un usuario con el rol de sistema 'agente' (con {@code prensa.publicar} por default) y abre su sesión. */
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
