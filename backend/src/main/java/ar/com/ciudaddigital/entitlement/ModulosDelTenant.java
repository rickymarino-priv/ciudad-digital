package ar.com.ciudaddigital.entitlement;

import java.util.Optional;
import java.util.Set;

/**
 * SPI que consulta qué módulos tiene habilitados el tenant del request en
 * curso (ADR 0012 §2). La implementa el módulo {@code tenants}: {@code
 * entitlement} no sabe cómo ni dónde se guarda esa configuración, solo la
 * consume por acá. Es la inversión de dependencia que evita el ciclo entre
 * el gating y el módulo de tenants —{@code tenants} necesita el catálogo
 * para validar lo que le mandan, y el gating necesita la configuración del
 * tenant— sin que ninguno de los dos dependa del otro.
 */
public interface ModulosDelTenant {

    /**
     * Códigos de módulo habilitados para el tenant del request en curso.
     *
     * <p>{@link Optional#empty()} significa que no se pudo determinar —por
     * ejemplo, fuera de un request con tenant resuelto— y se interpreta
     * fail-closed: ningún módulo gateado responde. Un conjunto vacío, en
     * cambio, significa que el tenant no contrató ninguno; la diferencia
     * importa porque un error de resolución nunca puede abrir un módulo.
     */
    Optional<Set<String>> habilitadosDelRequestEnCurso();
}
