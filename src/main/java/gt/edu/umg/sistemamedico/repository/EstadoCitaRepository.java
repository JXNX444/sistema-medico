package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.EstadoCita;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EstadoCitaRepository extends JpaRepository<EstadoCita, Integer> {

    List<EstadoCita> findByStateOrderByOrdenAsc(Short state);

    /** [CU-03] para buscar el estado "Pendiente de pago" al crear la cita. */
    Optional<EstadoCita> findByCodigoIgnoreCase(String codigo);
}