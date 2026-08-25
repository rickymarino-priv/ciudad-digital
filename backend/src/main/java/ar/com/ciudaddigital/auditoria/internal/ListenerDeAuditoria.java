package ar.com.ciudaddigital.auditoria.internal;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import ar.com.ciudaddigital.acceso.UsuarioCreado;

/**
 * Deja constancia en {@code registro_auditoria} de las acciones que
 * disparan eventos de dominio. Hoy, solo el alta de un usuario
 * (ADR 0013 §3).
 *
 * <p>No usa {@code @ApplicationModuleListener} —la anotación recomendada
 * por Spring Modulith para integrar módulos por eventos— porque compone
 * {@code @Async}, y el hilo del pool de tareas no hereda el
 * {@code ThreadLocal} que resuelve la base del tenant en curso. En su
 * lugar usa la mitad no asíncrona: {@code @TransactionalEventListener} con
 * fase {@code AFTER_COMMIT} y {@code @Transactional("tenantTransactionManager")}
 * explícito, para correr en el mismo hilo del request que hizo el commit y
 * escribir en la base del municipio correcto sin ningún mecanismo nuevo de
 * propagación (ADR 0013 §2).
 *
 * <p>{@code AFTER_COMMIT} corre después de que la transacción original ya
 * terminó, así que no hay ninguna transacción en curso a la que
 * adherirse: Spring exige que un {@code @TransactionalEventListener} con
 * {@code @Transactional} declare {@code REQUIRES_NEW} (o
 * {@code NOT_SUPPORTED}) para dejar esa falta de contexto explícita, no
 * una nueva transacción implícita.
 */
@Component
class ListenerDeAuditoria {

    private static final String ACCION_USUARIO_CREADO = "usuario.creado";
    private static final String ENTIDAD_USUARIO = "usuario";

    private final RegistroAuditoriaRepository registros;

    ListenerDeAuditoria(RegistroAuditoriaRepository registros) {
        this.registros = registros;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(transactionManager = "tenantTransactionManager", propagation = Propagation.REQUIRES_NEW)
    void alCrearUsuario(UsuarioCreado evento) {
        String detalle = "Creó al usuario %s (%s).".formatted(
                evento.nombreUsuario(), evento.emailUsuario());

        registros.save(RegistroAuditoriaEntity.nueva(
                evento.idActor(), evento.nombreActor(), evento.emailActor(),
                ACCION_USUARIO_CREADO, ENTIDAD_USUARIO, String.valueOf(evento.idUsuario()),
                detalle));
    }
}
