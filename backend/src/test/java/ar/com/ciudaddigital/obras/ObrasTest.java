package ar.com.ciudaddigital.obras;

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
 * Alta protegida, lectura pública y actualización de estado de obras
 * públicas (R19, ADR 0023).
 *
 * <p>Cada test fija explícitamente qué módulos tiene contratado cada
 * municipio antes de verificar nada, mismo criterio que
 * {@code MultasTest}/{@code BoletinTest}: el contenedor de Postgres se
 * comparte entre clases de test.
 */
class ObrasTest extends SoporteDeIntegracion {

    private static final String A = "lanus";
    private static final String B = "avellaneda";

    @BeforeEach
    void municipiosDePrueba() throws Exception {
        asegurarMunicipio(A, "Lanús", "#1B5E20");
        asegurarMunicipio(B, "Avellaneda", "#B71C1C");
    }

    @Test
    @DisplayName("alta con el módulo contratado y el permiso responde 201 con la obra PLANIFICADA")
    void altaConElPermisoQuedaPlanificada() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "obras");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        mvc.perform(registrar(A, administradorDeA, """
                {"nombre":"Repavimentación Av. Principal","tipo":"VIALIDAD","ubicacion":"Av. Principal km 3",
                 "descripcion":"Repavimentación completa.","fechaInicioEstimada":"2026-09-01",
                 "fechaFinEstimada":"2026-12-01"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.nombre").value("Repavimentación Av. Principal"))
                .andExpect(jsonPath("$.estado").value("PLANIFICADA"))
                .andExpect(jsonPath("$.publicadoPorNombre").value("Administrador de Lanús"))
                .andExpect(jsonPath("$.publicadoPorEmail").value(emailDelAdministrador(A)));
    }

    @Test
    @DisplayName("alta sin el permiso obras.gestionar se rechaza con 403 sin código")
    void altaSinElPermisoSeRechaza() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "obras");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        MockHttpSession agenteSinPermiso =
                crearUsuarioConSoloOtroPermiso(A, administradorDeA, "agente-sin-obras@lanus.gob.ar");

        mvc.perform(registrar(A, agenteSinPermiso, """
                {"nombre":"Plaza nueva","tipo":"ESPACIO_PUBLICO","ubicacion":"Barrio Norte",
                 "descripcion":null,"fechaInicioEstimada":null,"fechaFinEstimada":null}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").doesNotExist());
    }

    @Test
    @DisplayName("alta con fechaFinEstimada anterior a fechaInicioEstimada da 400")
    void altaConFechasInvalidasSeRechaza() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "obras");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        mvc.perform(registrar(A, administradorDeA, """
                {"nombre":"Obra con fechas invertidas","tipo":"VIALIDAD","ubicacion":"Calle 1",
                 "descripcion":null,"fechaInicioEstimada":"2026-12-01","fechaFinEstimada":"2026-09-01"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("listado público sin sesión, con filtros por estado, tipo y q, por separado y combinados")
    void listadoPublicoConFiltros() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "obras");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        String sufijo = UUID.randomUUID().toString();
        String nombreVialidad = "Bacheo de calle " + sufijo;
        String nombreEspacioPublico = "Plaza " + sufijo;

        registrarObra(A, administradorDeA, nombreVialidad, "VIALIDAD", "Zona norte " + sufijo);
        registrarObra(A, administradorDeA, nombreEspacioPublico, "ESPACIO_PUBLICO", "Zona sur " + sufijo);

        // Por estado: ambas siguen PLANIFICADA.
        mvc.perform(get(portalDe(A, "/api/obras?estado=PLANIFICADA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreVialidad + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreEspacioPublico + "')]").isNotEmpty());
        mvc.perform(get(portalDe(A, "/api/obras?estado=FINALIZADA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreVialidad + "')]").isEmpty());

        // Por tipo.
        mvc.perform(get(portalDe(A, "/api/obras?tipo=VIALIDAD")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreVialidad + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreEspacioPublico + "')]").isEmpty());

        // Por texto: matchea nombre o ubicación.
        mvc.perform(get(portalDe(A, "/api/obras?q=" + sufijo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreVialidad + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreEspacioPublico + "')]").isNotEmpty());

        // Combinados: tipo + estado + q.
        mvc.perform(get(portalDe(A, "/api/obras?tipo=VIALIDAD&estado=PLANIFICADA&q=" + sufijo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreVialidad + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreEspacioPublico + "')]").isEmpty());

        // Estado o tipo inválidos dan 400, no "sin filtro".
        mvc.perform(get(portalDe(A, "/api/obras?estado=INEXISTENTE")))
                .andExpect(status().isBadRequest());
        mvc.perform(get(portalDe(A, "/api/obras?tipo=INEXISTENTE")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("circuito completo de transiciones válidas y una transición inválida da 400")
    void circuitoDeTransicionesDeEstado() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "obras");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        Long id = registrarObra(A, administradorDeA, "Obra de prueba " + UUID.randomUUID(),
                "SERVICIOS", "Ubicación de prueba");

        // Transición inválida directa: PLANIFICADA → FINALIZADA.
        mvc.perform(actualizarEstado(A, administradorDeA, id, "FINALIZADA"))
                .andExpect(status().isBadRequest());

        mvc.perform(actualizarEstado(A, administradorDeA, id, "EN_EJECUCION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_EJECUCION"));

        mvc.perform(actualizarEstado(A, administradorDeA, id, "PARALIZADA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PARALIZADA"));

        mvc.perform(actualizarEstado(A, administradorDeA, id, "EN_EJECUCION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_EJECUCION"));

        mvc.perform(actualizarEstado(A, administradorDeA, id, "FINALIZADA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FINALIZADA"));

        // FINALIZADA es terminal: cualquier transición desde ahí da 400.
        mvc.perform(actualizarEstado(A, administradorDeA, id, "EN_EJECUCION"))
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
                {"nombre":"Obra sin módulo","tipo":"VIALIDAD","ubicacion":"Calle 1",
                 "descripcion":null,"fechaInicioEstimada":null,"fechaFinEstimada":null}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("obras"));

        mvc.perform(get(portalDe(B, "/api/obras")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("obras"));

        mvc.perform(actualizarEstado(B, administradorDeB, 1L, "EN_EJECUCION"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("obras"));
    }

    @Test
    @DisplayName("aislamiento: una obra registrada en un municipio no es visible ni actualizable desde otro")
    void aislamientoEntreTenants() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "obras");
        fijarModulos(B, plataforma, "obras");

        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);
        MockHttpSession administradorDeB = iniciarSesionDeAdministrador(B);

        String sufijo = UUID.randomUUID().toString();
        String nombreDeA = "Obra de Lanús " + sufijo;
        String ubicacionDeA = "Ubicación de Lanús " + sufijo;
        Long idDeA = registrarObra(A, administradorDeA, nombreDeA, "VIALIDAD", ubicacionDeA);

        // No aparece en el listado del otro municipio, con ni sin filtros.
        mvc.perform(get(portalDe(B, "/api/obras")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeA + "')]").isEmpty());
        mvc.perform(get(portalDe(B, "/api/obras?estado=PLANIFICADA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeA + "')]").isEmpty());
        mvc.perform(get(portalDe(B, "/api/obras?tipo=VIALIDAD")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeA + "')]").isEmpty());
        mvc.perform(get(portalDe(B, "/api/obras?q=" + sufijo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeA + "')]").isEmpty());

        // Sigue visible en el listado del municipio dueño.
        mvc.perform(get(portalDe(A, "/api/obras")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeA + "')]").isNotEmpty());

        // El id de la obra de A no existe en la base de B: PATCH da 404, no
        // "la encuentra y la actualiza" (garantía real: el datasource ruteado
        // por tenant, no una validación de aplicación).
        mvc.perform(actualizarEstado(B, administradorDeB, idDeA, "EN_EJECUCION"))
                .andExpect(status().isNotFound());
    }

    private MockHttpServletRequestBuilder registrar(String subdominio, MockHttpSession sesion, String cuerpo) {
        return post(portalDe(subdominio, "/api/obras"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private MockHttpServletRequestBuilder actualizarEstado(
            String subdominio, MockHttpSession sesion, Long id, String estadoNuevo) {

        return patch(portalDe(subdominio, "/api/obras/" + id + "/estado"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estadoNuevo\":\"" + estadoNuevo + "\"}");
    }

    private Long registrarObra(String subdominio, MockHttpSession sesionAdmin, String nombre, String tipo,
            String ubicacion) throws Exception {

        MvcResult resultado = mvc.perform(registrar(subdominio, sesionAdmin, """
                {"nombre":"%s","tipo":"%s","ubicacion":"%s",
                 "descripcion":null,"fechaInicioEstimada":null,"fechaFinEstimada":null}"""
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
     * {@code obras.gestionar} (ADR 0011: el municipio compone sus propios
     * roles), y abre su sesión.
     */
    private MockHttpSession crearUsuarioConSoloOtroPermiso(
            String subdominio, MockHttpSession sesionAdmin, String email) throws Exception {

        String cuerpoDelRol = mvc.perform(post(portalDe(subdominio, "/api/roles"))
                .session(sesionAdmin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"codigo":"sin-obras","nombre":"Sin permiso de obras",
                         "descripcion":"No puede gestionar obras.","permisos":[]}"""))
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
