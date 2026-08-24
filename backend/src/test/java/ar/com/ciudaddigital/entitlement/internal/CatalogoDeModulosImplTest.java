package ar.com.ciudaddigital.entitlement.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import ar.com.ciudaddigital.entitlement.DescriptorDeModulo;
import ar.com.ciudaddigital.entitlement.ModulosDelTenant;

/**
 * Fija por test el contrato fail-closed que el javadoc de {@link
 * ModulosDelTenant#habilitadosDelRequestEnCurso()} declara (ADR 0012 §3):
 * no poder determinar qué tiene habilitado el tenant nunca es equivalente a
 * tenerlo todo habilitado.
 *
 * <p>Unitario y no de integración por HTTP: forzar por API pública un
 * estado en el que el tenant está resuelto pero {@code ModulosDelTenant} no
 * puede responder no es un escenario alcanzable sin invasión del filtro; acá
 * se lo simula directamente sobre la pieza que traduce esa señal en la
 * decisión de {@code habilitado()}.
 */
class CatalogoDeModulosImplTest {

    private static final String CODIGO = "ejemplo";

    @Test
    void noPoderDeterminarLosModulosDelTenantRechazaEnVezDeAbrir() {
        ModulosDelTenant modulosDelTenant = mock(ModulosDelTenant.class);
        when(modulosDelTenant.habilitadosDelRequestEnCurso()).thenReturn(Optional.empty());

        CatalogoDeModulosImpl catalogo =
                new CatalogoDeModulosImpl(List.of(descriptorDe(CODIGO)), modulosDelTenant);

        assertThat(catalogo.habilitado(CODIGO)).isFalse();
    }

    @Test
    void unConjuntoVacioTambienRechazaPorqueNoContratoNada() {
        ModulosDelTenant modulosDelTenant = mock(ModulosDelTenant.class);
        when(modulosDelTenant.habilitadosDelRequestEnCurso()).thenReturn(Optional.of(Set.of()));

        CatalogoDeModulosImpl catalogo =
                new CatalogoDeModulosImpl(List.of(descriptorDe(CODIGO)), modulosDelTenant);

        assertThat(catalogo.habilitado(CODIGO)).isFalse();
    }

    @Test
    void unModuloPresenteEnElConjuntoHabilitadoSiResponde() {
        ModulosDelTenant modulosDelTenant = mock(ModulosDelTenant.class);
        when(modulosDelTenant.habilitadosDelRequestEnCurso()).thenReturn(Optional.of(Set.of(CODIGO)));

        CatalogoDeModulosImpl catalogo =
                new CatalogoDeModulosImpl(List.of(descriptorDe(CODIGO)), modulosDelTenant);

        assertThat(catalogo.habilitado(CODIGO)).isTrue();
    }

    private static DescriptorDeModulo descriptorDe(String codigo) {
        return new DescriptorDeModulo() {
            @Override
            public String codigo() {
                return codigo;
            }

            @Override
            public String nombre() {
                return "Módulo de prueba";
            }

            @Override
            public String descripcion() {
                return "Descriptor de prueba para el test de fail-closed.";
            }

            @Override
            public List<String> prefijosDeApi() {
                return List.of("/api/" + codigo);
            }
        };
    }
}
