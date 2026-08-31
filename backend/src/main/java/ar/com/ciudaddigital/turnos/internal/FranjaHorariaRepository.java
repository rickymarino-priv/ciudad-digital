package ar.com.ciudaddigital.turnos.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Franjas horarias del municipio del request en curso.
 *
 * <p>Va contra el datasource ruteado por tenant (ADR 0001), así que cada
 * consulta ve únicamente las franjas del municipio resuelto por el
 * {@code Host}. No hay filtro por tenant que se pueda olvidar.
 */
interface FranjaHorariaRepository extends JpaRepository<FranjaHorariaEntity, Long> {

    List<FranjaHorariaEntity> findByActividadIdOrderByFechaAscHoraInicioAsc(Long actividadId);

    /**
     * Decisión central de la rebanada (ADR 0026 §4): decrementa el cupo
     * disponible de la franja en una única sentencia atómica, cuya
     * condición y escritura son la misma operación de base de datos —así
     * que no hay ventana entre "leer si hay cupo" y "escribir el nuevo
     * valor" en la que dos solicitudes concurrentes puedan leer el mismo
     * valor y las dos concluir que hay lugar. Devuelve la cantidad de
     * filas afectadas: {@code 1} si había cupo y se decrementó, {@code 0}
     * si no había (la fila existe pero no matcheó la condición). No hace
     * falta bloqueo optimista con reintento: bajo {@code READ COMMITTED}
     * de Postgres, dos {@code UPDATE} concurrentes sobre la misma fila se
     * serializan a nivel de fila, y el segundo vuelve a evaluar el
     * {@code where} contra el valor ya confirmado por el primero.
     */
    @Modifying
    @Query("update FranjaHorariaEntity f set f.cupoDisponible = f.cupoDisponible - 1 "
            + "where f.id = :id and f.cupoDisponible > 0")
    int reservarUnLugar(@Param("id") Long id);

    /**
     * Lectura directa del cupo disponible, sin pasar por el objeto
     * gestionado en el contexto de persistencia: como
     * {@code GestionDeReservas#reservar} ya tiene la entidad de la franja
     * cargada en memoria de antes de aplicar {@link #reservarUnLugar}, un
     * {@code findById} devolvería esa misma instancia sin volver a
     * consultar la base y con el valor de cupo desactualizado. Esta
     * consulta escalar sí ejecuta una sentencia real contra la base, para
     * informar en la respuesta el cupo resultante de verdad.
     */
    @Query("select f.cupoDisponible from FranjaHorariaEntity f where f.id = :id")
    Integer cupoDisponibleDe(@Param("id") Long id);
}
