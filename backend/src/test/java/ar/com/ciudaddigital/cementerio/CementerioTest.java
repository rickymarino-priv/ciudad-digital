package ar.com.ciudaddigital.cementerio;

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
 * Registro protegido y búsqueda pública de sepulturas del cementerio
 * municipal (backlog R8), calcado de {@code BoletinTest}: acá también la
 * escritura requiere sesión y permiso, y la lectura es pública sin
 * sesión — a diferencia de {@code boletin}, el permiso
 * {@code cementerio.registrar} se asigna a ambos roles de sistema.
 *
 * <p>Cada test fija explícitamente qué módulos tiene contratado cada
 * municipio antes de verificar nada, mismo criterio que
 * {@code BoletinTest}: el contenedor de Postgres se comparte entre clases
 * de test.
 */
class CementerioTest extends SoporteDeIntegracion {

    private static final String A = "tandil";
    private static final String B = "olavarria";

    @BeforeEach
    void municipiosDePrueba() throws Exception {
        asegurarMunicipio(A, "Tandil", "#00695C");
        asegurarMunicipio(B, "Olavarría", "#4527A0");
    }

    @Test
    @DisplayName("alta con el módulo contratado y el permiso responde 201 con el registro completo; "
            + "sin el módulo, 403 MODULO_NO_CONTRATADO aunque haya sesión y permiso")
    void altaSoloConElModuloContratado() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "cementerio");
        fijarModulos(B, plataforma);
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);
        MockHttpSession administradorDeB = iniciarSesionDeAdministrador(B);

        mvc.perform(registrar(A, administradorDeA, """
                {"tipoParcela":"NICHO","sector":"A","fila":"3","numero":"12",
                 "nombreDifunto":"Juan Pérez","fechaFallecimiento":"2026-01-10",
                 "fechaInhumacion":"2026-01-12","nombreTitular":"María Pérez",
                 "contactoTitular":"maria@example.com","observaciones":"Sin observaciones."}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.tipoParcela").value("NICHO"))
                .andExpect(jsonPath("$.nombreDifunto").value("Juan Pérez"))
                .andExpect(jsonPath("$.nombreTitular").value("María Pérez"))
                .andExpect(jsonPath("$.observaciones").value("Sin observaciones."))
                .andExpect(jsonPath("$.registradoPorNombre").value("Administrador de Tandil"))
                .andExpect(jsonPath("$.registradoPorEmail").value(emailDelAdministrador(A)));

        mvc.perform(registrar(B, administradorDeB, """
                {"tipoParcela":"NICHO","sector":"A","fila":"1","numero":"1",
                 "nombreDifunto":"Otro Difunto","fechaFallecimiento":"2026-01-10",
                 "fechaInhumacion":"2026-01-12"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("cementerio"));
    }

    @Test
    @DisplayName("un agente (que sí tiene cementerio.registrar) puede registrar una sepultura")
    void altaConUsuarioAgenteSeAcepta() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "cementerio");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        MockHttpSession agenteDeA =
                crearAgenteYLoguear(A, administradorDeA, "agente-cementerio@tandil.gob.ar");

        mvc.perform(registrar(A, agenteDeA, """
                {"tipoParcela":"PARCELA","sector":"B","fila":"2","numero":"7",
                 "nombreDifunto":"Carlos Gómez","fechaFallecimiento":"2026-02-01",
                 "fechaInhumacion":"2026-02-02"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registradoPorEmail").value("agente-cementerio@tandil.gob.ar"));
    }

    @Test
    @DisplayName("sin número, con fecha de inhumación anterior a la de fallecimiento, o con un tipo "
            + "de parcela inexistente, se rechaza con 400")
    void altaInvalidaSeRechaza() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "cementerio");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        mvc.perform(registrar(A, administradorDeA, """
                {"tipoParcela":"NICHO","sector":"A","fila":"3",
                 "nombreDifunto":"Sin Numero","fechaFallecimiento":"2026-01-10",
                 "fechaInhumacion":"2026-01-12"}"""))
                .andExpect(status().isBadRequest());

        mvc.perform(registrar(A, administradorDeA, """
                {"tipoParcela":"NICHO","sector":"A","fila":"3","numero":"12",
                 "nombreDifunto":"Fecha Invertida","fechaFallecimiento":"2026-01-12",
                 "fechaInhumacion":"2026-01-10"}"""))
                .andExpect(status().isBadRequest());

        mvc.perform(registrar(A, administradorDeA, """
                {"tipoParcela":"INEXISTENTE","sector":"A","fila":"3","numero":"12",
                 "nombreDifunto":"Tipo Invalido","fechaFallecimiento":"2026-01-10",
                 "fechaInhumacion":"2026-01-12"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("lectura pública: sin sesión, con el módulo contratado, devuelve lo registrado sin "
            + "los datos privados; sin el módulo, 403 MODULO_NO_CONTRATADO aun sin sesión")
    void lecturaPublicaSoloConElModuloContratado() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "cementerio");
        fijarModulos(B, plataforma);
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        String nombreDifunto = "Difunto Público " + UUID.randomUUID();
        mvc.perform(registrar(A, administradorDeA, """
                {"tipoParcela":"BOVEDA","sector":"C","fila":"1","numero":"9",
                 "nombreDifunto":"%s","fechaFallecimiento":"2026-01-10",
                 "fechaInhumacion":"2026-01-12","nombreTitular":"Titular Privado",
                 "contactoTitular":"contacto@privado.com","observaciones":"Dato privado."}"""
                .formatted(nombreDifunto)))
                .andExpect(status().isCreated());

        mvc.perform(get(portalDe(A, "/api/cementerio")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombreDifunto == '" + nombreDifunto + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombreDifunto == '" + nombreDifunto + "')][0].nombreTitular")
                        .doesNotExist())
                .andExpect(jsonPath("$[?(@.nombreDifunto == '" + nombreDifunto + "')][0].contactoTitular")
                        .doesNotExist())
                .andExpect(jsonPath("$[?(@.nombreDifunto == '" + nombreDifunto + "')][0].observaciones")
                        .doesNotExist())
                .andExpect(jsonPath("$[?(@.nombreDifunto == '" + nombreDifunto + "')][0].registradoPorNombre")
                        .doesNotExist())
                .andExpect(jsonPath("$[?(@.nombreDifunto == '" + nombreDifunto + "')][0].registradoPorEmail")
                        .doesNotExist());

        mvc.perform(get(portalDe(B, "/api/cementerio")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("cementerio"));
    }

    @Test
    @DisplayName("filtros: por tipo de parcela y por texto en el nombre del difunto")
    void filtrosDeTipoYTexto() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "cementerio");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        String sufijo = UUID.randomUUID().toString();
        String nombreNicho = "Fernández " + sufijo;
        String nombrePanteon = "Giménez " + sufijo;

        mvc.perform(registrar(A, administradorDeA, """
                {"tipoParcela":"NICHO","sector":"A","fila":"1","numero":"1",
                 "nombreDifunto":"%s","fechaFallecimiento":"2026-01-05",
                 "fechaInhumacion":"2026-01-06"}"""
                .formatted(nombreNicho)))
                .andExpect(status().isCreated());
        mvc.perform(registrar(A, administradorDeA, """
                {"tipoParcela":"PANTEON","sector":"B","fila":"2","numero":"2",
                 "nombreDifunto":"%s","fechaFallecimiento":"2026-01-07",
                 "fechaInhumacion":"2026-01-08"}"""
                .formatted(nombrePanteon)))
                .andExpect(status().isCreated());

        mvc.perform(get(portalDe(A, "/api/cementerio?tipoParcela=PANTEON")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombreDifunto == '" + nombrePanteon + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombreDifunto == '" + nombreNicho + "')]").isEmpty());

        mvc.perform(get(portalDe(A, "/api/cementerio?q=Fern")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombreDifunto == '" + nombreNicho + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombreDifunto == '" + nombrePanteon + "')]").isEmpty());
    }

    @Test
    @DisplayName("aislamiento: una sepultura registrada en un municipio no aparece en la búsqueda del otro")
    void aislamientoEntreTenants() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "cementerio");
        fijarModulos(B, plataforma, "cementerio");

        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);
        MockHttpSession administradorDeB = iniciarSesionDeAdministrador(B);

        // Nombres únicos (con un sufijo aleatorio) para no confundirse con
        // filas que hayan quedado de otro test de esta misma clase, que
        // reutiliza estos dos municipios (mismo criterio que BoletinTest).
        String nombreDeA = "Sepultura de Tandil " + UUID.randomUUID();
        String nombreDeB = "Sepultura de Olavarría " + UUID.randomUUID();

        mvc.perform(registrar(A, administradorDeA, """
                {"tipoParcela":"NICHO","sector":"A","fila":"1","numero":"1",
                 "nombreDifunto":"%s","fechaFallecimiento":"2026-01-10",
                 "fechaInhumacion":"2026-01-12"}"""
                .formatted(nombreDeA)))
                .andExpect(status().isCreated());
        mvc.perform(registrar(B, administradorDeB, """
                {"tipoParcela":"NICHO","sector":"A","fila":"1","numero":"1",
                 "nombreDifunto":"%s","fechaFallecimiento":"2026-01-10",
                 "fechaInhumacion":"2026-01-12"}"""
                .formatted(nombreDeB)))
                .andExpect(status().isCreated());

        // El id por sí solo no sirve para comparar entre municipios: cada
        // base tiene su propia secuencia. Lo que prueba el aislamiento es
        // el nombre del difunto, único por el sufijo aleatorio.
        mvc.perform(get(portalDe(A, "/api/cementerio")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombreDifunto == '" + nombreDeA + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombreDifunto == '" + nombreDeB + "')]").isEmpty());

        mvc.perform(get(portalDe(B, "/api/cementerio")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombreDifunto == '" + nombreDeB + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombreDifunto == '" + nombreDeA + "')]").isEmpty());
    }

    private MockHttpServletRequestBuilder registrar(String subdominio, MockHttpSession sesion, String cuerpo) {
        return post(portalDe(subdominio, "/api/cementerio"))
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

    /** Crea un usuario con el rol de sistema 'agente' (que sí tiene {@code cementerio.registrar}) y abre su sesión. */
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
