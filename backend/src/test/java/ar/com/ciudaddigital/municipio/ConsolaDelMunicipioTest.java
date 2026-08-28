package ar.com.ciudaddigital.municipio;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import com.jayway.jsonpath.JsonPath;

import ar.com.ciudaddigital.SoporteDeIntegracion;

/**
 * Contrato de solo lectura y solicitud de alta/baja de módulo desde el
 * propio portal de municipio (ADR 0022).
 *
 * <p>El test de aislamiento es el más importante de la clase: a diferencia
 * del resto de las entidades de {@code municipio}, {@code solicitud_modulo}
 * vive en la base de <strong>control</strong>, compartida entre todos los
 * tenants (ver el Javadoc de {@code SolicitudDeModuloRepository}). Ahí no
 * hay separación física de base que garantice el aislamiento por sí sola:
 * depende enteramente de que cada consulta filtre por {@code tenant_id}.
 */
class ConsolaDelMunicipioTest extends SoporteDeIntegracion {

    private static final String A = "moron";
    private static final String B = "hurlingham";

    @BeforeEach
    void municipiosDePrueba() throws Exception {
        asegurarMunicipio(A, "Morón", "#1B5E20");
        asegurarMunicipio(B, "Hurlingham", "#4A148C");
    }

    @Test
    @DisplayName("circuito feliz: un administrador crea una solicitud y la ve PENDIENTE en su historial")
    void circuitoFelizCrearYVerLaSolicitud() throws Exception {
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        mvc.perform(post(portalDe(A, "/api/municipio/solicitudes-de-modulo"))
                .session(administradorDeA)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"moduloCodigo":"ejemplo","tipo":"ALTA","justificacion":"Lo necesitamos para el área de tránsito."}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.moduloCodigo").value("ejemplo"))
                .andExpect(jsonPath("$.tipo").value("ALTA"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.atendidaEn").doesNotExist());

        mvc.perform(get(portalDe(A, "/api/municipio/solicitudes-de-modulo")).session(administradorDeA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].moduloCodigo").value("ejemplo"))
                .andExpect(jsonPath("$[0].estado").value("PENDIENTE"));
    }

    @Test
    @DisplayName("GET /contrato devuelve los valores por defecto de un municipio nuevo, sin notaFacturacion")
    void contratoDevuelveLosValoresPorDefectoSinNotaFacturacion() throws Exception {
        // Slug propio de este test, distinto de A: aislamientoEntreTenants
        // muta la información comercial de A, y este test necesita los
        // valores por defecto de un alta recién hecha, sin depender de qué
        // otro test corrió antes (mismo criterio que
        // ConsolaDelProveedorTest.unMunicipioNuevoArrancaConLosValoresPorDefecto).
        String slugNuevo = "ezeiza";
        asegurarMunicipio(slugNuevo, "Ezeiza", "#01579B");
        MockHttpSession administradorDelNuevo = iniciarSesionDeAdministrador(slugNuevo);

        mvc.perform(get(portalDe(slugNuevo, "/api/municipio/contrato")).session(administradorDelNuevo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tramoPoblacional").value("MEDIANO"))
                .andExpect(jsonPath("$.estadoFacturacion").value("AL_DIA"))
                .andExpect(jsonPath("$.notaFacturacion").doesNotExist());
    }

    @Test
    @DisplayName("un código de módulo inexistente se rechaza con 400")
    void unCodigoDeModuloInexistenteSeRechaza() throws Exception {
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        mvc.perform(post(portalDe(A, "/api/municipio/solicitudes-de-modulo"))
                .session(administradorDeA)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"moduloCodigo":"no-existe","tipo":"ALTA","justificacion":"Justificación válida."}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("un tipo distinto de ALTA/BAJA se rechaza con 400")
    void unTipoInvalidoSeRechaza() throws Exception {
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        mvc.perform(post(portalDe(A, "/api/municipio/solicitudes-de-modulo"))
                .session(administradorDeA)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"moduloCodigo":"ejemplo","tipo":"MODIFICAR","justificacion":"Justificación válida."}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("una justificación vacía se rechaza con 400")
    void unaJustificacionVaciaSeRechaza() throws Exception {
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        mvc.perform(post(portalDe(A, "/api/municipio/solicitudes-de-modulo"))
                .session(administradorDeA)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"moduloCodigo":"ejemplo","tipo":"ALTA","justificacion":"   "}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("aislamiento entre tenants: una solicitud y un cambio comercial de A no se ven desde B")
    void aislamientoEntreTenants() throws Exception {
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);
        MockHttpSession administradorDeB = iniciarSesionDeAdministrador(B);

        mvc.perform(post(portalDe(A, "/api/municipio/solicitudes-de-modulo"))
                .session(administradorDeA)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"moduloCodigo":"ejemplo","tipo":"ALTA","justificacion":"Solicitud propia de Morón."}"""))
                .andExpect(status().isCreated());

        // La tabla solicitud_modulo vive en la base de control, compartida
        // entre todos los tenants: si el filtro por tenant_id fallara, esta
        // solicitud de A aparecería acá.
        mvc.perform(get(portalDe(B, "/api/municipio/solicitudes-de-modulo")).session(administradorDeB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        MockHttpSession plataforma = iniciarSesionDePlataforma();
        mvc.perform(patch("/api/admin/municipios/" + A + "/comercial")
                .session(plataforma)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"tramoPoblacional":"GRANDE","estadoFacturacion":"ATRASADO","notaFacturacion":"Nota interna de A"}"""))
                .andExpect(status().isOk());

        // El cambio comercial de A nunca se refleja en el contrato de B.
        mvc.perform(get(portalDe(B, "/api/municipio/contrato")).session(administradorDeB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tramoPoblacional").value("MEDIANO"))
                .andExpect(jsonPath("$.estadoFacturacion").value("AL_DIA"));
    }

    @Test
    @DisplayName("permisos: verContrato sin solicitarModulo puede ver pero no crear solicitudes")
    void permisosDeVerYSolicitarNoSeMezclan() throws Exception {
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);
        MockHttpSession soloVerDeA = crearUsuarioConSoloVerContrato(A, administradorDeA, "soporte@moron.gob.ar");

        mvc.perform(get(portalDe(A, "/api/municipio/contrato")).session(soloVerDeA))
                .andExpect(status().isOk());

        mvc.perform(get(portalDe(A, "/api/municipio/solicitudes-de-modulo")).session(soloVerDeA))
                .andExpect(status().isOk());

        mvc.perform(post(portalDe(A, "/api/municipio/solicitudes-de-modulo"))
                .session(soloVerDeA)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"moduloCodigo":"ejemplo","tipo":"ALTA","justificacion":"Intento sin permiso."}"""))
                .andExpect(status().isForbidden());
    }

    /**
     * Crea un usuario con un rol propio del municipio que tiene solo
     * {@code municipio.verContrato} (ADR 0011: el municipio compone sus
     * propios roles), y abre su sesión.
     */
    private MockHttpSession crearUsuarioConSoloVerContrato(
            String subdominio, MockHttpSession sesionAdmin, String email) throws Exception {

        String cuerpoDelRol = mvc.perform(post(portalDe(subdominio, "/api/roles"))
                .session(sesionAdmin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"codigo":"solo-ver-contrato","nombre":"Solo ve el contrato",
                         "descripcion":"Solo lee el contrato y el historial.","permisos":["municipio.verContrato"]}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long idDelRol = ((Number) JsonPath.read(cuerpoDelRol, "$.id")).longValue();

        String password = "otra-contrasena-larga";
        mvc.perform(post(portalDe(subdominio, "/api/usuarios"))
                .session(sesionAdmin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombre":"Solo ve el contrato","email":"%s","password":"%s","roles":[%d]}
                        """.formatted(email, password, idDelRol)))
                .andExpect(status().isCreated());

        return iniciarSesion(subdominio, email, password);
    }
}
