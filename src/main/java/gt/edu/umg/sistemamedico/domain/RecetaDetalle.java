package gt.edu.umg.sistemamedico.domain;

import jakarta.persistence.*;

/**
 * Un medicamento dentro de una receta. Tabla: his.receta_detalle
 * [CU-08 FA04 paso 3 / RN-CU08-03] medicamento, dosis, frecuencia,
 * duracion, cantidad e indicaciones.
 *
 * No tiene row_version ni created_at: es parte de la receta y no se
 * edita por separado.
 */
@Entity
@Table(name = "receta_detalle", schema = "his")
public class RecetaDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "receta_detalle_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receta_id", nullable = false)
    private Receta receta;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "medicamento_id", nullable = false)
    private Medicamento medicamento;

    /** Ej: "500 mg". */
    @Column(name = "dosis", nullable = false, length = 100)
    private String dosis;

    /** Ej: "Cada 8 horas". */
    @Column(name = "frecuencia", nullable = false, length = 100)
    private String frecuencia;

    /** Ej: "7 dias". */
    @Column(name = "duracion", nullable = false, length = 100)
    private String duracion;

    /** Unidades a despachar en farmacia. Mayor que 0. */
    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    @Column(name = "indicaciones", length = 500)
    private String indicaciones;

    @Column(name = "state", nullable = false)
    private Short state = 1;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Receta getReceta() { return receta; }
    public void setReceta(Receta receta) { this.receta = receta; }
    public Medicamento getMedicamento() { return medicamento; }
    public void setMedicamento(Medicamento medicamento) { this.medicamento = medicamento; }
    public String getDosis() { return dosis; }
    public void setDosis(String dosis) { this.dosis = dosis; }
    public String getFrecuencia() { return frecuencia; }
    public void setFrecuencia(String frecuencia) { this.frecuencia = frecuencia; }
    public String getDuracion() { return duracion; }
    public void setDuracion(String duracion) { this.duracion = duracion; }
    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
    public String getIndicaciones() { return indicaciones; }
    public void setIndicaciones(String indicaciones) { this.indicaciones = indicaciones; }
    public Short getState() { return state; }
    public void setState(Short state) { this.state = state; }
}