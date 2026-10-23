package gt.edu.umg.sistemamedico.web;

import gt.edu.umg.sistemamedico.domain.Usuario;
import org.springframework.data.jpa.domain.Specification;

/** Traduce el filtro dinamico del listado (campo + valor) a una consulta JPA. [RN-CU01-01] */
public final class UsuarioSpecifications {

    private UsuarioSpecifications() {
    }

    public static Specification<Usuario> porCampo(String campo, String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String like = "%" + valor.trim().toLowerCase() + "%";

        return switch (campo) {
            case "nombre" -> (root, query, cb) ->
                    cb.like(cb.lower(root.get("nombreCompleto")), like);
            case "nit" -> (root, query, cb) ->
                    cb.like(cb.lower(root.get("nit")), like);
            case "rol" -> (root, query, cb) ->
                    cb.like(cb.lower(root.join("rol").get("nombre")), like);
            case "sucursal" -> (root, query, cb) ->
                    cb.like(cb.lower(root.join("sucursal").get("nombre")), like);
            default -> (root, query, cb) ->
                    cb.like(cb.lower(root.get("username")), like);
        };
    }
}
