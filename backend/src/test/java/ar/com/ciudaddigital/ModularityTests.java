package ar.com.ciudaddigital;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Verifica los límites entre módulos (ADR 0003).
 *
 * <p>Falla el build si un módulo alcanza los internals de otro. Es la red
 * que sostiene la decisión de monolito modular: sin esto, la separación
 * entre módulos es solo una convención de nombres.
 */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(BackendApplication.class);

    @Test
    void losModulosRespetanSusLimites() {
        modules.verify();
    }
}
