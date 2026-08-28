package ar.com.ciudaddigital.arbolado;

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
 * Alta protegida, lectura pública y actualización de estado sanitario de
 * árboles urbanos (R20, ADR 0024).
 *
 * <p>Cada test fija explícitamente qué módulos tiene contratado cada
 * municipio antes de verificar nada, mismo criterio que
 * {@code ObrasTest}/{@code MultasTest}: el contenedor de Postgres se
 * comparte entre clases de test.
 */
class ArboladoTest extends SoporteDeIntegracion {

    private static final String A = "lanus";
    private static final String B = "avellaneda";

    @BeforeEach
    void municipiosDePrueba() throws Exception {
        asegurarMunicipio(A, "Lanús", "#1B5E20");
        asegurarMunicipio(B, "Avellaneda", "#B71C1C");
    }

    @Test
    @DisplayName("alta con el módulo contratado y el permiso responde 201 con el árbol PLANTADO")
    void altaConElPermisoQuedaPlantado() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "arbolado");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        mvc.perform(registrar(A, administradorDeA, """
                {"especie":"Jacarandá","ubicacion":"Vereda de Av. San Martín 450",
                 "descripcion":"Ejemplar joven.","fechaDePlantacion":"2026-05-01"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.especie").value("Jacarandá"))
                .andExpect(jsonPath("$.estado").value("PLANTADO"))
                .andExpect(jsonPath("$.publicadoPorNombre").value("Administrador de Lanús"))
                .andExpect(jsonPath("$.publicadoPorEmail").value(emailDelAdministrador(A)));
    }

    @Test
    @DisplayName("alta sin el permiso arbolado.gestionar se rechaza con 403 sin código")
    void altaSinElPermisoSeRechaza() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "arbolado");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        MockHttpSession agenteSinPermiso =
                crearUsuarioConSoloOtroPermiso(A, administradorDeA, "agente-sin-arbolado@lanus.gob.ar");

        mvc.perform(registrar(A, agenteSinPermiso, """
                {"especie":"Fresno americano","ubicacion":"Plaza Norte",
                 "descripcion":null,"fechaDePlantacion":null}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").doesNotExist());
    }

    @Test
    @DisplayName("listado público sin sesión, con filtros por estado y q, por separado y combinados")
    void listadoPublicoConFiltros() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "arbolado");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        String sufijo = UUID.randomUUID().toString();
        String especieJacaranda = "Jacarandá " + sufijo;
        String especieFresno = "Fresno " + sufijo;

        Long idJacaranda = registrarArbol(A, administradorDeA, especieJacaranda, "Zona norte " + sufijo);
        registrarArbol(A, administradorDeA, especieFresno, "Zona sur " + sufijo);

        mvc.perform(actualizarEstado(A, administradorDeA, idJacaranda, "SANO"))
                .andExpect(status().isOk());

        // Por estado: uno SANO, el otro sigue PLANTADO.
        mvc.perform(get(portalDe(A, "/api/arbolado?estado=SANO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.especie == '" + especieJacaranda + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.especie == '" + especieFresno + "')]").isEmpty());
        mvc.perform(get(portalDe(A, "/api/arbolado?estado=PLANTADO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.especie == '" + especieFresno + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.especie == '" + especieJacaranda + "')]").isEmpty());

        // Por texto: matchea especie o ubicación.
        mvc.perform(get(portalDe(A, "/api/arbolado?q=" + sufijo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.especie == '" + especieJacaranda + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.especie == '" + especieFresno + "')]").isNotEmpty());

        // Combinados: estado + q.
        mvc.perform(get(portalDe(A, "/api/arbolado?estado=SANO&q=" + sufijo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.especie == '" + especieJacaranda + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.especie == '" + especieFresno + "')]").isEmpty());

        // Estado inválido da 400, no "sin filtro".
        mvc.perform(get(portalDe(A, "/api/arbolado?estado=INEXISTENTE")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("circuito completo de transiciones válidas y transiciones inválidas dan 400")
    void circuitoDeTransicionesDeEstado() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "arbolado");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        Long id = registrarArbol(A, administradorDeA, "Especie de prueba " + UUID.randomUUID(),
                "Ubicación de prueba");

        // Transiciones inválidas directas.
        mvc.perform(actualizarEstado(A, administradorDeA, id, "REQUIERE_INTERVENCION"))
                .andExpect(status().isBadRequest());
        mvc.perform(actualizarEstado(A, administradorDeA, id, "RETIRADO"))
                .andExpect(status().isBadRequest());

        mvc.perform(actualizarEstado(A, administradorDeA, id, "SANO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SANO"));

        // SANO → RETIRADO directo no es válido (ADR 0024 §4).
        mvc.perform(actualizarEstado(A, administradorDeA, id, "RETIRADO"))
                .andExpect(status().isBadRequest());

        mvc.perform(actualizarEstado(A, administradorDeA, id, "REQUIERE_INTERVENCION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("REQUIERE_INTERVENCION"));

        mvc.perform(actualizarEstado(A, administradorDeA, id, "SANO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SANO"));

        mvc.perform(actualizarEstado(A, administradorDeA, id, "REQUIERE_INTERVENCION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("REQUIERE_INTERVENCION"));

        mvc.perform(actualizarEstado(A, administradorDeA, id, "RETIRADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RETIRADO"));

        // RETIRADO es terminal: cualquier transición desde ahí da 400.
        mvc.perform(actualizarEstado(A, administradorDeA, id, "SANO"))
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
                {"especie":"Árbol sin módulo","ubicacion":"Calle 1",
                 "descripcion":null,"fechaDePlantacion":null}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("arbolado"));

        mvc.perform(get(portalDe(B, "/api/arbolado")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("arbolado"));

        mvc.perform(actualizarEstado(B, administradorDeB, 1L, "SANO"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("arbolado"));
    }

    @Test
    @DisplayName("aislamiento: un árbol registrado en un municipio no es visible ni actualizable desde otro")
    void aislamientoEntreTenants() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "arbolado");
        fijarModulos(B, plataforma, "arbolado");

        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);
        MockHttpSession administradorDeB = iniciarSesionDeAdministrador(B);

        String sufijo = UUID.randomUUID().toString();
        String especieDeA = "Especie de Lanús " + sufijo;
        String ubicacionDeA = "Ubicación de Lanús " + sufijo;
        Long idDeA = registrarArbol(A, administradorDeA, especieDeA, ubicacionDeA);

        // No aparece en el listado del otro municipio, con ni sin filtros.
        mvc.perform(get(portalDe(B, "/api/arbolado")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.especie == '" + especieDeA + "')]").isEmpty());
        mvc.perform(get(portalDe(B, "/api/arbolado?estado=PLANTADO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.especie == '" + especieDeA + "')]").isEmpty());
        mvc.perform(get(portalDe(B, "/api/arbolado?q=" + sufijo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.especie == '" + especieDeA + "')]").isEmpty());

        // Sigue visible en el listado del municipio dueño.
        mvc.perform(get(portalDe(A, "/api/arbolado")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.especie == '" + especieDeA + "')]").isNotEmpty());

        // El id del árbol de A no existe en la base de B: PATCH da 404, no
        // "lo encuentra y lo actualiza" (garantía real: el datasource ruteado
        // por tenant, no una validación de aplicación).
        mvc.perform(actualizarEstado(B, administradorDeB, idDeA, "SANO"))
                .andExpect(status().isNotFound());
    }

    private MockHttpServletRequestBuilder registrar(String subdominio, MockHttpSession sesion, String cuerpo) {
        return post(portalDe(subdominio, "/api/arbolado"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private MockHttpServletRequestBuilder actualizarEstado(
            String subdominio, MockHttpSession sesion, Long id, String estadoNuevo) {

        return patch(portalDe(subdominio, "/api/arbolado/" + id + "/estado"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estadoNuevo\":\"" + estadoNuevo + "\"}");
    }

    private Long registrarArbol(String subdominio, MockHttpSession sesionAdmin, String especie, String ubicacion)
            throws Exception {

        MvcResult resultado = mvc.perform(registrar(subdominio, sesionAdmin, """
                {"especie":"%s","ubicacion":"%s",
                 "descripcion":null,"fechaDePlantacion":null}"""
                .formatted(especie, ubicacion)))
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
     * {@code arbolado.gestionar} (ADR 0011: el municipio compone sus
     * propios roles), y abre su sesión.
     */
    private MockHttpSession crearUsuarioConSoloOtroPermiso(
            String subdominio, MockHttpSession sesionAdmin, String email) throws Exception {

        String cuerpoDelRol = mvc.perform(post(portalDe(subdominio, "/api/roles"))
                .session(sesionAdmin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"codigo":"sin-arbolado","nombre":"Sin permiso de arbolado",
                         "descripcion":"No puede gestionar arbolado.","permisos":[]}"""))
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
