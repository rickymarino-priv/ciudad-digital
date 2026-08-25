package ar.com.ciudaddigital.notificaciones;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.Optional;

import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import ar.com.ciudaddigital.SoporteDeIntegracion;

/**
 * El alta de un usuario dispara el email de bienvenida del módulo
 * {@code notificaciones} (ADR 0013 §3), y una falla de ese envío no tira
 * abajo el request que dio de alta al usuario (ADR 0013 §2).
 */
class NotificacionesTest extends SoporteDeIntegracion {

    @Autowired
    private JavaMailSenderImpl mailSender;

    @BeforeEach
    void municipioDePrueba() throws Exception {
        asegurarMunicipio("sanmartin", "San Martín", "#1B4F9C");
    }

    @Test
    @DisplayName("dar de alta un usuario le manda un email de bienvenida que menciona el municipio")
    void elAltaDeUnUsuarioMandaElEmailDeBienvenida() throws Exception {
        MockHttpSession sesionAdmin = iniciarSesionDeAdministrador("sanmartin");
        String email = "juan.notificaciones@sanmartin.gob.ar";

        crearUsuario(sesionAdmin, "Juan Pérez", email);

        SERVIDOR_SMTP_FALSO.waitForIncomingEmail(5000, 1);

        MimeMessage mensaje = mensajeParaDestinatario(email)
                .orElseThrow(() -> new AssertionError("No llegó el email de bienvenida a " + email));

        Assertions.assertTrue(mensaje.getSubject().contains("San Martín"),
                "El asunto tiene que mencionar el municipio: " + mensaje.getSubject());

        String cuerpo = String.valueOf(mensaje.getContent());
        Assertions.assertTrue(cuerpo.contains("Juan Pérez"), "El cuerpo tiene que saludar al usuario nuevo.");
        Assertions.assertTrue(cuerpo.contains("San Martín"), "El cuerpo tiene que mencionar el municipio.");
    }

    @Test
    @DisplayName("si falla el envío del email, el alta de usuario igual responde 201")
    void unaFallaDeEnvioNoTiraAbajoElAltaDeUsuario() throws Exception {
        MockHttpSession sesionAdmin = iniciarSesionDeAdministrador("sanmartin");
        Long idDelRolAgente = idDelRol(sesionAdmin, "agente");

        String hostOriginal = mailSender.getHost();
        int puertoOriginal = mailSender.getPort();
        try {
            // Puerto sin nada escuchando: simula el SMTP caído para este
            // caso puntual sin apagar el servidor falso compartido por el
            // resto de la suite (ADR 0013 §2, alternativas del ADR).
            mailSender.setPort(1);

            mvc.perform(post(portalDe("sanmartin", "/api/usuarios"))
                    .session(sesionAdmin)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"nombre":"Sonia Falla","email":"sonia.notificaciones@sanmartin.gob.ar",
                            "password":"%s","roles":[%d]}
                            """.formatted(PASSWORD_DE_PRUEBA, idDelRolAgente)))
                    .andExpect(status().isCreated());
        } finally {
            mailSender.setHost(hostOriginal);
            mailSender.setPort(puertoOriginal);
        }
    }

    private void crearUsuario(MockHttpSession sesion, String nombre, String email) throws Exception {
        Long idDelRolAgente = idDelRol(sesion, "agente");

        mvc.perform(post(portalDe("sanmartin", "/api/usuarios"))
                .session(sesion)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombre":"%s","email":"%s","password":"%s","roles":[%d]}
                        """.formatted(nombre, email, PASSWORD_DE_PRUEBA, idDelRolAgente)))
                .andExpect(status().isCreated());
    }

    private Long idDelRol(MockHttpSession sesion, String codigo) throws Exception {
        MvcResult resultado = mvc.perform(get(portalDe("sanmartin", "/api/roles")).session(sesion))
                .andReturn();
        String cuerpo = resultado.getResponse().getContentAsString();

        java.util.List<java.util.Map<String, Object>> roles = com.jayway.jsonpath.JsonPath.read(
                cuerpo, "$[?(@.codigo=='" + codigo + "')]");
        return ((Number) roles.get(0).get("id")).longValue();
    }

    private Optional<MimeMessage> mensajeParaDestinatario(String destinatario) {
        return Arrays.stream(SERVIDOR_SMTP_FALSO.getReceivedMessages())
                .filter(mensaje -> tieneComoDestinatario(mensaje, destinatario))
                .findFirst();
    }

    private boolean tieneComoDestinatario(MimeMessage mensaje, String destinatario) {
        try {
            return mensaje.getAllRecipients() != null
                    && Arrays.stream(mensaje.getAllRecipients())
                            .anyMatch(direccion -> direccion.toString().contains(destinatario));
        } catch (jakarta.mail.MessagingException excepcion) {
            throw new RuntimeException(excepcion);
        }
    }
}
