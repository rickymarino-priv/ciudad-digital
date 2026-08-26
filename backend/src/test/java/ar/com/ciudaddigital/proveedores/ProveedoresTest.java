package ar.com.ciudaddigital.proveedores;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import ar.com.ciudaddigital.SoporteDeIntegracion;

/**
 * Registro público de proveedores y su gestión por el municipio (ADR
 * 0014), y consulta pública por token de seguimiento (ADR 0017), mismo
 * estilo que {@code ReclamosTest}/{@code TasasTest}.
 *
 * <p>Cada test fija explícitamente qué módulos tiene contratado cada
 * municipio antes de verificar nada, mismo criterio que el resto de los
 * tests de módulo: el contenedor de Postgres se comparte entre clases de
 * test.
 */
class ProveedoresTest extends SoporteDeIntegracion {

    private static final String A = "sanisidro";
    private static final String B = "tigre";

    @BeforeEach
    void municipiosDePrueba() throws Exception {
        asegurarMunicipio(A, "San Isidro", "#0D47A1");
        asegurarMunicipio(B, "Tigre", "#00695C");
    }

    @Test
    @DisplayName("alta pública con datos válidos responde 201, estado PENDIENTE y token de seguimiento no vacío")
    void altaPublicaConDatosValidos() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "proveedores");

        mvc.perform(registrar(A, cuerpoDeAlta("Constructora del Norte SA", cuitAleatorio())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.tokenDeSeguimiento").isNotEmpty());
    }

    @Test
    @DisplayName("alta con un CUIT ya registrado en el mismo municipio se rechaza con 400")
    void altaConCuitDuplicadoSeRechaza() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "proveedores");

        String cuit = cuitAleatorio();
        mvc.perform(registrar(A, cuerpoDeAlta("Primera SA", cuit)))
                .andExpect(status().isCreated());

        mvc.perform(registrar(A, cuerpoDeAlta("Segunda SA", cuit)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("alta con un CUIT mal formado (letras, o una cantidad de dígitos distinta de 11) se rechaza con 400")
    void altaConCuitMalFormadoSeRechaza() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "proveedores");

        mvc.perform(registrar(A, cuerpoDeAlta("Con Letras SA", "20-ABCDEFGH-1")))
                .andExpect(status().isBadRequest());

        mvc.perform(registrar(A, cuerpoDeAlta("Corto SA", "2012345")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("el mismo CUIT con y sin guiones se normaliza igual: la segunda alta se rechaza por duplicado")
    void normalizacionDeCuitEvitaEvadirLaUnicidad() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "proveedores");

        mvc.perform(registrar(A, cuerpoDeAlta("Con Guiones SA", "20-12345678-1")))
                .andExpect(status().isCreated());

        mvc.perform(registrar(A, cuerpoDeAlta("Sin Guiones SA", "20123456781")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("consulta por token: trae rubro y documentación declarada, sin email/teléfono/domicilio")
    void consultaPorTokenSinDatosDeContacto() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "proveedores");

        MvcResult resultadoDeAlta = mvc.perform(registrar(A, cuerpoDeAlta("Servicios del Río SA", cuitAleatorio())))
                .andExpect(status().isCreated())
                .andReturn();
        String token = JsonPath.read(resultadoDeAlta.getResponse().getContentAsString(), "$.tokenDeSeguimiento");

        mvc.perform(get(portalDe(A, "/api/proveedores/seguimiento/" + token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rubro").value("SERVICIOS"))
                .andExpect(jsonPath("$.declaraConstanciaAfip").value(true))
                .andExpect(jsonPath("$.declaraSeguroResponsabilidadCivil").value(true))
                .andExpect(jsonPath("$.declaraCertificadoAntecedentes").value(false))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.emailContacto").doesNotExist())
                .andExpect(jsonPath("$.telefonoContacto").doesNotExist())
                .andExpect(jsonPath("$.domicilio").doesNotExist());
    }

    @Test
    @DisplayName("consulta con un token inventado da 404 con mensaje genérico")
    void consultaConTokenInventado() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "proveedores");

        mvc.perform(get(portalDe(A, "/api/proveedores/seguimiento/token-inventado")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("No encontramos un proveedor con ese código."));
    }

    @Test
    @DisplayName("listado protegido: sin sesión 401; administrador y agente ven el shape completo")
    void listadoProtegidoPorPermiso() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "proveedores");
        MockHttpSession administrador = iniciarSesionDeAdministrador(A);

        MvcResult resultadoDeAlta = mvc.perform(
                registrar(A, cuerpoDeAlta("Tecnología Aplicada SA", cuitAleatorio())))
                .andExpect(status().isCreated())
                .andReturn();
        Long id = ((Number) JsonPath.read(resultadoDeAlta.getResponse().getContentAsString(), "$.id")).longValue();

        // Mismo comportamiento que ReclamosTest.permisoDeLectura: sin sesión el
        // 401 lo emite el punto de entrada de autenticación, no la
        // evaluación del permiso (que exige haber pasado por ahí primero).
        mvc.perform(get(portalDe(A, "/api/proveedores")))
                .andExpect(status().isUnauthorized());

        mvc.perform(get(portalDe(A, "/api/proveedores")).session(administrador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].emailContacto").isNotEmpty());

        MockHttpSession agente = crearAgenteYLoguear(A, administrador, "agente-proveedores@sanisidro.gob.ar");
        mvc.perform(get(portalDe(A, "/api/proveedores")).session(agente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").isNotEmpty());
    }

    @Test
    @DisplayName("aprobar refleja el nuevo estado en la consulta por token; rechazar refleja el comentario")
    void cambioDeEstadoSeReflejaEnLaConsultaPorToken() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "proveedores");
        MockHttpSession administrador = iniciarSesionDeAdministrador(A);

        MvcResult resultadoDeAlta = mvc.perform(
                registrar(A, cuerpoDeAlta("Antecedentes Profesionales SA", cuitAleatorio())))
                .andExpect(status().isCreated())
                .andReturn();
        String cuerpo = resultadoDeAlta.getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(cuerpo, "$.id")).longValue();
        String token = JsonPath.read(cuerpo, "$.tokenDeSeguimiento");

        mvc.perform(cambiarEstado(A, administrador, id, "APROBADO", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADO"));

        mvc.perform(get(portalDe(A, "/api/proveedores/seguimiento/" + token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADO"));

        // Segundo registro para ejercitar el camino de rechazo (el primero ya quedó terminal).
        MvcResult otraAlta = mvc.perform(
                registrar(A, cuerpoDeAlta("Rechazo SA", cuitAleatorio())))
                .andExpect(status().isCreated())
                .andReturn();
        String otroCuerpo = otraAlta.getResponse().getContentAsString();
        Long otroId = ((Number) JsonPath.read(otroCuerpo, "$.id")).longValue();
        String otroToken = JsonPath.read(otroCuerpo, "$.tokenDeSeguimiento");

        mvc.perform(cambiarEstado(A, administrador, otroId, "RECHAZADO", "Falta el seguro"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.comentarioGestion").value("Falta el seguro"));

        mvc.perform(get(portalDe(A, "/api/proveedores/seguimiento/" + otroToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.comentarioGestion").value("Falta el seguro"));
    }

    @Test
    @DisplayName("una transición inválida (rechazar un proveedor ya aprobado) se rechaza con 400")
    void transicionInvalidaSeRechaza() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "proveedores");
        MockHttpSession administrador = iniciarSesionDeAdministrador(A);

        MvcResult resultadoDeAlta = mvc.perform(
                registrar(A, cuerpoDeAlta("Ya Aprobada SA", cuitAleatorio())))
                .andExpect(status().isCreated())
                .andReturn();
        Long id = ((Number) JsonPath.read(resultadoDeAlta.getResponse().getContentAsString(), "$.id")).longValue();

        mvc.perform(cambiarEstado(A, administrador, id, "APROBADO", null))
                .andExpect(status().isOk());

        mvc.perform(cambiarEstado(A, administrador, id, "RECHAZADO", null))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("sin el módulo contratado, alta/listado/consulta por token/cambio de estado rechazan con 403 "
            + "MODULO_NO_CONTRATADO, incluso sin sesión y con datos/token válidos")
    void sinModuloContratadoRechazaTodasLasRutas() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(B, plataforma);

        mvc.perform(registrar(B, cuerpoDeAlta("Cualquiera SA", cuitAleatorio())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("proveedores"));

        mvc.perform(get(portalDe(B, "/api/proveedores")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("proveedores"));

        mvc.perform(get(portalDe(B, "/api/proveedores/seguimiento/cualquier-token")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("proveedores"));

        mvc.perform(cambiarEstado(B, null, 1L, "APROBADO", null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("proveedores"));
    }

    @Test
    @DisplayName("un usuario sin ningún rol recibe 403 sin código al intentar cambiar el estado")
    void usuarioSinRolesNoPuedeGestionar() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "proveedores");
        MockHttpSession administrador = iniciarSesionDeAdministrador(A);

        MvcResult resultadoDeAlta = mvc.perform(
                registrar(A, cuerpoDeAlta("Sin Permiso SA", cuitAleatorio())))
                .andExpect(status().isCreated())
                .andReturn();
        Long id = ((Number) JsonPath.read(resultadoDeAlta.getResponse().getContentAsString(), "$.id")).longValue();

        MockHttpSession sinRoles = crearUsuarioSinRoles(A, administrador, "vecino-sin-rol@sanisidro.gob.ar");

        mvc.perform(cambiarEstado(A, sinRoles, id, "APROBADO", null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").doesNotExist());
    }

    @Test
    @DisplayName("aislamiento: el token y el listado de un municipio no cruzan al otro, "
            + "pero el mismo CUIT sí puede registrarse en ambos")
    void aislamientoEntreTenants() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "proveedores");
        fijarModulos(B, plataforma, "proveedores");

        MockHttpSession administradorDeB = iniciarSesionDeAdministrador(B);

        String cuit = cuitAleatorio();
        MvcResult resultadoDeAlta = mvc.perform(registrar(A, cuerpoDeAlta("Proveedor de San Isidro SA", cuit)))
                .andExpect(status().isCreated())
                .andReturn();
        String cuerpoDeAlta = resultadoDeAlta.getResponse().getContentAsString();
        Long idEnA = ((Number) JsonPath.read(cuerpoDeAlta, "$.id")).longValue();
        String token = JsonPath.read(cuerpoDeAlta, "$.tokenDeSeguimiento");

        // El token es real (de un proveedor que existe en A), pero la
        // consulta corre contra la base de B, que no tiene esa fila: es la
        // garantía real de aislamiento (el datasource ruteado por tenant),
        // no un token "mal formado" (mismo razonamiento que
        // ReclamosTest.aislamientoDeTokenEntreTenants).
        mvc.perform(get(portalDe(B, "/api/proveedores/seguimiento/" + token)))
                .andExpect(status().isNotFound());

        mvc.perform(get(portalDe(B, "/api/proveedores")).session(administradorDeB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + idEnA + " && @.razonSocial == 'Proveedor de San Isidro SA')]")
                        .isEmpty());

        // Deliberadamente al revés de lo que uno esperaría de una unicidad
        // "global": el mismo CUIT registrado en A vuelve a registrarse sin
        // conflicto en B, porque la unicidad es por base de tenant, no
        // cross-tenant (ADR 0001).
        mvc.perform(registrar(B, cuerpoDeAlta("Proveedor de Tigre SA", cuit)))
                .andExpect(status().isCreated());
    }

    private MockHttpServletRequestBuilder registrar(String subdominio, String cuerpo) {
        return post(portalDe(subdominio, "/api/proveedores"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private MockHttpServletRequestBuilder cambiarEstado(
            String subdominio, MockHttpSession sesion, Long id, String estado, String comentario) {

        String comentarioJson = comentario == null ? "null" : "\"" + comentario + "\"";
        MockHttpServletRequestBuilder request = patch(portalDe(subdominio, "/api/proveedores/" + id + "/estado"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"" + estado + "\",\"comentario\":" + comentarioJson + "}");

        return sesion == null ? request : request.session(sesion);
    }

    private static String cuerpoDeAlta(String razonSocial, String cuit) {
        return """
                {"razonSocial":"%s","cuit":"%s","rubro":"SERVICIOS",
                 "emailContacto":"contacto@empresa.com","telefonoContacto":"011-4444-5555",
                 "domicilio":"Av. Libertador 1000","declaraConstanciaAfip":true,
                 "declaraSeguroResponsabilidadCivil":true,"declaraCertificadoAntecedentes":false,
                 "documentacionAdicional":"Certificado ISO 9001"}"""
                .formatted(razonSocial, cuit);
    }

    /** CUIT único por test, con guiones, para no chocar entre corridas de la misma clase. */
    private static String cuitAleatorio() {
        String digitos = String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits()));
        digitos = ("20" + digitos).substring(0, 10);
        return digitos.substring(0, 2) + "-" + digitos.substring(2) + "-1";
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

    /** Crea un usuario con el rol de sistema 'agente' y abre su sesión. */
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
