package ar.com.ciudaddigital.desarrollosocial;

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
 * Catálogo de programas sociales, alta pública de inscripciones,
 * seguimiento anónimo por token, y bandeja de gestión de inscripciones
 * (R21, ADR 0025).
 *
 * <p>Cada test fija explícitamente qué módulos tiene contratado cada
 * municipio antes de verificar nada, mismo criterio que
 * {@code ObrasTest}/{@code ArboladoTest}: el contenedor de Postgres se
 * comparte entre clases de test.
 */
class DesarrolloSocialTest extends SoporteDeIntegracion {

    private static final String A = "lanus";
    private static final String B = "avellaneda";

    @BeforeEach
    void municipiosDePrueba() throws Exception {
        asegurarMunicipio(A, "Lanús", "#1B5E20");
        asegurarMunicipio(B, "Avellaneda", "#B71C1C");
    }

    @Test
    @DisplayName("alta de programa con gestionarProgramas responde 201 con el programa ABIERTO")
    void altaDeProgramaConElPermisoQuedaAbierto() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "desarrollosocial");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        mvc.perform(registrarPrograma(A, administradorDeA, """
                {"nombre":"Refuerzo alimentario municipal","descripcion":"Asistencia alimentaria mensual.",
                 "criteriosDeElegibilidad":"Residir en el municipio."}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.nombre").value("Refuerzo alimentario municipal"))
                .andExpect(jsonPath("$.estado").value("ABIERTO"))
                .andExpect(jsonPath("$.publicadoPorNombre").value("Administrador de Lanús"))
                .andExpect(jsonPath("$.publicadoPorEmail").value(emailDelAdministrador(A)));
    }

    @Test
    @DisplayName("alta de programa sin desarrollosocial.gestionarProgramas se rechaza con 403 sin código")
    void altaDeProgramaSinElPermisoSeRechaza() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "desarrollosocial");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        MockHttpSession usuarioSinPermiso =
                crearUsuarioConPermisos(A, administradorDeA, "sin-programas@lanus.gob.ar");

        mvc.perform(registrarPrograma(A, usuarioSinPermiso, """
                {"nombre":"Programa sin permiso","descripcion":null,"criteriosDeElegibilidad":null}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").doesNotExist());
    }

    @Test
    @DisplayName("listado público de programas con filtros por estado y q, por separado y combinados")
    void listadoPublicoDeProgramasConFiltros() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "desarrollosocial");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        String sufijo = UUID.randomUUID().toString();
        String nombreAbierto = "Programa abierto " + sufijo;
        String nombreCerrado = "Programa cerrado " + sufijo;

        registrarProgramaSimple(A, administradorDeA, nombreAbierto);
        Long idCerrado = registrarProgramaSimple(A, administradorDeA, nombreCerrado);
        mvc.perform(actualizarEstadoDePrograma(A, administradorDeA, idCerrado, "CERRADO"))
                .andExpect(status().isOk());

        // Por estado.
        mvc.perform(get(portalDe(A, "/api/desarrollosocial/programas?estado=ABIERTO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreAbierto + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreCerrado + "')]").isEmpty());

        // Por texto.
        mvc.perform(get(portalDe(A, "/api/desarrollosocial/programas?q=" + sufijo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreAbierto + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreCerrado + "')]").isNotEmpty());

        // Combinados.
        mvc.perform(get(portalDe(A, "/api/desarrollosocial/programas?estado=CERRADO&q=" + sufijo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreCerrado + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreAbierto + "')]").isEmpty());

        // Estado inválido da 400, no "sin filtro".
        mvc.perform(get(portalDe(A, "/api/desarrollosocial/programas?estado=INEXISTENTE")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("cambio de estado de programa en ambos sentidos, ABIERTO -> CERRADO -> ABIERTO")
    void cambioDeEstadoDePrograma() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "desarrollosocial");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        Long id = registrarProgramaSimple(A, administradorDeA, "Programa de prueba " + UUID.randomUUID());

        mvc.perform(actualizarEstadoDePrograma(A, administradorDeA, id, "CERRADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CERRADO"));

        mvc.perform(actualizarEstadoDePrograma(A, administradorDeA, id, "ABIERTO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ABIERTO"));
    }

    @Test
    @DisplayName("alta de inscripción pública contra un programa ABIERTO queda RECIBIDA y devuelve token")
    void altaDeInscripcionContraProgramaAbierto() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "desarrollosocial");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        Long idPrograma = registrarProgramaSimple(A, administradorDeA, "Programa abierto " + UUID.randomUUID());

        mvc.perform(inscribir(A, idPrograma, "Juana Pérez", "30111222", "juana@vecina.ar", 3,
                "EMPLEO_INFORMAL", "Necesito ayuda urgente."))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.estado").value("RECIBIDA"))
                .andExpect(jsonPath("$.tokenDeSeguimiento").isNotEmpty())
                // Minimización: la respuesta del alta no reexpone lo que el vecino envió.
                .andExpect(jsonPath("$.nombreSolicitante").doesNotExist())
                .andExpect(jsonPath("$.dniSolicitante").doesNotExist())
                .andExpect(jsonPath("$.contacto").doesNotExist())
                .andExpect(jsonPath("$.situacionDeclarada").doesNotExist());
    }

    @Test
    @DisplayName("alta de inscripción contra un programa CERRADO da 400 con mensaje genérico")
    void altaDeInscripcionContraProgramaCerrado() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "desarrollosocial");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        Long idPrograma = registrarProgramaSimple(A, administradorDeA, "Programa cerrado " + UUID.randomUUID());
        mvc.perform(actualizarEstadoDePrograma(A, administradorDeA, idPrograma, "CERRADO"))
                .andExpect(status().isOk());

        mvc.perform(inscribir(A, idPrograma, "Juan Gómez", "30222333", "juan@vecino.ar", 2,
                "DESOCUPADO", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("El programa no existe o no admite inscripciones en este momento."));
    }

    @Test
    @DisplayName("alta de inscripción contra un programaId inexistente da 400 con el mismo mensaje que contra uno cerrado")
    void altaDeInscripcionContraProgramaInexistente() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "desarrollosocial");

        mvc.perform(inscribir(A, 999999L, "Juan Gómez", "30222333", "juan@vecino.ar", 2, "DESOCUPADO", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("El programa no existe o no admite inscripciones en este momento."));
    }

    @Test
    @DisplayName("seguimiento por token válido devuelve el shape minimizado; token inválido da 404 genérico")
    void seguimientoPorToken() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "desarrollosocial");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        String nombrePrograma = "Programa con seguimiento " + UUID.randomUUID();
        Long idPrograma = registrarProgramaSimple(A, administradorDeA, nombrePrograma);

        MvcResult resultado = mvc.perform(inscribir(A, idPrograma, "Ana Ruiz", "30333444", "ana@vecina.ar", 4,
                "JUBILADO_O_PENSIONADO", "Vivo sola con mis nietos."))
                .andExpect(status().isCreated())
                .andReturn();

        String token = JsonPath.read(resultado.getResponse().getContentAsString(), "$.tokenDeSeguimiento");

        mvc.perform(get(portalDe(A, "/api/desarrollosocial/inscripciones/seguimiento/" + token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombrePrograma").value(nombrePrograma))
                .andExpect(jsonPath("$.estado").value("RECIBIDA"))
                .andExpect(jsonPath("$.comentarioDeResolucion").doesNotExist())
                // Minimización: nunca vuelve a exponer lo que el vecino ya tiene.
                .andExpect(jsonPath("$.nombreSolicitante").doesNotExist())
                .andExpect(jsonPath("$.dniSolicitante").doesNotExist())
                .andExpect(jsonPath("$.contacto").doesNotExist())
                .andExpect(jsonPath("$.cantidadIntegrantesGrupoFamiliar").doesNotExist())
                .andExpect(jsonPath("$.situacionDeclarada").doesNotExist())
                .andExpect(jsonPath("$.comentarioAdicional").doesNotExist());

        mvc.perform(get(portalDe(A, "/api/desarrollosocial/inscripciones/seguimiento/token-inventado")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("No encontramos una inscripción con ese código."));
    }

    @Test
    @DisplayName("GET de inscripciones con gestionarProgramas pero sin revisarInscripciones da 403 — barrera central de la rebanada")
    void listadoDeInscripcionesSinRevisarInscripcionesDaForbidden() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "desarrollosocial");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        MockHttpSession soloGestionaProgramas = crearUsuarioConPermisos(
                A, administradorDeA, "solo-programas@lanus.gob.ar", "desarrollosocial.gestionarProgramas");

        mvc.perform(get(portalDe(A, "/api/desarrollosocial/inscripciones")).session(soloGestionaProgramas))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").doesNotExist());

        // Sin sesión: 401, no 403 (mismo criterio que ReclamosTest, regla base
        // "anyRequest().authenticated()").
        mvc.perform(get(portalDe(A, "/api/desarrollosocial/inscripciones")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("circuito completo de transiciones de inscripción: RECIBIDA -> EN_EVALUACION -> APROBADA")
    void circuitoDeTransicionesAprobacion() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "desarrollosocial");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        Long idPrograma = registrarProgramaSimple(A, administradorDeA, "Programa aprobación " + UUID.randomUUID());
        Long idInscripcion = idDeInscripcion(mvc.perform(inscribir(A, idPrograma, "Lucía Díaz", "30444555",
                "lucia@vecina.ar", 1, "EMPLEO_FORMAL", null))
                .andExpect(status().isCreated())
                .andReturn());

        // RECIBIDA -> APROBADA directo no es válido.
        mvc.perform(actualizarEstadoDeInscripcion(A, administradorDeA, idInscripcion, "APROBADA", "Aprobado."))
                .andExpect(status().isBadRequest());

        mvc.perform(actualizarEstadoDeInscripcion(A, administradorDeA, idInscripcion, "EN_EVALUACION", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_EVALUACION"));

        // Aprobar sin comentario da 400.
        mvc.perform(actualizarEstadoDeInscripcion(A, administradorDeA, idInscripcion, "APROBADA", null))
                .andExpect(status().isBadRequest());
        mvc.perform(actualizarEstadoDeInscripcion(A, administradorDeA, idInscripcion, "APROBADA", "   "))
                .andExpect(status().isBadRequest());

        mvc.perform(actualizarEstadoDeInscripcion(
                A, administradorDeA, idInscripcion, "APROBADA", "Cumple los requisitos."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADA"))
                .andExpect(jsonPath("$.comentarioDeResolucion").value("Cumple los requisitos."))
                .andExpect(jsonPath("$.resueltoPorNombre").value("Administrador de Lanús"))
                .andExpect(jsonPath("$.resueltoPorEmail").value(emailDelAdministrador(A)));

        // APROBADA es terminal: cualquier transición desde ahí da 400.
        mvc.perform(actualizarEstadoDeInscripcion(A, administradorDeA, idInscripcion, "EN_EVALUACION", null))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("circuito completo de transiciones de inscripción: RECIBIDA -> EN_EVALUACION -> RECHAZADA")
    void circuitoDeTransicionesRechazo() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "desarrollosocial");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        Long idPrograma = registrarProgramaSimple(A, administradorDeA, "Programa rechazo " + UUID.randomUUID());
        Long idInscripcion = idDeInscripcion(mvc.perform(inscribir(A, idPrograma, "Marcos Sosa", "30555666",
                "marcos@vecino.ar", 2, "OTRO", null))
                .andExpect(status().isCreated())
                .andReturn());

        mvc.perform(actualizarEstadoDeInscripcion(A, administradorDeA, idInscripcion, "EN_EVALUACION", null))
                .andExpect(status().isOk());

        // Rechazar sin comentario da 400.
        mvc.perform(actualizarEstadoDeInscripcion(A, administradorDeA, idInscripcion, "RECHAZADA", null))
                .andExpect(status().isBadRequest());

        mvc.perform(actualizarEstadoDeInscripcion(
                A, administradorDeA, idInscripcion, "RECHAZADA", "No cumple los requisitos."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADA"))
                .andExpect(jsonPath("$.comentarioDeResolucion").value("No cumple los requisitos."));

        // RECHAZADA es terminal: cualquier transición desde ahí da 400.
        mvc.perform(actualizarEstadoDeInscripcion(A, administradorDeA, idInscripcion, "EN_EVALUACION", null))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("listado de gestión de inscripciones con revisarInscripciones ve todos los campos personales")
    void listadoDeInscripcionesParaGestion() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "desarrollosocial");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        Long idPrograma = registrarProgramaSimple(A, administradorDeA, "Programa bandeja " + UUID.randomUUID());
        String dni = "30666" + System.nanoTime() % 1000;
        mvc.perform(inscribir(A, idPrograma, "Rosa Medina", dni, "rosa@vecina.ar", 5, "DESOCUPADO", "Urgente"))
                .andExpect(status().isCreated());

        mvc.perform(get(portalDe(A, "/api/desarrollosocial/inscripciones?programaId=" + idPrograma))
                .session(administradorDeA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.dniSolicitante == '" + dni + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.dniSolicitante == '" + dni + "')].nombreSolicitante")
                        .value("Rosa Medina"));

        mvc.perform(get(portalDe(A, "/api/desarrollosocial/inscripciones?estado=RECIBIDA"))
                .session(administradorDeA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.dniSolicitante == '" + dni + "')]").isNotEmpty());

        mvc.perform(get(portalDe(A, "/api/desarrollosocial/inscripciones?estado=APROBADA"))
                .session(administradorDeA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.dniSolicitante == '" + dni + "')]").isEmpty());
    }

    @Test
    @DisplayName("sin el módulo contratado, todas las rutas rechazan con 403 MODULO_NO_CONTRATADO, "
            + "incluso sin sesión y con datos válidos")
    void sinModuloContratadoRechazaTodasLasRutas() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(B, plataforma);
        MockHttpSession administradorDeB = iniciarSesionDeAdministrador(B);

        mvc.perform(registrarPrograma(B, administradorDeB, """
                {"nombre":"Programa sin módulo","descripcion":null,"criteriosDeElegibilidad":null}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("desarrollosocial"));

        mvc.perform(get(portalDe(B, "/api/desarrollosocial/programas")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("desarrollosocial"));

        mvc.perform(actualizarEstadoDePrograma(B, administradorDeB, 1L, "CERRADO"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("desarrollosocial"));

        mvc.perform(inscribir(B, 1L, "Vecino sin módulo", "30777888", "sin@modulo.ar", 1, "OTRO", null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("desarrollosocial"));

        mvc.perform(get(portalDe(B, "/api/desarrollosocial/inscripciones/seguimiento/token-cualquiera")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("desarrollosocial"));

        mvc.perform(get(portalDe(B, "/api/desarrollosocial/inscripciones")).session(administradorDeB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("desarrollosocial"));

        mvc.perform(actualizarEstadoDeInscripcion(B, administradorDeB, 1L, "EN_EVALUACION", null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("desarrollosocial"));
    }

    @Test
    @DisplayName("aislamiento: un programa y una inscripción de un municipio no son visibles ni gestionables desde otro")
    void aislamientoEntreTenants() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "desarrollosocial");
        fijarModulos(B, plataforma, "desarrollosocial");

        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);
        MockHttpSession administradorDeB = iniciarSesionDeAdministrador(B);

        String sufijo = UUID.randomUUID().toString();
        String nombreDeA = "Programa de Lanús " + sufijo;
        Long idProgramaDeA = registrarProgramaSimple(A, administradorDeA, nombreDeA);

        MvcResult altaDeInscripcion = mvc.perform(inscribir(A, idProgramaDeA, "Vecina de Lanús", "30888999",
                "vecina@lanus.gob.ar", 2, "DESOCUPADO", null))
                .andExpect(status().isCreated())
                .andReturn();
        Long idInscripcionDeA = idDeInscripcion(altaDeInscripcion);
        String tokenDeA = JsonPath.read(altaDeInscripcion.getResponse().getContentAsString(), "$.tokenDeSeguimiento");

        // El programa de A no aparece en el listado público de B.
        mvc.perform(get(portalDe(B, "/api/desarrollosocial/programas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeA + "')]").isEmpty());

        // El id del programa de A no existe en la base de B: PATCH da 404.
        mvc.perform(actualizarEstadoDePrograma(B, administradorDeB, idProgramaDeA, "CERRADO"))
                .andExpect(status().isNotFound());

        // La inscripción de A no aparece en la bandeja de gestión de B.
        mvc.perform(get(portalDe(B, "/api/desarrollosocial/inscripciones")).session(administradorDeB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + idInscripcionDeA + ")]").isEmpty());

        // El token real de A no encuentra nada en la base de B: el hash vive
        // en la base de A, no hay fila con ese token_hash en la de B.
        mvc.perform(get(portalDe(B, "/api/desarrollosocial/inscripciones/seguimiento/" + tokenDeA)))
                .andExpect(status().isNotFound());

        // El id de la inscripción de A no existe en la base de B: PATCH da 404.
        mvc.perform(actualizarEstadoDeInscripcion(B, administradorDeB, idInscripcionDeA, "EN_EVALUACION", null))
                .andExpect(status().isNotFound());

        // Sigue todo visible/gestionable en el municipio dueño.
        mvc.perform(get(portalDe(A, "/api/desarrollosocial/programas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeA + "')]").isNotEmpty());
        mvc.perform(get(portalDe(A, "/api/desarrollosocial/inscripciones")).session(administradorDeA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + idInscripcionDeA + ")]").isNotEmpty());
    }

    private MockHttpServletRequestBuilder registrarPrograma(String subdominio, MockHttpSession sesion, String cuerpo) {
        return post(portalDe(subdominio, "/api/desarrollosocial/programas"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private Long registrarProgramaSimple(String subdominio, MockHttpSession sesionAdmin, String nombre)
            throws Exception {

        MvcResult resultado = mvc.perform(registrarPrograma(subdominio, sesionAdmin, """
                {"nombre":"%s","descripcion":null,"criteriosDeElegibilidad":null}""".formatted(nombre)))
                .andExpect(status().isCreated())
                .andReturn();

        return ((Number) JsonPath.read(resultado.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private MockHttpServletRequestBuilder actualizarEstadoDePrograma(
            String subdominio, MockHttpSession sesion, Long id, String estadoNuevo) {

        return patch(portalDe(subdominio, "/api/desarrollosocial/programas/" + id + "/estado"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estadoNuevo\":\"" + estadoNuevo + "\"}");
    }

    private MockHttpServletRequestBuilder inscribir(String subdominio, Long programaId, String nombreSolicitante,
            String dniSolicitante, String contacto, int cantidadIntegrantesGrupoFamiliar,
            String situacionDeclarada, String comentarioAdicional) {

        String comentarioJson = comentarioAdicional == null ? "null" : "\"" + comentarioAdicional + "\"";
        return post(portalDe(subdominio, "/api/desarrollosocial/inscripciones"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"programaId":%d,"nombreSolicitante":"%s","dniSolicitante":"%s","contacto":"%s",
                         "cantidadIntegrantesGrupoFamiliar":%d,"situacionDeclarada":"%s",
                         "comentarioAdicional":%s}"""
                        .formatted(programaId, nombreSolicitante, dniSolicitante, contacto,
                                cantidadIntegrantesGrupoFamiliar, situacionDeclarada, comentarioJson));
    }

    private Long idDeInscripcion(MvcResult resultado) throws Exception {
        return ((Number) JsonPath.read(resultado.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private MockHttpServletRequestBuilder actualizarEstadoDeInscripcion(
            String subdominio, MockHttpSession sesion, Long id, String estadoNuevo, String comentarioDeResolucion) {

        String comentarioJson = comentarioDeResolucion == null ? "null" : "\"" + comentarioDeResolucion + "\"";
        return patch(portalDe(subdominio, "/api/desarrollosocial/inscripciones/" + id + "/estado"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estadoNuevo\":\"%s\",\"comentarioDeResolucion\":%s}"
                        .formatted(estadoNuevo, comentarioJson));
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
     * Crea un usuario con un rol propio del municipio, con exactamente los
     * permisos indicados (ninguno si no se pasa ninguno), y abre su
     * sesión. Mismo criterio que {@code ObrasTest#crearUsuarioConSoloOtroPermiso},
     * generalizado para poder componer un usuario con
     * {@code desarrollosocial.gestionarProgramas} pero sin
     * {@code desarrollosocial.revisarInscripciones} (ADR 0025 §7).
     */
    private MockHttpSession crearUsuarioConPermisos(
            String subdominio, MockHttpSession sesionAdmin, String email, String... permisos) throws Exception {

        String listaDePermisos = String.join(",", Arrays.stream(permisos)
                .map(codigo -> "\"" + codigo + "\"").toList());

        String cuerpoDelRol = mvc.perform(post(portalDe(subdominio, "/api/roles"))
                .session(sesionAdmin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"codigo":"rol-de-prueba-%s","nombre":"Rol de prueba",
                         "descripcion":"Rol de prueba con permisos acotados.","permisos":[%s]}"""
                        .formatted(UUID.randomUUID(), listaDePermisos)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long idDelRol = ((Number) JsonPath.read(cuerpoDelRol, "$.id")).longValue();

        String password = "otra-contrasena-larga";
        mvc.perform(post(portalDe(subdominio, "/api/usuarios"))
                .session(sesionAdmin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombre":"Usuario de prueba","email":"%s","password":"%s","roles":[%d]}
                        """.formatted(email, password, idDelRol)))
                .andExpect(status().isCreated());

        return iniciarSesion(subdominio, email, password);
    }
}
