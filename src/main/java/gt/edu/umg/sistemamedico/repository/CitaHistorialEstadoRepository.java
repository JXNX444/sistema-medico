package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.CitaHistorialEstado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CitaHistorialEstadoRepository extends JpaRepository<CitaHistorialEstado, Integer> {

    /**
     * El registro de historial mas reciente de una cita (ej: el motivo
     * de su cancelacion). Se usa para mostrar "por que" quedo en su
     * estado actual, tanto en Recepcion como en "Mis Citas".
     */
    Optional<CitaHistorialEstado> findFirstByCitaIdOrderByCreatedAtDesc(Integer citaId);
}