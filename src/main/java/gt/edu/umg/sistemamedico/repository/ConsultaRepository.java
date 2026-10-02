package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.Consulta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConsultaRepository extends JpaRepository<Consulta, Integer> {

    /**
     * La consulta activa (state=1) de una cita. Solo puede haber una
     * (indice unico ux_consulta_cita). [CU-08 pasos 3-10]
     * Se usa para precargar el formulario si el medico la guardo
     * "En curso" y para saber si ya se finalizo.
     */
    Optional<Consulta> findByCitaIdAndState(Integer citaId, Short state);
}