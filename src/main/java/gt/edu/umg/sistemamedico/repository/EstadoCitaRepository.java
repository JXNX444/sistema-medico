package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.EstadoCita;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EstadoCitaRepository extends JpaRepository<EstadoCita, Integer> {

    List<EstadoCita> findByStateOrderByOrdenAsc(Short state);

    /** [CU-03] para buscar el estado "Pendiente de pago" al crear la cita. */
    Optional<EstadoCita> findByCodigoIgnoreCase(String codigo);

    /** [CU-14 paso 2] Listado paginado filtrando por nombre y por estado (activos, inactivos o ambos). */
    Page<EstadoCita> findByNombreContainingIgnoreCaseAndStateIn(String nombre, Collection<Short> estados, Pageable pageable);

    /** [CU-14 RN-CU15-01] Nombre unico entre los activos (idDistinto = el que se esta editando; 0 si es nuevo). */
    boolean existsByNombreIgnoreCaseAndStateAndIdNot(String nombre, Short state, Integer idDistinto);

    /** [CU-14] Para no repetir el codigo interno al crear un estado nuevo (ux_estado_cita_codigo). */
    boolean existsByCodigoIgnoreCase(String codigo);

    /** [CU-14] El estado nuevo se agrega al final de la lista. null si la tabla esta vacia. */
    @Query("select max(e.orden) from EstadoCita e")
    Short findMaxOrden();
}