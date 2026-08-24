package ar.com.ciudaddigital.acceso;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * ABM de usuarios y roles dentro del municipio (ADR 0011), incluidas las
 * invariantes que protegen al rol de administrador y el aislamiento entre
 * municipios.
 */
class AdministracionDeUsuariosYRolesTest extends SoporteDeIntegracion {

    @BeforeEach
    void municipiosDePrueba() throws Exception {
        asegurarMunicipio("sanmartin", "San Martín", "#1B4F9C");
        asegurarMunicipio("moron", "Morón", "#1F6B4A");
    }

    @Test
    @DisplayName("el administrador da de alta un usuario nuevo con rol de agente")
    void elAdministradorCreaUnUsuario() throws Exception {
        MockHttpSession sesion = iniciarSesionDeAdministrador("sanmartin");
        Long idDelRolAgente = idDelRol(sesion, "sanmartin", "agente");

        mvc.perform(crear("sanmartin", sesion,
                """
                        {"nombre":"Juan Pérez","email":"juan@sanmartin.gob.ar",
                         "password":"otra-contrasena-larga","roles":[%d]}
                        """.formatted(idDelRolAgente)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("juan@sanmartin.gob.ar"))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.roles[0].nombre").value("Agente municipal"));

        // Y ya puede entrar con esas credenciales.
        mvc.perform(post(portalDe("sanmartin", "/api/sesion"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"juan@sanmartin.gob.ar","password":"otra-contrasena-larga"}"""))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("desactivar un usuario le corta el acceso de inmediato")
    void desactivarUnUsuarioLeCortaElAcceso() throws Exception {
        MockHttpSession sesionAdmin = iniciarSesionDeAdministrador("sanmartin");
        Long idDelRolAgente = idDelRol(sesionAdmin, "sanmartin", "agente");

        Long idDeJuan = crearYObtenerId("sanmartin", sesionAdmin,
                "Juan Pérez", "juan2@sanmartin.gob.ar", idDelRolAgente);

        MockHttpSession sesionDeJuan =
                iniciarSesion("sanmartin", "juan2@sanmartin.gob.ar", "otra-contrasena-larga");
        mvc.perform(get(portalDe("sanmartin", "/api/roles")).session(sesionDeJuan))
                .andExpect(status().isForbidden()); // agente no tiene roles.ver

        mvc.perform(patch(portalDe("sanmartin", "/api/usuarios/" + idDeJuan))
                .session(sesionAdmin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombre":"Juan Pérez","activo":false,"roles":[%d]}
                        """.formatted(idDelRolAgente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        // La sesión de Juan ya estaba abierta: se relee en cada request, así
        // que la desactivación tiene efecto sin esperar a que expire nada.
        mvc.perform(get(portalDe("sanmartin", "/api/sesion")).session(sesionDeJuan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autenticado").value(false));
    }

    @Test
    @DisplayName("el rol de administrador no puede quedarse sin el permiso de administrar usuarios")
    void elRolDeAdministradorNoPierdeSuPermisoIndispensable() throws Exception {
        MockHttpSession sesion = iniciarSesionDeAdministrador("sanmartin");
        Long idDelRolAdmin = idDelRol(sesion, "sanmartin", "administrador");

        mvc.perform(patch(portalDe("sanmartin", "/api/roles/" + idDelRolAdmin))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombre":"Administrador del municipio","descripcion":null,"permisos":["usuarios.ver"]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString(
                        "no puede quedarse sin el permiso")));
    }

    @Test
    @DisplayName("un rol de sistema no se puede borrar")
    void unRolDeSistemaNoSePuedeBorrar() throws Exception {
        MockHttpSession sesion = iniciarSesionDeAdministrador("sanmartin");
        Long idDelRolAgente = idDelRol(sesion, "sanmartin", "agente");

        mvc.perform(delete(portalDe("sanmartin", "/api/roles/" + idDelRolAgente))
                .session(sesion)
                .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("un rol nuevo se puede crear, editar y borrar")
    void unRolNuevoSePuedeAdministrar() throws Exception {
        MockHttpSession sesion = iniciarSesionDeAdministrador("sanmartin");

        String cuerpo = mvc.perform(post(portalDe("sanmartin", "/api/roles"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"codigo":"mesa-de-entradas","nombre":"Mesa de entradas",
                         "descripcion":"Recibe trámites.","permisos":["usuarios.ver"]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.delSistema").value(false))
                .andReturn().getResponse().getContentAsString();

        Long id = ((Number) com.jayway.jsonpath.JsonPath.read(cuerpo, "$.id")).longValue();

        mvc.perform(patch(portalDe("sanmartin", "/api/roles/" + id))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombre":"Mesa de entradas","descripcion":"Actualizada.","permisos":[]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permisos").isEmpty());

        mvc.perform(delete(portalDe("sanmartin", "/api/roles/" + id))
                .session(sesion)
                .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("aislamiento: los roles de un municipio no aparecen en el catálogo del otro")
    void losRolesDeUnMunicipioNoApareceEnElOtro() throws Exception {
        MockHttpSession sesionSanMartin = iniciarSesionDeAdministrador("sanmartin");
        mvc.perform(post(portalDe("sanmartin", "/api/roles"))
                .session(sesionSanMartin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"codigo":"catastro","nombre":"Catastro","descripcion":null,"permisos":[]}"""))
                .andExpect(status().isCreated());

        MockHttpSession sesionMoron = iniciarSesionDeAdministrador("moron");
        mvc.perform(get(portalDe("moron", "/api/roles")).session(sesionMoron))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre", not(hasItem("Catastro"))));
    }

    @Test
    @DisplayName("sin el permiso de administrar usuarios, no se puede crear ninguno")
    void sinPermisoNoSePuedeCrearUsuarios() throws Exception {
        MockHttpSession sesionAdmin = iniciarSesionDeAdministrador("sanmartin");
        Long idDelRolAgente = idDelRol(sesionAdmin, "sanmartin", "agente");
        Long idDeJuan = crearYObtenerId("sanmartin", sesionAdmin,
                "Juan Pérez", "juan3@sanmartin.gob.ar", idDelRolAgente);

        MockHttpSession sesionDeJuan =
                iniciarSesion("sanmartin", "juan3@sanmartin.gob.ar", "otra-contrasena-larga");

        mvc.perform(crear("sanmartin", sesionDeJuan,
                """
                        {"nombre":"Otro","email":"otro@sanmartin.gob.ar",
                         "password":"otra-contrasena-larga","roles":[]}"""))
                .andExpect(status().isForbidden());

        org.junit.jupiter.api.Assertions.assertNotNull(idDeJuan);
    }

    private MockHttpServletRequestBuilder crear(String subdominio, MockHttpSession sesion,
            String cuerpo) {
        return post(portalDe(subdominio, "/api/usuarios"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private Long crearYObtenerId(String subdominio, MockHttpSession sesion, String nombre,
            String email, Long idDeRol) throws Exception {

        String cuerpo = mvc.perform(crear(subdominio, sesion,
                """
                        {"nombre":"%s","email":"%s","password":"otra-contrasena-larga","roles":[%d]}
                        """.formatted(nombre, email, idDeRol)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return ((Number) com.jayway.jsonpath.JsonPath.read(cuerpo, "$.id")).longValue();
    }

    private Long idDelRol(MockHttpSession sesion, String subdominio, String codigo)
            throws Exception {

        String cuerpo = mvc.perform(get(portalDe(subdominio, "/api/roles")).session(sesion))
                .andReturn().getResponse().getContentAsString();

        java.util.List<java.util.Map<String, Object>> roles = com.jayway.jsonpath.JsonPath.read(
                cuerpo, "$[?(@.codigo=='" + codigo + "')]");
        return ((Number) roles.get(0).get("id")).longValue();
    }
}
