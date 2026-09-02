package ar.com.ciudaddigital.reportes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
 * Tablero de reportes/BI (ADR 0033): aislamiento entre municipios de los
 * indicadores agregados, filtro por entitlement vigente y permiso
 * {@code reportes.ver}, mismo estilo que {@code ReclamosTest}/
 * {@code BromatologiaTest}.
 *
 * <p>Un municipio distinto por escenario —en vez de reutilizar A/B en toda
 * la clase— porque el contenedor de Postgres se comparte entre tests y acá
 * lo que se verifica son conteos exactos, no solo presencia de una fila.
 */
class ReportesTest extends SoporteDeIntegracion {

    private static final String A = "campana";
    private static final String B = "zarate";
    private static final String C = "lujan";
    private static final String D = "pilar";
    private static final String E = "escobar";

    @BeforeEach
    void municipiosDePrueba() throws Exception {
        asegurarMunicipio(A, "Campana", "#1565C0");
        asegurarMunicipio(B, "Zárate", "#C62828");
        asegurarMunicipio(C, "Luján", "#2E7D32");
        asegurarMunicipio(D, "Pilar", "#6A1B9A");
        asegurarMunicipio(E, "Escobar", "#EF6C00");
    }

    @Test
    @DisplayName("aislamiento real: los conteos del tablero de un municipio son exactamente los suyos, "
            + "nunca los del otro")
    void aislamientoRealDeIndicadoresEntreTenants() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "reclamos", "mesaentradas");
        fijarModulos(B, plataforma, "reclamos", "mesaentradas");

        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);
        MockHttpSession administradorDeB = iniciarSesionDeAdministrador(B);

        // A: 3 reclamos, uno pasado a EN_PROCESO -> NUEVO=2, EN_PROCESO=1.
        Long a1 = cargarReclamo(A, administradorDeA);
        cargarReclamo(A, administradorDeA);
        cargarReclamo(A, administradorDeA);
        mvc.perform(cambiarEstadoDeReclamo(A, administradorDeA, a1, "EN_PROCESO", null))
                .andExpect(status().isOk());

        // B: 4 reclamos, con conteos y estados distintos de A (incluido un
        // estado, RESUELTO, que A no tiene ninguno) -> NUEVO=3, RESUELTO=1,
        // sin ningún EN_PROCESO (pasó por ahí de camino a RESUELTO).
        Long b1 = cargarReclamo(B, administradorDeB);
        cargarReclamo(B, administradorDeB);
        cargarReclamo(B, administradorDeB);
        cargarReclamo(B, administradorDeB);
        mvc.perform(cambiarEstadoDeReclamo(B, administradorDeB, b1, "EN_PROCESO", null))
                .andExpect(status().isOk());
        mvc.perform(cambiarEstadoDeReclamo(B, administradorDeB, b1, "RESUELTO", null))
                .andExpect(status().isOk());

        // Expedientes cargados solo en A: 2 CERTIFICADO_DOMICILIO y 1
        // HABILITACION_COMERCIAL_SIMPLE, los tres todavía INICIADO.
        iniciarExpediente(A, """
                {"tipo":"CERTIFICADO_DOMICILIO","solicitanteNombre":"Vecino 1",
                 "domicilioACertificar":"Calle 1"}""");
        iniciarExpediente(A, """
                {"tipo":"CERTIFICADO_DOMICILIO","solicitanteNombre":"Vecino 2",
                 "domicilioACertificar":"Calle 2"}""");
        iniciarExpediente(A, """
                {"tipo":"HABILITACION_COMERCIAL_SIMPLE","solicitanteNombre":"Comerciante 1",
                 "rubroComercial":"Kiosco","direccionLocal":"Calle 3"}""");

        String tableroDeA = mvc.perform(get(portalDe(A, "/api/reportes/tablero")).session(administradorDeA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> reclamosDeA = fuenteDe(tableroDeA, "reclamos");
        Map<String, Object> serieDeA = serieDe(reclamosDeA, "Reclamos por estado");
        assertEquals(2L, cantidadDe(serieDeA, "NUEVO"));
        assertEquals(1L, cantidadDe(serieDeA, "EN_PROCESO"));
        assertNull(cantidadDe(serieDeA, "RESUELTO"));
        assertNull(cantidadDe(serieDeA, "RECHAZADO"));

        Map<String, Object> mesaentradasDeA = fuenteDe(tableroDeA, "mesaentradas");
        Map<String, Object> tiposDeA = serieDe(mesaentradasDeA, "Expedientes por tipo de trámite");
        assertEquals(2L, cantidadDe(tiposDeA, "CERTIFICADO_DOMICILIO"));
        assertEquals(1L, cantidadDe(tiposDeA, "HABILITACION_COMERCIAL_SIMPLE"));
        assertNull(cantidadDe(tiposDeA, "PERMISO_OBRA_MENOR"));
        Map<String, Object> estadosDeA = serieDe(mesaentradasDeA, "Expedientes por estado");
        assertEquals(3L, cantidadDe(estadosDeA, "INICIADO"));

        String tableroDeB = mvc.perform(get(portalDe(B, "/api/reportes/tablero")).session(administradorDeB))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> reclamosDeB = fuenteDe(tableroDeB, "reclamos");
        Map<String, Object> serieDeB = serieDe(reclamosDeB, "Reclamos por estado");
        assertEquals(3L, cantidadDe(serieDeB, "NUEVO"));
        assertEquals(1L, cantidadDe(serieDeB, "RESUELTO"));
        assertNull(cantidadDe(serieDeB, "EN_PROCESO"));
        assertNull(cantidadDe(serieDeB, "RECHAZADO"));

        // mesaentradas está contratado en B pero sin ningún expediente
        // cargado ahí: la fuente aparece (el módulo sí está contratado),
        // sus series están vacías -- ni un solo dato de los expedientes de
        // A se filtró para acá.
        Map<String, Object> mesaentradasDeB = fuenteDe(tableroDeB, "mesaentradas");
        assertTrue(puntosDe(serieDe(mesaentradasDeB, "Expedientes por tipo de trámite")).isEmpty());
        assertTrue(puntosDe(serieDe(mesaentradasDeB, "Expedientes por estado")).isEmpty());
    }

    @Test
    @DisplayName("filtro por entitlement real: al quitar reclamos de los módulos contratados, la fuente "
            + "deja de aparecer aunque los datos sigan cargados")
    void filtroPorEntitlementRealNoIncidental() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(C, plataforma, "reclamos");
        MockHttpSession administrador = iniciarSesionDeAdministrador(C);

        cargarReclamo(C, administrador);
        cargarReclamo(C, administrador);

        String conModuloContratado = mvc.perform(get(portalDe(C, "/api/reportes/tablero")).session(administrador))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(1, ((List<?>) JsonPath.read(conModuloContratado, "$[?(@.moduloCodigo == 'reclamos')]")).size());

        // Se quita reclamos de los módulos contratados, sin tocar los
        // reclamos ya cargados en la tabla.
        fijarModulos(C, plataforma);

        String sinModuloContratado = mvc.perform(get(portalDe(C, "/api/reportes/tablero")).session(administrador))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(((List<?>) JsonPath.read(sinModuloContratado, "$[?(@.moduloCodigo == 'reclamos')]"))
                .isEmpty());
    }

    @Test
    @DisplayName("permiso: sin sesión 401, sin reportes.ver 403 sin código, administrador (por seed) 200")
    void permisoDeLecturaDelTablero() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(D, plataforma, "reclamos");
        MockHttpSession administrador = iniciarSesionDeAdministrador(D);

        mvc.perform(get(portalDe(D, "/api/reportes/tablero")))
                .andExpect(status().isUnauthorized());

        MockHttpSession sinRoles = crearUsuarioSinRoles(D, administrador, "vecino-sin-rol@" + D + ".gob.ar");
        mvc.perform(get(portalDe(D, "/api/reportes/tablero")).session(sinRoles))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").doesNotExist());

        mvc.perform(get(portalDe(D, "/api/reportes/tablero")).session(administrador))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("municipio sin reclamos ni mesaentradas contratados: el tablero responde 200 con lista vacía")
    void municipioSinModulosContratadosDaTableroVacio() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(E, plataforma);
        MockHttpSession administrador = iniciarSesionDeAdministrador(E);

        mvc.perform(get(portalDe(E, "/api/reportes/tablero")).session(administrador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> fuenteDe(String tableroJson, String moduloCodigo) {
        List<Map<String, Object>> fuentes =
                JsonPath.read(tableroJson, "$[?(@.moduloCodigo == '" + moduloCodigo + "')]");
        assertEquals(1, fuentes.size(), "esperaba una única fuente '" + moduloCodigo + "' en el tablero");
        return fuentes.get(0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> serieDe(Map<String, Object> fuente, String nombreDeSerie) {
        List<Map<String, Object>> series = (List<Map<String, Object>>) fuente.get("series");
        return series.stream()
                .filter(serie -> nombreDeSerie.equals(serie.get("nombre")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No hay serie '" + nombreDeSerie + "'"));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> puntosDe(Map<String, Object> serie) {
        return (List<Map<String, Object>>) serie.get("puntos");
    }

    /** {@code null} si la etiqueta no tiene ningún punto en la serie (sin datos, no se rellena con cero). */
    private static Long cantidadDe(Map<String, Object> serie, String etiqueta) {
        return puntosDe(serie).stream()
                .filter(punto -> etiqueta.equals(punto.get("etiqueta")))
                .findFirst()
                .map(punto -> ((Number) punto.get("cantidad")).longValue())
                .orElse(null);
    }

    private Long cargarReclamo(String subdominio, MockHttpSession sesion) throws Exception {
        MvcResult resultado = mvc.perform(post(portalDe(subdominio, "/api/reclamos"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"categoria":"BACHE","descripcion":"Pozo en la vereda","direccion":"Calle de prueba"}"""))
                .andExpect(status().isCreated())
                .andReturn();

        return ((Number) JsonPath.read(resultado.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private MockHttpServletRequestBuilder cambiarEstadoDeReclamo(
            String subdominio, MockHttpSession sesion, Long id, String estado, String comentario) {

        String comentarioJson = comentario == null ? "null" : "\"" + comentario + "\"";
        return patch(portalDe(subdominio, "/api/reclamos/" + id + "/estado"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"" + estado + "\",\"comentario\":" + comentarioJson + "}");
    }

    private Long iniciarExpediente(String subdominio, String cuerpo) throws Exception {
        MvcResult resultado = mvc.perform(post(portalDe(subdominio, "/api/mesaentradas"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
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
}
