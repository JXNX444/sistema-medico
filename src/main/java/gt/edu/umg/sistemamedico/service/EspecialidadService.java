package gt.edu.umg.sistemamedico.service;

import gt.edu.umg.sistemamedico.config.CacheConfig;
import gt.edu.umg.sistemamedico.domain.Especialidad;
import gt.edu.umg.sistemamedico.repository.EspecialidadRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Servicio del catalogo de especialidades.
 *
 * Aqui es donde vive la logica de cache: los controllers ya NO le piden
 * la lista directamente al repositorio, sino a este servicio. La primera
 * lectura va a la base; las siguientes salen del cache en memoria.
 */
@Service
public class EspecialidadService {

    private final EspecialidadRepository especialidadRepository;

    public EspecialidadService(EspecialidadRepository especialidadRepository) {
        this.especialidadRepository = especialidadRepository;
    }

    /**
     * Lista de especialidades ACTIVAS (state = 1), ordenadas por nombre.
     * Es la que usan el portal (CU-00) y el formulario de usuarios (CU-01).
     *
     * @Cacheable(CacheConfig.ESPECIALIDADES):
     *   - 1ra vez  -> ejecuta el metodo, consulta la BD y guarda el
     *                 resultado en el cache llamado "especialidades".
     *   - 2da vez  -> NO entra al metodo; devuelve directo lo del cache.
     *
     * Como el metodo no recibe parametros, el cache guarda una sola
     * entrada (la lista completa de activas).
     */
    @Cacheable(CacheConfig.ESPECIALIDADES)
    @Transactional(readOnly = true)
    public List<Especialidad> listarActivas() {
        return especialidadRepository.findByStateOrderByNombreAsc((short) 1);
    }

    /** Busca una especialidad por id (para asignarla a un medico). Sin cache. */
    public Especialidad buscarPorId(Integer id) {
        return especialidadRepository.findById(id).orElse(null);
    }

    /**
     * Vacia el cache de especialidades.
     *
     * Se invocara desde el CU de mantenimiento de catalogos cada vez que
     * se cree, edite o elimine una especialidad, para que la siguiente
     * lectura vuelva a traer datos frescos de la BD.
     *
     * allEntries = true limpia TODA la lista cacheada de una vez.
     */
    @CacheEvict(value = CacheConfig.ESPECIALIDADES, allEntries = true)
    public void invalidarCache() {
        // Metodo intencionalmente vacio: su unico efecto es el @CacheEvict.
    }
}