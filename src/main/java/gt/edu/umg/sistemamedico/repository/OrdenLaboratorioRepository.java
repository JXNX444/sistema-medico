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
     * Formato "LAB-<anio>-<00000>". [CU-08 FA01 paso 6]
     */
    @Query(value = """
            SELECT COALESCE(MAX(CAST(RIGHT(numero_orden, 5) AS INTEGER)), 0)
              FROM his.orden_laboratorio
             WHERE numero_orden LIKE CONCAT('LAB-', :anio, '-%')
            """, nativeQuery = true)
    int maxCorrelativoDelAnio(@Param("anio") int anio);

    /** Cuantas ordenes tiene una consulta, para la tarjeta "Evaluados". */
    long countByConsultaIdAndState(Integer consultaId, Short state);

    /** [CU-09 paso 1] Tabla de ordenes con filtros por estado, paciente y medico. */
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

    /** [CU-09 pasos 2-3] Una orden con su paciente, medico y todos sus examenes. */
    @Query("""
            SELECT DISTINCT o FROM OrdenLaboratorio o
              JOIN FETCH o.paciente
              JOIN FETCH o.medico
              LEFT JOIN FETCH o.detalles
             WHERE o.id = :id
               AND o.state = 1
            """)
    Optional<OrdenLaboratorio> buscarConDetalles(@Param("id") Integer id);

    /**
     * [CU-10 pasos 2-3] Ordenes PENDIENTES de pago (order_status = 0) y NO externas,
     * buscando por numero de orden (porNumero = true) o por DPI del paciente.
     * Trae paciente, sucursal y detalles (para contar examenes) en una sola consulta.
     */
    @Query("""
            SELECT DISTINCT o FROM OrdenLaboratorio o
              JOIN FETCH o.paciente p
              JOIN FETCH o.sucursal
              LEFT JOIN FETCH o.detalles
             WHERE o.state = 1
               AND o.estadoOrden = 0
               AND o.esExterna = false
               AND ((:porNumero = true  AND UPPER(o.numeroOrden) = UPPER(:valor))
                 OR (:porNumero = false AND p.dpi = :valor))
             ORDER BY o.createdAt DESC
            """)
    List<OrdenLaboratorio> buscarPendientesPago(@Param("porNumero") boolean porNumero,
                                                @Param("valor") String valor);
}