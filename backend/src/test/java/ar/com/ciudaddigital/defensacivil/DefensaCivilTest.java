package ar.com.ciudaddigital.defensacivil;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * Alta protegida, lectura pública, finalización de alertas y cambio de
 * estado de recursos de Defensa Civil (R27, ADR 0031).
 *
 * <p>Cada test fija explícitamente qué módulos tiene contratado cada
 * municipio antes de verificar nada, mismo criterio que
 * {@code EventosTest}/{@code ArboladoTest}: el contenedor de Postgres se
 * comparte entre clases de test.
 */
class DefensaCivilTest extends SoporteDeIntegracion {

    private static final String A = "sanisidro";
    private static final String B = "tigre";

    @BeforeEach
    void municipiosDePrueba() throws Exception {
        asegurarMunicipio(A, "San Isidro", "#1B5E20");
        asegurarMunicipio(B, "Tigre", "#B71C1C");
    }

    @Test
    @DisplayName("alta de alerta con el módulo contratado y el permiso responde 201 con la alerta VIGENTE")
    void altaDeAlertaConElPermisoQuedaVigente() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "defensacivil");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        mvc.perform(publicarAlerta(A, administradorDeA, """
                {"tipo":"METEOROLOGICA","nivel":"NARANJA","titulo":"Tormenta fuerte con caída de granizo",
                 "descripcion":"Se espera una tormenta severa en las próximas horas.",
                 "recomendaciones":"Evitar circular, retirar objetos sueltos de balcones y terrazas.",
                 "zonaAfectada":"Zona norte del partido"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.tipo").value("METEOROLOGICA"))
                .andExpect(jsonPath("$.nivel").value("NARANJA"))
                .andExpect(jsonPath("$.estado").value("VIGENTE"))
                .andExpect(jsonPath("$.publicadoPorNombre").value("Administrador de San Isidro"))
                .andExpect(jsonPath("$.publicadoPorEmail").value(emailDelAdministrador(A)));
    }

    @Test
    @DisplayName("alta de alerta sin el permiso defensacivil.gestionar se rechaza con 403 sin código")
    void altaDeAlertaSinElPermisoSeRechaza() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "defensacivil");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        MockHttpSession agenteSinPermiso = crearUsuarioConSoloOtroPermiso(
                A, administradorDeA, "sin-defensacivil-alertas", "agente-sin-defensacivil-alertas@sanisidro.gob.ar");

        mvc.perform(publicarAlerta(A, agenteSinPermiso, """
                {"tipo":"INCENDIO","nivel":"ROJO","titulo":"Alerta sin permiso",
                 "descripcion":"Descripción.","recomendaciones":"Recomendaciones.","zonaAfectada":null}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").doesNotExist());
    }

    @Test
    @DisplayName("listado público de alertas sin sesión, con filtros por tipo, nivel, estado y q, "
            + "por separado y combinados, filtro inválido da 400")
    void listadoPublicoDeAlertasConFiltros() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "defensacivil");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        String sufijo = UUID.randomUUID().toString();
        String meteorologica = "Alerta meteorológica " + sufijo;
        String incendio = "Alerta de incendio " + sufijo;

        Long idMeteorologica = publicarAlertaYObtenerId(
                A, administradorDeA, meteorologica, "METEOROLOGICA", "NARANJA");
        publicarAlertaYObtenerId(A, administradorDeA, incendio, "INCENDIO", "ROJO");

        mvc.perform(finalizar(A, administradorDeA, idMeteorologica))
                .andExpect(status().isOk());

        // Por tipo.
        mvc.perform(get(portalDe(A, "/api/defensacivil/alertas?tipo=METEOROLOGICA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.titulo == '" + meteorologica + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.titulo == '" + incendio + "')]").isEmpty());

        // Por nivel.
        mvc.perform(get(portalDe(A, "/api/defensacivil/alertas?nivel=ROJO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.titulo == '" + incendio + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.titulo == '" + meteorologica + "')]").isEmpty());

        // Por estado: una FINALIZADA, la otra sigue VIGENTE.
        mvc.perform(get(portalDe(A, "/api/defensacivil/alertas?estado=FINALIZADA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.titulo == '" + meteorologica + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.titulo == '" + incendio + "')]").isEmpty());
        mvc.perform(get(portalDe(A, "/api/defensacivil/alertas?estado=VIGENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.titulo == '" + incendio + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.titulo == '" + meteorologica + "')]").isEmpty());

        // Por texto: matchea título o descripción.
        mvc.perform(get(portalDe(A, "/api/defensacivil/alertas?q=" + sufijo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.titulo == '" + meteorologica + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.titulo == '" + incendio + "')]").isNotEmpty());

        // Combinados.
        mvc.perform(get(portalDe(A, "/api/defensacivil/alertas?nivel=ROJO&estado=VIGENTE&q=" + sufijo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.titulo == '" + incendio + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.titulo == '" + meteorologica + "')]").isEmpty());

        // Filtros inválidos dan 400, no "sin filtro".
        mvc.perform(get(portalDe(A, "/api/defensacivil/alertas?tipo=INEXISTENTE")))
                .andExpect(status().isBadRequest());
        mvc.perform(get(portalDe(A, "/api/defensacivil/alertas?nivel=INEXISTENTE")))
                .andExpect(status().isBadRequest());
        mvc.perform(get(portalDe(A, "/api/defensacivil/alertas?estado=INEXISTENTE")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("finalización exitosa VIGENTE → FINALIZADA, finalizar dos veces o pedir VIGENTE da 400")
    void finalizacionYTransicionesInvalidas() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "defensacivil");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        Long id = publicarAlertaYObtenerId(
                A, administradorDeA, "Alerta a finalizar " + UUID.randomUUID(), "OLA_DE_CALOR", "AMARILLO");

        mvc.perform(finalizar(A, administradorDeA, id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FINALIZADA"));

        // Ya FINALIZADA: volver a finalizar da 400.
        mvc.perform(finalizar(A, administradorDeA, id))
                .andExpect(status().isBadRequest());

        // Pedir "volver" a VIGENTE tampoco es una transición válida.
        mvc.perform(patch(portalDe(A, "/api/defensacivil/alertas/" + id + "/estado"))
                .session(administradorDeA)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estadoNuevo\":\"VIGENTE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("alta de recurso con el módulo contratado y el permiso responde 201 con el recurso ACTIVO")
    void altaDeRecursoConElPermisoQuedaActivo() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "defensacivil");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        mvc.perform(registrarRecurso(A, administradorDeA, """
                {"tipo":"REFUGIO","nombre":"Polideportivo Municipal","direccion":"Av. Libertador 1200",
                 "capacidad":200,"telefonoContacto":"011-4444-5555","descripcion":"Refugio principal."}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.tipo").value("REFUGIO"))
                .andExpect(jsonPath("$.nombre").value("Polideportivo Municipal"))
                .andExpect(jsonPath("$.capacidad").value(200))
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.publicadoPorNombre").value("Administrador de San Isidro"))
                .andExpect(jsonPath("$.publicadoPorEmail").value(emailDelAdministrador(A)));
    }

    @Test
    @DisplayName("alta de recurso sin el permiso defensacivil.gestionar se rechaza con 403 sin código")
    void altaDeRecursoSinElPermisoSeRechaza() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "defensacivil");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        MockHttpSession agenteSinPermiso = crearUsuarioConSoloOtroPermiso(
                A, administradorDeA, "sin-defensacivil-recursos", "agente-sin-defensacivil-recursos@sanisidro.gob.ar");

        mvc.perform(registrarRecurso(A, agenteSinPermiso, """
                {"tipo":"PUNTO_DE_ENCUENTRO","nombre":"Recurso sin permiso","direccion":"Calle 1",
                 "capacidad":null,"telefonoContacto":null,"descripcion":null}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").doesNotExist());
    }

    @Test
    @DisplayName("alta de recurso con capacidad negativa da 400")
    void altaDeRecursoConCapacidadNegativa() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "defensacivil");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        mvc.perform(registrarRecurso(A, administradorDeA, """
                {"tipo":"CENTRO_DE_ACOPIO","nombre":"Recurso con capacidad negativa","direccion":"Calle 1",
                 "capacidad":-5,"telefonoContacto":null,"descripcion":null}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("listado público de recursos sin sesión, con filtros por tipo, estado y q, "
            + "por separado y combinados")
    void listadoPublicoDeRecursosConFiltros() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "defensacivil");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        String sufijo = UUID.randomUUID().toString();
        String refugio = "Refugio " + sufijo;
        String puntoDeEncuentro = "Punto de encuentro " + sufijo;

        Long idRefugio = registrarRecursoYObtenerId(A, administradorDeA, refugio, "REFUGIO");
        registrarRecursoYObtenerId(A, administradorDeA, puntoDeEncuentro, "PUNTO_DE_ENCUENTRO");

        mvc.perform(actualizarEstadoDeRecurso(A, administradorDeA, idRefugio, "INACTIVO"))
                .andExpect(status().isOk());

        // Por tipo.
        mvc.perform(get(portalDe(A, "/api/defensacivil/recursos?tipo=REFUGIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + refugio + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + puntoDeEncuentro + "')]").isEmpty());

        // Por estado: uno INACTIVO, el otro sigue ACTIVO.
        mvc.perform(get(portalDe(A, "/api/defensacivil/recursos?estado=INACTIVO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + refugio + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + puntoDeEncuentro + "')]").isEmpty());
        mvc.perform(get(portalDe(A, "/api/defensacivil/recursos?estado=ACTIVO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + puntoDeEncuentro + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + refugio + "')]").isEmpty());

        // Por texto: matchea nombre o dirección.
        mvc.perform(get(portalDe(A, "/api/defensacivil/recursos?q=" + sufijo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + refugio + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + puntoDeEncuentro + "')]").isNotEmpty());

        // Combinados.
        mvc.perform(get(portalDe(A, "/api/defensacivil/recursos?tipo=REFUGIO&estado=INACTIVO&q=" + sufijo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + refugio + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + puntoDeEncuentro + "')]").isEmpty());
    }

    @Test
    @DisplayName("cambio de estado de recurso en ambos sentidos, pedir el mismo estado da 400")
    void cambioDeEstadoDeRecursoEnAmbosSentidos() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "defensacivil");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        Long id = registrarRecursoYObtenerId(
                A, administradorDeA, "Recurso de prueba " + UUID.randomUUID(), "OTRO");

        // Ya está ACTIVO: pedir ACTIVO de nuevo da 400.
        mvc.perform(actualizarEstadoDeRecurso(A, administradorDeA, id, "ACTIVO"))
                .andExpect(status().isBadRequest());

        mvc.perform(actualizarEstadoDeRecurso(A, administradorDeA, id, "INACTIVO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INACTIVO"));

        // Ya está INACTIVO: pedir INACTIVO de nuevo da 400.
        mvc.perform(actualizarEstadoDeRecurso(A, administradorDeA, id, "INACTIVO"))
                .andExpect(status().isBadRequest());

        mvc.perform(actualizarEstadoDeRecurso(A, administradorDeA, id, "ACTIVO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVO"));
    }

    @Test
    @DisplayName("sin el módulo contratado, alta/listado/cambio de estado de ambas entidades rechazan con 403 "
            + "MODULO_NO_CONTRATADO, incluso sin sesión y con datos válidos")
    void sinModuloContratadoRechazaTodasLasRutas() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(B, plataforma);
        MockHttpSession administradorDeB = iniciarSesionDeAdministrador(B);

        mvc.perform(publicarAlerta(B, administradorDeB, """
                {"tipo":"OTRA","nivel":"AMARILLO","titulo":"Alerta sin módulo",
                 "descripcion":"Descripción.","recomendaciones":"Recomendaciones.","zonaAfectada":null}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("defensacivil"));

        mvc.perform(get(portalDe(B, "/api/defensacivil/alertas")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("defensacivil"));

        mvc.perform(finalizar(B, administradorDeB, 1L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("defensacivil"));

        mvc.perform(registrarRecurso(B, administradorDeB, """
                {"tipo":"REFUGIO","nombre":"Recurso sin módulo","direccion":"Calle 1",
                 "capacidad":null,"telefonoContacto":null,"descripcion":null}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("defensacivil"));

        mvc.perform(get(portalDe(B, "/api/defensacivil/recursos")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("defensacivil"));

        mvc.perform(actualizarEstadoDeRecurso(B, administradorDeB, 1L, "INACTIVO"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("defensacivil"));
    }

    @Test
    @DisplayName("aislamiento: una alerta publicada en un municipio no es visible ni finalizable desde otro")
    void aislamientoDeAlertasEntreTenants() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "defensacivil");
        fijarModulos(B, plataforma, "defensacivil");

        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);
        MockHttpSession administradorDeB = iniciarSesionDeAdministrador(B);

        String sufijo = UUID.randomUUID().toString();
        String tituloDeA = "Alerta de San Isidro " + sufijo;
        Long idDeA = publicarAlertaYObtenerId(A, administradorDeA, tituloDeA, "INUNDACION", "ROJO");

        // No aparece en el listado del otro municipio, con ni sin filtros.
        mvc.perform(get(portalDe(B, "/api/defensacivil/alertas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.titulo == '" + tituloDeA + "')]").isEmpty());
        mvc.perform(get(portalDe(B, "/api/defensacivil/alertas?estado=VIGENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.titulo == '" + tituloDeA + "')]").isEmpty());
        mvc.perform(get(portalDe(B, "/api/defensacivil/alertas?q=" + sufijo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.titulo == '" + tituloDeA + "')]").isEmpty());

        // Sigue visible en el listado del municipio dueño.
        mvc.perform(get(portalDe(A, "/api/defensacivil/alertas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.titulo == '" + tituloDeA + "')]").isNotEmpty());

        // El id de la alerta de A no existe en la base de B: PATCH da 404,
        // no "la encuentra y la finaliza" (garantía real: el datasource
        // ruteado por tenant, no una validación de aplicación).
        mvc.perform(finalizar(B, administradorDeB, idDeA))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("aislamiento: un recurso registrado en un municipio no es visible ni actualizable desde otro")
    void aislamientoDeRecursosEntreTenants() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "defensacivil");
        fijarModulos(B, plataforma, "defensacivil");

        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);
        MockHttpSession administradorDeB = iniciarSesionDeAdministrador(B);

        String sufijo = UUID.randomUUID().toString();
        String nombreDeA = "Recurso de San Isidro " + sufijo;
        Long idDeA = registrarRecursoYObtenerId(A, administradorDeA, nombreDeA, "CENTRO_DE_ACOPIO");

        // No aparece en el listado del otro municipio, con ni sin filtros.
        mvc.perform(get(portalDe(B, "/api/defensacivil/recursos")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeA + "')]").isEmpty());
        mvc.perform(get(portalDe(B, "/api/defensacivil/recursos?estado=ACTIVO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeA + "')]").isEmpty());
        mvc.perform(get(portalDe(B, "/api/defensacivil/recursos?q=" + sufijo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeA + "')]").isEmpty());

        // Sigue visible en el listado del municipio dueño.
        mvc.perform(get(portalDe(A, "/api/defensacivil/recursos")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeA + "')]").isNotEmpty());

        // El id del recurso de A no existe en la base de B: PATCH da 404,
        // no "lo encuentra y lo actualiza" (garantía real: el datasource
        // ruteado por tenant, no una validación de aplicación).
        mvc.perform(actualizarEstadoDeRecurso(B, administradorDeB, idDeA, "INACTIVO"))
                .andExpect(status().isNotFound());
    }

    private MockHttpServletRequestBuilder publicarAlerta(String subdominio, MockHttpSession sesion, String cuerpo) {
        return post(portalDe(subdominio, "/api/defensacivil/alertas"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private MockHttpServletRequestBuilder finalizar(String subdominio, MockHttpSession sesion, Long id) {
        return patch(portalDe(subdominio, "/api/defensacivil/alertas/" + id + "/estado"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estadoNuevo\":\"FINALIZADA\"}");
    }

    private MockHttpServletRequestBuilder registrarRecurso(String subdominio, MockHttpSession sesion, String cuerpo) {
        return post(portalDe(subdominio, "/api/defensacivil/recursos"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private MockHttpServletRequestBuilder actualizarEstadoDeRecurso(
            String subdominio, MockHttpSession sesion, Long id, String estadoNuevo) {

        return patch(portalDe(subdominio, "/api/defensacivil/recursos/" + id + "/estado"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estadoNuevo\":\"" + estadoNuevo + "\"}");
    }

    private Long publicarAlertaYObtenerId(
            String subdominio, MockHttpSession sesionAdmin, String titulo, String tipo, String nivel)
            throws Exception {

        MvcResult resultado = mvc.perform(publicarAlerta(subdominio, sesionAdmin, """
                {"tipo":"%s","nivel":"%s","titulo":"%s",
                 "descripcion":"Descripción de prueba.","recomendaciones":"Recomendaciones de prueba.",
                 "zonaAfectada":null}"""
                .formatted(tipo, nivel, titulo)))
                .andExpect(status().isCreated())
                .andReturn();

        return ((Number) JsonPath.read(resultado.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private Long registrarRecursoYObtenerId(
            String subdominio, MockHttpSession sesionAdmin, String nombre, String tipo) throws Exception {

        MvcResult resultado = mvc.perform(registrarRecurso(subdominio, sesionAdmin, """
                {"tipo":"%s","nombre":"%s","direccion":"Dirección de prueba",
                 "capacidad":null,"telefonoContacto":null,"descripcion":null}"""
                .formatted(tipo, nombre)))
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
     * {@code defensacivil.gestionar} (ADR 0011: el municipio compone sus
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
                        {"codigo":"%s","nombre":"Sin permiso de defensa civil",
                         "descripcion":"No puede gestionar defensa civil.","permisos":[]}"""
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
