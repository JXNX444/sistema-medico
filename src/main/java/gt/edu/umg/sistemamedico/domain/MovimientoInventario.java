package gt.edu.umg.sistemamedico.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Kardex: cada entrada o salida de inventario. Tabla: his.movimiento_inventario  [CU-11]
 *
 * ck_mov_coherencia: tipos 0,1,4 SUMAN (stock_nuevo = anterior + cantidad);
 *                    tipos 2,3,5,6 RESTAN (stock_nuevo = anterior - cantidad).
 * ck_mov_motivo: los tipos 1,3,4,5 exigen motivo (10-500 caracteres).
 * ck_mov_costo:  el tipo 0 exige costo_unitario > 0.
 *
 * CU-11 solo usa SALIDA_DESPACHO (2): resta y no exige motivo ni costo.
 */
@Entity
@Table(name = "movimiento_inventario", schema = "his")
public class MovimientoInventario {

    /** tipo_movimiento usado por el despacho de farmacia. */
    public static final short SALIDA_DESPACHO = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movimiento_id")
    private Long id;

    @Column(name = "medicamento_id", nullable = false)
    private Integer medicamentoId;

    @Column(name = "sucursal_id", nullable = false)
    private Integer sucursalId;

    @Column(name = "tipo_movimiento", nullable = false)
    private Short tipoMovimiento;

    /** ck_mov_cantidad: > 0 (siempre positiva; el tipo dice si suma o resta). */
    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    @Column(name = "stock_anterior", nullable = false)
    private Integer stockAnterior;

    @Column(name = "stock_nuevo", nullable = false)
    private Integer stockNuevo;

    @Column(name = "costo_unitario")
    private BigDecimal costoUnitario;

    @Column(name = "numero_referencia", length = 50)
    private String numeroReferencia;

    @Column(name = "motivo", length = 500)
    private String motivo;

    /** FK al despacho que origino la salida (fk_mov_despacho). */
    @Column(name = "despacho_id")
    private Integer despachoId;

    /** Farmaceutico que hizo el movimiento. */
    @Column(name = "usuario_id", nullable = false)
    private Integer usuarioId;

    @Column(name = "state", nullable = false)
    private Short state = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void alCrear() {
        this.createdAt = OffsetDateTime.now();
    }

    // ---- Getters / setters ----

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getMedicamentoId() { return medicamentoId; }
    public void setMedicamentoId(Integer medicamentoId) { this.medicamentoId = medicamentoId; }
    public Integer getSucursalId() { return sucursalId; }
    public void setSucursalId(Integer sucursalId) { this.sucursalId = sucursalId; }
    public Short getTipoMovimiento() { return tipoMovimiento; }
    public void setTipoMovimiento(Short tipoMovimiento) { this.tipoMovimiento = tipoMovimiento; }
    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
    public Integer getStockAnterior() { return stockAnterior; }
    public void setStockAnterior(Integer stockAnterior) { this.stockAnterior = stockAnterior; }
    public Integer getStockNuevo() { return stockNuevo; }
    public void setStockNuevo(Integer stockNuevo) { this.stockNuevo = stockNuevo; }
    public BigDecimal getCostoUnitario() { return costoUnitario; }
    public void setCostoUnitario(BigDecimal costoUnitario) { this.costoUnitario = costoUnitario; }
    public String getNumeroReferencia() { return numeroReferencia; }
    public void setNumeroReferencia(String numeroReferencia) { this.numeroReferencia = numeroReferencia; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public Integer getDespachoId() { return despachoId; }
    public void setDespachoId(Integer despachoId) { this.despachoId = despachoId; }
    public Integer getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Integer usuarioId) { this.usuarioId = usuarioId; }
    public Short getState() { return state; }
    public void setState(Short state) { this.state = state; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}