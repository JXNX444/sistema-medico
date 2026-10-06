package gt.edu.umg.sistemamedico.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * Item despachado de una receta. Tabla: his.despacho_detalle  [CU-11]
 *
 * FA02 (sustitucion): la BD exige (ck_despdet_sustitucion) que si
 * esSustitucion = true, vengan razonSustitucion y medicamentoOriginal;
 * y si es false, ambos vayan en null.
 */
@Entity
@Table(name = "despacho_detalle", schema = "his")
public class DespachoDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "despacho_detalle_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "despacho_id", nullable = false)
    private Despacho despacho;

    /** Medicamento que realmente se entrega (el alternativo si hubo sustitucion). */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "medicamento_id", nullable = false)
    private Medicamento medicamento;

    /** ck_despdet_cantidad: > 0 */
    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    /** ck_despdet_precio: >= 0 */
    @Column(name = "precio_unitario", nullable = false)
    private BigDecimal precioUnitario;

    @Column(name = "es_sustitucion", nullable = false)
    private boolean esSustitucion = false;

    /** Medicamento recetado originalmente (solo si hubo sustitucion). */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "medicamento_original_id")
    private Medicamento medicamentoOriginal;

    @Column(name = "razon_sustitucion", length = 500)
    private String razonSustitucion;

    /** cantidad x precio unitario. */
    public BigDecimal getSubtotal() {
        return precioUnitario.multiply(BigDecimal.valueOf(cantidad));
    }

    // ---- Getters / setters ----

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Despacho getDespacho() { return despacho; }
    public void setDespacho(Despacho despacho) { this.despacho = despacho; }
    public Medicamento getMedicamento() { return medicamento; }
    public void setMedicamento(Medicamento medicamento) { this.medicamento = medicamento; }
    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(BigDecimal precioUnitario) { this.precioUnitario = precioUnitario; }
    public boolean isEsSustitucion() { return esSustitucion; }
    public void setEsSustitucion(boolean esSustitucion) { this.esSustitucion = esSustitucion; }
    public Medicamento getMedicamentoOriginal() { return medicamentoOriginal; }
    public void setMedicamentoOriginal(Medicamento medicamentoOriginal) { this.medicamentoOriginal = medicamentoOriginal; }
    public String getRazonSustitucion() { return razonSustitucion; }
    public void setRazonSustitucion(String razonSustitucion) { this.razonSustitucion = razonSustitucion; }
}