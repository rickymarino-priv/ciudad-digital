package ar.com.ciudaddigital.transparencia;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import ar.com.ciudaddigital.SoporteDeIntegracion;

/**
 * Publicación protegida y búsqueda pública de partidas presupuestarias y
 * entradas de escala salarial de Transparencia Activa (backlog R11), mismo
 * patrón lectura pública/escritura protegida que {@code BoletinTest} y
 * {@code CementerioTest}.
 *
 * <p>Cada test fija explícitamente qué módulos tiene contratado cada
 * municipio antes de verificar nada, mismo criterio que
 * {@code BoletinTest}: el contenedor de Postgres se comparte entre clases
 * de test.
 */
class TransparenciaTest extends SoporteDeIntegracion {

    private static final String A = "lomas";
    private static final String B = "berazategui";

    @BeforeEach
    void municipiosDePrueba() throws Exception {
        asegurarMunicipio(A, "Lomas de Zamora", "#00838F");
        asegurarMunicipio(B, "Berazategui", "#EF6C00");
    }

    @Test
    @DisplayName("publicar una partida con el módulo contratado y el permiso responde 201; sin el módulo, "
            + "403 MODULO_NO_CONTRATADO aunque haya sesión y permiso")
    void publicacionDePartidaSoloConElModuloContratado() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "transparencia");
        fijarModulos(B, plataforma);
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);
        MockHttpSession administradorDeB = iniciarSesionDeAdministrador(B);

        mvc.perform(publicarPartida(A, administradorDeA, """
                {"anio":2026,"area":"Obras Públicas","numeroPartida":"1.1.1.01",
                 "concepto":"Bacheo de calles","montoAsignado":1500000.50,"montoEjecutado":800000}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.anio").value(2026))
                .andExpect(jsonPath("$.publicadoPorNombre").value("Administrador de Lomas de Zamora"))
                .andExpect(jsonPath("$.publicadoPorEmail").value(emailDelAdministrador(A)));

        mvc.perform(publicarPartida(B, administradorDeB, """
                {"anio":2026,"area":"Obras Públicas","numeroPartida":"1",
                 "concepto":"Otra","montoAsignado":100}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("transparencia"));
    }

    @Test
    @DisplayName("un agente (sin transparencia.publicar) recibe 403 sin código al publicar una partida")
    void publicacionDePartidaSinElPermisoSeRechaza() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "transparencia");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        MockHttpSession agenteDeA =
                crearAgenteYLoguear(A, administradorDeA, "agente-transparencia@lomas.gob.ar");

        mvc.perform(publicarPartida(A, agenteDeA, """
                {"anio":2026,"area":"Obras Públicas","numeroPartida":"1",
                 "concepto":"Bacheo","montoAsignado":100}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").doesNotExist());
    }

    @Test
    @DisplayName("sin área, con monto asignado negativo, o con año fuera de rango, se rechaza con 400")
    void publicacionDePartidaInvalidaSeRechaza() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "transparencia");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        mvc.perform(publicarPartida(A, administradorDeA, """
                {"anio":2026,"numeroPartida":"1","concepto":"Bacheo","montoAsignado":100}"""))
                .andExpect(status().isBadRequest());

        mvc.perform(publicarPartida(A, administradorDeA, """
                {"anio":2026,"area":"Obras Públicas","numeroPartida":"1",
                 "concepto":"Bacheo","montoAsignado":-1}"""))
                .andExpect(status().isBadRequest());

        mvc.perform(publicarPartida(A, administradorDeA, """
                {"anio":1900,"area":"Obras Públicas","numeroPartida":"1",
                 "concepto":"Bacheo","montoAsignado":100}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("lectura pública de presupuesto: sin sesión, con el módulo contratado, devuelve lo publicado; "
            + "sin el módulo, 403 MODULO_NO_CONTRATADO aun sin sesión")
    void lecturaPublicaDePresupuestoSoloConElModuloContratado() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "transparencia");
        fijarModulos(B, plataforma);
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        String concepto = "Concepto público " + UUID.randomUUID();
        mvc.perform(publicarPartida(A, administradorDeA, """
                {"anio":2026,"area":"Salud","numeroPartida":"2","concepto":"%s","montoAsignado":100}"""
                .formatted(concepto)))
                .andExpect(status().isCreated());

        mvc.perform(get(portalDe(A, "/api/transparencia/presupuesto")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.concepto == '" + concepto + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.concepto == '" + concepto + "')].publicadoPorNombre")
                        .value("Administrador de Lomas de Zamora"))
                .andExpect(jsonPath("$[?(@.concepto == '" + concepto + "')].publicadoPorEmail")
                        .value(emailDelAdministrador(A)));

        mvc.perform(get(portalDe(B, "/api/transparencia/presupuesto")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("transparencia"));
    }

    @Test
    @DisplayName("filtros de presupuesto: por año y por texto en área/concepto")
    void filtrosDePresupuesto() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "transparencia");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        String sufijo = UUID.randomUUID().toString();
        String conceptoUno = "Bacheo de arbolado " + sufijo;
        String conceptoDos = "Compra de insumos " + sufijo;

        mvc.perform(publicarPartida(A, administradorDeA, """
                {"anio":2025,"area":"Obras Públicas","numeroPartida":"1","concepto":"%s","montoAsignado":100}"""
                .formatted(conceptoUno)))
                .andExpect(status().isCreated());
        mvc.perform(publicarPartida(A, administradorDeA, """
                {"anio":2026,"area":"Salud","numeroPartida":"2","concepto":"%s","montoAsignado":200}"""
                .formatted(conceptoDos)))
                .andExpect(status().isCreated());

        mvc.perform(get(portalDe(A, "/api/transparencia/presupuesto?anio=2025")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.concepto == '" + conceptoUno + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.concepto == '" + conceptoDos + "')]").isEmpty());

        mvc.perform(get(portalDe(A, "/api/transparencia/presupuesto?q=arbolado")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.concepto == '" + conceptoUno + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.concepto == '" + conceptoDos + "')]").isEmpty());
    }

    @Test
    @DisplayName("publicar una entrada de escala salarial responde 201 sin ningún campo de nombre de persona; "
            + "un agente recibe 403; una entrada inválida, 400")
    void publicacionDeEscalaSalarial() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "transparencia");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        String cuerpoDeLaRespuesta = mvc.perform(publicarCargo(A, administradorDeA, """
                {"anio":2026,"area":"Salud","cargo":"Médico de guardia","cantidadCargos":3,
                 "montoBrutoMensual":1200000}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.cargo").value("Médico de guardia"))
                .andExpect(jsonPath("$.cantidadCargos").value(3))
                .andExpect(jsonPath("$.publicadoPorNombre").value("Administrador de Lomas de Zamora"))
                .andExpect(jsonPath("$.publicadoPorEmail").value(emailDelAdministrador(A)))
                // Confirmación explícita del modelo de minimización de datos:
                // el JSON no trae ningún campo de nombre de persona.
                .andExpect(jsonPath("$.nombre").doesNotExist())
                .andExpect(jsonPath("$.nombreCompleto").doesNotExist())
                .andExpect(jsonPath("$.persona").doesNotExist())
                .andExpect(jsonPath("$.apellido").doesNotExist())
                .andExpect(jsonPath("$.legajo").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        // Y, más fuerte que ir descartando nombres posibles uno por uno:
        // las claves del JSON son exactamente las esperadas, ni una más.
        Map<String, Object> camposDeLaRespuesta = JsonPath.read(cuerpoDeLaRespuesta, "$");
        assertThat(camposDeLaRespuesta.keySet())
                .containsExactlyInAnyOrder("id", "anio", "area", "cargo", "cantidadCargos",
                        "montoBrutoMensual", "publicadoPorNombre", "publicadoPorEmail", "creadoEn");

        MockHttpSession agenteDeA =
                crearAgenteYLoguear(A, administradorDeA, "agente-escala@lomas.gob.ar");
        mvc.perform(publicarCargo(A, agenteDeA, """
                {"anio":2026,"area":"Salud","cargo":"Enfermero","cantidadCargos":1,
                 "montoBrutoMensual":900000}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").doesNotExist());

        mvc.perform(publicarCargo(A, administradorDeA, """
                {"anio":2026,"area":"Salud","cargo":"","cantidadCargos":1,"montoBrutoMensual":900000}"""))
                .andExpect(status().isBadRequest());
        mvc.perform(publicarCargo(A, administradorDeA, """
                {"anio":2026,"area":"Salud","cargo":"Enfermero","cantidadCargos":1,
                 "montoBrutoMensual":-1}"""))
                .andExpect(status().isBadRequest());
        mvc.perform(publicarCargo(A, administradorDeA, """
                {"anio":2026,"area":"Salud","cargo":"Enfermero","cantidadCargos":0,
                 "montoBrutoMensual":900000}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("lectura pública de sueldos: sin sesión, con filtros por año y por texto en área/cargo")
    void lecturaPublicaDeSueldosConFiltros() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "transparencia");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        String sufijo = UUID.randomUUID().toString();
        String cargoUno = "Inspector de tránsito " + sufijo;
        String cargoDos = "Bibliotecario " + sufijo;

        mvc.perform(publicarCargo(A, administradorDeA, """
                {"anio":2025,"area":"Tránsito","cargo":"%s","cantidadCargos":2,"montoBrutoMensual":700000}"""
                .formatted(cargoUno)))
                .andExpect(status().isCreated());
        mvc.perform(publicarCargo(A, administradorDeA, """
                {"anio":2026,"area":"Cultura","cargo":"%s","cantidadCargos":1,"montoBrutoMensual":650000}"""
                .formatted(cargoDos)))
                .andExpect(status().isCreated());

        mvc.perform(get(portalDe(A, "/api/transparencia/sueldos?anio=2025")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.cargo == '" + cargoUno + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.cargo == '" + cargoDos + "')]").isEmpty());

        mvc.perform(get(portalDe(A, "/api/transparencia/sueldos?q=Bibliotecario")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.cargo == '" + cargoDos + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.cargo == '" + cargoUno + "')]").isEmpty());
    }

    @Test
    @DisplayName("aislamiento: una partida y una entrada de escala salarial publicadas en un municipio "
            + "no aparecen en la búsqueda del otro")
    void aislamientoEntreTenants() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "transparencia");
        fijarModulos(B, plataforma, "transparencia");

        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);
        MockHttpSession administradorDeB = iniciarSesionDeAdministrador(B);

        // Conceptos/cargos únicos (con un sufijo aleatorio) para no
        // confundirse con filas que hayan quedado de otro test de esta misma
        // clase, que reutiliza estos dos municipios (mismo criterio que
        // BoletinTest.aislamientoEntreTenants).
        String conceptoDeA = "Concepto de Lomas " + UUID.randomUUID();
        String conceptoDeB = "Concepto de Berazategui " + UUID.randomUUID();
        String cargoDeA = "Cargo de Lomas " + UUID.randomUUID();
        String cargoDeB = "Cargo de Berazategui " + UUID.randomUUID();

        mvc.perform(publicarPartida(A, administradorDeA, """
                {"anio":2026,"area":"Obras Públicas","numeroPartida":"1","concepto":"%s","montoAsignado":100}"""
                .formatted(conceptoDeA)))
                .andExpect(status().isCreated());
        mvc.perform(publicarPartida(B, administradorDeB, """
                {"anio":2026,"area":"Obras Públicas","numeroPartida":"1","concepto":"%s","montoAsignado":200}"""
                .formatted(conceptoDeB)))
                .andExpect(status().isCreated());

        mvc.perform(publicarCargo(A, administradorDeA, """
                {"anio":2026,"area":"Salud","cargo":"%s","cantidadCargos":1,"montoBrutoMensual":100}"""
                .formatted(cargoDeA)))
                .andExpect(status().isCreated());
        mvc.perform(publicarCargo(B, administradorDeB, """
                {"anio":2026,"area":"Salud","cargo":"%s","cantidadCargos":1,"montoBrutoMensual":200}"""
                .formatted(cargoDeB)))
                .andExpect(status().isCreated());

        // El id por sí solo no sirve para comparar entre municipios: cada
        // base tiene su propia secuencia. Lo que prueba el aislamiento es
        // el concepto/cargo, único por el sufijo aleatorio.
        mvc.perform(get(portalDe(A, "/api/transparencia/presupuesto")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.concepto == '" + conceptoDeA + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.concepto == '" + conceptoDeB + "')]").isEmpty());
        mvc.perform(get(portalDe(B, "/api/transparencia/presupuesto")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.concepto == '" + conceptoDeB + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.concepto == '" + conceptoDeA + "')]").isEmpty());

        mvc.perform(get(portalDe(A, "/api/transparencia/sueldos")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.cargo == '" + cargoDeA + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.cargo == '" + cargoDeB + "')]").isEmpty());
        mvc.perform(get(portalDe(B, "/api/transparencia/sueldos")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.cargo == '" + cargoDeB + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.cargo == '" + cargoDeA + "')]").isEmpty());
    }

    private MockHttpServletRequestBuilder publicarPartida(String subdominio, MockHttpSession sesion, String cuerpo) {
        return post(portalDe(subdominio, "/api/transparencia/presupuesto"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private MockHttpServletRequestBuilder publicarCargo(String subdominio, MockHttpSession sesion, String cuerpo) {
        return post(portalDe(subdominio, "/api/transparencia/sueldos"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
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

    /** Crea un usuario con el rol de sistema 'agente' (sin {@code transparencia.publicar}) y abre su sesión. */
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
