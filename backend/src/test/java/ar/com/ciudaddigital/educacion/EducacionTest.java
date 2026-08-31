package ar.com.ciudaddigital.educacion;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
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
 * Alta protegida, lectura pública y actualización de estado de
 * instituciones educativas municipales (R24, ADR 0028).
 *
 * <p>Cada test fija explícitamente qué módulos tiene contratado cada
 * municipio antes de verificar nada, mismo criterio que {@code ObrasTest}/
 * {@code ArboladoTest}: el contenedor de Postgres se comparte entre clases
 * de test.
 */
class EducacionTest extends SoporteDeIntegracion {

    private static final String A = "quilmes";
    private static final String B = "berazategui";

    @BeforeEach
    void municipiosDePrueba() throws Exception {
        asegurarMunicipio(A, "Quilmes", "#1B5E20");
        asegurarMunicipio(B, "Berazategui", "#B71C1C");
    }

    @Test
    @DisplayName("alta con el módulo contratado y el permiso responde 201 con la institución ACTIVA")
    void altaConElPermisoQuedaActiva() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "educacion");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        mvc.perform(registrar(A, administradorDeA, """
                {"nombre":"Jardín Municipal N° 1","tipo":"JARDIN_DE_INFANTES","ubicacion":"Calle 12 N° 345",
                 "descripcion":"Jardín de infantes municipal."}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.nombre").value("Jardín Municipal N° 1"))
                .andExpect(jsonPath("$.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.publicadoPorNombre").value("Administrador de Quilmes"))
                .andExpect(jsonPath("$.publicadoPorEmail").value(emailDelAdministrador(A)));
    }

    @Test
    @DisplayName("alta sin el permiso educacion.gestionar se rechaza con 403 sin código")
    void altaSinElPermisoSeRechaza() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "educacion");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        MockHttpSession agenteSinPermiso =
                crearUsuarioConSoloOtroPermiso(A, administradorDeA, "agente-sin-educacion@quilmes.gob.ar");

        mvc.perform(registrar(A, agenteSinPermiso, """
                {"nombre":"Centro de Formación Profesional Norte","tipo":"CENTRO_DE_FORMACION_PROFESIONAL",
                 "ubicacion":"Barrio Norte","descripcion":null}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").doesNotExist());
    }

    @Test
    @DisplayName("alta con tipo inválido da 400")
    void altaConTipoInvalidoSeRechaza() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "educacion");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        mvc.perform(registrar(A, administradorDeA, """
                {"nombre":"Escuela inventada","tipo":"ESCUELA_PRIMARIA","ubicacion":"Calle 1",
                 "descripcion":null}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("listado público sin sesión, con filtros por estado, tipo y q, por separado y combinados")
    void listadoPublicoConFiltros() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "educacion");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        String sufijo = UUID.randomUUID().toString();
        String nombreJardin = "Jardín Maternal " + sufijo;
        String nombreCentro = "Centro de Formación " + sufijo;

        registrarInstitucion(A, administradorDeA, nombreJardin, "JARDIN_MATERNAL", "Zona norte " + sufijo);
        registrarInstitucion(
                A, administradorDeA, nombreCentro, "CENTRO_DE_FORMACION_PROFESIONAL", "Zona sur " + sufijo);

        // Por estado: ambas siguen ACTIVA.
        mvc.perform(get(portalDe(A, "/api/educacion?estado=ACTIVA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreJardin + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreCentro + "')]").isNotEmpty());
        mvc.perform(get(portalDe(A, "/api/educacion?estado=CERRADA_DEFINITIVAMENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreJardin + "')]").isEmpty());

        // Por tipo.
        mvc.perform(get(portalDe(A, "/api/educacion?tipo=JARDIN_MATERNAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreJardin + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreCentro + "')]").isEmpty());

        // Por texto: matchea nombre o ubicación.
        mvc.perform(get(portalDe(A, "/api/educacion?q=" + sufijo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreJardin + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreCentro + "')]").isNotEmpty());

        // Combinados: tipo + estado + q.
        mvc.perform(get(portalDe(A, "/api/educacion?tipo=JARDIN_MATERNAL&estado=ACTIVA&q=" + sufijo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreJardin + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreCentro + "')]").isEmpty());

        // Estado o tipo inválidos dan 400, no "sin filtro".
        mvc.perform(get(portalDe(A, "/api/educacion?estado=INEXISTENTE")))
                .andExpect(status().isBadRequest());
        mvc.perform(get(portalDe(A, "/api/educacion?tipo=INEXISTENTE")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("circuito completo de transiciones válidas y transiciones inválidas dan 400")
    void circuitoDeTransicionesDeEstado() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "educacion");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        Long id = registrarInstitucion(A, administradorDeA, "Institución de prueba " + UUID.randomUUID(),
                "OTRA", "Ubicación de prueba");

        // Transición inválida directa: ACTIVA → CERRADA_DEFINITIVAMENTE.
        mvc.perform(actualizarEstado(A, administradorDeA, id, "CERRADA_DEFINITIVAMENTE"))
                .andExpect(status().isBadRequest());

        mvc.perform(actualizarEstado(A, administradorDeA, id, "CERRADA_TEMPORALMENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CERRADA_TEMPORALMENTE"));

        mvc.perform(actualizarEstado(A, administradorDeA, id, "ACTIVA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVA"));

        mvc.perform(actualizarEstado(A, administradorDeA, id, "CERRADA_TEMPORALMENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CERRADA_TEMPORALMENTE"));

        mvc.perform(actualizarEstado(A, administradorDeA, id, "CERRADA_DEFINITIVAMENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CERRADA_DEFINITIVAMENTE"));

        // CERRADA_DEFINITIVAMENTE es terminal: cualquier transición desde ahí da 400.
        mvc.perform(actualizarEstado(A, administradorDeA, id, "ACTIVA"))
                .andExpect(status().isBadRequest());
        mvc.perform(actualizarEstado(A, administradorDeA, id, "CERRADA_TEMPORALMENTE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("sin el módulo contratado, alta/listado/cambio de estado rechazan con 403 MODULO_NO_CONTRATADO, "
            + "incluso sin sesión y con datos válidos")
    void sinModuloContratadoRechazaTodasLasRutas() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(B, plataforma);
        MockHttpSession administradorDeB = iniciarSesionDeAdministrador(B);

        mvc.perform(registrar(B, administradorDeB, """
                {"nombre":"Institución sin módulo","tipo":"JARDIN_DE_INFANTES","ubicacion":"Calle 1",
                 "descripcion":null}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("educacion"));

        mvc.perform(get(portalDe(B, "/api/educacion")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("educacion"));

        mvc.perform(actualizarEstado(B, administradorDeB, 1L, "CERRADA_TEMPORALMENTE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("educacion"));
    }

    @Test
    @DisplayName("aislamiento: una institución registrada en un municipio no es visible ni actualizable "
            + "desde otro")
    void aislamientoEntreTenants() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "educacion");
        fijarModulos(B, plataforma, "educacion");

        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);
        MockHttpSession administradorDeB = iniciarSesionDeAdministrador(B);

        String sufijo = UUID.randomUUID().toString();
        String nombreDeA = "Institución de Quilmes " + sufijo;
        String ubicacionDeA = "Ubicación de Quilmes " + sufijo;
        Long idDeA = registrarInstitucion(A, administradorDeA, nombreDeA, "JARDIN_DE_INFANTES", ubicacionDeA);

        // No aparece en el listado del otro municipio, con ni sin filtros.
        mvc.perform(get(portalDe(B, "/api/educacion")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeA + "')]").isEmpty());
        mvc.perform(get(portalDe(B, "/api/educacion?estado=ACTIVA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeA + "')]").isEmpty());
        mvc.perform(get(portalDe(B, "/api/educacion?tipo=JARDIN_DE_INFANTES")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeA + "')]").isEmpty());
        mvc.perform(get(portalDe(B, "/api/educacion?q=" + sufijo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeA + "')]").isEmpty());

        // Sigue visible en el listado del municipio dueño.
        mvc.perform(get(portalDe(A, "/api/educacion")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeA + "')]").isNotEmpty());

        // El id de la institución de A no existe en la base de B: PATCH da 404, no
        // "la encuentra y la actualiza" (garantía real: el datasource ruteado
        // por tenant, no una validación de aplicación).
        mvc.perform(actualizarEstado(B, administradorDeB, idDeA, "CERRADA_TEMPORALMENTE"))
                .andExpect(status().isNotFound());
    }

    private MockHttpServletRequestBuilder registrar(String subdominio, MockHttpSession sesion, String cuerpo) {
        return post(portalDe(subdominio, "/api/educacion"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private MockHttpServletRequestBuilder actualizarEstado(
            String subdominio, MockHttpSession sesion, Long id, String estadoNuevo) {

        return patch(portalDe(subdominio, "/api/educacion/" + id + "/estado"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estadoNuevo\":\"" + estadoNuevo + "\"}");
    }

    private Long registrarInstitucion(String subdominio, MockHttpSession sesionAdmin, String nombre, String tipo,
            String ubicacion) throws Exception {

        MvcResult resultado = mvc.perform(registrar(subdominio, sesionAdmin, """
                {"nombre":"%s","tipo":"%s","ubicacion":"%s","descripcion":null}"""
                .formatted(nombre, tipo, ubicacion)))
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

    /**
     * Crea un usuario con un rol propio del municipio, sin
     * {@code educacion.gestionar} (ADR 0011: el municipio compone sus
     * propios roles), y abre su sesión.
     */
    private MockHttpSession crearUsuarioConSoloOtroPermiso(
            String subdominio, MockHttpSession sesionAdmin, String email) throws Exception {

        String cuerpoDelRol = mvc.perform(post(portalDe(subdominio, "/api/roles"))
                .session(sesionAdmin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"codigo":"sin-educacion","nombre":"Sin permiso de educación",
                         "descripcion":"No puede gestionar educación.","permisos":[]}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long idDelRol = ((Number) JsonPath.read(cuerpoDelRol, "$.id")).longValue();

        String password = "otra-contrasena-larga";
        mvc.perform(post(portalDe(subdominio, "/api/usuarios"))
                .session(sesionAdmin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombre":"Usuario sin permiso","email":"%s","password":"%s","roles":[%d]}
                        """.formatted(email, password, idDelRol)))
                .andExpect(status().isCreated());

        return iniciarSesion(subdominio, email, password);
    }
}
