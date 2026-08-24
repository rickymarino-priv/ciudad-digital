package ar.com.ciudaddigital.entitlement.internal;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import ar.com.ciudaddigital.entitlement.CatalogoDeModulos;
import ar.com.ciudaddigital.entitlement.DescriptorDeModulo;
import ar.com.ciudaddigital.entitlement.ModulosDelTenant;

/**
 * Junta los {@link DescriptorDeModulo} presentes en el contexto y resuelve
 * el estado de cada uno preguntándole al {@link ModulosDelTenant} del
 * tenant en curso (ADR 0012).
 *
 * <p>Spring inyecta acá la lista completa de beans {@code
 * DescriptorDeModulo} de todo el sistema: agregar un módulo funcional
 * nuevo no toca esta clase, alcanza con que publique su descriptor.
 */
@Component
class CatalogoDeModulosImpl implements CatalogoDeModulos {

    private final List<DescriptorDeModulo> descriptores;
    private final ModulosDelTenant modulosDelTenant;

    CatalogoDeModulosImpl(List<DescriptorDeModulo> descriptores, ModulosDelTenant modulosDelTenant) {
        this.descriptores = descriptores.stream()
                .sorted(Comparator.comparing(DescriptorDeModulo::codigo))
                .toList();
        this.modulosDelTenant = modulosDelTenant;
    }

    @Override
    public List<DescriptorDeModulo> catalogo() {
        return descriptores;
    }

    @Override
    public boolean habilitado(String codigo) {
        return modulosDelTenant.habilitadosDelRequestEnCurso()
                .map(habilitados -> habilitados.contains(codigo))
                .orElse(false);
    }
}
