package ar.com.ciudaddigital.tenants;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import ar.com.ciudaddigital.SoporteDeIntegracion;

/**
 * Alta de un municipio de punta a punta (ADR 0005) y aislamiento de los
 * datos que quedan en su propia base (ADR 0001).
 */
class AltaDeMunicipioTest extends SoporteDeIntegracion {

    @BeforeEach
    void municipiosDePrueba() throws Exception {
        asegurarMunicipio("sanmartin", "San Martín", "#1B4F9C");
        asegurarMunicipio("moron", "Morón", "#1F6B4A");
    }

    @Test
    @DisplayName("un municipio dado de alta queda activo, con su base propia migrada")
    void elAltaDejaElMunicipioActivo() throws Exception {
        asegurarMunicipio("tandil", "Tandil", "#5A2D82");

        mvc.perform(get("/api/admin/municipios").session(iniciarSesionDePlataforma()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slug=='tandil')].estado").value("ACTIVO"))
                .andExpect(jsonPath("$[?(@.slug=='tandil')].nombreBaseDatos").value("tenant_tandil"))
                // La versión se consulta contra la base del municipio: si
                // las migraciones no hubieran corrido —o hubieran corrido a
                // medias— no coincidiría con la última que existe.
                .andExpect(jsonPath("$[?(@.slug=='tandil')].versionDeEsquema")
                        .value(ultimaVersionDeEsquemaDeTenant()));
    }

    @Test
    @DisplayName("el municipio recién dado de alta atiende en su subdominio")
    void elMunicipioNuevoAtiendeEnSuSubdominio() throws Exception {
        asegurarMunicipio("olavarria", "Olavarría", "#00695C");

        mvc.perform(get(portalDe("olavarria", "/api/tenant/tema")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreMunicipio").value("Olavarría"));
    }

    @Test
    @DisplayName("aislamiento: cada municipio lee los datos de su propia base")
    void cadaMunicipioLeeSuPropiaBase() throws Exception {
        mvc.perform(get(portalDe("sanmartin", "/api/municipio/contacto")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("contacto@sanmartin.gob.ar"))
                .andExpect(content().string(not(containsString("moron"))));

        mvc.perform(get(portalDe("moron", "/api/municipio/contacto")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("contacto@moron.gob.ar"))
                .andExpect(content().string(not(containsString("sanmartin"))));
    }

    @Test
    @DisplayName("no se puede dar de alta dos veces el mismo municipio")
    void elSlugNoSePuedeRepetir() throws Exception {
        mvc.perform(post("/api/admin/municipios")
                .session(iniciarSesionDePlataforma())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"slug":"sanmartin","nombreMunicipio":"Otro San Martín",
                         "direccion":"x","telefono":"y","email":"z@x.ar"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("un slug con caracteres peligrosos se rechaza antes de tocar la base")
    void elSlugSeValida() throws Exception {
        mvc.perform(post("/api/admin/municipios")
                .session(iniciarSesionDePlataforma())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"slug":"ma\\"; drop database postgres; --","nombreMunicipio":"Malicioso",
                         "direccion":"x","telefono":"y","email":"z@x.ar"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("la API de administración exige sesión de usuario de plataforma")
    void laApiDeAdministracionEstaProtegida() throws Exception {
        mvc.perform(get("/api/admin/municipios"))
                .andExpect(status().isUnauthorized());

        MockHttpSession sesionSinLoguear = new MockHttpSession();
        mvc.perform(get("/api/admin/municipios").session(sesionSinLoguear))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("las credenciales de un municipio no sirven para operar la API de administración")
    void lasCredencialesDeMunicipioNoSonDePlataforma() throws Exception {
        MockHttpSession sesionDeMunicipio = iniciarSesionDeAdministrador("sanmartin");

        mvc.perform(get("/api/admin/municipios").session(sesionDeMunicipio))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("pedir datos de municipio sin municipio resuelto no devuelve nada de nadie")
    void sinTenantNoHayDatosDeMunicipio() throws Exception {
        mvc.perform(get("http://localhost/api/municipio/contacto"))
                .andExpect(status().isNotFound());
    }
}
