package ar.com.ciudaddigital.notificaciones.internal;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import ar.com.ciudaddigital.acceso.UsuarioCreado;
import ar.com.ciudaddigital.tenants.TenantContext;

/**
 * Manda el email de bienvenida al usuario nuevo dado de alta en un
 * municipio (ADR 0013 §3). Hoy, único disparador del motor de
 * notificaciones.
 *
 * <p>Igual que {@code ListenerDeAuditoria}, no usa
 * {@code @ApplicationModuleListener} porque compone {@code @Async}, y el
 * hilo del pool de tareas no hereda el {@code ThreadLocal} que resuelve el
 * tenant en curso. Usa la mitad no asíncrona:
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} más
 * {@code @Transactional("tenantTransactionManager")} explícito, para
 * correr en el mismo hilo del request que hizo el commit, con
 * {@link TenantContext#requerido()} todavía resuelto (ADR 0013 §2).
 *
 * <p>Este listener no escribe nada en la base —el canal de email no
 * necesita un {@code EntityManager}—, así que la transacción en sí no
 * tiene trabajo que hacer. Se mantiene de todos modos, con
 * {@code REQUIRES_NEW} igual que en {@code auditoria}, por dos motivos: es
 * el mismo patrón que exige un {@code @TransactionalEventListener} con
 * {@code @Transactional} explícito ({@code AFTER_COMMIT} corre fuera de
 * toda transacción, así que hace falta declarar {@code REQUIRES_NEW} o
 * {@code NOT_SUPPORTED} en vez de heredar una implícita), y mantiene los
 * dos listeners de {@code UsuarioCreado} simétricos si mañana este
 * necesita persistir algo (por ejemplo, un registro propio de
 * notificaciones enviadas/fallidas).
 *
 * <p>Una falla acá (SMTP caído, host inválido) no debe tirar abajo el
 * request original: Spring no repropaga una excepción de un callback
 * {@code afterCommit()}, así que el alta de usuario ya respondió 200/201
 * antes de que este listener corra.
 */
@Component
class ListenerDeNotificaciones {

    private final CanalDeNotificacion canalDeEmail;

    ListenerDeNotificaciones(CanalDeNotificacion canalDeEmail) {
        this.canalDeEmail = canalDeEmail;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(transactionManager = "tenantTransactionManager", propagation = Propagation.REQUIRES_NEW)
    void alCrearUsuario(UsuarioCreado evento) {
        String nombreMunicipio = TenantContext.requerido().nombreMunicipio();

        String asunto = "Bienvenido/a a Ciudad Digital " + nombreMunicipio;
        String cuerpo = """
                Hola %s,

                Se creó una cuenta a tu nombre en el portal de %s de Ciudad Digital.

                Ya podés iniciar sesión con tu correo electrónico y la contraseña
                que te asignaron.
                """.formatted(evento.nombreUsuario(), nombreMunicipio);

        canalDeEmail.enviar(new Notificacion(evento.emailUsuario(), asunto, cuerpo));
    }
}
