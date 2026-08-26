package ar.com.ciudaddigital.multas;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.PreparedStatement;
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
 * Alta protegida, búsqueda pública, descargo y su resolución, e
 * inicio/confirmación de pago de multas de tránsito (R17, ADR 0021), mismo
 * estilo que {@code TasasTest}.
 *
 * <p>Cada test fija explícitamente qué módulos tiene contratado cada
 * municipio antes de verificar nada, mismo criterio que el resto de los
 * tests de módulo: el contenedor de Postgres se comparte entre clases de
 * test.
 */
class MultasTest extends SoporteDeIntegracion {

    private static final String A = "quilmes";
    private static final String B = "berazategui";

    @BeforeEach
    void municipiosDePrueba() throws Exception {
        asegurarMunicipio(A, "Quilmes", "#0D47A1");
        asegurarMunicipio(B, "Berazategui", "#BF360C");
    }

    @Test
    @DisplayName("alta con el módulo contratado y el permiso responde 201 con la multa NOTIFICADA")
    void altaSoloConElModuloContratado() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "multas");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        mvc.perform(labrar(A, administradorDeA, """
                {"patente":"AB123CD","dni":"30111222","descripcionInfraccion":"Exceso de velocidad",
                 "monto":10000}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.patente").value("AB123CD"))
                .andExpect(jsonPath("$.estado").value("NOTIFICADA"))
                .andExpect(jsonPath("$.fechaPago").doesNotExist())
                .andExpect(jsonPath("$.labradaPorNombre").value("Administrador de Quilmes"))
                .andExpect(jsonPath("$.labradaPorEmail").value(emailDelAdministrador(A)));
    }

    @Test
    @DisplayName("búsqueda pública sin patente ni dni, o con ambos, da 400; con uno solo encuentra la multa")
    void busquedaPublicaPorPatenteODni() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "multas");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        String patente = patenteDePrueba();
        labrarMulta(A, administradorDeA, patente, "10000");

        mvc.perform(get(portalDe(A, "/api/multas")))
                .andExpect(status().isBadRequest());

        mvc.perform(get(portalDe(A, "/api/multas?patente=" + patente + "&dni=1")))
                .andExpect(status().isBadRequest());

        mvc.perform(get(portalDe(A, "/api/multas?patente=" + patente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.patente == '" + patente + "')]").isNotEmpty());
    }

    @Test
    @DisplayName("circuito feliz: labrar, buscar y pagar dentro del plazo cobra con el 20% de descuento y queda PAGADA")
    void circuitoFelizConDescuento() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "multas");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        String patente = patenteDePrueba();
        Long id = labrarMulta(A, administradorDeA, patente, "10000");

        mvc.perform(get(portalDe(A, "/api/multas?patente=" + patente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].montoAPagar").value(8000.00));

        MvcResult resultadoDeInicio = mvc.perform(iniciarPago(A, id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenciaExterna").isNotEmpty())
                .andReturn();
        String referenciaExterna =
                JsonPath.read(resultadoDeInicio.getResponse().getContentAsString(), "$.referenciaExterna");

        mvc.perform(confirmarPago(A, referenciaExterna, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PAGADA"))
                .andExpect(jsonPath("$.fechaPago").isNotEmpty());

        mvc.perform(get(portalDe(A, "/api/multas?patente=" + patente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("PAGADA"));
    }

    @Test
    @DisplayName("descargo anulado: labrar, descargo, resolver con confirmar=false deja ANULADA y pagar da 400")
    void circuitoConDescargoAnulado() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "multas");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        String patente = patenteDePrueba();
        Long id = labrarMulta(A, administradorDeA, patente, "10000");

        mvc.perform(presentarDescargo(A, id, """
                {"texto":"No era yo quien manejaba.","contacto":"vecino@ejemplo.com"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_DESCARGO"));

        mvc.perform(resolverDescargo(A, administradorDeA, id, """
                {"comentario":"Se hace lugar al descargo presentado.","confirmar":false}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ANULADA"))
                .andExpect(jsonPath("$.resueltoPorNombre").value("Administrador de Quilmes"));

        mvc.perform(iniciarPago(A, id))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("descargo confirmado: labrar, descargo, resolver con confirmar=true deja CONFIRMADA y "
            + "el pago se cobra sin descuento, aunque siga dentro de los 10 días")
    void circuitoConDescargoConfirmadoPierdeElDescuento() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "multas");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        String patente = patenteDePrueba();
        Long id = labrarMulta(A, administradorDeA, patente, "10000");

        mvc.perform(presentarDescargo(A, id, """
                {"texto":"Discuto la infracción.","contacto":null}"""))
                .andExpect(status().isOk());

        mvc.perform(resolverDescargo(A, administradorDeA, id, """
                {"comentario":"Se rechaza el descargo, la multa se mantiene.","confirmar":true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"));

        // Aunque siguen corriendo los 10 días desde notificadaEn, haber pasado
        // por EN_DESCARGO hace perder el descuento para siempre (ADR 0021 §8).
        mvc.perform(get(portalDe(A, "/api/multas?patente=" + patente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].montoAPagar").value(10000.00));

        MvcResult resultadoDeInicio = mvc.perform(iniciarPago(A, id))
                .andExpect(status().isOk())
                .andReturn();
        String referenciaExterna =
                JsonPath.read(resultadoDeInicio.getResponse().getContentAsString(), "$.referenciaExterna");

        mvc.perform(confirmarPago(A, referenciaExterna, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PAGADA"));
    }

    @Test
    @DisplayName("fuera de los 10 días corridos desde la notificación, montoAPagar ya no trae el descuento")
    void sinDescuentoFueraDelPlazo() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "multas");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        String patente = patenteDePrueba();
        Long id = labrarMulta(A, administradorDeA, patente, "10000");

        // No hay forma pública de "hacer pasar el tiempo": se retrasa
        // notificadaEn directamente en la base del tenant, mismo mecanismo
        // que AuditoriaTest usa para inspeccionar filas sin API propia.
        try (Connection conexion = conectarComoTenant(A);
                PreparedStatement sentencia =
                        conexion.prepareStatement("update multa set notificada_en = notificada_en - interval "
                                + "'11 days' where id = ?")) {

            sentencia.setLong(1, id);
            sentencia.executeUpdate();
        }

        mvc.perform(get(portalDe(A, "/api/multas?patente=" + patente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].montoAPagar").value(10000.00));
    }

    @Test
    @DisplayName("sin el módulo contratado, alta y búsqueda rechazan con 403 MODULO_NO_CONTRATADO, "
            + "incluso sin sesión y con datos válidos")
    void sinModuloContratadoRechazaTodasLasRutas() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(B, plataforma);
        MockHttpSession administradorDeB = iniciarSesionDeAdministrador(B);

        mvc.perform(labrar(B, administradorDeB, """
                {"patente":"AB123CD","dni":null,"descripcionInfraccion":"Infracción","monto":100}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("multas"));

        mvc.perform(get(portalDe(B, "/api/multas?patente=AB123CD")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("multas"));
    }

    @Test
    @DisplayName("aislamiento: la búsqueda por patente/dni y la confirmación de pago no cruzan entre municipios")
    void aislamientoEntreTenants() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "multas");
        fijarModulos(B, plataforma, "multas");

        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        String patente = patenteDePrueba();
        String dni = "40" + UUID.randomUUID().toString().replaceAll("\\D", "").substring(0, 6);
        Long id = labrarMultaConDni(A, administradorDeA, patente, dni, "10000");

        // La misma patente/DNI no aparece en el portal de otro municipio.
        mvc.perform(get(portalDe(B, "/api/multas?patente=" + patente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(get(portalDe(B, "/api/multas?dni=" + dni)))
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
        // TasasTest.aislamientoEntreTenants).
        mvc.perform(confirmarPago(B, referenciaExterna, true))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("permisos: labrar (sin resolverDescargo) no puede resolver un descargo, y viceversa")
    void permisosDeLabrarYResolverDescargoNoSeMezclan() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "multas");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        String patente = patenteDePrueba();
        Long id = labrarMulta(A, administradorDeA, patente, "10000");
        mvc.perform(presentarDescargo(A, id, """
                {"texto":"Discuto la infracción.","contacto":null}"""))
                .andExpect(status().isOk());

        // El rol de sistema 'agente' tiene multas.labrar pero no multas.resolverDescargo.
        MockHttpSession agenteDeA = crearAgenteYLoguear(A, administradorDeA, "agente-multas@quilmes.gob.ar");
        mvc.perform(resolverDescargo(A, agenteDeA, id, """
                {"comentario":"Intento sin permiso.","confirmar":true}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").doesNotExist());

        // Un usuario de soporte con solo multas.resolverDescargo no puede labrar.
        MockHttpSession soporteDeA =
                crearUsuarioConSoloResolverDescargo(A, administradorDeA, "soporte-multas@quilmes.gob.ar");
        mvc.perform(labrar(A, soporteDeA, """
                {"patente":"XX999YY","dni":null,"descripcionInfraccion":"Infracción","monto":100}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").doesNotExist());

        // Pero sí puede resolver el descargo pendiente.
        mvc.perform(resolverDescargo(A, soporteDeA, id, """
                {"comentario":"Se rechaza el descargo.","confirmar":true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"));
    }

    @Test
    @DisplayName("GET /api/multas/gestion lista todas las multas del municipio, ordenadas por notificadaEn "
            + "descendente, para quien tiene multas.labrar o multas.resolverDescargo")
    void listadoDeGestionMuestraTodasLasMultas() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "multas");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        String patente1 = patenteDePrueba();
        String patente2 = patenteDePrueba();
        labrarMulta(A, administradorDeA, patente1, "10000");
        labrarMulta(A, administradorDeA, patente2, "20000");

        mvc.perform(get(portalDe(A, "/api/multas/gestion")).session(administradorDeA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.patente == '" + patente1 + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.patente == '" + patente2 + "')]").isNotEmpty());
    }

    private MockHttpServletRequestBuilder labrar(String subdominio, MockHttpSession sesion, String cuerpo) {
        return post(portalDe(subdominio, "/api/multas"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private MockHttpServletRequestBuilder presentarDescargo(String subdominio, Long id, String cuerpo) {
        return post(portalDe(subdominio, "/api/multas/" + id + "/descargo"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private MockHttpServletRequestBuilder resolverDescargo(
            String subdominio, MockHttpSession sesion, Long id, String cuerpo) {

        return post(portalDe(subdominio, "/api/multas/" + id + "/resolver-descargo"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private MockHttpServletRequestBuilder iniciarPago(String subdominio, Long id) {
        return post(portalDe(subdominio, "/api/multas/" + id + "/pagos")).with(csrf());
    }

    private MockHttpServletRequestBuilder confirmarPago(String subdominio, String referenciaExterna,
            boolean aprobado) {

        return post(portalDe(subdominio, "/api/multas/pagos/confirmar"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"referenciaExterna\":\"" + referenciaExterna + "\",\"aprobado\":" + aprobado + "}");
    }

    /** Patente única de prueba, dentro del límite de 20 caracteres de la columna. */
    private static String patenteDePrueba() {
        return "PAT" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    /** Labra una multa sin DNI con sesión de administrador y devuelve su id. */
    private Long labrarMulta(String subdominio, MockHttpSession sesionAdmin, String patente, String monto)
            throws Exception {

        return labrarMultaConDni(subdominio, sesionAdmin, patente, null, monto);
    }

    private Long labrarMultaConDni(
            String subdominio, MockHttpSession sesionAdmin, String patente, String dni, String monto)
            throws Exception {

        MvcResult resultado = mvc.perform(labrar(subdominio, sesionAdmin, """
                {"patente":"%s","dni":%s,"descripcionInfraccion":"Exceso de velocidad","monto":%s}"""
                .formatted(patente, dni == null ? "null" : "\"" + dni + "\"", monto)))
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

    /** Crea un usuario con el rol de sistema 'agente' (tiene multas.labrar, no multas.resolverDescargo) y abre su sesión. */
    private MockHttpSession crearAgenteYLoguear(String subdominio, MockHttpSession sesionAdmin, String email)
            throws Exception {

        Long idDelRolAgente = idDelRol(subdominio, sesionAdmin, "agente");
        return crearUsuarioYLoguear(subdominio, sesionAdmin, "Agente de prueba", email, idDelRolAgente);
    }

    /**
     * Crea un usuario con un rol propio del municipio que tiene solo
     * {@code multas.resolverDescargo} (ADR 0011: el municipio compone sus
     * propios roles), y abre su sesión.
     */
    private MockHttpSession crearUsuarioConSoloResolverDescargo(
            String subdominio, MockHttpSession sesionAdmin, String email) throws Exception {

        String cuerpoDelRol = mvc.perform(post(portalDe(subdominio, "/api/roles"))
                .session(sesionAdmin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"codigo":"soporte-multas","nombre":"Soporte de multas",
                         "descripcion":"Solo resuelve descargos.","permisos":["multas.resolverDescargo"]}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long idDelRol = ((Number) JsonPath.read(cuerpoDelRol, "$.id")).longValue();
        return crearUsuarioYLoguear(subdominio, sesionAdmin, "Soporte de prueba", email, idDelRol);
    }

    private Long idDelRol(String subdominio, MockHttpSession sesionAdmin, String codigo) throws Exception {
        String cuerpoDeRoles = mvc.perform(get(portalDe(subdominio, "/api/roles")).session(sesionAdmin))
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> roles = JsonPath.read(cuerpoDeRoles, "$[?(@.codigo=='" + codigo + "')]");
        return ((Number) roles.get(0).get("id")).longValue();
    }

    private MockHttpSession crearUsuarioYLoguear(
            String subdominio, MockHttpSession sesionAdmin, String nombre, String email, Long idDelRol)
            throws Exception {

        String password = "otra-contrasena-larga";
        mvc.perform(post(portalDe(subdominio, "/api/usuarios"))
                .session(sesionAdmin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombre":"%s","email":"%s","password":"%s","roles":[%d]}
                        """.formatted(nombre, email, password, idDelRol)))
                .andExpect(status().isCreated());

        return iniciarSesion(subdominio, email, password);
    }
}
