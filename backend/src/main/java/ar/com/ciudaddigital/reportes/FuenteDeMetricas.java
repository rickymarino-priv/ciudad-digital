package ar.com.ciudaddigital.reportes;

import java.util.List;

/**
 * SPI que un módulo funcional implementa para aportar indicadores agregados
 * al tablero de reportes (ADR 0033 §2).
 *
 * <p>Cada implementación calcula sus {@link #series()} con una consulta
 * agregada sobre su propia tabla (un {@code group by} contra el datasource
 * ya ruteado por tenant, ADR 0001), no reaccionando a eventos de dominio:
 * {@code reportes} recolecta todos los beans de este tipo presentes en el
 * contexto de Spring, sin importar {@code .internal} de ningún módulo
 * funcional (inversión de dependencia, ADR 0033 §2).
 *
 * <p>{@link #moduloCodigo()} tiene que coincidir con el {@code codigo()} del
 * {@code DescriptorDeModulo} del módulo que implementa esta interfaz (mismo
 * código que ya usa el catálogo de entitlement, ADR 0012 §6): es lo que usa
 * {@code ReportesController} para filtrar las fuentes contra los módulos que
 * el tenant tiene efectivamente contratados.
 */
public interface FuenteDeMetricas {

    String moduloCodigo();

    String moduloNombre();

    List<SerieDeMetricas> series();
}
