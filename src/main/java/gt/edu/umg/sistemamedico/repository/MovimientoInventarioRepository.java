package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.MovimientoInventario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Kardex de inventario. [CU-11 paso 10] [CU-14] [CU-15]
 *
 * JpaSpecificationExecutor: permite el listado con filtros opcionales de
 * la bitacora (ver MovimientoInventarioSpecifications).
 */
public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, Long>,
        JpaSpecificationExecutor<MovimientoInventario> {

    /**
     * [CU-15 RN-CU13-03] Resumen mensual: por cada tipo, cuantos movimientos
     * activos hubo y cuantas unidades movieron entre dos fechas.
     * Cada fila: [tipo (Short), movimientos (Long), unidades (Long)].
     */
    @Query("""
            select m.tipoMovimiento, count(m), sum(m.cantidad)
            from MovimientoInventario m
            where m.state = 1 and m.createdAt >= :desde and m.createdAt < :hasta
            group by m.tipoMovimiento
            order by m.tipoMovimiento
            """)
    List<Object[]> resumenPorTipo(@Param("desde") OffsetDateTime desde,
                                  @Param("hasta") OffsetDateTime hasta);
}