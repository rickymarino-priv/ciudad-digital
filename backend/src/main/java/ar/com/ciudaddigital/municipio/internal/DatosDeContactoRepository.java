package ar.com.ciudaddigital.municipio.internal;

import java.util.Optional;

import org.springframework.data.repository.Repository;

/**
 * Lee los datos de contacto de la base del municipio en curso.
 *
 * <p>No recibe ningún identificador de municipio: la base contra la que
 * consulta la determina el tenant del request. Así no existe forma de
 * pedir, ni por error ni a propósito, los datos de otro municipio.
 */
interface DatosDeContactoRepository extends Repository<DatosDeContactoEntity, Integer> {

    Optional<DatosDeContactoEntity> findById(Integer id);
}
