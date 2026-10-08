package gt.edu.umg.sistemamedico.service.catalogo;

import gt.edu.umg.sistemamedico.domain.Sucursal;
import gt.edu.umg.sistemamedico.repository.SucursalRepository;
import gt.edu.umg.sistemamedico.service.AuditoriaService;
import gt.edu.umg.sistemamedico.service.SucursalService;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CU-14 Catalogo de Sucursales (BranchController en el documento).
 * Campos: Nombre (100), Telefono (8), Direccion (500), Descripcion (250), Estado.
 * Usado por CU-00, CU-03, CU-12, CU-13.
 */
@Component
@Order(2)
public class SucursalCatalogo extends CatalogoBase {

    private static final String ENTIDAD = "sucursal";

    private final SucursalRepository repository;
    private final SucursalService sucursalService;

    public SucursalCatalogo(AuditoriaService auditoria,
                            SucursalRepository repository,
                            SucursalService sucursalService) {
        super(auditoria);
        this.repository = repository;
        this.sucursalService = sucursalService;
    }

    @Override public String clave() { return "sucursales"; }
    @Override public String titulo() { return "Sucursales"; }
    @Override public String descripcion() { return "Sedes del hospital con su teléfono y dirección."; }
    @Override public List<String> columnas() { return List.of("Nombre", "Teléfono", "Dirección"); }

    @Override
    public List<Campo> campos(Integer id) {
        return List.of(
                texto("nombre", "Nombre", 100, true),
                texto("telefono", "Teléfono (8 dígitos)", 8, false),
                area("direccion", "Dirección", 500, false),
                area("descripcion", "Descripción", 250, false),
                estado());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Fila> listar(String texto, String estado, int pagina) {
        return repository.findByNombreContainingIgnoreCaseAndStateIn(filtro(texto), estados(estado), pagina(pagina, "nombre"))
                .map(s -> new Fila(s.getId(), s.getNombre(),
                        List.of(s.getNombre(), celda(s.getTelefono()), celda(s.getDireccion())),
                        s.estaActivo(), List.of(), true));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> valores(Integer id) {
        return repository.findById(id)
                .map(s -> mapa("nombre", s.getNombre(), "telefono", s.getTelefono(),
                        "direccion", s.getDireccion(), "descripcion", s.getDescripcion(), "state", s.getState()))
                .orElse(null);
    }

    @Override
    @Transactional
    public Resultado guardar(Integer id, Map<String, String> datos) {
        Sucursal s = id == null ? new Sucursal() : repository.findById(id).orElse(null);
        if (s == null) return Resultado.error("general", NO_EXISTE);

        // FA05: validaciones [RN-CU15-01] [RN-CU15-04]
        Map<String, String> errores = new LinkedHashMap<>();
        String nombre = limpiar(datos.get("nombre"));
        String telefono = limpiar(datos.get("telefono"));
        String direccion = limpiar(datos.get("direccion"));
        String descripcion = limpiar(datos.get("descripcion"));

        validarNombre(errores, nombre, 100);
        if (telefono != null && !telefono.matches("\\d{8}")) {
            errores.put("telefono", "El teléfono debe tener exactamente 8 dígitos.");
        }
        validarTexto(errores, "direccion", direccion, "La dirección", 500, false);
        validarTexto(errores, "descripcion", descripcion, "La descripción", 250, false);
        Short state = leerEstado(errores, datos.get("state"));

        if (errores.isEmpty() && state == ACTIVO
                && repository.existsByNombreIgnoreCaseAndStateAndIdNot(nombre, ACTIVO, idParaComparar(id))) {
            errores.put("nombre", duplicado(nombre));
        }
        if (!errores.isEmpty()) return Resultado.errores(errores);

        Map<String, Object> antes = id == null ? null : snapshot(s);

        s.setNombre(nombre);
        s.setTelefono(telefono);
        s.setDireccion(direccion);
        s.setDescripcion(descripcion);
        s.setState(state);
        marcarUsuario(s, id);
        s = repository.save(s);

        sucursalService.invalidarCache();
        auditoria.registrar(ENTIDAD, accion(id), s.getId(), antes, snapshot(s));
        return guardado(id, nombre);
    }

    @Override
    @Transactional
    public Resultado eliminar(Integer id) {
        Sucursal s = repository.findById(id).orElse(null);
        if (s == null || !s.estaActivo()) return Resultado.error("general", NO_EXISTE);

        Map<String, Object> antes = snapshot(s);
        s.setState(INACTIVO);
        marcarUsuario(s, id);
        repository.save(s);

        sucursalService.invalidarCache();
        auditoria.registrar(ENTIDAD, "ELIMINAR", s.getId(), antes, snapshot(s));
        return eliminado(s.getNombre());
    }

    private Map<String, Object> snapshot(Sucursal s) {
        return snapshot("id", s.getId(), "nombre", s.getNombre(), "telefono", s.getTelefono(),
                "direccion", s.getDireccion(), "descripcion", s.getDescripcion(), "state", s.getState());
    }
}