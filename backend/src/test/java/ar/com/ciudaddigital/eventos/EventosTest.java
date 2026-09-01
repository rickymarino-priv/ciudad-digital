package ar.com.ciudaddigital.eventos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
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
 * Alta protegida, lectura pública y cancelación de eventos de la agenda
 * (R26, ADR 0030).
 *
 * <p>Cada test fija explícitamente qué módulos tiene contratado cada
 * municipio antes de verificar nada, mismo criterio que
 * {@code EspaciosVerdesTest}/{@code ObrasTest}: el contenedor de Postgres
 * se comparte entre clases de test.
 */
class EventosTest extends SoporteDeIntegracion {

    private static final String A = "quilmes";
    private static final String B = "berazategui";

    @BeforeEach
    void municipiosDePrueba() throws Exception {
        asegurarMunicipio(A, "Quilmes", "#1B5E20");
        asegurarMunicipio(B, "Berazategui", "#B71C1C");
    }

    @Test
    @DisplayName("alta con el módulo contratado y el permiso responde 201 con el evento PROGRAMADO")
    void altaConElPermisoQuedaProgramado() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "eventos");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        mvc.perform(publicar(A, administradorDeA, """
                {"nombre":"Maratón Municipal","categoria":"DEPORTE","ubicacion":"Costanera",
                 "descripcion":"Circuito de 10km.","fechaInicio":"2026-10-15","fechaFin":"2026-10-15",
                 "horaInicio":"09:00:00"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.nombre").value("Maratón Municipal"))
                .andExpect(jsonPath("$.categoria").value("DEPORTE"))
                .andExpect(jsonPath("$.estado").value("PROGRAMADO"))
                .andExpect(jsonPath("$.fechaInicio").value("2026-10-15"))
                .andExpect(jsonPath("$.fechaFin").value("2026-10-15"))
                .andExpect(jsonPath("$.horaInicio").value("09:00:00"))
                .andExpect(jsonPath("$.publicadoPorNombre").value("Administrador de Quilmes"))
                .andExpect(jsonPath("$.publicadoPorEmail").value(emailDelAdministrador(A)));
    }

    @Test
    @DisplayName("alta sin el permiso eventos.gestionar se rechaza con 403 sin código")
    void altaSinElPermisoSeRechaza() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "eventos");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        MockHttpSession agenteSinPermiso =
                crearUsuarioConSoloOtroPermiso(A, administradorDeA, "agente-sin-eventos@quilmes.gob.ar");

        mvc.perform(publicar(A, agenteSinPermiso, """
                {"nombre":"Evento sin permiso","categoria":"CULTURA","ubicacion":"Teatro Municipal",
                 "descripcion":null,"fechaInicio":"2026-11-01","fechaFin":null,"horaInicio":null}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").doesNotExist());
    }

    @Test
    @DisplayName("alta con fechaFin anterior a fechaInicio da 400")
    void altaConFechaFinAnteriorAFechaInicio() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "eventos");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        mvc.perform(publicar(A, administradorDeA, """
                {"nombre":"Evento con fechas invertidas","categoria":"TURISMO","ubicacion":"Costanera",
                 "descripcion":null,"fechaInicio":"2026-10-15","fechaFin":"2026-10-10","horaInicio":null}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("alta sin fechaFin ni horaInicio deja ambos en null")
    void altaSinFechaFinNiHoraInicio() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "eventos");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        mvc.perform(publicar(A, administradorDeA, """
                {"nombre":"Feria de artesanos","categoria":"CULTURA","ubicacion":"Plaza Central",
                 "descripcion":null,"fechaInicio":"2026-11-20","fechaFin":null,"horaInicio":null}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fechaFin").doesNotExist())
                .andExpect(jsonPath("$.horaInicio").doesNotExist());
    }

    @Test
    @DisplayName("listado público sin sesión, con filtros por categoría, estado y q, por separado y combinados")
    void listadoPublicoConFiltros() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "eventos");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        String sufijo = UUID.randomUUID().toString();
        String maraton = "Maratón " + sufijo;
        String muestra = "Muestra " + sufijo;

        Long idMaraton = publicarEvento(
                A, administradorDeA, maraton, "DEPORTE", "Costanera norte " + sufijo, "2026-10-15");
        publicarEvento(A, administradorDeA, muestra, "CULTURA", "Museo sur " + sufijo, "2026-10-20");

        mvc.perform(cancelar(A, administradorDeA, idMaraton))
                .andExpect(status().isOk());

        // Por estado: una CANCELADO, la otra sigue PROGRAMADO.
        mvc.perform(get(portalDe(A, "/api/eventos?estado=CANCELADO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + maraton + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + muestra + "')]").isEmpty());
        mvc.perform(get(portalDe(A, "/api/eventos?estado=PROGRAMADO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + muestra + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + maraton + "')]").isEmpty());

        // Por categoría.
        mvc.perform(get(portalDe(A, "/api/eventos?categoria=DEPORTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + maraton + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + muestra + "')]").isEmpty());

        // Por texto: matchea nombre o ubicación.
        mvc.perform(get(portalDe(A, "/api/eventos?q=" + sufijo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + maraton + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + muestra + "')]").isNotEmpty());

        // Combinados: estado + categoría + q.
        mvc.perform(get(portalDe(A, "/api/eventos?estado=CANCELADO&categoria=DEPORTE&q=" + sufijo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + maraton + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + muestra + "')]").isEmpty());
        mvc.perform(get(portalDe(A, "/api/eventos?estado=PROGRAMADO&categoria=DEPORTE&q=" + sufijo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + maraton + "')]").isEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + muestra + "')]").isEmpty());

        // Categoría y estado inválidos dan 400, no "sin filtro".
        mvc.perform(get(portalDe(A, "/api/eventos?categoria=INEXISTENTE")))
                .andExpect(status().isBadRequest());
        mvc.perform(get(portalDe(A, "/api/eventos?estado=INEXISTENTE")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("el listado ordena por fechaInicio ascendente, no por orden de alta")
    void ordenDelListadoPorFechaInicio() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "eventos");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        String sufijo = UUID.randomUUID().toString();
        String ultimo = "Z evento tardío " + sufijo;
        String primero = "A evento temprano " + sufijo;
        String medio = "M evento medio " + sufijo;

        // Se cargan fuera de orden cronológico a propósito.
        publicarEvento(A, administradorDeA, ultimo, "OTRA", "Lugar " + sufijo, "2026-12-31");
        publicarEvento(A, administradorDeA, primero, "OTRA", "Lugar " + sufijo, "2026-10-01");
        publicarEvento(A, administradorDeA, medio, "OTRA", "Lugar " + sufijo, "2026-11-15");

        MvcResult resultado = mvc.perform(get(portalDe(A, "/api/eventos?q=" + sufijo)))
                .andExpect(status().isOk())
                .andReturn();

        String cuerpo = resultado.getResponse().getContentAsString();
        List<String> nombres = JsonPath.read(cuerpo, "$[*].nombre");
        int posicionPrimero = nombres.indexOf(primero);
        int posicionMedio = nombres.indexOf(medio);
        int posicionUltimo = nombres.indexOf(ultimo);

        assertThat(posicionPrimero).isLessThan(posicionMedio);
        assertThat(posicionMedio).isLessThan(posicionUltimo);
    }

    @Test
    @DisplayName("cancelación exitosa PROGRAMADO → CANCELADO, cancelar dos veces o volver a PROGRAMADO da 400")
    void cancelacionYTransicionesInvalidas() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "eventos");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        Long id = publicarEvento(A, administradorDeA, "Evento a cancelar " + UUID.randomUUID(),
                "OTRA", "Ubicación de prueba", "2026-12-01");

        mvc.perform(cancelar(A, administradorDeA, id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"));

        // Ya CANCELADO: volver a cancelar da 400.
        mvc.perform(cancelar(A, administradorDeA, id))
                .andExpect(status().isBadRequest());

        // Intentar "volver" a PROGRAMADO tampoco es una transición válida.
        mvc.perform(patch(portalDe(A, "/api/eventos/" + id + "/estado"))
                .session(administradorDeA)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estadoNuevo\":\"PROGRAMADO\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("sin el módulo contratado, alta/listado/cambio de estado rechazan con 403 MODULO_NO_CONTRATADO, "
            + "incluso sin sesión y con datos válidos")
    void sinModuloContratadoRechazaTodasLasRutas() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(B, plataforma);
        MockHttpSession administradorDeB = iniciarSesionDeAdministrador(B);

        mvc.perform(publicar(B, administradorDeB, """
                {"nombre":"Evento sin módulo","categoria":"CULTURA","ubicacion":"Calle 1",
                 "descripcion":null,"fechaInicio":"2026-10-01","fechaFin":null,"horaInicio":null}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("eventos"));

        mvc.perform(get(portalDe(B, "/api/eventos")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("eventos"));

        mvc.perform(cancelar(B, administradorDeB, 1L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("eventos"));
    }

    @Test
    @DisplayName("aislamiento: un evento publicado en un municipio no es visible ni cancelable desde otro")
    void aislamientoEntreTenants() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "eventos");
        fijarModulos(B, plataforma, "eventos");

        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);
        MockHttpSession administradorDeB = iniciarSesionDeAdministrador(B);

        String sufijo = UUID.randomUUID().toString();
        String nombreDeA = "Evento de Quilmes " + sufijo;
        String ubicacionDeA = "Ubicación de Quilmes " + sufijo;
        Long idDeA = publicarEvento(A, administradorDeA, nombreDeA, "DEPORTE", ubicacionDeA, "2026-10-15");

        // No aparece en el listado del otro municipio, con ni sin filtros.
        mvc.perform(get(portalDe(B, "/api/eventos")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeA + "')]").isEmpty());
        mvc.perform(get(portalDe(B, "/api/eventos?estado=PROGRAMADO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeA + "')]").isEmpty());
        mvc.perform(get(portalDe(B, "/api/eventos?categoria=DEPORTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeA + "')]").isEmpty());
        mvc.perform(get(portalDe(B, "/api/eventos?q=" + sufijo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeA + "')]").isEmpty());

        // Sigue visible en el listado del municipio dueño.
        mvc.perform(get(portalDe(A, "/api/eventos")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeA + "')]").isNotEmpty());

        // El id del evento de A no existe en la base de B: PATCH da 404, no
        // "lo encuentra y lo cancela" (garantía real: el datasource ruteado
        // por tenant, no una validación de aplicación).
        mvc.perform(cancelar(B, administradorDeB, idDeA))
                .andExpect(status().isNotFound());
    }

    private MockHttpServletRequestBuilder publicar(String subdominio, MockHttpSession sesion, String cuerpo) {
        return post(portalDe(subdominio, "/api/eventos"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private MockHttpServletRequestBuilder cancelar(String subdominio, MockHttpSession sesion, Long id) {
        return patch(portalDe(subdominio, "/api/eventos/" + id + "/estado"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estadoNuevo\":\"CANCELADO\"}");
    }

    private Long publicarEvento(String subdominio, MockHttpSession sesionAdmin, String nombre, String categoria,
            String ubicacion, String fechaInicio) throws Exception {

        MvcResult resultado = mvc.perform(publicar(subdominio, sesionAdmin, """
                {"nombre":"%s","categoria":"%s","ubicacion":"%s",
                 "descripcion":null,"fechaInicio":"%s","fechaFin":null,"horaInicio":null}"""
                .formatted(nombre, categoria, ubicacion, fechaInicio)))
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
     * {@code eventos.gestionar} (ADR 0011: el municipio compone sus propios
     * roles), y abre su sesión.
     */
    private MockHttpSession crearUsuarioConSoloOtroPermiso(
            String subdominio, MockHttpSession sesionAdmin, String email) throws Exception {

        String cuerpoDelRol = mvc.perform(post(portalDe(subdominio, "/api/roles"))
                .session(sesionAdmin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"codigo":"sin-eventos","nombre":"Sin permiso de eventos",
                         "descripcion":"No puede gestionar eventos.","permisos":[]}"""))
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
