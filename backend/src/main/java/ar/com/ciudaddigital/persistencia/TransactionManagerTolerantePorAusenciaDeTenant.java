package ar.com.ciudaddigital.persistencia;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;

import ar.com.ciudaddigital.tenants.TenantContext;

/**
 * Envuelve el gestor de transacciones de tenant para que abrir una
 * transacción sin ningún tenant resuelto no intente conectarse a ninguna
 * base — compañero de {@link EntityManagerTolerantePorAusenciaDeTenant},
 * mismo motivo (ver su Javadoc): el registro persistente de eventos de
 * Spring Modulith abre sus propias transacciones al reintentar
 * publicaciones pendientes al arrancar, antes de que exista ningún request.
 *
 * <p>{@code JpaTransactionManager.getTransaction(...)} obtiene una conexión
 * JDBC real para empezar la transacción de base de datos, sin pasar por el
 * {@link jakarta.persistence.EntityManager} que envuelve la otra clase: hay
 * que interceptar acá también, más temprano, para que el intento de arrancar
 * la transacción en sí no llegue a {@link DataSourceDeTenants}.
 *
 * <p>Mismo alcance angosto que el otro envoltorio: sin tenant resuelto, la
 * transacción es un no-op (ni abre conexión ni hace nada al confirmar o
 * deshacer); con tenant resuelto —el caso de todo el código de la
 * aplicación, siempre dentro de un request— delega sin cambios al gestor
 * real.
 */
final class TransactionManagerTolerantePorAusenciaDeTenant implements PlatformTransactionManager {

    private final PlatformTransactionManager real;

    private TransactionManagerTolerantePorAusenciaDeTenant(PlatformTransactionManager real) {
        this.real = real;
    }

    static PlatformTransactionManager envolver(PlatformTransactionManager real) {
        return new TransactionManagerTolerantePorAusenciaDeTenant(real);
    }

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
        if (TenantContext.actual().isEmpty()) {
            return SinTenant.INSTANCIA;
        }
        return real.getTransaction(definition);
    }

    @Override
    public void commit(TransactionStatus status) throws TransactionException {
        if (status != SinTenant.INSTANCIA) {
            real.commit(status);
        }
    }

    @Override
    public void rollback(TransactionStatus status) throws TransactionException {
        if (status != SinTenant.INSTANCIA) {
            real.rollback(status);
        }
    }

    /** Transacción inerte: no hay conexión de ningún lado que confirmar o deshacer. */
    private enum SinTenant implements TransactionStatus {
        INSTANCIA;

        @Override
        public Object createSavepoint() throws TransactionException {
            throw new TransactionException("No hay tenant resuelto: no hay transacción real que savepointear.") {
            };
        }

        @Override
        public void rollbackToSavepoint(Object savepoint) throws TransactionException {
            throw new TransactionException("No hay tenant resuelto: no hay transacción real que savepointear.") {
            };
        }

        @Override
        public void releaseSavepoint(Object savepoint) throws TransactionException {
            throw new TransactionException("No hay tenant resuelto: no hay transacción real que savepointear.") {
            };
        }
    }
}
