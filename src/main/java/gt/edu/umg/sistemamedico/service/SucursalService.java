package gt.edu.umg.sistemamedico.service;

import gt.edu.umg.sistemamedico.config.CacheConfig;
import gt.edu.umg.sistemamedico.domain.Sucursal;
import gt.edu.umg.sistemamedico.repository.SucursalRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Servicio del catalogo de sucursales.
 * Mismo patron que EspecialidadService y RolService.
 */
@Service
public class SucursalService {

    private final SucursalRepository sucursalRepository;

    public SucursalService(SucursalRepository sucursalRepository) {
        this.sucursalRepository = sucursalRepository;
    }

    /**
     * Lista de sucursales ACTIVAS para el portal (CU-00) y el formulario
     * de usuarios (CU-01). Se cachea en "sucursales".  [RN-CU01-10]
     */
    @Cacheable(CacheConfig.SUCURSALES)
    @Transactional(readOnly = true)
    public List<Sucursal> listarActivas() {
        return sucursalRepository.findByStateOrderByNombreAsc((short) 1);
    }

    /** Busca una sucursal por id (para asignarla a un usuario). Sin cache. */
    public Sucursal buscarPorId(Integer id) {
        return sucursalRepository.findById(id).orElse(null);
    }

    /** Vacia el cache de sucursales (para el mantenimiento de catalogos). */
    @CacheEvict(value = CacheConfig.SUCURSALES, allEntries = true)
    public void invalidarCache() {
        // Metodo intencionalmente vacio: su unico efecto es el @CacheEvict.
    }
}