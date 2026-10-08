package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.Laboratorio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** Consultas sobre his.laboratorio. [CU-14] */
public interface LaboratorioRepository extends JpaRepository<Laboratorio, Integer> {

    /** Laboratorios activos, para el dropdown de examenes [RN-CU15-03]. */
    List<Laboratorio> findByStateOrderByNombreAsc(Short state);

    /** [CU-14 paso 2] Listado paginado filtrando por nombre y por estado. */
    Page<Laboratorio> findByNombreContainingIgnoreCaseAndStateIn(String nombre, Collection<Short> estados, Pageable pageable);

    /** [RN-CU15-01] Nombre unico entre los activos (idDistinto = el que se esta editando). */
    boolean existsByNombreIgnoreCaseAndStateAndIdNot(String nombre, Short state, Integer idDistinto);
}