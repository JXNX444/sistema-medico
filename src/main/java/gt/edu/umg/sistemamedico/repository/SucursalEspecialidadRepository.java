package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.SucursalEspecialidad;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SucursalEspecialidadRepository extends JpaRepository<SucursalEspecialidad, Integer> {

    /** [CU-03 paso 2] Especialidades activas que ofrece una sucursal especifica. */
    List<SucursalEspecialidad> findBySucursalIdAndState(Integer sucursalId, Short state);

    /**
     * [CU-13 paso 3] Listado paginado de las asignaciones activas.
     * Las eliminadas (state = 0, borrado logico) no se muestran.
     */
    Page<SucursalEspecialidad> findByState(Short state, Pageable pageable);

    /**
     * [CU-13 paso 3] Filtro por ID: la misma tabla, pero solo con la
     * asignacion de ese ID (si existe y esta activa).
     */
    Page<SucursalEspecialidad> findByIdAndState(Integer id, Short state, Pageable pageable);

    /**
     * [CU-13 paso 8 / FA05] Busca la combinacion sede + especialidad, este
     * activa o no. Sirve para saber si ya existe (duplicado) o si estaba
     * eliminada y hay que reactivarla. La BD solo permite una fila por
     * combinacion (indice unico ux_sucesp_combinacion).
     */
    Optional<SucursalEspecialidad> findBySucursalIdAndEspecialidadId(Integer sucursalId, Integer especialidadId);
}