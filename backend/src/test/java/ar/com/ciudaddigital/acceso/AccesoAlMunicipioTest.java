package ar.com.ciudaddigital.acceso;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import ar.com.ciudaddigital.SoporteDeIntegracion;

/**
 * Entrada de usuarios al portal de su municipio y aislamiento entre
 * municipios (ADR 0010).
 *
 * <p>El aislamiento de credenciales es criterio de completitud de la
 * rebanada, no un test para después: una credencial que sirva en el
 * municipio equivocado es el peor error que puede tener este producto.
 */
class AccesoAlMunicipioTest extends SoporteDeIntegracion {

    @BeforeEach
    void municipiosDePrueba() throws Exception {
        asegurarMunicipio("sanmartin", "San Martín", "#1B4F9C");
        asegurarMunicipio("moron", "Morón", "#1F6B4A");
    }

    @Test
    @DisplayName("el administrador entra a su municipio y recibe sus permisos")
    void elAdministradorEntraASuMunicipio() throws Exception {
        mvc.perform(login("sanmartin", emailDelAdministrador("sanmartin"), PASSWORD_DE_PRUEBA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autenticado").value(true))
                .andExpect(jsonPath("$.usuario.email").value("admin@sanmartin.gob.ar"))
                .andExpect(jsonPath("$.usuario.permisos", hasItem("usuarios.administrar")))
                .andExpect(jsonPath("$.usuario.permisos", hasItem("roles.administrar")));
    }

    @Test
    @DisplayName("aislamiento: las credenciales de un municipio no sirven en otro")
    void credencialesDeUnMunicipioNoSirvenEnOtro() throws Exception {
        // Mismo usuario, misma contraseña, otro municipio: el usuario no
        // existe en esa base, así que no hay nada que verificar.
        mvc.perform(login("moron", emailDelAdministrador("sanmartin"), PASSWORD_DE_PRUEBA))
                .andExpect(status().isUnauthorized());

        mvc.perform(login("sanmartin", emailDelAdministrador("moron"), PASSWORD_DE_PRUEBA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("aislamiento: una sesión abierta en un municipio no vale en otro")
    void laSesionNoCruzaDeMunicipio() throws Exception {
        MockHttpSession sesion = iniciarSesionDeAdministrador("sanmartin");

        mvc.perform(get(portalDe("sanmartin", "/api/usuarios")).session(sesion))
                .andExpect(status().isOk());

        // Un browser nunca mandaría esta cookie acá, porque se emite
        // host-only. Pero un cliente cualquiera sí puede, y el backend
        // tiene que rechazarlo igual.
        mvc.perform(get(portalDe("moron", "/api/usuarios")).session(sesion))
                .andExpect(status().isUnauthorized());

        // Y la sesión queda cerrada: presentarla en el municipio
        // equivocado no es un error a reintentar, es motivo para cortarla.
        mvc.perform(get(portalDe("sanmartin", "/api/usuarios")).session(sesion))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("aislamiento: la lista de usuarios de un municipio no menciona al otro")
    void ningunMunicipioVeLosUsuariosDelOtro() throws Exception {
        MockHttpSession sesion = iniciarSesionDeAdministrador("sanmartin");

        mvc.perform(get(portalDe("sanmartin", "/api/usuarios")).session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].email", hasItem("admin@sanmartin.gob.ar")))
                .andExpect(jsonPath("$[*].email", not(hasItem("admin@moron.gob.ar"))));
    }

    @Test
    @DisplayName("sin sesión no se accede a lo que necesita sesión")
    void sinSesionNoHayAcceso() throws Exception {
        mvc.perform(get(portalDe("sanmartin", "/api/usuarios")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("la contraseña equivocada no abre sesión")
    void contrasenaEquivocadaNoEntra() throws Exception {
        mvc.perform(login("sanmartin", emailDelAdministrador("sanmartin"), "no-es-la-contrasena"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un email inexistente da exactamente el mismo error que una contraseña mala")
    void elErrorNoDelataQueUsuariosExisten() throws Exception {
        String porContrasena = mvc
                .perform(login("sanmartin", emailDelAdministrador("sanmartin"), "no-es-la-contrasena"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String porEmail = mvc
                .perform(login("sanmartin", "nadie@sanmartin.gob.ar", PASSWORD_DE_PRUEBA))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        Assertions.assertEquals(porContrasena, porEmail,
                "El error tiene que ser el mismo: si no, revela qué emails existen.");
    }

    @Test
    @DisplayName("cerrar sesión corta el acceso")
    void cerrarSesionCortaElAcceso() throws Exception {
        MockHttpSession sesion = iniciarSesionDeAdministrador("sanmartin");

        mvc.perform(delete(portalDe("sanmartin", "/api/sesion")).session(sesion).with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get(portalDe("sanmartin", "/api/usuarios")).session(sesion))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("sin sesión, el portal responde que no hay nadie autenticado")
    void elPortalRespondeQueNoHaySesion() throws Exception {
        mvc.perform(get(portalDe("sanmartin", "/api/sesion")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autenticado").value(false));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
            String subdominio, String email, String password) {

        return post(portalDe(subdominio, "/api/sesion"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}""".formatted(email, password));
    }
}
