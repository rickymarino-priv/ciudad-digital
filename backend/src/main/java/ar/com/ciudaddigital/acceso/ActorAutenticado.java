package ar.com.ciudaddigital.acceso;

/**
 * Identidad mínima de quien hace el request autenticado en el municipio.
 *
 * <p>Los módulos funcionales que necesitan dejar constancia de "quién hizo
 * esto" —la firma de una norma publicada, el actor de un cambio— leen este
 * tipo desde {@code Authentication#getPrincipal()} en vez de depender de
 * {@code UsuarioAutenticado}, que es interno de {@code acceso}: mismo
 * patrón que {@code TenantContext}/{@code TenantInfo} para el municipio
 * resuelto (ver {@code ar.com.ciudaddigital.tenants}), aplicado acá al
 * usuario en sesión.
 */
public interface ActorAutenticado {

    Long id();

    String nombre();

    String email();
}
