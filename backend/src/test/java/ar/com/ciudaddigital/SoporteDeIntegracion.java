package ar.com.ciudaddigital;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.net.URI;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base de los tests de integración: levanta un Postgres real y apunta ahí
 * tanto la base de control como las bases de municipio.
 *
 * <p>Los municipios de prueba se dan de alta por la API de administración,
 * igual que en producción, en vez de sembrarse por migración. Así los
 * tests ejercitan el alta de verdad —creación de base, migraciones y
 * siembra— y no una versión simplificada que podría no coincidir.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class SoporteDeIntegracion {

    protected static final String TOKEN_ADMIN = "token-de-prueba";

    /**
     * Un único contenedor para toda la suite: cada alta crea una base de
     * datos real, y levantar un motor por clase de test sería carísimo.
     */
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mvc;

    @DynamicPropertySource
    static void configurarBases(DynamicPropertyRegistry registro) {
        registro.add("ciudad.control.url", POSTGRES::getJdbcUrl);
        registro.add("ciudad.control.usuario", POSTGRES::getUsername);
        registro.add("ciudad.control.password", POSTGRES::getPassword);

        registro.add("ciudad.tenants.servidor", SoporteDeIntegracion::servidorDeTenants);
        registro.add("ciudad.tenants.usuario", POSTGRES::getUsername);
        registro.add("ciudad.tenants.password", POSTGRES::getPassword);
        registro.add("ciudad.tenants.base-de-mantenimiento", () -> "postgres");
        registro.add("ciudad.tenants.tamano-de-pool", () -> 2);

        registro.add("ciudad.admin.token", () -> TOKEN_ADMIN);
    }

    private static String servidorDeTenants() {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/";
    }

    /**
     * Da de alta un municipio si todavía no existe.
     *
     * <p>El contenedor se comparte entre clases de test, así que el mismo
     * municipio puede haber sido creado por otra: repetir el alta fallaría
     * por duplicado.
     */
    protected void asegurarMunicipio(String slug, String nombre, String colorPrimario)
            throws Exception {

        String listado = mvc.perform(get("/api/admin/municipios").header("X-Admin-Token", TOKEN_ADMIN))
                .andReturn().getResponse().getContentAsString();

        if (listado.contains("\"slug\":\"" + slug + "\"")) {
            return;
        }

        String cuerpo = """
                {
                  "slug": "%s",
                  "nombreMunicipio": "%s",
                  "direccion": "Av. Siempreviva 742",
                  "telefono": "0800-%s",
                  "email": "contacto@%s.gob.ar",
                  "tema": {
                    "colorPrimario": "%s",
                    "colorPrimarioContraste": "#FFFFFF",
                    "colorAcento": "#8A5A00",
                    "colorFondo": "#F4F6FA",
                    "colorSuperficie": "#FFFFFF",
                    "colorTexto": "#16181D",
                    "colorTextoTenue": "#4A4F57",
                    "tipografia": "system-ui, sans-serif",
                    "logoUrl": "data:image/svg+xml;base64,PHN2Zy8+"
                  }
                }
                """.formatted(slug, nombre, slug, slug, colorPrimario);

        mvc.perform(post("/api/admin/municipios")
                .header("X-Admin-Token", TOKEN_ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
                .andExpect(result -> {
                    int estado = result.getResponse().getStatus();
                    if (estado != 201) {
                        throw new AssertionError("No se pudo dar de alta " + slug + ": HTTP "
                                + estado + " — " + result.getResponse().getContentAsString());
                    }
                });
    }

    /** Request al portal de un municipio, identificado por su subdominio. */
    protected static URI portalDe(String subdominio, String ruta) {
        return URI.create("http://" + subdominio + ".localhost" + ruta);
    }
}
