package gt.edu.umg.sistemamedico.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * Stock de un medicamento en una sucursal. Tabla: his.inventario_medicamento  [CU-11]
 *
 * - ux_inv_med_sucursal: una sola fila por (medicamento, sucursal).
 * - ck_inv_stock: stock_actual >= 0 (la BD no deja quedar en negativo).
 * - updated_at lo llena el trigger tr_inventario_medicamento_updated_at.
 *
 * No extiende BaseEntity: la tabla no tiene created_by ni updated_by.
 */
@Entity
@Table(name = "inventario_medicamento", schema = "his")
public class InventarioMedicamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inventario_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "medicamento_id", nullable = false)
    private Medicamento medicamento;

    @Column(name = "sucursal_id", nullable = false)
    private Integer sucursalId;

    @Column(name = "stock_actual", nullable = false)
    private Integer stockActual = 0;

    @Column(name = "state", nullable = false)
    private Short state = 1;

    /** Bloqueo optimista: si dos farmaceuticos despachan a la vez, uno falla en vez de descuadrar el stock. */
    @Version
    @Column(name = "row_version", nullable = false)
    private Integer rowVersion = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void alCrear() {
        this.createdAt = OffsetDateTime.now();
    }

    /**
     * [RN-CU10-03] true si el stock esta en o por debajo del minimo del catalogo.
     * Si el medicamento no tiene minimo definido, nunca alerta.
     */
    public boolean esStockBajo() {
        Integer minimo = medicamento.getStockMinimo();
        return minimo != null && stockActual <= minimo;
    }

    // ---- Getters / setters ----

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Medicamento getMedicamento() { return medicamento; }
    public void setMedicamento(Medicamento medicamento) { this.medicamento = medicamento; }
    public Integer getSucursalId() { return sucursalId; }
    public void setSucursalId(Integer sucursalId) { this.sucursalId = sucursalId; }
    public Integer getStockActual() { return stockActual; }
    public void setStockActual(Integer stockActual) { this.stockActual = stockActual; }
    public Short getState() { return state; }
    public void setState(Short state) { this.state = state; }
    public Integer getRowVersion() { return rowVersion; }
    public void setRowVersion(Integer rowVersion) { this.rowVersion = rowVersion; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}