package ar.com.ciudaddigital.entitlement;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ar.com.ciudaddigital.SoporteDeIntegracion;

/**
 * Contratación y gating de módulos por municipio (ADR 0012), y el orden de
 * evaluación entitlement→permiso del ADR 0011.
 *
 * <p>Ninguno de los dos municipios de esta clase se reutiliza para
 * afirmar un estado inicial: cada test fija explícitamente qué módulos
 * tiene prendidos cada uno con un {@code PUT} antes de verificar nada,
 * porque el contenedor de Postgres —y los municipios ya dados de alta en
 * él— se comparte entre clases de test.
 */
class EntitlementDeModulosTest extends SoporteDeIntegracion {

    private static final String A = "banfield";
    private static final String B = "quilmes";

    @BeforeEach
    void municipiosDePrueba() throws Exception {
        asegurarMunicipio(A, "Banfield", "#2E7D32");
        asegurarMunicipio(B, "Quilmes", "#6A1B9A");
    }

    @Test
    @DisplayName("el módulo contratado responde 200 y el no contratado 403 MODULO_NO_CONTRATADO")
    void pingRespondeSoloDondeElModuloEstaContratado() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "ejemplo");
        fijarModulos(B, plataforma);

        mvc.perform(get(portalDe(A, "/api/ejemplo/ping")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modulo").value("ejemplo"))
                .andExpect(jsonPath("$.municipio").value("Banfield"));

        mvc.perform(get(portalDe(B, "/api/ejemplo/ping")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("ejemplo"));
    }

    @Test
    @DisplayName("aislamiento: el catálogo público refleja el estado de cada municipio "
            + "según el subdominio, sin ningún parámetro")
    void catalogoDeModulosPorSubdominio() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "ejemplo");
        fijarModulos(B, plataforma);

        mvc.perform(get(portalDe(A, "/api/modulos")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.codigo=='ejemplo')].habilitado").value(true));

        mvc.perform(get(portalDe(B, "/api/modulos")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.codigo=='ejemplo')].habilitado").value(false));
    }

    @Test
    @DisplayName("aislamiento: prender el módulo en un municipio no lo prende en otro")
    void prenderEnUnMunicipioNoAfectaAlOtro() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma);
        fijarModulos(B, plataforma);

        fijarModulos(A, plataforma, "ejemplo");

        mvc.perform(get(portalDe(A, "/api/modulos")))
                .andExpect(jsonPath("$[?(@.codigo=='ejemplo')].habilitado").value(true));
        mvc.perform(get(portalDe(B, "/api/modulos")))
                .andExpect(jsonPath("$[?(@.codigo=='ejemplo')].habilitado").value(false));
    }

    @Test
    @DisplayName("ciclo completo: prendido responde, apagado vuelve a rechazar")
    void cicloCompletoDePrendidoYApagado() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();

        fijarModulos(A, plataforma, "ejemplo");
        mvc.perform(get(portalDe(A, "/api/ejemplo/ping"))).andExpect(status().isOk());

        fijarModulos(A, plataforma);
        mvc.perform(get(portalDe(A, "/api/ejemplo/ping")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"));
    }

    @Test
    @DisplayName("aislamiento: la configuración de módulos no se toca desde el portal del municipio")
    void laApiDeModulosNoEsAccesibleDesdeElPortal() throws Exception {
        mvc.perform(get("/api/admin/municipios/" + A + "/modulos"))
                .andExpect(status().isUnauthorized());

        MockHttpSession sesionDeA = iniciarSesionDeAdministrador(A);
        mvc.perform(get("/api/admin/municipios/" + A + "/modulos").session(sesionDeA))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/municipios/" + B + "/modulos").session(sesionDeA))
                .andExpect(status().isUnauthorized());

        mvc.perform(put("/api/admin/municipios/" + A + "/modulos")
                .session(sesionDeA)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"modulos":["ejemplo"]}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("entitlement antes que permiso: módulo apagado gana aunque el usuario "
            + "tenga el permiso, y el permiso decide cuando el módulo está prendido")
    void elOrdenEsEntitlementPrimeroYPermisoDespues() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();

        // A: módulo apagado. Su administrador sí tiene 'ejemplo.usar'
        // (lo trae el rol de sistema, migración V3), pero eso no alcanza.
        fijarModulos(A, plataforma);
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);
        mvc.perform(eco(A, administradorDeA, "hola"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"));

        // B: módulo prendido. Un agente (sin 'ejemplo.usar') recibe el 403
        // de permisos, no el de entitlement: cuerpo distinto, sin 'codigo'.
        fijarModulos(B, plataforma);
        MockHttpSession administradorDeB = iniciarSesionDeAdministrador(B);
        fijarModulos(B, plataforma, "ejemplo");

        MockHttpSession agenteDeB = crearAgenteYLoguear(B, administradorDeB, "agente-eco@quilmes.gob.ar");
        mvc.perform(eco(B, agenteDeB, "hola"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").doesNotExist());

        // Y con el módulo prendido y el permiso puesto, el eco funciona.
        mvc.perform(eco(B, administradorDeB, "hola"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("hola"))
                .andExpect(jsonPath("$.usuario").value(emailDelAdministrador(B)));
    }

    @Test
    @DisplayName("un código de módulo inexistente rechaza el PUT y no cambia nada")
    void unCodigoInexistenteNoSePersiste() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "ejemplo");

        mvc.perform(put("/api/admin/municipios/" + A + "/modulos")
                .session(plataforma)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"modulos":["no-existe"]}"""))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/admin/municipios/" + A + "/modulos").session(plataforma))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modulos[?(@.codigo=='ejemplo')].habilitado").value(true));
    }

    @Test
    @DisplayName("la plataforma base no se gatea: con la lista de módulos vacía "
            + "se puede iniciar sesión y administrar usuarios y roles")
    void laPlataformaBaseNoSeGateaConLaListaVacia() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma);

        MockHttpSession sesion = iniciarSesionDeAdministrador(A);
        mvc.perform(get(portalDe(A, "/api/usuarios")).session(sesion))
                .andExpect(status().isOk());
        mvc.perform(get(portalDe(A, "/api/roles")).session(sesion))
                .andExpect(status().isOk());
    }

    private void fijarModulos(String slug, MockHttpSession sesionDePlataforma, String... modulos)
            throws Exception {

        String lista = String.join(",", java.util.Arrays.stream(modulos)
                .map(codigo -> "\"" + codigo + "\"").toList());

        mvc.perform(put("/api/admin/municipios/" + slug + "/modulos")
                .session(sesionDePlataforma)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"modulos\":[" + lista + "]}"))
                .andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder eco(String subdominio, MockHttpSession sesion, String mensaje) {
        return post(portalDe(subdominio, "/api/ejemplo/eco"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mensaje\":\"" + mensaje + "\"}");
    }

    /** Crea un usuario con el rol de sistema 'agente' (sin {@code ejemplo.usar}) y abre su sesión. */
    private MockHttpSession crearAgenteYLoguear(String subdominio, MockHttpSession sesionAdmin,
            String email) throws Exception {

        String cuerpoDeRoles = mvc.perform(get(portalDe(subdominio, "/api/roles")).session(sesionAdmin))
                .andReturn().getResponse().getContentAsString();

        java.util.List<java.util.Map<String, Object>> roles = com.jayway.jsonpath.JsonPath.read(
                cuerpoDeRoles, "$[?(@.codigo=='agente')]");
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

    /** Sanity check aparte de los de aislamiento: el eco recorta y devuelve lo esperado. */
    @Test
    @DisplayName("el eco devuelve el mensaje, el municipio y el email del usuario autenticado")
    void elEcoFuncionaConModuloYPermiso() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "ejemplo");
        MockHttpSession administrador = iniciarSesionDeAdministrador(A);

        mvc.perform(eco(A, administrador, "hola vecinos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("hola vecinos"))
                .andExpect(jsonPath("$.municipio").value("Banfield"))
                .andExpect(jsonPath("$.usuario").value(emailDelAdministrador(A)));
    }

    @Test
    @DisplayName("el eco sin mensaje se rechaza con 400")
    void elEcoSinMensajeSeRechaza() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "ejemplo");
        MockHttpSession administrador = iniciarSesionDeAdministrador(A);

        mvc.perform(post(portalDe(A, "/api/ejemplo/eco"))
                .session(administrador)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
