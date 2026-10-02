package gt.edu.umg.sistemamedico.service;

import gt.edu.umg.sistemamedico.config.CacheConfig;
import gt.edu.umg.sistemamedico.domain.Cie10;
import gt.edu.umg.sistemamedico.repository.Cie10Repository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Servicio del catalogo CIE-10. [CU-08 paso 7]
 * Mismo patron que EspecialidadService/RolService/SucursalService.
 *
 * El autocompletado NO tiene metodo propio aqui: si listarActivas() se
 * llamara desde otro metodo de esta misma clase, Spring no pasaria por
 * el proxy y el @Cacheable se ignoraria. Por eso ConsultaService pide
 * la lista (ya cacheada) y filtra ahi.
 */
@Service
public class Cie10Service {

    private final Cie10Repository cie10Repository;

    public Cie10Service(Cie10Repository cie10Repository) {
        this.cie10Repository = cie10Repository;
    }

    /** Codigos CIE-10 activos, ordenados por codigo. Se cachea. */
    @Cacheable(CacheConfig.CIE10)
    @Transactional(readOnly = true)
    public List<Cie10> listarActivas() {
        return cie10Repository.findByStateOrderByCodigoAsc((short) 1);
    }

    /** Busca un codigo por id (para validarlo al guardar la consulta). Sin cache. */
    public Cie10 buscarPorId(Integer id) {
        return cie10Repository.findById(id).orElse(null);
    }

    /** Vacia el cache del CIE-10 (para el mantenimiento de catalogos). */
    @CacheEvict(value = CacheConfig.CIE10, allEntries = true)
    public void invalidarCache() {
        // Metodo intencionalmente vacio: su unico efecto es el @CacheEvict.
    }
}