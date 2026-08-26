package ar.com.ciudaddigital.tenants;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import ar.com.ciudaddigital.SoporteDeIntegracion;

/**
 * Contrato mínimo por municipio en la consola del proveedor (ADR 0019):
 * tramo poblacional, estado de facturación y cantidad de módulos
 * contratados sobre la API de administración existente.
 *
 * <p>Es una superficie legítimamente cross-tenant (ver "Aislamiento" de la
 * spec CD-23): no lleva un test de "un municipio no ve los datos de otro"
 * porque acá ver todos los municipios a la vez es el comportamiento
 * correcto. El criterio que sí aplica es quién puede llegar a esta vista
 * ({@link #soloUnaSesionDePlataformaPuedeOperarElContrato}).
 */
class ConsolaDelProveedorTest extends SoporteDeIntegracion {

    private static final String SLUG = "tigre";

    @BeforeEach
    void municipioDePrueba() throws Exception {
        asegurarMunicipio(SLUG, "Tigre", "#2E7D32");
    }

    @Test
    @DisplayName("un municipio recién dado de alta arranca con el tramo intermedio, al día, "
            + "sin nota y sin módulos")
    void unMunicipioNuevoArrancaConLosValoresPorDefecto() throws Exception {
        // Slug propio de este test, distinto de SLUG: los demás tests de la
        // clase mutan la información comercial de SLUG, y este test necesita
        // los valores por defecto de un alta recién hecha, sin depender de
        // qué otro test corrió antes.
        String slugNuevo = "moreno";
        asegurarMunicipio(slugNuevo, "Moreno", "#4E342E");
        MockHttpSession plataforma = iniciarSesionDePlataforma();

        mvc.perform(get("/api/admin/municipios").session(plataforma))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slug=='" + slugNuevo + "')].tramoPoblacional").value("MEDIANO"))
                .andExpect(jsonPath("$[?(@.slug=='" + slugNuevo + "')].estadoFacturacion").value("AL_DIA"))
                .andExpect(jsonPath("$[?(@.slug=='" + slugNuevo + "')].notaFacturacion",
                        hasItem(nullValue())))
                .andExpect(jsonPath("$[?(@.slug=='" + slugNuevo + "')].cantidadDeModulosContratados")
                        .value(0));
    }

    @Test
    @DisplayName("PATCH /comercial actualiza tramo, estado y nota, y el cambio persiste")
    void elPatchActualizaYPersisteLaInformacionComercial() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();

        mvc.perform(patch("/api/admin/municipios/" + SLUG + "/comercial")
                .session(plataforma)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"tramoPoblacional":"GRANDE","estadoFacturacion":"ATRASADO",
                         "notaFacturacion":"Esperando transferencia"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tramoPoblacional").value("GRANDE"))
                .andExpect(jsonPath("$.estadoFacturacion").value("ATRASADO"))
                .andExpect(jsonPath("$.notaFacturacion").value("Esperando transferencia"));

        mvc.perform(get("/api/admin/municipios").session(plataforma))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slug=='" + SLUG + "')].tramoPoblacional").value("GRANDE"))
                .andExpect(jsonPath("$[?(@.slug=='" + SLUG + "')].estadoFacturacion").value("ATRASADO"))
                .andExpect(jsonPath("$[?(@.slug=='" + SLUG + "')].notaFacturacion")
                        .value("Esperando transferencia"));
    }

    @Test
    @DisplayName("una nota null explícita limpia una nota que ya existía")
    void unaNotaNulaLimpiaLaNotaExistente() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();

        mvc.perform(patch("/api/admin/municipios/" + SLUG + "/comercial")
                .session(plataforma)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"tramoPoblacional":"CHICO","estadoFacturacion":"AL_DIA",
                         "notaFacturacion":"Nota que después se limpia"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notaFacturacion").value("Nota que después se limpia"));

        mvc.perform(patch("/api/admin/municipios/" + SLUG + "/comercial")
                .session(plataforma)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"tramoPoblacional":"CHICO","estadoFacturacion":"AL_DIA",
                         "notaFacturacion":null}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notaFacturacion").doesNotExist());
    }

    @Test
    @DisplayName("un tramo poblacional inexistente se rechaza con 400")
    void unTramoPoblacionalInexistenteSeRechaza() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();

        mvc.perform(patch("/api/admin/municipios/" + SLUG + "/comercial")
                .session(plataforma)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"tramoPoblacional":"ENORME","estadoFacturacion":"AL_DIA","notaFacturacion":null}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("un estado de facturación inexistente se rechaza con 400")
    void unEstadoDeFacturacionInexistenteSeRechaza() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();

        mvc.perform(patch("/api/admin/municipios/" + SLUG + "/comercial")
                .session(plataforma)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"tramoPoblacional":"MEDIANO","estadoFacturacion":"MOROSO","notaFacturacion":null}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH sobre un slug inexistente devuelve el mismo código que el resto de la "
            + "API de administración para 'no existe el municipio'")
    void elPatchSobreUnSlugInexistenteDaElMismoCodigoQueElRestoDeLaApi() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();

        mvc.perform(get("/api/admin/municipios/no-existe-este-municipio/modulos").session(plataforma))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/api/admin/municipios/no-existe-este-municipio/comercial")
                .session(plataforma)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"tramoPoblacional":"MEDIANO","estadoFacturacion":"AL_DIA","notaFacturacion":null}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("cantidadDeModulosContratados refleja config, no un valor cacheado")
    void laCantidadDeModulosContratadosReflejaLaConfiguracionReal() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();

        mvc.perform(get("/api/admin/municipios").session(plataforma))
                .andExpect(jsonPath("$[?(@.slug=='" + SLUG + "')].cantidadDeModulosContratados").value(0));

        mvc.perform(put("/api/admin/municipios/" + SLUG + "/modulos")
                .session(plataforma)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"modulos":["ejemplo"]}"""))
                .andExpect(status().isOk());

        mvc.perform(get("/api/admin/municipios").session(plataforma))
                .andExpect(jsonPath("$[?(@.slug=='" + SLUG + "')].cantidadDeModulosContratados").value(1));
    }

    @Test
    @DisplayName("solo una sesión de usuario de plataforma puede operar el contrato de un municipio")
    void soloUnaSesionDePlataformaPuedeOperarElContrato() throws Exception {
        String cuerpo = """
                {"tramoPoblacional":"GRANDE","estadoFacturacion":"ATRASADO","notaFacturacion":null}""";

        mvc.perform(patch("/api/admin/municipios/" + SLUG + "/comercial")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
                .andExpect(status().isUnauthorized());

        MockHttpSession sesionDeMunicipio = iniciarSesionDeAdministrador(SLUG);
        mvc.perform(patch("/api/admin/municipios/" + SLUG + "/comercial")
                .session(sesionDeMunicipio)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
                .andExpect(status().isUnauthorized());
    }
}
