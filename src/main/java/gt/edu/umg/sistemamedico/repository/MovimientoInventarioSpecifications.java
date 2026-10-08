package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.MovimientoInventario;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;

/**
 * Filtros de la bitacora de inventario [CU-15 paso 2]:
 * Tipo, Medicamento, Sucursal, Numero de Referencia, Usuario y rango de fechas.
 *
 * Cada metodo devuelve un pedacito del WHERE, o null si ese filtro viene
 * vacio (Spring ignora los null al combinarlos con .and()).
 * Mismo estilo que UsuarioSpecifications de CU-01.
 */
public final class MovimientoInventarioSpecifications {

    private MovimientoInventarioSpecifications() {
    }

    public static Specification<MovimientoInventario> tipo(Short tipo) {
        if (tipo == null) return null;
        return (root, query, cb) -> cb.equal(root.get("tipoMovimiento"), tipo);
    }

    public static Specification<MovimientoInventario> medicamento(Integer medicamentoId) {
        if (medicamentoId == null) return null;
        return (root, query, cb) -> cb.equal(root.get("medicamentoId"), medicamentoId);
    }

    public static Specification<MovimientoInventario> sucursal(Integer sucursalId) {
        if (sucursalId == null) return null;
        return (root, query, cb) -> cb.equal(root.get("sucursalId"), sucursalId);
    }

    public static Specification<MovimientoInventario> referencia(String texto) {
        if (texto == null || texto.isBlank()) return null;
        String like = "%" + texto.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("numeroReferencia")), like);
    }

    /** Busca en el usuario (login) o en el nombre completo de quien registro. */
    public static Specification<MovimientoInventario> usuario(String texto) {
        if (texto == null || texto.isBlank()) return null;
        String like = "%" + texto.trim().toLowerCase() + "%";
        return (root, query, cb) -> {
            var usuario = root.join("usuario");
            return cb.or(
                    cb.like(cb.lower(usuario.get("username")), like),
                    cb.like(cb.lower(usuario.get("nombreCompleto")), like));
        };
    }

    /** Desde (incluido). */
    public static Specification<MovimientoInventario> desde(OffsetDateTime desde) {
        if (desde == null) return null;
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), desde);
    }

    /** Hasta (NO incluido): se le pasa el inicio del dia siguiente. */
    public static Specification<MovimientoInventario> hasta(OffsetDateTime hasta) {
        if (hasta == null) return null;
        return (root, query, cb) -> cb.lessThan(root.get("createdAt"), hasta);
    }
}