package gt.edu.umg.sistemamedico.service.catalogo;

import gt.edu.umg.sistemamedico.domain.BaseEntity;
import gt.edu.umg.sistemamedico.security.UsuarioDetails;
import gt.edu.umg.sistemamedico.service.AuditoriaService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lo que TODOS los catalogos de CU-14 hacen igual: paginacion, filtro por
 * estado, validaciones comunes [RN-CU15-01], mensajes del flujo y
 * auditoria. Cada catalogo extiende esta clase y solo escribe lo suyo.
 *
 * "abstract": no se puede usar sola, solo a traves de sus hijas.
 */
public abstract class CatalogoBase implements Catalogo {

    public static final short ACTIVO = 1;
    public static final short INACTIVO = 0;

    private static final int TAM_PAGINA = 10;

    protected static final String NO_EXISTE = "El registro no existe o ya fue eliminado.";

    protected final AuditoriaService auditoria;

    protected CatalogoBase(AuditoriaService auditoria) {
        this.auditoria = auditoria;
    }

    // ---------- Listado (paso 2) ----------

    /** Traduce el filtro de la pantalla a los valores de state que se buscan. */
    protected Collection<Short> estados(String estado) {
        if ("inactivos".equals(estado)) return List.of(INACTIVO);
        if ("todos".equals(estado)) return List.of(ACTIVO, INACTIVO);
        return List.of(ACTIVO);   // por defecto solo activos
    }

    /** Pagina de 10 registros ordenada por la propiedad indicada. */
    protected Pageable pagina(int pagina, String ordenarPor) {
        return PageRequest.of(Math.max(pagina, 0), TAM_PAGINA, Sort.by(ordenarPor).ascending());
    }

    /** El texto del filtro nunca va null (el repositorio hace un LIKE con el). */
    protected String filtro(String texto) {
        return texto == null ? "" : texto.trim();
    }

    // ---------- Campos del formulario (paso 4) ----------

    protected Campo texto(String nombre, String etiqueta, int max, boolean obligatorio) {
        return new Campo(nombre, etiqueta, "texto", max, obligatorio, false, List.of());
    }

    protected Campo area(String nombre, String etiqueta, int max, boolean obligatorio) {
        return new Campo(nombre, etiqueta, "area", max, obligatorio, false, List.of());
    }

    protected Campo decimal(String nombre, String etiqueta, boolean obligatorio) {
        return new Campo(nombre, etiqueta, "decimal", 13, obligatorio, false, List.of());
    }

    protected Campo entero(String nombre, String etiqueta, boolean obligatorio) {
        return new Campo(nombre, etiqueta, "entero", 9, obligatorio, false, List.of());
    }

    protected Campo lista(String nombre, String etiqueta, List<Opcion> opciones) {
        return new Campo(nombre, etiqueta, "lista", 0, true, false, opciones);
    }

    protected Campo casilla(String nombre, String etiqueta) {
        return new Campo(nombre, etiqueta, "casilla", 0, false, false, List.of());
    }

    /** [RN-CU15-01] Estado obligatorio: 1 = Activo, 0 = Inactivo. */
    protected Campo estado() {
        return lista("state", "Estado", List.of(new Opcion("1", "Activo"), new Opcion("0", "Inactivo")));
    }

    // ---------- Validaciones comunes [RN-CU15-01] (FA05) ----------

    /** Quita espacios; si queda vacio devuelve null. */
    protected String limpiar(String texto) {
        if (texto == null) return null;
        String t = texto.trim();
        return t.isEmpty() ? null : t;
    }

    /** Nombre obligatorio y con largo maximo segun el catalogo (tabla 2.3). */
    protected void validarNombre(Map<String, String> errores, String nombre, int max) {
        if (nombre == null) {
            errores.put("nombre", "El nombre es obligatorio.");
        } else if (nombre.length() > max) {
            errores.put("nombre", "El nombre no puede exceder " + max + " caracteres.");
        }
    }

    /**
     * Texto opcional u obligatorio con largo maximo.
     * @param sujeto como se nombra en el mensaje, ej: "La descripción"
     */
    protected void validarTexto(Map<String, String> errores, String campo, String valor,
                                String sujeto, int max, boolean obligatorio) {
        if (valor == null) {
            if (obligatorio) errores.put(campo, sujeto + " es obligatoria.");
        } else if (valor.length() > max) {
            errores.put(campo, sujeto + " no puede exceder " + max + " caracteres.");
        }
    }

    /** Estado 1 o 0. Si viene otra cosa, error. */
    protected Short leerEstado(Map<String, String> errores, String valor) {
        if ("1".equals(valor)) return ACTIVO;
        if ("0".equals(valor)) return INACTIVO;
        errores.put("state", "Debe seleccionar el estado.");
        return null;
    }

    /**
     * Precio obligatorio, mayor a 0, precision (10,2) [RN-CU15-02 / RN-CU15-03].
     * @param mensaje el mensaje del documento para ese catalogo
     */
    protected BigDecimal leerPrecio(Map<String, String> errores, String campo, String valor, String mensaje) {
        String limpio = limpiar(valor);
        try {
            BigDecimal precio = new BigDecimal(limpio);
            if (precio.compareTo(BigDecimal.ZERO) <= 0) {
                errores.put(campo, mensaje);
                return null;
            }
            if (precio.scale() > 2 || precio.compareTo(new BigDecimal("99999999.99")) > 0) {
                errores.put(campo, "El precio admite hasta 8 enteros y 2 decimales.");
                return null;
            }
            return precio;
        } catch (NullPointerException | NumberFormatException e) {
            errores.put(campo, mensaje);
            return null;
        }
    }

    /**
     * Entero mayor o igual a 0.
     * @return null si viene vacio (y no es obligatorio) o si hay error
     */
    protected Integer leerEnteroNoNegativo(Map<String, String> errores, String campo, String valor,
                                           boolean obligatorio, String msgObligatorio, String msgInvalido) {
        String limpio = limpiar(valor);
        if (limpio == null) {
            if (obligatorio) errores.put(campo, msgObligatorio);
            return null;
        }
        try {
            int numero = Integer.parseInt(limpio);
            if (numero < 0) {
                errores.put(campo, msgInvalido);
                return null;
            }
            return numero;
        } catch (NumberFormatException e) {
            errores.put(campo, msgInvalido);
            return null;
        }
    }

    /** Lee el id de un dropdown. null si viene vacio o no es numero. */
    protected Integer leerId(String valor) {
        try {
            return Integer.valueOf(valor.trim());
        } catch (NullPointerException | NumberFormatException e) {
            return null;
        }
    }

    /** Para existsBy...IdNot: si es nuevo no hay id, se usa 0 (ningun registro tiene id 0). */
    protected Integer idParaComparar(Integer id) {
        return id == null ? 0 : id;
    }

    // ---------- Mensajes del flujo ----------

    /** [RN-CU15-01] */
    protected String duplicado(String nombre) {
        return "Ya existe un registro con el nombre " + nombre + ".";
    }

    /** Paso 8 (crear) o FA02 paso 5 (editar). */
    protected Resultado guardado(Integer idOriginal, String nombre) {
        return idOriginal == null
                ? Resultado.exito("El registro " + nombre + " ha sido creado exitosamente.")
                : Resultado.exito("El registro " + nombre + " ha sido actualizado correctamente.");
    }

    /** FA03 paso 4. */
    protected Resultado eliminado(String nombre) {
        return Resultado.exito("El registro " + nombre + " ha sido eliminado correctamente.");
    }

    // ---------- Auditoria (postcondicion) ----------

    /** CREAR si es nuevo, EDITAR si ya existia. */
    protected String accion(Integer idOriginal) {
        return idOriginal == null ? "CREAR" : "EDITAR";
    }

    /** Llena created_by (si es nuevo) o updated_by (si se edita) con el administrador logueado. */
    protected void marcarUsuario(BaseEntity entidad, Integer idOriginal) {
        if (idOriginal == null) {
            entidad.setCreatedBy(usuarioActualId());
        } else {
            entidad.setUpdatedBy(usuarioActualId());
        }
    }

    /** Id del usuario en sesion (null si no hay sesion). */
    protected Integer usuarioActualId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioDetails ud) {
            return ud.getUsuario().getId();
        }
        return null;
    }

    /**
     * Arma un mapa con pares clave/valor: mapa("nombre", "X", "state", 1).
     * Los null se guardan como "" (el formulario no sabe mostrar null).
     */
    protected Map<String, String> mapa(Object... pares) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            Object valor = pares[i + 1];
            m.put(pares[i].toString(), valor == null ? "" : valor.toString());
        }
        return m;
    }

    /** Lo mismo que mapa(), pero con Object para la bitacora (datos_antes / datos_despues). */
    protected Map<String, Object> snapshot(Object... pares) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            m.put(pares[i].toString(), pares[i + 1]);
        }
        return m;
    }

    /** Texto para la tabla: "" si viene null. */
    protected String celda(Object valor) {
        return valor == null ? "" : valor.toString();
    }

    /** Precio con 2 decimales y Q delante, para la tabla. */
    protected String quetzales(BigDecimal valor) {
        return valor == null ? "" : "Q " + valor.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}