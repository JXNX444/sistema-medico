package gt.edu.umg.sistemamedico.service;

/**
 * Catalogo de reportes del sistema.
 *
 * Cada reporte es una constante con su titulo, sus columnas, el tipo de grafica
 * y el SQL que lo genera. Para agregar un reporte nuevo basta con agregar una
 * constante aqui: el repositorio, el servicio, el PDF, el Excel y la pantalla
 * lo toman solos.
 *
 * Reglas del SQL:
 *   - Debe devolver 3 columnas: etiqueta (texto), detalle (texto o NULL) y valor (numero).
 *   - Parametros disponibles:
 *       :desde     fecha inicial (date, incluida)
 *       :hasta     fecha final   (date, incluida)
 *       :sucursal  id de la sede, 0 = todas
 *       :top       cuantas filas mostrar (solo si usaTop = true)
 *   - Las fechas se comparan en hora de Guatemala.
 */
public enum TipoReporte {

    MEDICOS_TOP(
            "medicos-top",
            "Médicos que más atienden",
            "Consultas finalizadas por médico en el rango de fechas",
            "Médico", "Especialidad", "Consultas",
            Grafica.BARRAS_HORIZONTALES, false, true,
            """
            SELECT u.nombre_completo                AS etiqueta,
                   e.nombre                         AS detalle,
                   COUNT(*)                         AS valor
              FROM his.consulta c
              JOIN his.cita ci        ON ci.cita_id = c.cita_id
              JOIN his.usuario u      ON u.usuario_id = c.medico_id
              JOIN his.especialidad e ON e.especialidad_id = ci.especialidad_id
             WHERE c.state = 1
               AND c.estado_consulta = 1
               AND c.finalizada_en >= (CAST(:desde AS date)::timestamp AT TIME ZONE 'America/Guatemala')
               AND c.finalizada_en <  ((CAST(:hasta AS date) + 1)::timestamp AT TIME ZONE 'America/Guatemala')
               AND (:sucursal = 0 OR ci.sucursal_id = :sucursal)
             GROUP BY u.usuario_id, u.nombre_completo, e.nombre
             ORDER BY valor DESC, etiqueta
             LIMIT :top
            """),

    ESPECIALIDADES(
            "especialidades",
            "Especialidades más demandadas",
            "Citas agendadas por especialidad (sin contar las canceladas)",
            "Especialidad", null, "Citas",
            Grafica.BARRAS_HORIZONTALES, false, true,
            """
            SELECT e.nombre  AS etiqueta,
                   NULL      AS detalle,
                   COUNT(*)  AS valor
              FROM his.cita ci
              JOIN his.especialidad e ON e.especialidad_id = ci.especialidad_id
              JOIN his.estado_cita ec ON ec.estado_cita_id = ci.estado_cita_id
             WHERE ci.state = 1
               AND ec.codigo <> 'CANCELADA'
               AND ci.fecha_hora >= (CAST(:desde AS date)::timestamp AT TIME ZONE 'America/Guatemala')
               AND ci.fecha_hora <  ((CAST(:hasta AS date) + 1)::timestamp AT TIME ZONE 'America/Guatemala')
               AND (:sucursal = 0 OR ci.sucursal_id = :sucursal)
             GROUP BY e.especialidad_id, e.nombre
             ORDER BY valor DESC, etiqueta
             LIMIT :top
            """),

    CITAS_ESTADO(
            "citas-estado",
            "Citas por estado",
            "Cuántas citas hay en cada estado (atendidas, canceladas, no asistió...)",
            "Estado", null, "Citas",
            Grafica.DONA, false, false,
            """
            SELECT ec.nombre AS etiqueta,
                   NULL      AS detalle,
                   COUNT(*)  AS valor
              FROM his.cita ci
              JOIN his.estado_cita ec ON ec.estado_cita_id = ci.estado_cita_id
             WHERE ci.state = 1
               AND ci.fecha_hora >= (CAST(:desde AS date)::timestamp AT TIME ZONE 'America/Guatemala')
               AND ci.fecha_hora <  ((CAST(:hasta AS date) + 1)::timestamp AT TIME ZONE 'America/Guatemala')
               AND (:sucursal = 0 OR ci.sucursal_id = :sucursal)
             GROUP BY ec.estado_cita_id, ec.nombre, ec.orden
             ORDER BY valor DESC, ec.orden
            """),

    INGRESOS_SEDE(
            "ingresos-sede",
            "Ingresos por sede",
            "Total cobrado (pagos aprobados de citas y laboratorio) por sede",
            "Sede", null, "Ingresos (Q)",
            Grafica.BARRAS, true, false,
            """
            SELECT COALESCE(s.nombre, 'Sin sede') AS etiqueta,
                   NULL                           AS detalle,
                   SUM(p.monto)                   AS valor
              FROM his.pago p
              LEFT JOIN his.sucursal s ON s.sucursal_id = p.sucursal_id
             WHERE p.state = 1
               AND p.estado_pago = 1
               AND p.created_at >= (CAST(:desde AS date)::timestamp AT TIME ZONE 'America/Guatemala')
               AND p.created_at <  ((CAST(:hasta AS date) + 1)::timestamp AT TIME ZONE 'America/Guatemala')
               AND (:sucursal = 0 OR p.sucursal_id = :sucursal)
             GROUP BY s.sucursal_id, s.nombre
             ORDER BY valor DESC, etiqueta
            """),

    INGRESOS_METODO(
            "ingresos-metodo",
            "Ingresos por método de pago",
            "Total cobrado por método de pago (efectivo, tarjetas)",
            "Método de pago", null, "Ingresos (Q)",
            Grafica.DONA, true, false,
            """
            SELECT mp.nombre      AS etiqueta,
                   NULL           AS detalle,
                   SUM(p.monto)   AS valor
              FROM his.pago p
              JOIN his.metodo_pago mp ON mp.metodo_pago_id = p.metodo_pago
             WHERE p.state = 1
               AND p.estado_pago = 1
               AND p.created_at >= (CAST(:desde AS date)::timestamp AT TIME ZONE 'America/Guatemala')
               AND p.created_at <  ((CAST(:hasta AS date) + 1)::timestamp AT TIME ZONE 'America/Guatemala')
               AND (:sucursal = 0 OR p.sucursal_id = :sucursal)
             GROUP BY mp.metodo_pago_id, mp.nombre
             ORDER BY valor DESC, etiqueta
            """),

    CONSULTAS_MES(
            "consultas-mes",
            "Consultas atendidas por mes",
            "Tendencia mensual de consultas finalizadas",
            "Mes", null, "Consultas",
            Grafica.LINEA, false, false,
            """
            SELECT to_char(date_trunc('month', c.finalizada_en AT TIME ZONE 'America/Guatemala'), 'YYYY-MM') AS etiqueta,
                   NULL      AS detalle,
                   COUNT(*)  AS valor
              FROM his.consulta c
              JOIN his.cita ci ON ci.cita_id = c.cita_id
             WHERE c.state = 1
               AND c.estado_consulta = 1
               AND c.finalizada_en >= (CAST(:desde AS date)::timestamp AT TIME ZONE 'America/Guatemala')
               AND c.finalizada_en <  ((CAST(:hasta AS date) + 1)::timestamp AT TIME ZONE 'America/Guatemala')
               AND (:sucursal = 0 OR ci.sucursal_id = :sucursal)
             GROUP BY 1
             ORDER BY 1
            """),

    MEDICAMENTOS_TOP(
            "medicamentos-top",
            "Medicamentos más despachados",
            "Unidades entregadas en farmacia por medicamento",
            "Medicamento", "Unidad", "Unidades",
            Grafica.BARRAS_HORIZONTALES, false, true,
            """
            SELECT m.nombre            AS etiqueta,
                   m.unidad            AS detalle,
                   SUM(dd.cantidad)    AS valor
              FROM his.despacho_detalle dd
              JOIN his.despacho d     ON d.despacho_id = dd.despacho_id
              JOIN his.medicamento m  ON m.medicamento_id = dd.medicamento_id
             WHERE d.state = 1
               AND d.estado_despacho = 0
               AND d.created_at >= (CAST(:desde AS date)::timestamp AT TIME ZONE 'America/Guatemala')
               AND d.created_at <  ((CAST(:hasta AS date) + 1)::timestamp AT TIME ZONE 'America/Guatemala')
               AND (:sucursal = 0 OR d.sucursal_id = :sucursal)
             GROUP BY m.medicamento_id, m.nombre, m.unidad
             ORDER BY valor DESC, etiqueta
             LIMIT :top
            """),

    DIAGNOSTICOS_TOP(
            "diagnosticos-top",
            "Diagnósticos más frecuentes (CIE-10)",
            "Consultas finalizadas por código CIE-10",
            "Diagnóstico", "Código", "Consultas",
            Grafica.BARRAS_HORIZONTALES, false, true,
            """
            SELECT cie.descripcion  AS etiqueta,
                   cie.codigo       AS detalle,
                   COUNT(*)         AS valor
              FROM his.consulta c
              JOIN his.cita ci   ON ci.cita_id = c.cita_id
              JOIN his.cie10 cie ON cie.cie10_id = c.cie10_id
             WHERE c.state = 1
               AND c.estado_consulta = 1
               AND c.finalizada_en >= (CAST(:desde AS date)::timestamp AT TIME ZONE 'America/Guatemala')
               AND c.finalizada_en <  ((CAST(:hasta AS date) + 1)::timestamp AT TIME ZONE 'America/Guatemala')
               AND (:sucursal = 0 OR ci.sucursal_id = :sucursal)
             GROUP BY cie.cie10_id, cie.descripcion, cie.codigo
             ORDER BY valor DESC, etiqueta
             LIMIT :top
            """);

    /** Tipo de la grafica principal. La segunda grafica la decide graficaSecundaria(). */
    public enum Grafica { BARRAS, BARRAS_HORIZONTALES, LINEA, DONA }

    private final String slug;
    private final String titulo;
    private final String descripcion;
    private final String columnaEtiqueta;
    private final String columnaDetalle;   // null = el reporte no tiene columna de detalle
    private final String columnaValor;
    private final Grafica grafica;
    private final boolean moneda;          // true = el valor son quetzales
    private final boolean usaTop;          // true = el filtro "Mostrar Top N" aplica
    private final String sql;

    TipoReporte(String slug, String titulo, String descripcion,
                String columnaEtiqueta, String columnaDetalle, String columnaValor,
                Grafica grafica, boolean moneda, boolean usaTop, String sql) {
        this.slug = slug;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.columnaEtiqueta = columnaEtiqueta;
        this.columnaDetalle = columnaDetalle;
        this.columnaValor = columnaValor;
        this.grafica = grafica;
        this.moneda = moneda;
        this.usaTop = usaTop;
        this.sql = sql;
    }

    /** Busca el reporte por su slug de la URL (/reportes/medicos-top). Devuelve null si no existe. */
    public static TipoReporte porSlug(String slug) {
        for (TipoReporte t : values()) {
            if (t.slug.equalsIgnoreCase(slug)) {
                return t;
            }
        }
        return null;
    }

    /** Segunda grafica: la participacion (dona) o, si la principal ya es dona, barras. */
    public Grafica graficaSecundaria() {
        return switch (grafica) {
            case DONA, LINEA -> Grafica.BARRAS;
            default -> Grafica.DONA;
        };
    }

    public String getSlug() { return slug; }
    public String getTitulo() { return titulo; }
    public String getDescripcion() { return descripcion; }
    public String getColumnaEtiqueta() { return columnaEtiqueta; }
    public String getColumnaDetalle() { return columnaDetalle; }
    public boolean isTieneDetalle() { return columnaDetalle != null; }
    public String getColumnaValor() { return columnaValor; }
    public Grafica getGrafica() { return grafica; }
    public boolean isMoneda() { return moneda; }
    public boolean isUsaTop() { return usaTop; }
    public String getSql() { return sql; }
}