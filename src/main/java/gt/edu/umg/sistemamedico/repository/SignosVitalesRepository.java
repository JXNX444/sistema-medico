package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.SignosVitales;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SignosVitalesRepository extends JpaRepository<SignosVitales, Integer> {

    /** Para saber si una cita ya tiene signos registrados (evita duplicarlos). */
    Optional<SignosVitales> findByCitaId(Integer citaId);
}