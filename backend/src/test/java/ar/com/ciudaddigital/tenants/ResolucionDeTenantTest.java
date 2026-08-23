package ar.com.ciudaddigital.tenants;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import ar.com.ciudaddigital.TestcontainersConfiguration;

/**
 * Resolución de municipio por host (ADR 0004) y aislamiento entre
 * municipios.
 *
 * <p>Los tenants de prueba (San Martín y Morón) los siembra la migración
 * V2 de la base de control.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ResolucionDeTenantTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("cada subdominio sirve la marca de su propio municipio")
    void cadaSubdominioSirveSuMarca() throws Exception {
        mvc.perform(get(URI.create("http://sanmartin.localhost/api/tenant/tema")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("sanmartin"))
                .andExpect(jsonPath("$.nombreMunicipio").value("San Martín"))
                .andExpect(jsonPath("$.tema.colorPrimario").value("#1B4F9C"));

        mvc.perform(get(URI.create("http://moron.localhost/api/tenant/tema")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("moron"))
                .andExpect(jsonPath("$.nombreMunicipio").value("Morón"))
                .andExpect(jsonPath("$.tema.colorPrimario").value("#1F6B4A"));
    }

    @Test
    @DisplayName("aislamiento: la respuesta de un municipio no filtra datos del otro")
    void ningunMunicipioVeDatosDelOtro() throws Exception {
        mvc.perform(get(URI.create("http://sanmartin.localhost/api/tenant/tema")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Morón"))))
                .andExpect(content().string(not(containsString("moron"))))
                .andExpect(content().string(not(containsString("#1F6B4A"))));

        mvc.perform(get(URI.create("http://moron.localhost/api/tenant/tema")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("San Martín"))))
                .andExpect(content().string(not(containsString("sanmartin"))))
                .andExpect(content().string(not(containsString("#1B4F9C"))));
    }

    @Test
    @DisplayName("un host sin municipio no cae a ningún tenant por defecto")
    void hostDesconocidoNoResuelve() throws Exception {
        mvc.perform(get(URI.create("http://noexiste.localhost/api/tenant/tema")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("el dominio base sin subdominio no es un municipio")
    void dominioBaseNoResuelve() throws Exception {
        mvc.perform(get(URI.create("http://localhost/api/tenant/tema")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("el host resuelve sin importar mayúsculas ni puerto")
    void elHostSeNormaliza() throws Exception {
        mvc.perform(get(URI.create("http://SanMartin.LOCALHOST:5173/api/tenant/tema")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("sanmartin"));
    }
}
