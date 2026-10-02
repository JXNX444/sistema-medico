package gt.edu.umg.sistemamedico.service;

import gt.edu.umg.sistemamedico.config.CacheConfig;
import gt.edu.umg.sistemamedico.domain.Medicamento;
import gt.edu.umg.sistemamedico.repository.MedicamentoRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Servicio del catalogo de medicamentos. [CU-08 FA04 paso 2]
 * Mismo patron que EspecialidadService/RolService/SucursalService.
 */
@Service
public class MedicamentoService {

    private final MedicamentoRepository medicamentoRepository;

    public MedicamentoService(MedicamentoRepository medicamentoRepository) {
        this.medicamentoRepository = medicamentoRepository;
    }

    /** Medicamentos activos, ordenados por nombre. Se cachea. */
    @Cacheable(CacheConfig.MEDICAMENTOS)
    @Transactional(readOnly = true)
    public List<Medicamento> listarActivas() {
        return medicamentoRepository.findByStateOrderByNombreAsc((short) 1);
    }

    /** Vacia el cache de medicamentos (para el mantenimiento de catalogos). */
    @CacheEvict(value = CacheConfig.MEDICAMENTOS, allEntries = true)
    public void invalidarCache() {
        // Metodo intencionalmente vacio: su unico efecto es el @CacheEvict.
    }
}