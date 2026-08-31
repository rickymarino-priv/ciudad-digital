package ar.com.ciudaddigital.turnos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

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
 * Catálogo de actividades municipales, franjas horarias con cupo, reserva
 * pública anónima con decremento atómico de cupo, y agenda de gestión de
 * reservas (R22, ADR 0026).
 *
 * <p>Cada test fija explícitamente qué módulos tiene contratado cada
 * municipio antes de verificar nada, mismo criterio que
 * {@code ObrasTest}/{@code ArboladoTest}/{@code DesarrolloSocialTest}: el
 * contenedor de Postgres se comparte entre clases de test.
 */
class TurnosTest extends SoporteDeIntegracion {

    private static final String A = "lanus";
    private static final String B = "avellaneda";

    @BeforeEach
    void municipiosDePrueba() throws Exception {
        asegurarMunicipio(A, "Lanús", "#1B5E20");
        asegurarMunicipio(B, "Avellaneda", "#B71C1C");
    }

    @Test
    @DisplayName("alta de actividad con turnos.gestionar responde 201 con la actividad ACTIVA")
    void altaDeActividadConElPermisoQuedaActiva() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "turnos");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        mvc.perform(publicarActividad(A, administradorDeA, """
                {"nombre":"Cancha de Fútbol 5","tipo":"DEPORTE","descripcion":"Polideportivo Municipal.",
                 "ubicacion":"Polideportivo Municipal"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.nombre").value("Cancha de Fútbol 5"))
                .andExpect(jsonPath("$.tipo").value("DEPORTE"))
                .andExpect(jsonPath("$.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.publicadoPorNombre").value("Administrador de Lanús"))
                .andExpect(jsonPath("$.publicadoPorEmail").value(emailDelAdministrador(A)));
    }

    @Test
    @DisplayName("alta de actividad sin turnos.gestionar se rechaza con 403 sin código")
    void altaDeActividadSinElPermisoSeRechaza() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "turnos");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        MockHttpSession usuarioSinPermiso = crearUsuarioConPermisos(A, administradorDeA, "sin-turnos@lanus.gob.ar");

        mvc.perform(publicarActividad(A, usuarioSinPermiso, """
                {"nombre":"Actividad sin permiso","tipo":"CULTURA","descripcion":null,"ubicacion":"Centro Cultural"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").doesNotExist());
    }

    @Test
    @DisplayName("listado público de actividades con filtros por tipo, estado y q, por separado y combinados")
    void listadoPublicoDeActividadesConFiltros() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "turnos");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        String sufijo = UUID.randomUUID().toString();
        String nombreDeporte = "Actividad deporte " + sufijo;
        String nombreCultura = "Actividad cultura " + sufijo;

        Long idDeporte = publicarActividadSimple(A, administradorDeA, nombreDeporte, "DEPORTE");
        Long idCultura = publicarActividadSimple(A, administradorDeA, nombreCultura, "CULTURA");
        mvc.perform(cambiarEstadoDeActividad(A, administradorDeA, idCultura, "INACTIVA"))
                .andExpect(status().isOk());

        // Por tipo.
        mvc.perform(get(portalDe(A, "/api/turnos/actividades?tipo=DEPORTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeporte + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreCultura + "')]").isEmpty());

        // Por estado.
        mvc.perform(get(portalDe(A, "/api/turnos/actividades?estado=INACTIVA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreCultura + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeporte + "')]").isEmpty());

        // Por texto.
        mvc.perform(get(portalDe(A, "/api/turnos/actividades?q=" + sufijo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeporte + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreCultura + "')]").isNotEmpty());

        // Combinados.
        mvc.perform(get(portalDe(A, "/api/turnos/actividades?tipo=CULTURA&estado=INACTIVA&q=" + sufijo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreCultura + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeporte + "')]").isEmpty());

        // Tipo/estado inválidos dan 400, no "sin filtro".
        mvc.perform(get(portalDe(A, "/api/turnos/actividades?tipo=INEXISTENTE")))
                .andExpect(status().isBadRequest());
        mvc.perform(get(portalDe(A, "/api/turnos/actividades?estado=INEXISTENTE")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("cambio de estado de actividad en ambos sentidos, ACTIVA -> INACTIVA -> ACTIVA")
    void cambioDeEstadoDeActividad() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "turnos");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        Long id = publicarActividadSimple(A, administradorDeA, "Actividad de prueba " + UUID.randomUUID(), "TURISMO");

        mvc.perform(cambiarEstadoDeActividad(A, administradorDeA, id, "INACTIVA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INACTIVA"));

        mvc.perform(cambiarEstadoDeActividad(A, administradorDeA, id, "ACTIVA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVA"));
    }

    @Test
    @DisplayName("alta de franja con datos válidos inicializa cupoDisponible en cupoTotal")
    void altaDeFranjaConDatosValidos() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "turnos");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        Long idActividad = publicarActividadSimple(A, administradorDeA, "Actividad con franja " + UUID.randomUUID(), "DEPORTE");

        mvc.perform(crearFranja(A, administradorDeA, idActividad, "2026-09-05", "10:00", "11:00", 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.actividadId").value(idActividad))
                .andExpect(jsonPath("$.cupoTotal").value(2))
                .andExpect(jsonPath("$.cupoDisponible").value(2));
    }

    @Test
    @DisplayName("alta de franja con horaFin anterior o igual a horaInicio da 400")
    void altaDeFranjaConHoraFinInvalida() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "turnos");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        Long idActividad = publicarActividadSimple(A, administradorDeA, "Actividad " + UUID.randomUUID(), "DEPORTE");

        mvc.perform(crearFranja(A, administradorDeA, idActividad, "2026-09-05", "11:00", "10:00", 2))
                .andExpect(status().isBadRequest());
        mvc.perform(crearFranja(A, administradorDeA, idActividad, "2026-09-05", "10:00", "10:00", 2))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("alta de franja con cupoTotal cero o negativo da 400")
    void altaDeFranjaConCupoInvalido() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "turnos");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        Long idActividad = publicarActividadSimple(A, administradorDeA, "Actividad " + UUID.randomUUID(), "DEPORTE");

        mvc.perform(crearFranja(A, administradorDeA, idActividad, "2026-09-05", "10:00", "11:00", 0))
                .andExpect(status().isBadRequest());
        mvc.perform(crearFranja(A, administradorDeA, idActividad, "2026-09-05", "10:00", "11:00", -1))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("alta de franja contra un actividadId inexistente da 404")
    void altaDeFranjaContraActividadInexistente() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "turnos");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        mvc.perform(crearFranja(A, administradorDeA, 999999L, "2026-09-05", "10:00", "11:00", 2))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("reserva pública contra una franja con cupo responde 201 y baja el cupo en 1")
    void reservaPublicaContraFranjaConCupo() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "turnos");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        String nombreActividad = "Cancha de Fútbol 5 " + UUID.randomUUID();
        Long idActividad = publicarActividadSimple(A, administradorDeA, nombreActividad, "DEPORTE");
        Long idFranja = crearFranjaSimple(A, administradorDeA, idActividad, "2026-09-12", "10:00", "11:00", 2);

        mvc.perform(reservar(A, idFranja, "Juana Pérez", "30111222", "juana@vecina.ar"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.nombreActividad").value(nombreActividad))
                .andExpect(jsonPath("$.fecha").value("2026-09-12"))
                .andExpect(jsonPath("$.cupoDisponibleRestante").value(1))
                // Minimización: la confirmación no reexpone lo que el vecino ya tiene.
                .andExpect(jsonPath("$.nombreSolicitante").doesNotExist())
                .andExpect(jsonPath("$.dniSolicitante").doesNotExist())
                .andExpect(jsonPath("$.contacto").doesNotExist());

        mvc.perform(get(portalDe(A, "/api/turnos/franjas?actividadId=" + idActividad)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + idFranja + ")].cupoDisponible").value(1));
    }

    @Test
    @DisplayName("reserva pública contra una actividad INACTIVA da 400")
    void reservaPublicaContraActividadInactiva() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "turnos");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        Long idActividad = publicarActividadSimple(A, administradorDeA, "Actividad inactiva " + UUID.randomUUID(), "TURISMO");
        Long idFranja = crearFranjaSimple(A, administradorDeA, idActividad, "2026-09-12", "10:00", "11:00", 2);
        mvc.perform(cambiarEstadoDeActividad(A, administradorDeA, idActividad, "INACTIVA"))
                .andExpect(status().isOk());

        mvc.perform(reservar(A, idFranja, "Juan Gómez", "30222333", "juan@vecino.ar"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("reserva pública contra un franjaId inexistente da 404")
    void reservaPublicaContraFranjaInexistente() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "turnos");

        mvc.perform(reservar(A, 999999L, "Juan Gómez", "30222333", "juan@vecino.ar"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET de reservas sin sesión da 401; con sesión pero sin turnos.gestionar da 403")
    void listadoDeReservasSinPermisoDaForbidden() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "turnos");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        Long idActividad = publicarActividadSimple(A, administradorDeA, "Actividad " + UUID.randomUUID(), "DEPORTE");
        Long idFranja = crearFranjaSimple(A, administradorDeA, idActividad, "2026-09-12", "10:00", "11:00", 2);

        mvc.perform(get(portalDe(A, "/api/turnos/reservas?franjaId=" + idFranja)))
                .andExpect(status().isUnauthorized());

        MockHttpSession usuarioSinPermiso = crearUsuarioConPermisos(A, administradorDeA, "sin-turnos-2@lanus.gob.ar");
        mvc.perform(get(portalDe(A, "/api/turnos/reservas?franjaId=" + idFranja)).session(usuarioSinPermiso))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").doesNotExist());
    }

    @Test
    @DisplayName("agenda de gestión de reservas devuelve nombre, DNI y contacto completos")
    void agendaDeGestionDeReservas() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "turnos");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        Long idActividad = publicarActividadSimple(A, administradorDeA, "Actividad agenda " + UUID.randomUUID(), "CULTURA");
        Long idFranja = crearFranjaSimple(A, administradorDeA, idActividad, "2026-09-12", "10:00", "11:00", 2);

        String dni = "30666" + System.nanoTime() % 1000;
        mvc.perform(reservar(A, idFranja, "Rosa Medina", dni, "rosa@vecina.ar"))
                .andExpect(status().isCreated());

        mvc.perform(get(portalDe(A, "/api/turnos/reservas?franjaId=" + idFranja)).session(administradorDeA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.dniSolicitante == '" + dni + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.dniSolicitante == '" + dni + "')].nombreSolicitante").value("Rosa Medina"))
                .andExpect(jsonPath("$[?(@.dniSolicitante == '" + dni + "')].contacto").value("rosa@vecina.ar"));
    }

    @Test
    @DisplayName("reserva duplicada del mismo DNI en la misma franja da 409 y no consume cupo en el segundo intento")
    void reservaDuplicadaNoConsumeCupo() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "turnos");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        Long idActividad = publicarActividadSimple(A, administradorDeA, "Actividad duplicado " + UUID.randomUUID(), "DEPORTE");
        Long idFranja = crearFranjaSimple(A, administradorDeA, idActividad, "2026-09-19", "15:00", "16:00", 3);

        String dni = "30777" + System.nanoTime() % 1000;
        mvc.perform(reservar(A, idFranja, "Marcos Sosa", dni, "marcos@vecino.ar"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cupoDisponibleRestante").value(2));

        Integer cupoAntesDelSegundoIntento = cupoDisponibleDe(A, idActividad, idFranja);
        assertThat(cupoAntesDelSegundoIntento).isEqualTo(2);

        mvc.perform(reservar(A, idFranja, "Marcos Sosa", dni, "marcos@vecino.ar"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Ya existe una reserva con ese DNI para esta franja."));

        Integer cupoDespuesDelSegundoIntento = cupoDisponibleDe(A, idActividad, idFranja);
        assertThat(cupoDespuesDelSegundoIntento).isEqualTo(cupoAntesDelSegundoIntento);
    }

    @Test
    @DisplayName("concurrencia: N reservas simultáneas sobre una franja con cupo M dejan exactamente M reservas exitosas")
    void concurrenciaDeReservasSobreCupoLimitado() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "turnos");
        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);

        Long idActividad = publicarActividadSimple(A, administradorDeA, "Actividad concurrencia " + UUID.randomUUID(), "DEPORTE");
        Long idFranja = crearFranjaSimple(A, administradorDeA, idActividad, "2026-09-26", "18:00", "19:00", 2);

        int cantidadDeSolicitudes = 5;
        List<Callable<Integer>> tareas = IntStream.range(0, cantidadDeSolicitudes)
                .<Callable<Integer>>mapToObj(i -> () -> mvc.perform(
                                reservar(A, idFranja, "Vecino " + i, "409" + i + System.nanoTime() % 1000, "vecino" + i + "@prueba.ar"))
                        .andReturn().getResponse().getStatus())
                .toList();

        ExecutorService pool = Executors.newFixedThreadPool(cantidadDeSolicitudes);
        try {
            List<Future<Integer>> resultados = pool.invokeAll(tareas);

            AtomicInteger exitosas = new AtomicInteger();
            AtomicInteger sinCupo = new AtomicInteger();
            for (Future<Integer> resultado : resultados) {
                int estado = resultado.get();
                if (estado == 201) {
                    exitosas.incrementAndGet();
                } else if (estado == 409) {
                    sinCupo.incrementAndGet();
                }
            }

            assertThat(exitosas.get()).isEqualTo(2);
            assertThat(sinCupo.get()).isEqualTo(3);
        } finally {
            pool.shutdown();
        }

        Integer cupoFinal = cupoDisponibleDe(A, idActividad, idFranja);
        assertThat(cupoFinal).isZero();

        mvc.perform(get(portalDe(A, "/api/turnos/reservas?franjaId=" + idFranja)).session(administradorDeA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("sin el módulo contratado, todas las rutas rechazan con 403 MODULO_NO_CONTRATADO, "
            + "incluso sin sesión y con datos válidos")
    void sinModuloContratadoRechazaTodasLasRutas() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(B, plataforma);
        MockHttpSession administradorDeB = iniciarSesionDeAdministrador(B);

        mvc.perform(publicarActividad(B, administradorDeB, """
                {"nombre":"Actividad sin módulo","tipo":"DEPORTE","descripcion":null,"ubicacion":"Sede"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("turnos"));

        mvc.perform(get(portalDe(B, "/api/turnos/actividades")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("turnos"));

        mvc.perform(cambiarEstadoDeActividad(B, administradorDeB, 1L, "INACTIVA"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("turnos"));

        mvc.perform(crearFranja(B, administradorDeB, 1L, "2026-09-05", "10:00", "11:00", 2))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("turnos"));

        mvc.perform(get(portalDe(B, "/api/turnos/franjas?actividadId=1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("turnos"));

        mvc.perform(reservar(B, 1L, "Vecino sin módulo", "30888999", "sin@modulo.ar"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("turnos"));

        mvc.perform(get(portalDe(B, "/api/turnos/reservas?franjaId=1")).session(administradorDeB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"))
                .andExpect(jsonPath("$.modulo").value("turnos"));
    }

    @Test
    @DisplayName("aislamiento: una actividad, una franja o una reserva de un municipio no son visibles ni "
            + "gestionables desde otro")
    void aislamientoEntreTenants() throws Exception {
        MockHttpSession plataforma = iniciarSesionDePlataforma();
        fijarModulos(A, plataforma, "turnos");
        fijarModulos(B, plataforma, "turnos");

        MockHttpSession administradorDeA = iniciarSesionDeAdministrador(A);
        MockHttpSession administradorDeB = iniciarSesionDeAdministrador(B);

        String sufijo = UUID.randomUUID().toString();
        String nombreDeA = "Actividad de Lanús " + sufijo;
        Long idActividadDeA = publicarActividadSimple(A, administradorDeA, nombreDeA, "DEPORTE");
        Long idFranjaDeA = crearFranjaSimple(A, administradorDeA, idActividadDeA, "2026-10-03", "09:00", "10:00", 2);

        mvc.perform(reservar(A, idFranjaDeA, "Vecina de Lanús", "30999000", "vecina@lanus.gob.ar"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cupoDisponibleRestante").value(1));

        // La actividad de A no aparece en el listado público de B.
        mvc.perform(get(portalDe(B, "/api/turnos/actividades")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeA + "')]").isEmpty());

        // El id de la actividad de A no existe en la base de B: la búsqueda de
        // franjas por ese id da lista vacía, no error.
        mvc.perform(get(portalDe(B, "/api/turnos/franjas?actividadId=" + idActividadDeA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        // El id de la actividad de A no existe en la base de B: PATCH da 404.
        mvc.perform(cambiarEstadoDeActividad(B, administradorDeB, idActividadDeA, "INACTIVA"))
                .andExpect(status().isNotFound());

        // El id de la actividad de A no existe en la base de B: crear una franja
        // contra ese id da 404.
        mvc.perform(crearFranja(B, administradorDeB, idActividadDeA, "2026-10-03", "09:00", "10:00", 2))
                .andExpect(status().isNotFound());

        // La reserva de la franja de A no aparece en la agenda de gestión de B
        // (con turnos.gestionar en B, id de franja de A: lista vacía).
        mvc.perform(get(portalDe(B, "/api/turnos/reservas?franjaId=" + idFranjaDeA)).session(administradorDeB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        // El id de la franja de A no existe en la base de B: reservar contra
        // ese franjaId da 404, y de paso no decrementa el cupo de la franja de A.
        mvc.perform(reservar(B, idFranjaDeA, "Vecino de otro municipio", "30111000", "otro@avellaneda.gob.ar"))
                .andExpect(status().isNotFound());

        Integer cupoDeADespuesDelIntentoDesdeB = cupoDisponibleDe(A, idActividadDeA, idFranjaDeA);
        assertThat(cupoDeADespuesDelIntentoDesdeB).isEqualTo(1);

        // Sigue todo visible/gestionable en el municipio dueño.
        mvc.perform(get(portalDe(A, "/api/turnos/actividades")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreDeA + "')]").isNotEmpty());
        mvc.perform(get(portalDe(A, "/api/turnos/reservas?franjaId=" + idFranjaDeA)).session(administradorDeA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    private MockHttpServletRequestBuilder publicarActividad(String subdominio, MockHttpSession sesion, String cuerpo) {
        return post(portalDe(subdominio, "/api/turnos/actividades"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private Long publicarActividadSimple(String subdominio, MockHttpSession sesionAdmin, String nombre, String tipo)
            throws Exception {

        MvcResult resultado = mvc.perform(publicarActividad(subdominio, sesionAdmin, """
                {"nombre":"%s","tipo":"%s","descripcion":null,"ubicacion":"Sede municipal"}"""
                .formatted(nombre, tipo)))
                .andExpect(status().isCreated())
                .andReturn();

        return ((Number) JsonPath.read(resultado.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private MockHttpServletRequestBuilder cambiarEstadoDeActividad(
            String subdominio, MockHttpSession sesion, Long id, String estadoNuevo) {

        return patch(portalDe(subdominio, "/api/turnos/actividades/" + id + "/estado"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estadoNuevo\":\"" + estadoNuevo + "\"}");
    }

    private MockHttpServletRequestBuilder crearFranja(String subdominio, MockHttpSession sesion, Long actividadId,
            String fecha, String horaInicio, String horaFin, Integer cupoTotal) {

        return post(portalDe(subdominio, "/api/turnos/actividades/" + actividadId + "/franjas"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fecha":"%s","horaInicio":"%s","horaFin":"%s","cupoTotal":%s}"""
                        .formatted(fecha, horaInicio, horaFin, cupoTotal));
    }

    private Long crearFranjaSimple(String subdominio, MockHttpSession sesionAdmin, Long actividadId,
            String fecha, String horaInicio, String horaFin, Integer cupoTotal) throws Exception {

        MvcResult resultado = mvc.perform(crearFranja(subdominio, sesionAdmin, actividadId, fecha, horaInicio, horaFin, cupoTotal))
                .andExpect(status().isCreated())
                .andReturn();

        return ((Number) JsonPath.read(resultado.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private MockHttpServletRequestBuilder reservar(
            String subdominio, Long franjaId, String nombreSolicitante, String dniSolicitante, String contacto) {

        return post(portalDe(subdominio, "/api/turnos/reservas"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"franjaId":%d,"nombreSolicitante":"%s","dniSolicitante":"%s","contacto":"%s"}"""
                        .formatted(franjaId, nombreSolicitante, dniSolicitante, contacto));
    }

    /** Cupo disponible actual de una franja, leído del listado público (sin sesión). */
    private Integer cupoDisponibleDe(String subdominio, Long actividadId, Long franjaId) throws Exception {
        MvcResult resultado = mvc.perform(get(portalDe(subdominio, "/api/turnos/franjas?actividadId=" + actividadId)))
                .andExpect(status().isOk())
                .andReturn();

        List<Integer> cupos = JsonPath.read(
                resultado.getResponse().getContentAsString(),
                "$[?(@.id == " + franjaId + ")].cupoDisponible");
        return cupos.isEmpty() ? null : cupos.get(0);
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
     * sesión. Mismo criterio que
     * {@code DesarrolloSocialTest#crearUsuarioConPermisos}.
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
