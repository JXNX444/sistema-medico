package gt.edu.umg.sistemamedico.service;

import gt.edu.umg.sistemamedico.config.CacheConfig;
import gt.edu.umg.sistemamedico.domain.Rol;
import gt.edu.umg.sistemamedico.repository.RolRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Servicio del catalogo de roles.
 *
 * Mismo patron que EspecialidadService: la lista de roles activos se
 * cachea; las busquedas puntuales (por id o por nombre) delegan al
 * repositorio.
 */
@Service
public class RolService {

    private final RolRepository rolRepository;

    public RolService(RolRepository rolRepository) {
        this.rolRepository = rolRepository;
    }

    /**
     * Lista de roles ACTIVOS para el dropdown del formulario de usuarios.
     * Se cachea en "roles": 1ra vez BD, siguientes de memoria.
     */
    @Cacheable(CacheConfig.ROLES)
    @Transactional(readOnly = true)
    public List<Rol> listarActivas() {
        return rolRepository.findByStateOrderByNombreAsc((short) 1);
    }

    /** Busca un rol por id (para asignarlo a un usuario). Sin cache. */
    public Rol buscarPorId(Integer id) {
        return rolRepository.findById(id).orElse(null);
    }

    /** [CU-02] Rol con el que se registran los pacientes externos. Sin cache. */
    public Rol buscarPorNombre(String nombre) {
        return rolRepository.findByNombreIgnoreCase(nombre).orElse(null);
    }

    /** Vacia el cache de roles (para el mantenimiento de catalogos). */
    @CacheEvict(value = CacheConfig.ROLES, allEntries = true)
    public void invalidarCache() {
        // Metodo intencionalmente vacio: su unico efecto es el @CacheEvict.
    }
}