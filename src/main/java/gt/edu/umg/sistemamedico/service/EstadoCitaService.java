package gt.edu.umg.sistemamedico.service;

import gt.edu.umg.sistemamedico.config.CacheConfig;
import gt.edu.umg.sistemamedico.domain.EstadoCita;
import gt.edu.umg.sistemamedico.repository.EstadoCitaRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Servicio del catalogo de estados de cita.
 * Mismo patron que EspecialidadService/RolService/SucursalService.
 */
@Service
public class EstadoCitaService {

    private final EstadoCitaRepository estadoCitaRepository;

    public EstadoCitaService(EstadoCitaRepository estadoCitaRepository) {
        this.estadoCitaRepository = estadoCitaRepository;
    }

    /** Lista de estados activos, ordenados para mostrar en pantalla. Se cachea. */
    @Cacheable(CacheConfig.ESTADOS_CITA)
    @Transactional(readOnly = true)
    public List<EstadoCita> listarActivas() {
        return estadoCitaRepository.findByStateOrderByOrdenAsc((short) 1);
    }

    /**
     * Busca un estado por su codigo (ej: "PENDIENTE_PAGO"). Sin cache
     * propio: se apoya en la lista ya cacheada por listarActivas()
     * en vez de golpear la base de nuevo.
     */
    public EstadoCita buscarPorCodigo(String codigo) {
        return listarActivas().stream()
                .filter(e -> e.getCodigo().equalsIgnoreCase(codigo))
                .findFirst()
                .orElse(null);
    }

    /** Vacia el cache de estados de cita (para el mantenimiento de catalogos). */
    @CacheEvict(value = CacheConfig.ESTADOS_CITA, allEntries = true)
    public void invalidarCache() {
        // Metodo intencionalmente vacio: su unico efecto es el @CacheEvict.
    }
}