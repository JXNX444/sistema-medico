package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface PagoRepository extends JpaRepository<Pago, Integer> {

    /** [RNF-016] Si ya existe un pago con esa llave, no se procesa de nuevo. */
    Optional<Pago> findByIdempotencyKey(UUID idempotencyKey);

    /** Pago aprobado de una cita (evita cobrar dos veces la misma cita). */
    Optional<Pago> findFirstByCitaIdAndEstadoPago(Integer citaId, Short estadoPago);

    /** [CU-10] Pago aprobado de una orden de laboratorio (evita cobrarla dos veces). */
    Optional<Pago> findFirstByOrdenIdAndEstadoPago(Integer ordenId, Short estadoPago);

    /** Para generar el consecutivo de numero_transaccion. */
    long count();

    /**
     * Correlativo mas alto ya usado en numero_transaccion para un anio dado.
     * Formato "TRX-<anio>-<00000>". Devuelve 0 si aun no hay pagos ese anio.
     */
    @Query(value = """
            SELECT COALESCE(MAX(CAST(RIGHT(numero_transaccion, 5) AS INTEGER)), 0)
              FROM his.pago
             WHERE numero_transaccion LIKE CONCAT('TRX-', :anio, '-%')
            """, nativeQuery = true)
    int maxCorrelativoDelAnio(int anio);
}