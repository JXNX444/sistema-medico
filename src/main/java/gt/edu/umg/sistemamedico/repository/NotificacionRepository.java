package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * CU-12: cola de correos (his.notificacion).
 */
public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    /**
     * [RN-CU11-04, RN-CU11-05] Los correos que ya se pueden enviar:
     * estan en el estado indicado (Pendiente) y su hora programada ya llego.
     * Del mas antiguo al mas nuevo, maximo 20 por vuelta para no saturar el SMTP.
     *
     * Spring arma la consulta leyendo el nombre del metodo:
     *   WHERE estado_envio = ? AND programado_para <= ?
     *   ORDER BY programado_para ASC  LIMIT 20
     */
    List<Notificacion> findTop20ByEstadoEnvioAndProgramadoParaLessThanEqualOrderByProgramadoParaAsc(
            Short estadoEnvio, OffsetDateTime ahora);
}