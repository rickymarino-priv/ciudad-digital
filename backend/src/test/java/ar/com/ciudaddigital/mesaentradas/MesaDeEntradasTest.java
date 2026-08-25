package ar.com.ciudaddigital.mesaentradas;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import ar.com.ciudaddigital.SoporteDeIntegracion;

/**
 * Alta pública de trámites de Mesa de Entradas y su gestión por el
 * municipio (ADR 0015), calcado de {@code BoletinTest}/{@code ReclamosTest}:
 * mismo {@code SoporteDeIntegracion}, mismo criterio de fijar módulos
 * contratados antes de verificar nada.
 */
class MesaDeEntradasTest extends SoporteDeIntegracion {

    private static final String A = "tandil";
    private static final String B = "olavarria";

    @BeforeEach
    void municipiosDePrueba() throws Exception {
        asegurarMunicipio(A, "Tandil", "#00695C");
        asegurarMunicipio(B, "Olavarría", "#4527A0");
    }

    @Test
    @DisplayName("alta pública: con el módulo contratado responde 201, sin él 403 MODULO_NO_CONTRATADO")
    void altaPublicaSoloConElModuloContratado() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "mesaentradas");
        fijarModulos(B, plataforma);

        mvc.perform(iniciar(A, """
                {"tipo":"CERTIFICADO_DOMICILIO","solicitanteNombre":"Ana Vecina",
                 "solicitanteContacto":"ana@mail.com","domicilioACertificar":"San Martín 123"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.tipo").value("CERTIFICADO_DOMICILIO"))
                .andExpect(jsonPath("$.estado").value("INICIADO"))
                .andExpect(jsonPath("$.creadoEn").exists());

        mvc.perform(iniciar(B, """
                {"tipo":"CERTIFICADO_DOMICILIO","solicitanteNombre":"Ana Vecina",
                 "domicilioACertificar":"San Martín 123"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("mesaentradas"));
    }

    @Test
    @DisplayName("alta inválida: sin solicitanteNombre, sin domicilioACertificar, o con tipo inexistente, 400")
    void altaInvalidaSeRechaza() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "mesaentradas");

        mvc.perform(iniciar(A, """
                {"tipo":"CERTIFICADO_DOMICILIO","domicilioACertificar":"San Martín 123"}"""))
                .andExpect(status().isBadRequest());

        mvc.perform(iniciar(A, """
                {"tipo":"CERTIFICADO_DOMICILIO","solicitanteNombre":"Ana Vecina"}"""))
                .andExpect(status().isBadRequest());

        mvc.perform(iniciar(A, """
                {"tipo":"INEXISTENTE","solicitanteNombre":"Ana Vecina","domicilioACertificar":"San Martín 123"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("listado protegido: devuelve el expediente completo con un movimiento inicial anónimo; "
            + "sin el permiso, 403 sin código")
    void listadoProtegido() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "mesaentradas");
        MockHttpSession administrador = iniciarSesionDeAdministrador(A);

        // Filtrado por solicitanteNombre (único, con sufijo aleatorio), no
        // por id: mismo criterio que BoletinTest/CementerioTest, más
        // confiable con JsonPath que encadenar campos después de un filtro
        // por id numérico.
        String nombre = "Ana Vecina " + UUID.randomUUID();
        iniciarExpediente(A, nombre);

        ResultActions listado = mvc.perform(get(portalDe(A, "/api/mesaentradas")).session(administrador))
                .andExpect(status().isOk());

        // Jayway no encadena bien "[?(filtro)][0].campo" cuando el campo
        // vale null o hay que indexar más adentro de una colección anidada
        // (a diferencia del acceso a campos planos, que sí funciona con ese
        // patrón): se navega el JSON directamente en Java en vez de eso.
        List<Map<String, Object>> movimientos = movimientosDe(listado, nombre);
        assertEquals(1, movimientos.size());
        Map<String, Object> movimiento = movimientos.get(0);
        assertNull(movimiento.get("estadoAnterior"));
        assertEquals("INICIADO", movimiento.get("estadoNuevo"));
        assertNull(movimiento.get("actorNombre"));
        assertNull(movimiento.get("actorEmail"));

        MockHttpSession sinRoles = crearUsuarioSinRoles(A, administrador, "vecino-sin-rol@" + A + ".gob.ar");
        mvc.perform(get(portalDe(A, "/api/mesaentradas")).session(sinRoles))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").doesNotExist());
    }

    @Test
    @DisplayName("avanzar de INICIADO a EN_REVISION con mesaentradas.gestionar y un agente responde 200, "
            + "y el listado siguiente muestra dos movimientos")
    void avanzarEstadoValidoConAgente() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "mesaentradas");
        MockHttpSession administrador = iniciarSesionDeAdministrador(A);
        MockHttpSession agente = crearAgenteYLoguear(A, administrador, "agente-mesaentradas@" + A + ".gob.ar");

        String nombre = "Ana Vecina " + UUID.randomUUID();
        Long id = iniciarExpediente(A, nombre);

        mvc.perform(avanzarEstado(A, agente, id, "EN_REVISION", "Empezamos a revisarlo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_REVISION"));

        ResultActions listado = mvc.perform(get(portalDe(A, "/api/mesaentradas")).session(administrador))
                .andExpect(status().isOk());

        List<Map<String, Object>> movimientos = movimientosDe(listado, nombre);
        assertEquals(2, movimientos.size());
        Map<String, Object> segundoMovimiento = movimientos.get(1);
        assertEquals("INICIADO", segundoMovimiento.get("estadoAnterior"));
        assertEquals("EN_REVISION", segundoMovimiento.get("estadoNuevo"));
        assertEquals("Agente de prueba", segundoMovimiento.get("actorNombre"));
        assertEquals("agente-mesaentradas@" + A + ".gob.ar", segundoMovimiento.get("actorEmail"));
    }

    @Test
    @DisplayName("encadenar EN_REVISION -> APROBADO funciona; avanzar un expediente ya APROBADO (terminal) da 400")
    void avanzarHastaEstadoTerminalYLuegoRechazar() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "mesaentradas");
        MockHttpSession administrador = iniciarSesionDeAdministrador(A);
        Long id = iniciarExpediente(A, "Ana Vecina " + UUID.randomUUID());

        mvc.perform(avanzarEstado(A, administrador, id, "EN_REVISION", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_REVISION"));

        mvc.perform(avanzarEstado(A, administrador, id, "APROBADO", "Domicilio verificado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADO"));

        mvc.perform(avanzarEstado(A, administrador, id, "EN_REVISION", null))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("transición inválida directa (INICIADO -> APROBADO, saltando EN_REVISION) se rechaza con 400")
    void transicionInvalidaDirectaSeRechaza() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "mesaentradas");
        MockHttpSession administrador = iniciarSesionDeAdministrador(A);
        Long id = iniciarExpediente(A, "Ana Vecina " + UUID.randomUUID());

        mvc.perform(avanzarEstado(A, administrador, id, "APROBADO", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("No se puede pasar de INICIADO a APROBADO."));
    }

    @Test
    @DisplayName("aislamiento: un expediente iniciado en un municipio no aparece en el listado del otro")
    void aislamientoEntreTenants() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "mesaentradas");
        fijarModulos(B, plataforma, "mesaentradas");

        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);
        MockHttpSession administradorDeB = iniciarSesionDeAdministrador(B);

        // Nombres únicos (con un sufijo aleatorio) para no confundirse con
        // filas que hayan quedado de otro test de esta misma clase, que
        // reutiliza estos dos municipios (mismo criterio que BoletinTest).
        String nombreDeA = "Vecino de Tandil " + UUID.randomUUID();
        String nombreDeB = "Vecino de Olavarría " + UUID.randomUUID();

        mvc.perform(iniciar(A, """
                {"tipo":"CERTIFICADO_DOMICILIO","solicitanteNombre":"%s","domicilioACertificar":"Calle 1"}"""
                .formatted(nombreDeA)))
                .andExpect(status().isCreated());
        mvc.perform(iniciar(B, """
                {"tipo":"CERTIFICADO_DOMICILIO","solicitanteNombre":"%s","domicilioACertificar":"Calle 2"}"""
                .formatted(nombreDeB)))
                .andExpect(status().isCreated());

        mvc.perform(get(portalDe(A, "/api/mesaentradas")).session(administradorDeA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.solicitanteNombre == '" + nombreDeA + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.solicitanteNombre == '" + nombreDeB + "')]").isEmpty());

        mvc.perform(get(portalDe(B, "/api/mesaentradas")).session(administradorDeB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.solicitanteNombre == '" + nombreDeB + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.solicitanteNombre == '" + nombreDeA + "')]").isEmpty());
    }

    /** Movimientos del expediente cuyo solicitanteNombre matchea, leídos directo del JSON de respuesta. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> movimientosDe(ResultActions listado, String solicitanteNombre) throws Exception {
        String cuerpo = listado.andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> coincidencias = JsonPath.read(
                cuerpo, "$[?(@.solicitanteNombre == '" + solicitanteNombre + "')]");
        return (List<Map<String, Object>>) coincidencias.get(0).get("movimientos");
    }

    private MockHttpServletRequestBuilder iniciar(String subdominio, String cuerpo) {
        return post(portalDe(subdominio, "/api/mesaentradas"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private MockHttpServletRequestBuilder avanzarEstado(
            String subdominio, MockHttpSession sesion, Long id, String estado, String comentario) {

        String comentarioJson = comentario == null ? "null" : "\"" + comentario + "\"";
        return patch(portalDe(subdominio, "/api/mesaentradas/" + id + "/estado"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"" + estado + "\",\"comentario\":" + comentarioJson + "}");
    }

    /** Inicia un expediente sin sesión (alta pública) y devuelve su id. */
    private Long iniciarExpediente(String subdominio, String solicitanteNombre) throws Exception {
        MvcResult resultado = mvc.perform(iniciar(subdominio, """
                {"tipo":"CERTIFICADO_DOMICILIO","solicitanteNombre":"%s","domicilioACertificar":"San Martín 123"}"""
                .formatted(solicitanteNombre)))
                .andExpect(status().isCreated())
                .andReturn();

        return ((Number) JsonPath.read(resultado.getResponse().getContentAsString(), "$.id")).longValue();
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

    /** Crea un usuario sin ningún rol y abre su sesión: no puede hacer nada, que es el default correcto. */
    private MockHttpSession crearUsuarioSinRoles(String subdominio, MockHttpSession sesionAdmin, String email)
            throws Exception {

        String password = "otra-contrasena-larga";
        mvc.perform(post(portalDe(subdominio, "/api/usuarios"))
                .session(sesionAdmin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombre":"Vecino sin rol","email":"%s","password":"%s","roles":[]}
                        """.formatted(email, password)))
                .andExpect(status().isCreated());

        return iniciarSesion(subdominio, email, password);
    }

    /** Crea un usuario con el rol de sistema 'agente' (que sí tiene mesaentradas.gestionar) y abre su sesión. */
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
