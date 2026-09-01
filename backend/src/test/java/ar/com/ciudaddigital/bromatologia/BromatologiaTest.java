package ar.com.ciudaddigital.bromatologia;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * Alta protegida y lectura pública del padrón de comercios, y alta y
 * lectura protegidas del historial de inspecciones de Bromatología (R28,
 * ADR 0032).
 *
 * <p>Cada test fija explícitamente qué módulos tiene contratado cada
 * municipio antes de verificar nada, mismo criterio que
 * {@code DefensaCivilTest}: el contenedor de Postgres se comparte entre
 * clases de test.
 */
class BromatologiaTest extends SoporteDeIntegracion {

    private static final String A = "moron";
    private static final String B = "quilmes";

    @BeforeEach
    void municipiosDePrueba() throws Exception {
        asegurarMunicipio(A, "Morón", "#004D40");
        asegurarMunicipio(B, "Quilmes", "#4A148C");
    }

    @Test
    @DisplayName("alta de comercio con el módulo contratado y el permiso responde 201 con el comercio HABILITADO")
    void altaDeComercioConElPermisoQuedaHabilitado() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "bromatologia");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        mvc.perform(registrarComercio(A, administradorDeA, """
                {"nombre":"Verdulería Don José","rubro":"VERDULERIA","direccion":"San Martín 450",
                 "fechaHabilitacion":"2026-01-01","fechaVencimientoHabilitacion":"2027-01-01"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.nombre").value("Verdulería Don José"))
                .andExpect(jsonPath("$.rubro").value("VERDULERIA"))
                .andExpect(jsonPath("$.estado").value("HABILITADO"))
                .andExpect(jsonPath("$.publicadoPorNombre").value("Administrador de Morón"))
                .andExpect(jsonPath("$.publicadoPorEmail").value(emailDelAdministrador(A)));
    }

    @Test
    @DisplayName("alta de comercio sin el permiso bromatologia.gestionar se rechaza con 403 sin código")
    void altaDeComercioSinElPermisoSeRechaza() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "bromatologia");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        MockHttpSession agenteSinPermiso = crearUsuarioConSoloOtroPermiso(
                A, administradorDeA, "sin-bromatologia", "agente-sin-bromatologia@moron.gob.ar");

        mvc.perform(registrarComercio(A, agenteSinPermiso, """
                {"nombre":"Comercio sin permiso","rubro":"ALMACEN","direccion":"Calle 1",
                 "fechaHabilitacion":"2026-01-01","fechaVencimientoHabilitacion":"2027-01-01"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").doesNotExist());
    }

    @Test
    @DisplayName("alta de comercio con vencimiento anterior o igual a la habilitación da 400")
    void altaDeComercioConVencimientoInvalido() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "bromatologia");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        mvc.perform(registrarComercio(A, administradorDeA, """
                {"nombre":"Comercio con vencimiento anterior","rubro":"ALMACEN","direccion":"Calle 1",
                 "fechaHabilitacion":"2026-01-01","fechaVencimientoHabilitacion":"2025-01-01"}"""))
                .andExpect(status().isBadRequest());

        mvc.perform(registrarComercio(A, administradorDeA, """
                {"nombre":"Comercio con vencimiento igual","rubro":"ALMACEN","direccion":"Calle 1",
                 "fechaHabilitacion":"2026-01-01","fechaVencimientoHabilitacion":"2026-01-01"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("alta de comercio con nombre vacío, dirección vacía o rubro inválido da 400")
    void altaDeComercioConCamposInvalidos() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "bromatologia");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        mvc.perform(registrarComercio(A, administradorDeA, """
                {"nombre":"","rubro":"ALMACEN","direccion":"Calle 1",
                 "fechaHabilitacion":"2026-01-01","fechaVencimientoHabilitacion":"2027-01-01"}"""))
                .andExpect(status().isBadRequest());

        mvc.perform(registrarComercio(A, administradorDeA, """
                {"nombre":"Comercio","rubro":"ALMACEN","direccion":"",
                 "fechaHabilitacion":"2026-01-01","fechaVencimientoHabilitacion":"2027-01-01"}"""))
                .andExpect(status().isBadRequest());

        mvc.perform(registrarComercio(A, administradorDeA, """
                {"nombre":"Comercio","rubro":"INEXISTENTE","direccion":"Calle 1",
                 "fechaHabilitacion":"2026-01-01","fechaVencimientoHabilitacion":"2027-01-01"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("listado público de comercios sin sesión, con filtros por rubro, estado y q, "
            + "por separado y combinados, filtro inválido da 400")
    void listadoPublicoDeComerciosConFiltros() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "bromatologia");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        String sufijo = UUID.randomUUID().toString();
        String verduleria = "Verdulería " + sufijo;
        String carniceria = "Carnicería " + sufijo;

        Long idVerduleria = registrarComercioYObtenerId(A, administradorDeA, verduleria, "VERDULERIA");
        Long idCarniceria = registrarComercioYObtenerId(A, administradorDeA, carniceria, "CARNICERIA");

        mvc.perform(registrarInspeccion(A, administradorDeA, idCarniceria, "2026-02-01", "OBSERVADO", "Falta higiene."))
                .andExpect(status().isCreated());

        // Por rubro.
        mvc.perform(get(portalDe(A, "/api/bromatologia/comercios?rubro=VERDULERIA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + verduleria + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + carniceria + "')]").isEmpty());

        // Por estado: una OBSERVADO, la otra sigue HABILITADO.
        mvc.perform(get(portalDe(A, "/api/bromatologia/comercios?estado=OBSERVADO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + carniceria + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + verduleria + "')]").isEmpty());
        mvc.perform(get(portalDe(A, "/api/bromatologia/comercios?estado=HABILITADO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + verduleria + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + carniceria + "')]").isEmpty());

        // Por texto: matchea nombre o dirección.
        mvc.perform(get(portalDe(A, "/api/bromatologia/comercios?q=" + sufijo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + verduleria + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + carniceria + "')]").isNotEmpty());

        // Combinados.
        mvc.perform(get(portalDe(A, "/api/bromatologia/comercios?rubro=CARNICERIA&estado=OBSERVADO&q=" + sufijo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + carniceria + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + verduleria + "')]").isEmpty());

        // El listado público no expone ningún campo de inspección.
        mvc.perform(get(portalDe(A, "/api/bromatologia/comercios?rubro=CARNICERIA&q=" + sufijo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].observaciones").doesNotExist());

        // Filtros inválidos dan 400, no "sin filtro".
        mvc.perform(get(portalDe(A, "/api/bromatologia/comercios?rubro=INEXISTENTE")))
                .andExpect(status().isBadRequest());
        mvc.perform(get(portalDe(A, "/api/bromatologia/comercios?estado=INEXISTENTE")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("registrar una inspección actualiza el estado del comercio al resultado, en la misma operación")
    void registrarInspeccionActualizaElEstadoDelComercio() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "bromatologia");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        Long id = registrarComercioYObtenerId(
                A, administradorDeA, "Restaurante a inspeccionar " + UUID.randomUUID(), "RESTAURANTE");

        mvc.perform(registrarInspeccion(A, administradorDeA, id, "2026-03-01", "OBSERVADO", "Faltan matayuyos."))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.comercioId").value(id))
                .andExpect(jsonPath("$.resultado").value("OBSERVADO"))
                .andExpect(jsonPath("$.observaciones").value("Faltan matayuyos."))
                .andExpect(jsonPath("$.inspeccionadoPorNombre").value("Administrador de Morón"))
                .andExpect(jsonPath("$.inspeccionadoPorEmail").value(emailDelAdministrador(A)));

        mvc.perform(get(portalDe(A, "/api/bromatologia/comercios?q=" + id)))
                .andExpect(status().isOk());

        mvc.perform(get(portalDe(A, "/api/bromatologia/comercios")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + " && @.estado == 'OBSERVADO')]").isNotEmpty());
    }

    @Test
    @DisplayName("una reinspección con el mismo resultado del estado actual del comercio es válida, no se rechaza")
    void reinspeccionConMismoResultadoEsValida() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "bromatologia");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        Long id = registrarComercioYObtenerId(
                A, administradorDeA, "Comercio reinspeccionado " + UUID.randomUUID(), "ALMACEN");

        // Nace HABILITADO: una inspección con resultado HABILITADO (mismo
        // estado actual) es una reinspección de rutina válida (ADR 0032 §3).
        mvc.perform(registrarInspeccion(A, administradorDeA, id, "2026-03-01", "HABILITADO", "Todo en orden."))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultado").value("HABILITADO"));

        mvc.perform(registrarInspeccion(A, administradorDeA, id, "2026-04-01", "HABILITADO", "Sigue todo en orden."))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultado").value("HABILITADO"));

        mvc.perform(get(portalDe(A, "/api/bromatologia/comercios/" + id + "/inspecciones")).session(administradorDeA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("historial de inspecciones ordenado por fecha descendente, requiere sesión y permiso")
    void historialDeInspeccionesOrdenadoYProtegido() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "bromatologia");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        Long id = registrarComercioYObtenerId(
                A, administradorDeA, "Panadería con historial " + UUID.randomUUID(), "PANADERIA");

        mvc.perform(registrarInspeccion(A, administradorDeA, id, "2026-01-10", "HABILITADO", "Primera inspección."))
                .andExpect(status().isCreated());
        mvc.perform(registrarInspeccion(A, administradorDeA, id, "2026-03-10", "CLAUSURADO", "Segunda inspección."))
                .andExpect(status().isCreated());

        mvc.perform(get(portalDe(A, "/api/bromatologia/comercios/" + id + "/inspecciones")).session(administradorDeA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].fecha").value("2026-03-10"))
                .andExpect(jsonPath("$[0].observaciones").value("Segunda inspección."))
                .andExpect(jsonPath("$[1].fecha").value("2026-01-10"));

        // Sin sesión: no está en rutasDeLecturaPublica, requiere autenticación.
        mvc.perform(get(portalDe(A, "/api/bromatologia/comercios/" + id + "/inspecciones")))
                .andExpect(status().isUnauthorized());

        // Con sesión pero sin el permiso: 403.
        MockHttpSession agenteSinPermiso = crearUsuarioConSoloOtroPermiso(
                A, administradorDeA, "sin-bromatologia-historial", "sin-historial@moron.gob.ar");
        mvc.perform(get(portalDe(A, "/api/bromatologia/comercios/" + id + "/inspecciones")).session(agenteSinPermiso))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").doesNotExist());
    }

    @Test
    @DisplayName("registrar inspección sin el permiso se rechaza con 403, comercio inexistente da 404")
    void registrarInspeccionSinPermisoOComercioInexistente() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "bromatologia");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        Long id = registrarComercioYObtenerId(
                A, administradorDeA, "Comercio para inspección sin permiso " + UUID.randomUUID(), "OTRO");

        MockHttpSession agenteSinPermiso = crearUsuarioConSoloOtroPermiso(
                A, administradorDeA, "sin-bromatologia-inspeccion", "sin-inspeccion@moron.gob.ar");
        mvc.perform(registrarInspeccion(A, agenteSinPermiso, id, "2026-01-01", "OBSERVADO", null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").doesNotExist());

        mvc.perform(registrarInspeccion(A, administradorDeA, 999999L, "2026-01-01", "OBSERVADO", null))
                .andExpect(status().isNotFound());

        mvc.perform(get(portalDe(A, "/api/bromatologia/comercios/999999/inspecciones")).session(administradorDeA))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("sin el módulo contratado, alta/listado de comercios e inspecciones rechazan con 403 "
            + "MODULO_NO_CONTRATADO, incluso sin sesión y con datos válidos")
    void sinModuloContratadoRechazaTodasLasRutas() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(B, plataforma);
        MockHttpSession administradorDeB = iniciarSesionDeAdministrador(B);

        mvc.perform(registrarComercio(B, administradorDeB, """
                {"nombre":"Comercio sin módulo","rubro":"OTRO","direccion":"Calle 1",
                 "fechaHabilitacion":"2026-01-01","fechaVencimientoHabilitacion":"2027-01-01"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("bromatologia"));

        mvc.perform(get(portalDe(B, "/api/bromatologia/comercios")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("bromatologia"));

        mvc.perform(registrarInspeccion(B, administradorDeB, 1L, "2026-01-01", "OBSERVADO", null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("bromatologia"));

        mvc.perform(get(portalDe(B, "/api/bromatologia/comercios/1/inspecciones")).session(administradorDeB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("bromatologia"));
    }

    @Test
    @DisplayName("aislamiento: un comercio registrado en un municipio no aparece en ningún listado de otro, "
            + "y sus inspecciones son inaccesibles desde otro municipio (POST y GET dan 404)")
    void aislamientoDeComerciosEInspeccionesEntreTenants() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "bromatologia");
        fijarModulos(B, plataforma, "bromatologia");

        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);
        MockHttpSession administradorDeB = iniciarSesionDeAdministrador(B);

        String sufijo = UUID.randomUUID().toString();
        String nombreDeA = "Comercio de Morón " + sufijo;
        Long idDeA = registrarComercioYObtenerId(A, administradorDeA, nombreDeA, "RESTAURANTE");

        mvc.perform(registrarInspeccion(A, administradorDeA, idDeA, "2026-02-01", "OBSERVADO", "Observación privada de A"))
                .andExpect(status().isCreated());

        // (a) El comercio de A no aparece en ningún listado de B.
        mvc.perform(get(portalDe(B, "/api/bromatologia/comercios")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeA + "')]").isEmpty());
        mvc.perform(get(portalDe(B, "/api/bromatologia/comercios?estado=OBSERVADO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeA + "')]").isEmpty());
        mvc.perform(get(portalDe(B, "/api/bromatologia/comercios?q=" + sufijo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeA + "')]").isEmpty());

        // Sigue visible en el listado del municipio dueño, con el estado actualizado.
        mvc.perform(get(portalDe(A, "/api/bromatologia/comercios")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeA + "' && @.estado == 'OBSERVADO')]").isNotEmpty());

        // (b) POST y GET de inspecciones sobre el id de A desde B dan 404,
        // sin revelar que el comercio existe en otro tenant (garantía
        // real: el datasource ruteado por tenant, no una validación de
        // aplicación — el id de A no existe en la base de B).
        mvc.perform(registrarInspeccion(B, administradorDeB, idDeA, "2026-02-02", "CLAUSURADO", "Intento desde B"))
                .andExpect(status().isNotFound());
        mvc.perform(get(portalDe(B, "/api/bromatologia/comercios/" + idDeA + "/inspecciones")).session(administradorDeB))
                .andExpect(status().isNotFound());

        // (c) La inspección de A no aparece en ningún listado de B (no
        // hay ningún endpoint de listado global de inspecciones, pero se
        // verifica igual que el comercio de A, ya observado, no aparece
        // en el padrón de B con ningún filtro combinado).
        mvc.perform(get(portalDe(B, "/api/bromatologia/comercios?rubro=RESTAURANTE&estado=OBSERVADO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeA + "')]").isEmpty());

        // El comercio de B, en cambio, sigue HABILITADO: la inspección de
        // A no afectó ningún dato de B.
        mvc.perform(get(portalDe(B, "/api/bromatologia/comercios")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.estado == 'CLAUSURADO')]").isEmpty());
    }

    private MockHttpServletRequestBuilder registrarComercio(String subdominio, MockHttpSession sesion, String cuerpo) {
        return post(portalDe(subdominio, "/api/bromatologia/comercios"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private MockHttpServletRequestBuilder registrarInspeccion(
            String subdominio, MockHttpSession sesion, Long comercioId, String fecha, String resultado,
            String observaciones) {

        String cuerpo = observaciones == null
                ? "{\"fecha\":\"%s\",\"resultado\":\"%s\",\"observaciones\":null}".formatted(fecha, resultado)
                : "{\"fecha\":\"%s\",\"resultado\":\"%s\",\"observaciones\":\"%s\"}"
                        .formatted(fecha, resultado, observaciones);

        return post(portalDe(subdominio, "/api/bromatologia/comercios/" + comercioId + "/inspecciones"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private Long registrarComercioYObtenerId(
            String subdominio, MockHttpSession sesionAdmin, String nombre, String rubro) throws Exception {

        MvcResult resultado = mvc.perform(registrarComercio(subdominio, sesionAdmin, """
                {"nombre":"%s","rubro":"%s","direccion":"Dirección de prueba",
                 "fechaHabilitacion":"2026-01-01","fechaVencimientoHabilitacion":"2027-01-01"}"""
                .formatted(nombre, rubro)))
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
     * {@code bromatologia.gestionar} (ADR 0011: el municipio compone sus
     * propios roles), y abre su sesión. {@code codigoDeRol} es parámetro
     * porque {@code rol.codigo} es único por municipio (V2) y este test
     * llama a este helper más de una vez sobre el mismo municipio A.
     */
    private MockHttpSession crearUsuarioConSoloOtroPermiso(
            String subdominio, MockHttpSession sesionAdmin, String codigoDeRol, String email) throws Exception {

        String cuerpoDelRol = mvc.perform(post(portalDe(subdominio, "/api/roles"))
                .session(sesionAdmin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"codigo":"%s","nombre":"Sin permiso de bromatología",
                         "descripcion":"No puede gestionar bromatología.","permisos":[]}"""
                        .formatted(codigoDeRol)))
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
