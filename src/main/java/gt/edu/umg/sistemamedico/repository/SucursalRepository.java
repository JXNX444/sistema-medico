package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.Sucursal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * Consultas sobre his.sucursal.
 *
 * CU-00: el portal muestra las sedes y sus ubicaciones.
 */
public interface SucursalRepository extends JpaRepository<Sucursal, Integer> {

    /**
     * Solo las sedes activas, ordenadas por nombre.
     *
     * El nombre del metodo se traduce a:
     *   SELECT * FROM sucursal WHERE state = ? ORDER BY nombre ASC
     *
     * Se le pasa (short) 1 para traer solo las activas. [RN-CU01-10]
     */
    List<Sucursal> findByStateOrderByNombreAsc(Short state);

    /** [CU-14 paso 2] Listado paginado filtrando por nombre y por estado (activos, inactivos o ambos). */
    Page<Sucursal> findByNombreContainingIgnoreCaseAndStateIn(String nombre, Collection<Short> estados, Pageable pageable);

    /** [CU-14 RN-CU15-01] Nombre unico entre los activos (idDistinto = el que se esta editando; 0 si es nuevo). */
    boolean existsByNombreIgnoreCaseAndStateAndIdNot(String nombre, Short state, Integer idDistinto);
}