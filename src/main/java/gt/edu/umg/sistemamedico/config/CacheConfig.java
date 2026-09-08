package gt.edu.umg.sistemamedico.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion central del cache de catalogos.
 *
 * - @EnableCaching activa el soporte de @Cacheable / @CacheEvict en toda la app.
 * - El CacheManager usa ConcurrentMapCacheManager: un cache en memoria (dentro
 *   de la JVM) muy simple, ideal para catalogos que casi nunca cambian.
 *
 * Los nombres de cache se declaran como constantes para no tener errores de
 * tipeo: un @Cacheable(CacheConfig.ESPECIALIDADES) siempre apunta al mismo
 * cache. Como al constructor le pasamos la lista de nombres, el manager queda
 * en modo "fijo": si alguien pide un cache que NO esta en esta lista, Spring
 * lanza excepcion en vez de crear un cache fantasma silencioso.
 *
 * Cada vez que agreguemos un catalogo nuevo (medicamentos, cie10, etc.),
 * agregamos aqui su constante y su nombre al constructor.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Cache del catalogo de roles (tabla his.rol). */
    public static final String ROLES = "roles";

    /** Cache del catalogo de especialidades (tabla his.especialidad). */
    public static final String ESPECIALIDADES = "especialidades";

    /** Cache del catalogo de sucursales (tabla his.sucursal). */
    public static final String SUCURSALES = "sucursales";

    /** Cache del catalogo de estados de cita (tabla his.estado_cita). [CU-03] */
    public static final String ESTADOS_CITA = "estadosCita";

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
                ROLES,
                ESPECIALIDADES,
                SUCURSALES,
                ESTADOS_CITA
        );
    }
}