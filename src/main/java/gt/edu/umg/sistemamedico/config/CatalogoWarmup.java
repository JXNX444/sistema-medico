package gt.edu.umg.sistemamedico.config;

import gt.edu.umg.sistemamedico.service.EspecialidadService;
import gt.edu.umg.sistemamedico.service.RolService;
import gt.edu.umg.sistemamedico.service.SucursalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Precarga de catalogos en cache al arrancar la aplicacion.
 *
 * Varios casos de uso (CU-00, CU-01, CU-03, CU-07) tienen como
 * PRECONDICION que los catalogos ya esten "cargados en cache". Esta clase
 * cumple esa precondicion: al terminar de levantar la app, llama una vez a
 * cada listarActivas(), lo que dispara el @Cacheable y deja las listas
 * guardadas en memoria ANTES de que llegue el primer usuario.
 *
 * ApplicationRunner.run() se ejecuta automaticamente una sola vez, cuando
 * el contexto de Spring ya esta listo.
 */
@Component
public class CatalogoWarmup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogoWarmup.class);

    private final RolService rolService;
    private final EspecialidadService especialidadService;
    private final SucursalService sucursalService;

    public CatalogoWarmup(RolService rolService,
                          EspecialidadService especialidadService,
                          SucursalService sucursalService) {
        this.rolService = rolService;
        this.especialidadService = especialidadService;
        this.sucursalService = sucursalService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Precargando catalogos en cache...");

        int roles = rolService.listarActivas().size();
        int especialidades = especialidadService.listarActivas().size();
        int sucursales = sucursalService.listarActivas().size();

        log.info("Cache de catalogos lista -> roles={}, especialidades={}, sucursales={}",
                roles, especialidades, sucursales);
    }
}