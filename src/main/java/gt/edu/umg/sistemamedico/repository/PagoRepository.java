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

    /** Para generar el consecutivo de numero_transaccion. */
    long count();

    /**
     * Correlativo mas alto ya usado en numero_transaccion para un anio dado,
     * a prueba de huecos por borrados. Espera el formato "TRX-<anio>-<00000>":
     * corta los ultimos 5 digitos, los pasa a entero y devuelve el maximo.
     * Devuelve 0 si aun no hay pagos de ese anio.
     *
     * Se usa en vez de count()+1 porque count se rompe si se borran filas
     * de pago en pruebas (podria repetir un numero ya existente).
     */
    @Query(value = """
            SELECT COALESCE(MAX(CAST(RIGHT(numero_transaccion, 5) AS INTEGER)), 0)
              FROM his.pago
             WHERE numero_transaccion LIKE CONCAT('TRX-', :anio, '-%')
            """, nativeQuery = true)
    int maxCorrelativoDelAnio(int anio);
}