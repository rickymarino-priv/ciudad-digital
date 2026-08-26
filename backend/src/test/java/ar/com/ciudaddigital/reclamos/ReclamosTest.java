package ar.com.ciudaddigital.reclamos;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * Alta pública de reclamos y su gestión por el municipio (ADR 0014), y
 * gating por entitlement (ADR 0012) usando {@code reclamos} como primer
 * módulo funcional real, en vez de {@code ejemplo}.
 *
 * <p>Cada test fija explícitamente qué módulos tiene contratado cada
 * municipio antes de verificar nada, mismo criterio que
 * {@code EntitlementDeModulosTest}: el contenedor de Postgres se comparte
 * entre clases de test.
 */
class ReclamosTest extends SoporteDeIntegracion {

    private static final String A = "avellaneda";
    private static final String B = "lanus";

    @BeforeEach
    void municipiosDePrueba() throws Exception {
        asegurarMunicipio(A, "Avellaneda", "#1565C0");
        asegurarMunicipio(B, "Lanús", "#C62828");
    }

    @Test
    @DisplayName("alta anónima: con el módulo contratado responde 201, sin él 403 MODULO_NO_CONTRATADO")
    void altaAnonimaSoloConElModuloContratado() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "reclamos");
        fijarModulos(B, plataforma);

        mvc.perform(cargar(A, """
                {"categoria":"BACHE","descripcion":"Pozo grande en la vereda",
                 "direccion":"San Martín 123","nombreContacto":"Ana","contacto":"ana@mail.com"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.categoria").value("BACHE"))
                .andExpect(jsonPath("$.estado").value("NUEVO"))
                .andExpect(jsonPath("$.tokenDeSeguimiento").isNotEmpty());

        mvc.perform(cargar(B, """
                {"categoria":"BACHE","descripcion":"Pozo grande en la vereda","direccion":"San Martín 123"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("reclamos"));
    }

    @Test
    @DisplayName("alta anónima sin descripción, o con categoría inexistente, se rechaza con 400")
    void altaAnonimaInvalidaSeRechaza() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "reclamos");

        mvc.perform(cargar(A, """
                {"categoria":"BACHE","direccion":"San Martín 123"}"""))
                .andExpect(status().isBadRequest());

        mvc.perform(cargar(A, """
                {"categoria":"INEXISTENTE","descripcion":"algo","direccion":"San Martín 123"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("permiso de lectura: sin sesión 401, sin roles 403 sin código, administrador 200")
    void permisoDeLectura() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "reclamos");
        MockHttpSession administrador = iniciarSesionDeAdministrador(A);
        Long id = cargarReclamo(A, administrador);

        mvc.perform(get(portalDe(A, "/api/reclamos")))
                .andExpect(status().isUnauthorized());

        MockHttpSession sinRoles = crearUsuarioSinRoles(A, administrador, "vecino-sin-rol@avellaneda.gob.ar");
        mvc.perform(get(portalDe(A, "/api/reclamos")).session(sinRoles))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").doesNotExist());

        // Sin longitud exacta: este municipio se reutiliza entre tests de
        // esta clase (mismo criterio que EntitlementDeModulosTest), así que
        // lo único que importa es que lo recién cargado aparezca listado.
        mvc.perform(get(portalDe(A, "/api/reclamos")).session(administrador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").isNotEmpty());
    }

    @Test
    @DisplayName("permiso de gestión distinto del de lectura: solo reclamos.ver deja listar pero no gestionar")
    void permisoDeGestionEsDistintoDelDeLectura() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "reclamos");
        MockHttpSession administrador = iniciarSesionDeAdministrador(A);
        Long id = cargarReclamo(A, administrador);

        MockHttpSession soloLectura =
                crearUsuarioConRolPersonalizado(A, administrador, "solo-lee@avellaneda.gob.ar", "reclamos.ver");

        mvc.perform(get(portalDe(A, "/api/reclamos")).session(soloLectura))
                .andExpect(status().isOk());

        mvc.perform(cambiarEstado(A, soloLectura, id, "EN_PROCESO", null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").doesNotExist());
    }

    @Test
    @DisplayName("transiciones: nuevo -> en_proceso -> resuelto funciona; un salto inválido se rechaza")
    void transicionesDeEstado() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "reclamos");
        MockHttpSession administrador = iniciarSesionDeAdministrador(A);
        Long id = cargarReclamo(A, administrador);

        mvc.perform(cambiarEstado(A, administrador, id, "EN_PROCESO", "Lo estamos viendo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_PROCESO"))
                .andExpect(jsonPath("$.comentarioGestion").value("Lo estamos viendo"));

        mvc.perform(cambiarEstado(A, administrador, id, "RESUELTO", "Reparado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RESUELTO"))
                .andExpect(jsonPath("$.comentarioGestion").value("Reparado"));

        mvc.perform(cambiarEstado(A, administrador, id, "EN_PROCESO", null))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("un salto de nuevo directo a resuelto, sin pasar por en_proceso, se rechaza con 400")
    void saltoDeNuevoAResueltoSeRechaza() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "reclamos");
        MockHttpSession administrador = iniciarSesionDeAdministrador(A);
        Long id = cargarReclamo(A, administrador);

        mvc.perform(cambiarEstado(A, administrador, id, "RESUELTO", null))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("aislamiento: un reclamo cargado en un municipio no aparece en el listado del otro")
    void aislamientoEntreTenants() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "reclamos");
        fijarModulos(B, plataforma, "reclamos");

        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);
        MockHttpSession administradorDeB = iniciarSesionDeAdministrador(B);

        // Alta anónima en A, alta con sesión en B: da igual el origen, lo
        // que importa es a qué base va cada fila. Descripciones únicas
        // (con un sufijo aleatorio) para no confundirse con filas que
        // hayan quedado de otro test de esta misma clase, que reutiliza
        // estos dos municipios (mismo criterio que EntitlementDeModulosTest).
        String descripcionDeA = "Reclamo de Avellaneda " + java.util.UUID.randomUUID();
        String descripcionDeB = "Reclamo de Lanús " + java.util.UUID.randomUUID();

        mvc.perform(cargar(A, """
                {"categoria":"BACHE","descripcion":"%s","direccion":"Calle 1"}"""
                .formatted(descripcionDeA)))
                .andExpect(status().isCreated());
        mvc.perform(cargar(B, """
                {"categoria":"ALUMBRADO","descripcion":"%s","direccion":"Calle 2"}"""
                .formatted(descripcionDeB)))
                .andExpect(status().isCreated());

        // El id por sí solo no sirve para comparar entre municipios: cada
        // base tiene su propia secuencia, así que un id de A puede
        // coincidir con uno de B sin que eso signifique nada. Lo que
        // prueba el aislamiento es la descripción, única por el sufijo
        // aleatorio: el reclamo de A tiene que aparecer en el listado de
        // A y no en el de B, y viceversa.
        mvc.perform(get(portalDe(A, "/api/reclamos")).session(administradorDeA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.descripcion == '" + descripcionDeA + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.descripcion == '" + descripcionDeB + "')]").isEmpty());

        mvc.perform(get(portalDe(B, "/api/reclamos")).session(administradorDeB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.descripcion == '" + descripcionDeB + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.descripcion == '" + descripcionDeA + "')]").isEmpty());
    }

    @Test
    @DisplayName("seguimiento por token: consulta pública sin sesión trae id/categoria/estado sin datos de "
            + "gestión, y refleja el estado/comentario luego de un cambio con sesión")
    void seguimientoPorTokenSinDatosDeGestion() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "reclamos");
        MockHttpSession administrador = iniciarSesionDeAdministrador(A);

        MvcResult resultadoDeAlta = mvc.perform(cargar(A, """
                {"categoria":"BACHE","descripcion":"Pozo grande en la vereda",
                 "direccion":"San Martín 123","nombreContacto":"Ana","contacto":"ana@mail.com"}"""))
                .andExpect(status().isCreated())
                .andReturn();

        String cuerpoDeAlta = resultadoDeAlta.getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(cuerpoDeAlta, "$.id")).longValue();
        String token = JsonPath.read(cuerpoDeAlta, "$.tokenDeSeguimiento");

        mvc.perform(get(portalDe(A, "/api/reclamos/seguimiento/" + token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.categoria").value("BACHE"))
                .andExpect(jsonPath("$.estado").value("NUEVO"))
                .andExpect(jsonPath("$.descripcion").doesNotExist())
                .andExpect(jsonPath("$.direccion").doesNotExist())
                .andExpect(jsonPath("$.nombreContacto").doesNotExist())
                .andExpect(jsonPath("$.contacto").doesNotExist());

        mvc.perform(cambiarEstado(A, administrador, id, "EN_PROCESO", "Lo estamos viendo"))
                .andExpect(status().isOk());

        mvc.perform(get(portalDe(A, "/api/reclamos/seguimiento/" + token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_PROCESO"))
                .andExpect(jsonPath("$.comentarioGestion").value("Lo estamos viendo"));
    }

    @Test
    @DisplayName("seguimiento por token: un token inventado da 404; con el módulo sin contratar, "
            + "un token válido da 403 MODULO_NO_CONTRATADO")
    void seguimientoPorTokenInexistenteOSinModulo() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "reclamos");
        fijarModulos(B, plataforma);

        mvc.perform(get(portalDe(A, "/api/reclamos/seguimiento/token-inventado")))
                .andExpect(status().isNotFound());

        MvcResult resultadoDeAlta = mvc.perform(cargar(A, """
                {"categoria":"BACHE","descripcion":"Pozo grande en la vereda","direccion":"San Martín 123"}"""))
                .andExpect(status().isCreated())
                .andReturn();
        String token = JsonPath.read(resultadoDeAlta.getResponse().getContentAsString(), "$.tokenDeSeguimiento");

        mvc.perform(get(portalDe(B, "/api/reclamos/seguimiento/" + token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("reclamos"));
    }

    @Test
    @DisplayName("aislamiento: el token de un reclamo de un municipio no encuentra nada en el otro")
    void aislamientoDeTokenEntreTenants() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "reclamos");
        fijarModulos(B, plataforma, "reclamos");

        MvcResult resultadoDeAlta = mvc.perform(cargar(A, """
                {"categoria":"BACHE","descripcion":"Pozo grande en la vereda","direccion":"San Martín 123"}"""))
                .andExpect(status().isCreated())
                .andReturn();
        String token = JsonPath.read(resultadoDeAlta.getResponse().getContentAsString(), "$.tokenDeSeguimiento");

        // El token es real (de un reclamo que existe en A), pero la
        // consulta corre contra la base de B, que no tiene esa fila: es la
        // garantía real de aislamiento (el datasource ruteado por tenant),
        // no un token "mal formado".
        mvc.perform(get(portalDe(B, "/api/reclamos/seguimiento/" + token)))
                .andExpect(status().isNotFound());
    }

    private MockHttpServletRequestBuilder cargar(String subdominio, String cuerpo) {
        return post(portalDe(subdominio, "/api/reclamos"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private MockHttpServletRequestBuilder cambiarEstado(
            String subdominio, MockHttpSession sesion, Long id, String estado, String comentario) {

        String comentarioJson = comentario == null ? "null" : "\"" + comentario + "\"";
        return patch(portalDe(subdominio, "/api/reclamos/" + id + "/estado"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"" + estado + "\",\"comentario\":" + comentarioJson + "}");
    }

    /** Carga un reclamo con sesión (más simple para tests que no ejercitan el alta en sí) y devuelve su id. */
    private Long cargarReclamo(String subdominio, MockHttpSession sesion) throws Exception {
        MvcResult resultado = mvc.perform(cargar(subdominio, """
                {"categoria":"RESIDUOS","descripcion":"No pasa el camión hace una semana",
                 "direccion":"Belgrano 456"}""").session(sesion))
                .andExpect(status().isCreated())
                .andReturn();

        return ((Number) JsonPath.read(resultado.getResponse().getContentAsString(), "$.id")).longValue();
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

    /** Crea un rol nuevo con exactamente los permisos indicados, asigna un usuario nuevo y abre su sesión. */
    private MockHttpSession crearUsuarioConRolPersonalizado(
            String subdominio, MockHttpSession sesionAdmin, String email, String... permisos)
            throws Exception {

        String listaDePermisos = String.join(",", java.util.Arrays.stream(permisos)
                .map(codigo -> "\"" + codigo + "\"").toList());

        MvcResult resultadoDelRol = mvc.perform(post(portalDe(subdominio, "/api/roles"))
                .session(sesionAdmin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"codigo":"rol-de-prueba-%s","nombre":"Rol de prueba",
                         "descripcion":"Rol armado para un test","permisos":[%s]}
                        """.formatted(subdominio, listaDePermisos)))
                .andExpect(status().isCreated())
                .andReturn();

        Long idDelRol = ((Number) JsonPath.read(
                resultadoDelRol.getResponse().getContentAsString(), "$.id")).longValue();

        String password = "otra-contrasena-larga";
        mvc.perform(post(portalDe(subdominio, "/api/usuarios"))
                .session(sesionAdmin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombre":"Usuario con rol de prueba","email":"%s","password":"%s","roles":[%d]}
                        """.formatted(email, password, idDelRol)))
                .andExpect(status().isCreated());

        return iniciarSesion(subdominio, email, password);
    }
}
