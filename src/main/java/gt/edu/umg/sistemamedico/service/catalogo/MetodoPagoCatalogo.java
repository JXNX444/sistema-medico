package gt.edu.umg.sistemamedico.service.catalogo;

import gt.edu.umg.sistemamedico.domain.MetodoPago;
import gt.edu.umg.sistemamedico.repository.MetodoPagoRepository;
import gt.edu.umg.sistemamedico.service.AuditoriaService;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CU-14 Catalogo de Metodos de Pago.
 * Campos: Nombre (100), Descripcion (500), Estado.  Usado por CU-04, CU-06 y CU-10.
 *
 * Los 4 metodos base (ids 0-3) los usa el cobro por su NUMERO
 * (Pago.METODO_EFECTIVO = 0 ... METODO_DEBITO = 3). Por eso a esos no se les
 * puede desactivar ni eliminar: solo cambiar nombre y descripcion.
 */
@Component
@Order(10)
public class MetodoPagoCatalogo extends CatalogoBase {

    private static final String ENTIDAD = "metodo_pago";

    /** Ids que usa el codigo de cobro (Pago.java lineas 34-37). */
    private static final short ULTIMO_ID_SISTEMA = 3;

    private final MetodoPagoRepository repository;

    public MetodoPagoCatalogo(AuditoriaService auditoria, MetodoPagoRepository repository) {
        super(auditoria);
        this.repository = repository;
    }

    @Override public String clave() { return "metodos-pago"; }
    @Override public String titulo() { return "Métodos de Pago"; }
    @Override public String descripcion() { return "Formas de pago que acepta la caja y el pago en línea."; }
    @Override public List<String> columnas() { return List.of("Código", "Nombre", "Descripción"); }

    @Override
    public List<Campo> campos(Integer id) {
        boolean sistema = id != null && esDelSistema(id.shortValue());
        Campo estado = estado();
        return List.of(
                texto("nombre", "Nombre", 100, true),
                area("descripcion", "Descripción", 500, false),
                sistema ? estado.bloqueado() : estado);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Fila> listar(String texto, String estado, int pagina) {
        return repository.findByNombreContainingIgnoreCaseAndStateIn(filtro(texto), estados(estado), pagina(pagina, "id"))
                .map(m -> new Fila(m.getId().intValue(), m.getNombre(),
                        List.of(String.valueOf(m.getId()), m.getNombre(), celda(m.getDescripcion())),
                        m.estaActivo(),
                        esDelSistema(m.getId()) ? List.of("Sistema") : List.of(),
                        !esDelSistema(m.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> valores(Integer id) {
        return repository.findById(id.shortValue())
                .map(m -> mapa("nombre", m.getNombre(), "descripcion", m.getDescripcion(), "state", m.getState()))
                .orElse(null);
    }

    @Override
    @Transactional
    public Resultado guardar(Integer id, Map<String, String> datos) {
        Short sid = id == null ? null : id.shortValue();
        MetodoPago m = sid == null ? new MetodoPago() : repository.findById(sid).orElse(null);
        if (m == null) return Resultado.error("general", NO_EXISTE);
        boolean sistema = sid != null && esDelSistema(sid);

        String nombre = limpiar(datos.get("nombre"));
        String descripcion = limpiar(datos.get("descripcion"));

        // FA05: validaciones [RN-CU15-01]
        Map<String, String> errores = new LinkedHashMap<>();
        validarNombre(errores, nombre, 100);
        validarTexto(errores, "descripcion", descripcion, "La descripción", 500, false);
        // Metodo del sistema: siempre queda activo (el estado viene bloqueado en el formulario)
        Short state = sistema ? Short.valueOf(ACTIVO) : leerEstado(errores, datos.get("state"));

        // -1 cuando es nuevo: el 0 ya es un id real (Efectivo)
        Short idComparar = sid == null ? (short) -1 : sid;
        if (errores.isEmpty() && state == ACTIVO
                && repository.existsByNombreIgnoreCaseAndStateAndIdNot(nombre, ACTIVO, idComparar)) {
            errores.put("nombre", duplicado(nombre));
        }
        if (!errores.isEmpty()) return Resultado.errores(errores);

        Map<String, Object> antes = sid == null ? null : snapshot(m);

        m.setNombre(nombre);
        m.setDescripcion(descripcion);
        m.setState(state);
        marcarUsuario(m, id);
        m = repository.save(m);

        auditoria.registrar(ENTIDAD, accion(id), m.getId(), antes, snapshot(m));
        return guardado(id, nombre);
    }

    @Override
    @Transactional
    public Resultado eliminar(Integer id) {
        MetodoPago m = repository.findById(id.shortValue()).orElse(null);
        if (m == null || !m.estaActivo()) return Resultado.error("general", NO_EXISTE);
        if (esDelSistema(m.getId())) {
            return Resultado.error("general", "El método " + m.getNombre() + " lo usa el cobro y no se puede eliminar.");
        }

        Map<String, Object> antes = snapshot(m);
        m.setState(INACTIVO);
        marcarUsuario(m, id);
        repository.save(m);

        auditoria.registrar(ENTIDAD, "ELIMINAR", m.getId(), antes, snapshot(m));
        return eliminado(m.getNombre());
    }

    /** true si es uno de los 4 metodos base (0 Efectivo ... 3 Debito). */
    private boolean esDelSistema(Short id) {
        return id != null && id >= 0 && id <= ULTIMO_ID_SISTEMA;
    }

    private Map<String, Object> snapshot(MetodoPago m) {
        return snapshot("id", m.getId(), "nombre", m.getNombre(), "descripcion", m.getDescripcion(), "state", m.getState());
    }
}