package ar.com.ciudaddigital.tasas;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * Alta protegida, búsqueda pública, e inicio/confirmación pública de pago
 * de tasas municipales (backlog R13, ADR 0018), mismo estilo que
 * {@code ReclamosTest}/{@code CementerioTest}.
 *
 * <p>Cada test fija explícitamente qué módulos tiene contratado cada
 * municipio antes de verificar nada, mismo criterio que el resto de los
 * tests de módulo: el contenedor de Postgres se comparte entre clases de
 * test.
 */
class TasasTest extends SoporteDeIntegracion {

    private static final String A = "moron";
    private static final String B = "merlo";

    @BeforeEach
    void municipiosDePrueba() throws Exception {
        asegurarMunicipio(A, "Morón", "#1B5E20");
        asegurarMunicipio(B, "Merlo", "#4E342E");
    }

    @Test
    @DisplayName("alta con el módulo contratado y el permiso responde 201 con la tasa PENDIENTE")
    void altaSoloConElModuloContratado() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "tasas");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        mvc.perform(publicar(A, administradorDeA, """
                {"numeroCuenta":"1234-5","concepto":"Alumbrado, barrido y limpieza",
                 "periodo":"2026-08","monto":15000.50}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.numeroCuenta").value("1234-5"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.fechaPago").doesNotExist())
                .andExpect(jsonPath("$.publicadoPorNombre").value("Administrador de Morón"))
                .andExpect(jsonPath("$.publicadoPorEmail").value(emailDelAdministrador(A)));
    }

    @Test
    @DisplayName("búsqueda pública sin numeroCuenta responde 400; con numeroCuenta encuentra la tasa recién creada")
    void busquedaPublicaPorCuenta() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "tasas");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        String cuenta = "cuenta-" + UUID.randomUUID();
        mvc.perform(publicar(A, administradorDeA, """
                {"numeroCuenta":"%s","concepto":"Tasa de servicios",
                 "periodo":"2026-08","monto":1000}"""
                .formatted(cuenta)))
                .andExpect(status().isCreated());

        mvc.perform(get(portalDe(A, "/api/tasas")))
                .andExpect(status().isBadRequest());

        mvc.perform(get(portalDe(A, "/api/tasas?numeroCuenta=" + cuenta)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.numeroCuenta == '" + cuenta + "')]").isNotEmpty());
    }

    @Test
    @DisplayName("iniciar pago de una tasa PENDIENTE responde 200 con referenciaExterna, sin cambiar el estado")
    void iniciarPagoNoCambiaElEstadoHastaConfirmar() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "tasas");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        String cuenta = "cuenta-" + UUID.randomUUID();
        Long id = publicarTasa(A, administradorDeA, cuenta);

        mvc.perform(iniciarPago(A, id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenciaExterna").isNotEmpty());

        mvc.perform(get(portalDe(A, "/api/tasas?numeroCuenta=" + cuenta)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("PENDIENTE"));
    }

    @Test
    @DisplayName("confirmar un pago aprobado pasa la tasa a PAGADA con fechaPago, y lo refleja la búsqueda pública")
    void confirmarPagoAprobado() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "tasas");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        String cuenta = "cuenta-" + UUID.randomUUID();
        Long id = publicarTasa(A, administradorDeA, cuenta);

        MvcResult resultadoDeInicio = mvc.perform(iniciarPago(A, id))
                .andExpect(status().isOk())
                .andReturn();
        String referenciaExterna =
                JsonPath.read(resultadoDeInicio.getResponse().getContentAsString(), "$.referenciaExterna");

        mvc.perform(confirmarPago(A, referenciaExterna, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PAGADA"))
                .andExpect(jsonPath("$.fechaPago").isNotEmpty());

        mvc.perform(get(portalDe(A, "/api/tasas?numeroCuenta=" + cuenta)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("PAGADA"))
                .andExpect(jsonPath("$[0].fechaPago").isNotEmpty());
    }

    @Test
    @DisplayName("camino de rechazo: la tasa sigue PENDIENTE y se puede volver a iniciar un pago sobre ella")
    void confirmarPagoRechazadoPermiteReintentar() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "tasas");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        String cuenta = "cuenta-" + UUID.randomUUID();
        Long id = publicarTasa(A, administradorDeA, cuenta);

        MvcResult resultadoDeInicio = mvc.perform(iniciarPago(A, id))
                .andExpect(status().isOk())
                .andReturn();
        String referenciaExterna =
                JsonPath.read(resultadoDeInicio.getResponse().getContentAsString(), "$.referenciaExterna");

        mvc.perform(confirmarPago(A, referenciaExterna, false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));

        // No quedó bloqueada: se puede volver a iniciar un pago nuevo.
        mvc.perform(iniciarPago(A, id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenciaExterna").isNotEmpty());
    }

    @Test
    @DisplayName("confirmar con una referencia inventada da 404; iniciar pago sobre una tasa ya PAGADA da 400")
    void confirmarInexistenteYReiniciarPagadaSeRechazan() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "tasas");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        mvc.perform(confirmarPago(A, "referencia-inventada", true))
                .andExpect(status().isNotFound());

        String cuenta = "cuenta-" + UUID.randomUUID();
        Long id = publicarTasa(A, administradorDeA, cuenta);
        MvcResult resultadoDeInicio = mvc.perform(iniciarPago(A, id))
                .andExpect(status().isOk())
                .andReturn();
        String referenciaExterna =
                JsonPath.read(resultadoDeInicio.getResponse().getContentAsString(), "$.referenciaExterna");
        mvc.perform(confirmarPago(A, referenciaExterna, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PAGADA"));

        mvc.perform(iniciarPago(A, id))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("sin el módulo contratado, alta/búsqueda/inicio/confirmación rechazan con 403 "
            + "MODULO_NO_CONTRATADO, incluso sin sesión y con datos válidos")
    void sinModuloContratadoRechazaTodasLasRutas() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(B, plataforma);
        MockHttpSession administradorDeB = iniciarSesionDeAdministrador(B);

        mvc.perform(publicar(B, administradorDeB, """
                {"numeroCuenta":"1","concepto":"Tasa","periodo":"2026-08","monto":100}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("tasas"));

        mvc.perform(get(portalDe(B, "/api/tasas?numeroCuenta=1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("tasas"));

        mvc.perform(post(portalDe(B, "/api/tasas/1/pagos")).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("tasas"));

        mvc.perform(confirmarPago(B, "cualquier-referencia", true))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("tasas"));
    }

    @Test
    @DisplayName("un agente (sin tasas.publicar) recibe 403 sin código al intentar publicar una tasa")
    void publicarSinElPermisoSeRechaza() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "tasas");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        MockHttpSession agenteDeA = crearAgenteYLoguear(A, administradorDeA, "agente-tasas@moron.gob.ar");

        mvc.perform(publicar(A, agenteDeA, """
                {"numeroCuenta":"1","concepto":"Tasa","periodo":"2026-08","monto":100}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").doesNotExist());
    }

    @Test
    @DisplayName("aislamiento: la búsqueda por cuenta y la confirmación de pago no cruzan entre municipios")
    void aislamientoEntreTenants() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "tasas");
        fijarModulos(B, plataforma, "tasas");

        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        String cuenta = "cuenta-" + UUID.randomUUID();
        Long id = publicarTasa(A, administradorDeA, cuenta);

        // El mismo número de cuenta no aparece en el portal de otro municipio.
        mvc.perform(get(portalDe(B, "/api/tasas?numeroCuenta=" + cuenta)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        MvcResult resultadoDeInicio = mvc.perform(iniciarPago(A, id))
                .andExpect(status().isOk())
                .andReturn();
        String referenciaExterna =
                JsonPath.read(resultadoDeInicio.getResponse().getContentAsString(), "$.referenciaExterna");

        // La referencia es real (de un pago iniciado en A), pero la
        // confirmación corre contra la base de B, que no tiene esa fila: es
        // la garantía real de aislamiento (el datasource ruteado por
        // tenant), no una referencia "mal formada" (mismo razonamiento que
        // ReclamosTest.aislamientoDeTokenEntreTenants).
        mvc.perform(confirmarPago(B, referenciaExterna, true))
                .andExpect(status().isNotFound());
    }

    private MockHttpServletRequestBuilder publicar(String subdominio, MockHttpSession sesion, String cuerpo) {
        return post(portalDe(subdominio, "/api/tasas"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private MockHttpServletRequestBuilder iniciarPago(String subdominio, Long id) {
        return post(portalDe(subdominio, "/api/tasas/" + id + "/pagos")).with(csrf());
    }

    private MockHttpServletRequestBuilder confirmarPago(String subdominio, String referenciaExterna,
            boolean aprobado) {

        return post(portalDe(subdominio, "/api/tasas/pagos/confirmar"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"referenciaExterna\":\"" + referenciaExterna + "\",\"aprobado\":" + aprobado + "}");
    }

    /** Publica una tasa con sesión de administrador y devuelve su id. */
    private Long publicarTasa(String subdominio, MockHttpSession sesionAdmin, String numeroCuenta) throws Exception {
        MvcResult resultado = mvc.perform(publicar(subdominio, sesionAdmin, """
                {"numeroCuenta":"%s","concepto":"Tasa de servicios","periodo":"2026-08","monto":5000}"""
                .formatted(numeroCuenta)))
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

    /** Crea un usuario con el rol de sistema 'agente' (sin {@code tasas.publicar}) y abre su sesión. */
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
