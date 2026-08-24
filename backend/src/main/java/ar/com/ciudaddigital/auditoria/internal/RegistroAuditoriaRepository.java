package ar.com.ciudaddigital.auditoria.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Filas de auditoría del municipio del request en curso.
 *
 * <p>Va contra el datasource ruteado por tenant (ADR 0001), así que cada
 * consulta ve únicamente los registros del municipio resuelto por el
 * {@code Host}. No hay filtro por tenant que se pueda olvidar.
 */
interface RegistroAuditoriaRepository extends JpaRepository<RegistroAuditoriaEntity, Long> {

    List<RegistroAuditoriaEntity> findAllByOrderByOcurridoEnDesc();
}
