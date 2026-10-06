package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.OrdenLaboratorio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrdenLaboratorioRepository extends JpaRepository<OrdenLaboratorio, Integer> {

    /**
     * Correlativo mas alto ya usado en numero_orden para un anio dado.
     * Formato "LAB-<anio>-<00000>": corta los ultimos 5 digitos y devuelve
     * el maximo, o 0 si aun no hay ordenes ese anio. [CU-08 FA01 paso 6]
     * Mismo patron que PagoRepository.maxCorrelativoDelAnio (a prueba de
     * huecos por borrados, a diferencia de count()+1).
     */
    @Query(value = """
            SELECT COALESCE(MAX(CAST(RIGHT(numero_orden, 5) AS INTEGER)), 0)
              FROM his.orden_laboratorio
             WHERE numero_orden LIKE CONCAT('LAB-', :anio, '-%')
            """, nativeQuery = true)
    int maxCorrelativoDelAnio(@Param("anio") int anio);

    /** Cuantas ordenes tiene una consulta, para mostrarlo en la tarjeta "Evaluados". */
    long countByConsultaIdAndState(Integer consultaId, Short state);

    /**
     * [CU-09 paso 1] Tabla de ordenes con filtros por estado, paciente y medico.
     *
     * - todosLosEstados = true  -> ignora el filtro de estado.
     * - paciente = ''           -> ignora el filtro (busca en nombre o DPI).
     * - medico = ''             -> ignora el filtro (busca en nombre).
     *
     * Los textos deben llegar ya en minusculas (lo hace el service).
     *
     * JOIN FETCH: como open-in-view esta en false, el paciente y el medico
     * se traen en la misma consulta; si no, fallarian al leerlos despues.
     */
    @Query("""
            SELECT o FROM OrdenLaboratorio o
              JOIN FETCH o.paciente p
              JOIN FETCH o.medico m
             WHERE o.state = 1
               AND (:todosLosEstados = true OR o.estadoOrden = :estado)
               AND (:paciente = ''
                    OR LOWER(p.nombreCompleto) LIKE CONCAT('%', :paciente, '%')
                    OR p.dpi LIKE CONCAT('%', :paciente, '%'))
               AND (:medico = ''
                    OR LOWER(m.nombreCompleto) LIKE CONCAT('%', :medico, '%'))
             ORDER BY o.createdAt DESC
            """)
    List<OrdenLaboratorio> buscarConFiltros(@Param("todosLosEstados") boolean todosLosEstados,
                                            @Param("estado") Short estado,
                                            @Param("paciente") String paciente,
                                            @Param("medico") String medico);

    /**
     * [CU-09 pasos 2-3] Una orden con su paciente, medico y todos sus examenes,
     * en una sola consulta. Cada detalle trae su ExamenLaboratorio (EAGER).
     */
    @Query("""
            SELECT DISTINCT o FROM OrdenLaboratorio o
              JOIN FETCH o.paciente
              JOIN FETCH o.medico
              LEFT JOIN FETCH o.detalles
             WHERE o.id = :id
               AND o.state = 1
            """)
    Optional<OrdenLaboratorio> buscarConDetalles(@Param("id") Integer id);
}