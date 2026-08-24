package ar.com.ciudaddigital.persistencia;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.stream.Stream;

import ar.com.ciudaddigital.tenants.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;

/**
 * Envuelve el {@link EntityManager} de tenant para que las consultas de
 * lectura de Spring Modulith no tiren abajo el arranque de la aplicación
 * cuando corren sin ningún tenant resuelto.
 *
 * <p>{@code spring.modulith.events.republish-outstanding-events-on-restart}
 * (ADR 0013 §1) hace que, al terminar de arrancar el contexto —antes de que
 * exista ningún request, y por lo tanto ningún tenant resuelto—, Spring
 * Modulith consulte {@code event_publication} para reintentar publicaciones
 * pendientes. Como esa tabla vive en la base de cada municipio
 * ({@link DataSourceDeTenants}), esa consulta no tiene a qué base
 * conectarse: sin este envoltorio, {@code DataSourceDeTenants} —
 * correctamente, para no arriesgarse a leer o escribir en el municipio
 * equivocado— tira una excepción que aborta el arranque del contexto
 * completo, algo que el ADR 0013 no anticipó (más detalle en el resumen que
 * acompaña este cambio).
 *
 * <p>Alcance deliberadamente angosto: solo se intercepta {@code
 * createQuery(...)} —lo único que este mecanismo de arranque usa— y solo
 * cuando no hay tenant resuelto, devolviendo un resultado vacío en vez de
 * conectarse a ninguna base. Cualquier otra operación (persistir, buscar por
 * id, iniciar una transacción) se delega sin cambios al EntityManager real:
 * ese código de la aplicación corre siempre dentro de un request con tenant
 * resuelto, y tiene que seguir fallando fuerte si alguna vez no lo está.
 */
final class EntityManagerTolerantePorAusenciaDeTenant {

    private EntityManagerTolerantePorAusenciaDeTenant() {
    }

    static EntityManager envolver(EntityManager real) {
        return (EntityManager) Proxy.newProxyInstance(
                EntityManagerTolerantePorAusenciaDeTenant.class.getClassLoader(),
                new Class<?>[] { EntityManager.class },
                new Handler(real));
    }

    private static final class Handler implements InvocationHandler {

        private final EntityManager real;

        Handler(EntityManager real) {
            this.real = real;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            boolean esCreateQuery = "createQuery".equals(method.getName())
                    && method.getParameterCount() > 0
                    && method.getParameterTypes()[0] == String.class;

            if (esCreateQuery && TenantContext.actual().isEmpty()) {
                // createQuery(String) devuelve Query; createQuery(String, Class<T>)
                // devuelve TypedQuery<T>: el proxy tiene que implementar la
                // interfaz que el llamador espera, o el cast del lado de
                // Modulith revienta con ClassCastException.
                boolean tipada = method.getReturnType() == TypedQuery.class;
                return consultaVacia(tipada);
            }

            try {
                return method.invoke(real, args);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        }

        private Query consultaVacia(boolean tipada) {
            Class<?>[] interfaces = tipada
                    ? new Class<?>[] { TypedQuery.class }
                    : new Class<?>[] { Query.class };

            return (Query) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    interfaces,
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getResultList" -> List.of();
                        case "getResultStream" -> Stream.empty();
                        case "getSingleResult" -> throw new NoResultException(
                                "No hay tenant resuelto: no hay nada que consultar.");
                        case "executeUpdate" -> 0;
                        // El resto de la API de Query es "fluida" (setParameter,
                        // setMaxResults, etc. devuelven la misma consulta): al
                        // no tener tenant no hay nada que configurar, pero hay
                        // que seguir devolviendo algo encadenable.
                        default -> method.getReturnType().isInstance(proxy) ? proxy : null;
                    });
        }
    }
}
