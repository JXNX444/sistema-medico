package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.MetodoPago;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MetodoPagoRepository extends JpaRepository<MetodoPago, Short> {

    /** Solo los metodos activos, ordenados por id (0 Efectivo, 1 Visa...), para la caja. */
    List<MetodoPago> findByStateOrderByIdAsc(Short state);

    /** [CU-14 paso 2] Listado paginado filtrando por nombre y por estado (activos, inactivos o ambos). */
    Page<MetodoPago> findByNombreContainingIgnoreCaseAndStateIn(String nombre, Collection<Short> estados, Pageable pageable);

    /** [CU-14 RN-CU15-01] Nombre unico entre los activos (idDistinto = el que se esta editando; -1 si es nuevo). */
    boolean existsByNombreIgnoreCaseAndStateAndIdNot(String nombre, Short state, Short idDistinto);
}