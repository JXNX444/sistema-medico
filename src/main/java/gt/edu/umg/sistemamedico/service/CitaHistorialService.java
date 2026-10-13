package gt.edu.umg.sistemamedico.service;

import gt.edu.umg.sistemamedico.domain.CitaHistorialEstado;
import gt.edu.umg.sistemamedico.repository.CitaHistorialEstadoRepository;
import org.springframework.stereotype.Service;

/**
 * Guarda el MOTIVO de un cambio de estado automatico de una cita
 * (cancelacion por falta de pago, No Asistio, etc.).
 *
 * El trigger tr_cita_historial de la BD ya inserta una fila en
 * cita_historial_estado cada vez que cambia estado_cita_id. Antes los
 * schedulers insertaban OTRA fila a mano con el motivo, y quedaban dos
 * filas por cada cambio. Ahora se completa la fila del trigger con la
 * observacion; solo si no existe (ej: BD sin el trigger) se inserta una.
 *
 * Importante: llamarlo DESPUES de citaRepository.saveAndFlush(cita),
 * para que el UPDATE ya haya llegado a la BD y el trigger haya corrido.
 */
@Service
public class CitaHistorialService {

    private final CitaHistorialEstadoRepository historialRepository;

    public CitaHistorialService(CitaHistorialEstadoRepository historialRepository) {
        this.historialRepository = historialRepository;
    }

    public void registrarMotivo(Integer citaId, Integer estadoAnteriorId, Integer estadoNuevoId,
                                String observacion) {
        CitaHistorialEstado fila = historialRepository.findFirstByCitaIdOrderByIdDesc(citaId)
                .filter(h -> estadoNuevoId.equals(h.getEstadoNuevoId()) && h.getObservacion() == null)
                .orElse(null);

        if (fila == null) {
            fila = new CitaHistorialEstado();
            fila.setCitaId(citaId);
            fila.setEstadoAnteriorId(estadoAnteriorId);
            fila.setEstadoNuevoId(estadoNuevoId);
            fila.setUsuarioId(null); // accion automatica, sin usuario
        }
        fila.setObservacion(observacion);
        historialRepository.save(fila);
    }
}
