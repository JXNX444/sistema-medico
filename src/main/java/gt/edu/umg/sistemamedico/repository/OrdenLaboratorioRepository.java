package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.OrdenLaboratorio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}