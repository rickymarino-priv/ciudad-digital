package ar.com.ciudaddigital.espaciosverdes;

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
 * Alta protegida, lectura pública y actualización de estado de espacios
 * verdes (R25, ADR 0029).
 *
 * <p>Cada test fija explícitamente qué módulos tiene contratado cada
 * municipio antes de verificar nada, mismo criterio que
 * {@code ObrasTest}/{@code ArboladoTest}: el contenedor de Postgres se
 * comparte entre clases de test.
 */
class EspaciosVerdesTest extends SoporteDeIntegracion {

    private static final String A = "quilmes";
    private static final String B = "berazategui";

    @BeforeEach
    void municipiosDePrueba() throws Exception {
        asegurarMunicipio(A, "Quilmes", "#1B5E20");
        asegurarMunicipio(B, "Berazategui", "#B71C1C");
    }

    @Test
    @DisplayName("alta con el módulo contratado y el permiso responde 201 con el espacio verde DISPONIBLE")
    void altaConElPermisoQuedaDisponible() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "espaciosverdes");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        mvc.perform(registrar(A, administradorDeA, """
                {"nombre":"Plaza San Martín","tipo":"PLAZA","ubicacion":"Av. San Martín 450",
                 "descripcion":"Plaza central con juegos.","superficie":1200.50}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.nombre").value("Plaza San Martín"))
                .andExpect(jsonPath("$.tipo").value("PLAZA"))
                .andExpect(jsonPath("$.estado").value("DISPONIBLE"))
                .andExpect(jsonPath("$.superficie").value(1200.50))
                .andExpect(jsonPath("$.publicadoPorNombre").value("Administrador de Quilmes"))
                .andExpect(jsonPath("$.publicadoPorEmail").value(emailDelAdministrador(A)));
    }

    @Test
    @DisplayName("alta sin el permiso espaciosverdes.gestionar se rechaza con 403 sin código")
    void altaSinElPermisoSeRechaza() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "espaciosverdes");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        MockHttpSession agenteSinPermiso =
                crearUsuarioConSoloOtroPermiso(A, administradorDeA, "agente-sin-espaciosverdes@quilmes.gob.ar");

        mvc.perform(registrar(A, agenteSinPermiso, """
                {"nombre":"Parque Sin Permiso","tipo":"PARQUE","ubicacion":"Calle 1",
                 "descripcion":null,"superficie":null}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").doesNotExist());
    }

    @Test
    @DisplayName("alta con superficie inválida (cero o negativa) da 400")
    void altaConSuperficieInvalida() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "espaciosverdes");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        mvc.perform(registrar(A, administradorDeA, """
                {"nombre":"Paseo Superficie Cero","tipo":"PASEO","ubicacion":"Calle 2",
                 "descripcion":null,"superficie":0}"""))
                .andExpect(status().isBadRequest());

        mvc.perform(registrar(A, administradorDeA, """
                {"nombre":"Paseo Superficie Negativa","tipo":"PASEO","ubicacion":"Calle 3",
                 "descripcion":null,"superficie":-10}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("listado público sin sesión, con filtros por estado, tipo y q, por separado y combinados")
    void listadoPublicoConFiltros() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "espaciosverdes");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        String sufijo = UUID.randomUUID().toString();
        String plaza = "Plaza " + sufijo;
        String parque = "Parque " + sufijo;

        Long idPlaza = registrarEspacioVerde(A, administradorDeA, plaza, "PLAZA", "Zona norte " + sufijo);
        registrarEspacioVerde(A, administradorDeA, parque, "PARQUE", "Zona sur " + sufijo);

        mvc.perform(actualizarEstado(A, administradorDeA, idPlaza, "EN_MANTENIMIENTO"))
                .andExpect(status().isOk());

        // Por estado: una EN_MANTENIMIENTO, la otra sigue DISPONIBLE.
        mvc.perform(get(portalDe(A, "/api/espaciosverdes?estado=EN_MANTENIMIENTO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + plaza + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + parque + "')]").isEmpty());
        mvc.perform(get(portalDe(A, "/api/espaciosverdes?estado=DISPONIBLE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + parque + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + plaza + "')]").isEmpty());

        // Por tipo.
        mvc.perform(get(portalDe(A, "/api/espaciosverdes?tipo=PLAZA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + plaza + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + parque + "')]").isEmpty());

        // Por texto: matchea nombre o ubicación.
        mvc.perform(get(portalDe(A, "/api/espaciosverdes?q=" + sufijo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + plaza + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + parque + "')]").isNotEmpty());

        // Combinados: estado + tipo + q.
        mvc.perform(get(portalDe(A, "/api/espaciosverdes?estado=EN_MANTENIMIENTO&tipo=PLAZA&q=" + sufijo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + plaza + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + parque + "')]").isEmpty());
        mvc.perform(get(portalDe(A, "/api/espaciosverdes?estado=DISPONIBLE&tipo=PLAZA&q=" + sufijo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + plaza + "')]").isEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + parque + "')]").isEmpty());

        // Estado y tipo inválidos dan 400, no "sin filtro".
        mvc.perform(get(portalDe(A, "/api/espaciosverdes?estado=INEXISTENTE")))
                .andExpect(status().isBadRequest());
        mvc.perform(get(portalDe(A, "/api/espaciosverdes?tipo=INEXISTENTE")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("circuito completo de transiciones válidas y transiciones inválidas dan 400")
    void circuitoDeTransicionesDeEstado() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "espaciosverdes");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        Long id = registrarEspacioVerde(A, administradorDeA, "Espacio de prueba " + UUID.randomUUID(),
                "PARQUE", "Ubicación de prueba");

        // DISPONIBLE → CERRADO directo no es válido (ADR 0029 §5).
        mvc.perform(actualizarEstado(A, administradorDeA, id, "CERRADO"))
                .andExpect(status().isBadRequest());

        mvc.perform(actualizarEstado(A, administradorDeA, id, "EN_MANTENIMIENTO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_MANTENIMIENTO"));

        mvc.perform(actualizarEstado(A, administradorDeA, id, "DISPONIBLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DISPONIBLE"));

        mvc.perform(actualizarEstado(A, administradorDeA, id, "EN_MANTENIMIENTO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_MANTENIMIENTO"));

        mvc.perform(actualizarEstado(A, administradorDeA, id, "CERRADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CERRADO"));

        // CERRADO es terminal: cualquier transición desde ahí da 400.
        mvc.perform(actualizarEstado(A, administradorDeA, id, "DISPONIBLE"))
                .andExpect(status().isBadRequest());
        mvc.perform(actualizarEstado(A, administradorDeA, id, "EN_MANTENIMIENTO"))
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
                {"nombre":"Espacio sin módulo","tipo":"PLAZA","ubicacion":"Calle 1",
                 "descripcion":null,"superficie":null}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("espaciosverdes"));

        mvc.perform(get(portalDe(B, "/api/espaciosverdes")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("espaciosverdes"));

        mvc.perform(actualizarEstado(B, administradorDeB, 1L, "EN_MANTENIMIENTO"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("espaciosverdes"));
    }

    @Test
    @DisplayName("aislamiento: un espacio verde registrado en un municipio no es visible ni actualizable "
            + "desde otro")
    void aislamientoEntreTenants() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "espaciosverdes");
        fijarModulos(B, plataforma, "espaciosverdes");

        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);
        MockHttpSession administradorDeB = iniciarSesionDeAdministrador(B);

        String sufijo = UUID.randomUUID().toString();
        String nombreDeA = "Espacio de Quilmes " + sufijo;
        String ubicacionDeA = "Ubicación de Quilmes " + sufijo;
        Long idDeA = registrarEspacioVerde(A, administradorDeA, nombreDeA, "PLAZA", ubicacionDeA);

        // No aparece en el listado del otro municipio, con ni sin filtros.
        mvc.perform(get(portalDe(B, "/api/espaciosverdes")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeA + "')]").isEmpty());
        mvc.perform(get(portalDe(B, "/api/espaciosverdes?estado=DISPONIBLE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeA + "')]").isEmpty());
        mvc.perform(get(portalDe(B, "/api/espaciosverdes?tipo=PLAZA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeA + "')]").isEmpty());
        mvc.perform(get(portalDe(B, "/api/espaciosverdes?q=" + sufijo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeA + "')]").isEmpty());

        // Sigue visible en el listado del municipio dueño.
        mvc.perform(get(portalDe(A, "/api/espaciosverdes")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeA + "')]").isNotEmpty());

        // El id del espacio verde de A no existe en la base de B: PATCH da
        // 404, no "lo encuentra y lo actualiza" (garantía real: el
        // datasource ruteado por tenant, no una validación de aplicación).
        mvc.perform(actualizarEstado(B, administradorDeB, idDeA, "EN_MANTENIMIENTO"))
                .andExpect(status().isNotFound());
    }

    private MockHttpServletRequestBuilder registrar(String subdominio, MockHttpSession sesion, String cuerpo) {
        return post(portalDe(subdominio, "/api/espaciosverdes"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private MockHttpServletRequestBuilder actualizarEstado(
            String subdominio, MockHttpSession sesion, Long id, String estadoNuevo) {

        return patch(portalDe(subdominio, "/api/espaciosverdes/" + id + "/estado"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estadoNuevo\":\"" + estadoNuevo + "\"}");
    }

    private Long registrarEspacioVerde(
            String subdominio, MockHttpSession sesionAdmin, String nombre, String tipo, String ubicacion)
            throws Exception {

        MvcResult resultado = mvc.perform(registrar(subdominio, sesionAdmin, """
                {"nombre":"%s","tipo":"%s","ubicacion":"%s",
                 "descripcion":null,"superficie":null}"""
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
     * {@code espaciosverdes.gestionar} (ADR 0011: el municipio compone sus
     * propios roles), y abre su sesión.
     */
    private MockHttpSession crearUsuarioConSoloOtroPermiso(
            String subdominio, MockHttpSession sesionAdmin, String email) throws Exception {

        String cuerpoDelRol = mvc.perform(post(portalDe(subdominio, "/api/roles"))
                .session(sesionAdmin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"codigo":"sin-espaciosverdes","nombre":"Sin permiso de espacios verdes",
                         "descripcion":"No puede gestionar espacios verdes.","permisos":[]}"""))
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
