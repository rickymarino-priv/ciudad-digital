package ar.com.ciudaddigital.tenants;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ar.com.ciudaddigital.SoporteDeIntegracion;

/**
 * Resolución de municipio por host (ADR 0004) y aislamiento entre
 * municipios.
 */
class ResolucionDeTenantTest extends SoporteDeIntegracion {

    private static final String AZUL_SAN_MARTIN = "#1B4F9C";
    private static final String VERDE_MORON = "#1F6B4A";

    @BeforeEach
    void municipiosDePrueba() throws Exception {
        asegurarMunicipio("sanmartin", "San Martín", AZUL_SAN_MARTIN);
        asegurarMunicipio("moron", "Morón", VERDE_MORON);
    }

    @Test
    @DisplayName("cada subdominio sirve la marca de su propio municipio")
    void cadaSubdominioSirveSuMarca() throws Exception {
        mvc.perform(get(portalDe("sanmartin", "/api/tenant/tema")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("sanmartin"))
                .andExpect(jsonPath("$.nombreMunicipio").value("San Martín"))
                .andExpect(jsonPath("$.tema.colorPrimario").value(AZUL_SAN_MARTIN));

        mvc.perform(get(portalDe("moron", "/api/tenant/tema")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("moron"))
                .andExpect(jsonPath("$.nombreMunicipio").value("Morón"))
                .andExpect(jsonPath("$.tema.colorPrimario").value(VERDE_MORON));
    }

    @Test
    @DisplayName("aislamiento: la respuesta de un municipio no filtra datos del otro")
    void ningunMunicipioVeDatosDelOtro() throws Exception {
        mvc.perform(get(portalDe("sanmartin", "/api/tenant/tema")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Morón"))))
                .andExpect(content().string(not(containsString("moron"))))
                .andExpect(content().string(not(containsString(VERDE_MORON))));

        mvc.perform(get(portalDe("moron", "/api/tenant/tema")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("San Martín"))))
                .andExpect(content().string(not(containsString("sanmartin"))))
                .andExpect(content().string(not(containsString(AZUL_SAN_MARTIN))));
    }

    @Test
    @DisplayName("un host sin municipio no cae a ningún tenant por defecto")
    void hostDesconocidoNoResuelve() throws Exception {
        mvc.perform(get(portalDe("noexiste", "/api/tenant/tema")))
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
