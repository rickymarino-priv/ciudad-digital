package ar.com.ciudaddigital.auditoria;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import ar.com.ciudaddigital.SoporteDeIntegracion;

/**
 * El alta de un usuario deja constancia en el registro de auditoría del
 * municipio (ADR 0013 §3), aislado por tenant, y solo lo puede leer quien
 * tiene el permiso {@code auditoria.ver}.
 */
class AuditoriaTest extends SoporteDeIntegracion {

    @BeforeEach
    void municipiosDePrueba() throws Exception {
        asegurarMunicipio("sanmartin", "San Martín", "#1B4F9C");
        asegurarMunicipio("moron", "Morón", "#1F6B4A");
    }

    @Test
    @DisplayName("dar de alta un usuario queda en el registro de auditoría con el actor correcto")
    void elAltaDeUnUsuarioQuedaAuditada() throws Exception {
        MockHttpSession sesionAdmin = iniciarSesionDeAdministrador("sanmartin");

        Long idDeJuan = crearUsuario(sesionAdmin, "sanmartin",
                "Juan Pérez", "juan.auditoria@sanmartin.gob.ar");

        mvc.perform(get(portalDe("sanmartin", "/api/auditoria")).session(sesionAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accion").value("usuario.creado"))
                .andExpect(jsonPath("$[0].entidadTipo").value("usuario"))
                .andExpect(jsonPath("$[0].entidadId").value(String.valueOf(idDeJuan)))
                // El actor es quien hizo el alta (el administrador con la
                // sesión abierta), no el usuario recién creado.
                .andExpect(jsonPath("$[0].actorNombre").value("Administrador de San Martín"))
                .andExpect(jsonPath("$[0].actorEmail").value(emailDelAdministrador("sanmartin")))
                .andExpect(jsonPath("$[0].detalle").value(
                        "Creó al usuario Juan Pérez (juan.auditoria@sanmartin.gob.ar)."));
    }

    @Test
    @DisplayName("la publicación del listener de auditoría queda marcada completa")
    void laPublicacionDelListenerQuedaCompleta() throws Exception {
        MockHttpSession sesionAdmin = iniciarSesionDeAdministrador("sanmartin");
        crearUsuario(sesionAdmin, "sanmartin", "Ana López", "ana.auditoria@sanmartin.gob.ar");

        // El registro persistente de Spring Modulith (ADR 0013 §2) es lo
        // que sostiene la entrega al menos una vez: si el listener nunca
        // terminara, esta fila seguiría con completiondate nulo.
        try (Connection conexion = conectarComoTenant("sanmartin");
                Statement sentencia = conexion.createStatement();
                ResultSet fila = sentencia.executeQuery("""
                        select status, completiondate
                        from event_publication
                        where eventtype like '%UsuarioCreado%'
                        """)) {

            Assertions.assertTrue(fila.next(),
                    "Tiene que haber una fila de event_publication para UsuarioCreado.");
            Assertions.assertEquals("COMPLETED", fila.getString("status"));
            Assertions.assertNotNull(fila.getTimestamp("completiondate"));
        }
    }

    @Test
    @DisplayName("aislamiento: la auditoría de un municipio no aparece en la del otro")
    void laAuditoriaDeUnMunicipioNoApareceEnLaDelOtro() throws Exception {
        MockHttpSession sesionSanMartin = iniciarSesionDeAdministrador("sanmartin");
        crearUsuario(sesionSanMartin, "sanmartin",
                "Carla Ríos", "carla.auditoria@sanmartin.gob.ar");

        MockHttpSession sesionMoron = iniciarSesionDeAdministrador("moron");
        crearUsuario(sesionMoron, "moron",
                "Diego Sosa", "diego.auditoria@moron.gob.ar");

        mvc.perform(get(portalDe("sanmartin", "/api/auditoria")).session(sesionSanMartin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].actorEmail",
                        org.hamcrest.Matchers.everyItem(
                                org.hamcrest.Matchers.not("diego.auditoria@moron.gob.ar"))))
                .andExpect(jsonPath("$[*].detalle",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.containsString("Diego Sosa")))));

        mvc.perform(get(portalDe("moron", "/api/auditoria")).session(sesionMoron))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].detalle",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.containsString("Carla Ríos")))));
    }

    @Test
    @DisplayName("sin el permiso auditoria.ver, el registro no se puede leer")
    void sinPermisoNoSePuedeLeerLaAuditoria() throws Exception {
        MockHttpSession sesionAdmin = iniciarSesionDeAdministrador("sanmartin");
        Long idDelRolAgente = idDelRol(sesionAdmin, "sanmartin", "agente");
        crearYObtenerId(sesionAdmin, "sanmartin",
                "Bruno Ferro", "bruno.auditoria@sanmartin.gob.ar", idDelRolAgente);

        MockHttpSession sesionDeBruno = iniciarSesion(
                "sanmartin", "bruno.auditoria@sanmartin.gob.ar", "otra-contrasena-larga");

        mvc.perform(get(portalDe("sanmartin", "/api/auditoria")).session(sesionDeBruno))
                .andExpect(status().isForbidden());
    }

    private Long crearUsuario(MockHttpSession sesion, String subdominio, String nombre, String email)
            throws Exception {

        Long idDelRolAgente = idDelRol(sesion, subdominio, "agente");
        return crearYObtenerId(sesion, subdominio, nombre, email, idDelRolAgente);
    }

    private Long crearYObtenerId(MockHttpSession sesion, String subdominio, String nombre,
            String email, Long idDeRol) throws Exception {

        MvcResult resultado = mvc.perform(post(portalDe(subdominio, "/api/usuarios"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombre":"%s","email":"%s","password":"otra-contrasena-larga","roles":[%d]}
                        """.formatted(nombre, email, idDeRol)))
                .andExpect(status().isCreated())
                .andReturn();

        String cuerpo = resultado.getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(cuerpo, "$.id")).longValue();
    }

    private Long idDelRol(MockHttpSession sesion, String subdominio, String codigo) throws Exception {
        String cuerpo = mvc.perform(get(portalDe(subdominio, "/api/roles")).session(sesion))
                .andReturn().getResponse().getContentAsString();

        java.util.List<java.util.Map<String, Object>> roles = com.jayway.jsonpath.JsonPath.read(
                cuerpo, "$[?(@.codigo=='" + codigo + "')]");
        return ((Number) roles.get(0).get("id")).longValue();
    }
}
