package gt.edu.umg.sistemamedico.service.catalogo;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;

/**
 * CU-14 Mantenimiento de Catalogos del Sistema. Rol: Administrador.
 *
 * Contrato que cumple CADA catalogo (Especialidades, Sucursales, Estados de
 * Cita, Laboratorios, Examenes, Medicamentos, Roles e Inventario).
 *
 * Gracias a esta interfaz hay UN solo controller (CatalogoController) y
 * DOS paginas (listado y formulario) para los 8 catalogos: cada catalogo
 * solo dice que columnas muestra, que campos pide y como valida y guarda.
 *
 * Sucursal-Especialidad NO esta aqui: ya la cubre CU-13 (/sedes-especialidades).
 */
public interface Catalogo {

    /** Va en la URL: /catalogos/{clave}. Ej: "especialidades". */
    String clave();

    /** Titulo de la pantalla. Ej: "Especialidades". */
    String titulo();

    /** Texto corto de la tarjeta en el menu de catalogos. */
    String descripcion();

    /** Encabezados de la tabla, sin ID, Estado ni Acciones (esas las pone la pagina). */
    List<String> columnas();

    /**
     * [Paso 4 / FA02 paso 2] Campos del formulario (tabla 2.3).
     * @param id null = crear; un numero = editar ese registro
     */
    List<Campo> campos(Integer id);

    /**
     * [Paso 2 / FA01] Listado con filtro de busqueda y paginacion.
     * @param texto  busca en el nombre (vacio = todos)
     * @param estado "activos" (default), "inactivos" o "todos"
     * @param pagina empieza en 0
     */
    Page<Fila> listar(String texto, String estado, int pagina);

    /** [FA02 paso 2] Valores actuales para precargar el formulario. null si no existe. */
    Map<String, String> valores(Integer id);

    /** [Pasos 6-8 / FA02 pasos 4-5 / FA05] Valida y crea (id null) o actualiza. */
    Resultado guardar(Integer id, Map<String, String> datos);

    /** [FA03] Eliminacion logica (state = 0). */
    Resultado eliminar(Integer id);

    // =====================================================================
    // Tipos que usan las paginas
    // =====================================================================

    /** Una opcion de un dropdown. */
    record Opcion(String valor, String texto) {}

    /**
     * Un campo del formulario.
     * tipo: "texto", "area", "decimal", "entero", "lista" o "casilla".
     * soloLectura: se muestra pero no se puede cambiar (ej: la sede de un inventario al editar).
     */
    record Campo(String nombre, String etiqueta, String tipo, int max,
                 boolean obligatorio, boolean soloLectura, List<Opcion> opciones) {

        /** Copia del campo, pero bloqueado. */
        public Campo bloqueado() {
            return new Campo(nombre, etiqueta, tipo, max, obligatorio, true, opciones);
        }
    }

    /**
     * Una fila de la tabla.
     * avisos: etiquetas que se pintan junto al nombre (ej: "Controlado", "Stock bajo").
     * eliminable: false para los registros que usa el sistema (ej: el rol Administrador).
     */
    record Fila(Integer id, String nombre, List<String> celdas, boolean activo,
                List<String> avisos, boolean eliminable) {}

    /** Resultado de guardar o eliminar: ok + mensaje, o los errores por campo. */
    record Resultado(boolean ok, String mensaje, Map<String, String> errores) {

        public static Resultado exito(String mensaje) {
            return new Resultado(true, mensaje, Map.of());
        }

        public static Resultado error(String campo, String mensaje) {
            return new Resultado(false, null, Map.of(campo, mensaje));
        }

        public static Resultado errores(Map<String, String> errores) {
            return new Resultado(false, null, errores);
        }
    }
}