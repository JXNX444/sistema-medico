package gt.edu.umg.sistemamedico.service;

import gt.edu.umg.sistemamedico.config.CacheConfig;
import gt.edu.umg.sistemamedico.domain.ExamenLaboratorio;
import gt.edu.umg.sistemamedico.repository.ExamenLaboratorioRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Servicio del catalogo de examenes de laboratorio. [CU-08 FA01 paso 3]
 * Mismo patron que EspecialidadService/RolService/SucursalService.
 */
@Service
public class ExamenLaboratorioService {

    private final ExamenLaboratorioRepository examenLaboratorioRepository;

    public ExamenLaboratorioService(ExamenLaboratorioRepository examenLaboratorioRepository) {
        this.examenLaboratorioRepository = examenLaboratorioRepository;
    }

    /** Examenes activos, ordenados por nombre. Se cachea. */
    @Cacheable(CacheConfig.EXAMENES_LABORATORIO)
    @Transactional(readOnly = true)
    public List<ExamenLaboratorio> listarActivas() {
        return examenLaboratorioRepository.findByStateOrderByNombreAsc((short) 1);
    }

    /** Vacia el cache de examenes (para el mantenimiento de catalogos). */
    @CacheEvict(value = CacheConfig.EXAMENES_LABORATORIO, allEntries = true)
    public void invalidarCache() {
        // Metodo intencionalmente vacio: su unico efecto es el @CacheEvict.
    }
}