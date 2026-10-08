package gt.edu.umg.sistemamedico.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Kardex: cada entrada o salida de inventario. Tabla: his.movimiento_inventario
 * [CU-11 despacho] [CU-14 ajustes del catalogo] [CU-15 bitacora]
 *
 * Tipos de movimiento [CU-15 / RN-CU13-01]:
 *   0 Compra      (entrada)  -> exige costo_unitario > 0 (ck_mov_costo)
 *   1 Devolucion  (entrada)  -> exige motivo 10-500 (ck_mov_motivo)
 *   2 Venta       (salida)
 *   3 Reclamo     (salida)   -> exige motivo
 *   4 Ajuste+     (entrada)  -> exige motivo
 *   5 Ajuste-     (salida)   -> exige motivo
 *   6 Despacho    (salida)   -> AUTOMATICO, lo genera CU-11; no se ofrece en el formulario
 *
 * ck_mov_coherencia: 0,1,4 SUMAN (stock_nuevo = anterior + cantidad);
 *                    2,3,5,6 RESTAN (stock_nuevo = anterior - cantidad).
 */
@Entity
@Table(name = "movimiento_inventario", schema = "his")
public class MovimientoInventario {

    public static final short COMPRA = 0;
    public static final short DEVOLUCION = 1;
    public static final short VENTA = 2;
    public static final short RECLAMO = 3;
    public static final short AJUSTE_ENTRADA = 4;
    public static final short AJUSTE_SALIDA = 5;

    /** [CU-15] El despacho de farmacia (CU-11) es el tipo 6, no el 2 (Venta). */
    public static final short SALIDA_DESPACHO = 6;

    private static final ZoneId ZONA_GT = ZoneId.of("America/Guatemala");

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

    /** Usuario que hizo el movimiento. */
    @Column(name = "usuario_id", nullable = false)
    private Integer usuarioId;

    @Column(name = "state", nullable = false)
    private Short state = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    // ---- Relaciones SOLO LECTURA para la bitacora (CU-15) ----
    // Usan las mismas columnas de arriba; insertable/updatable = false para que
    // Hibernate no las escriba dos veces. Se llenan al LEER de la BD.

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "medicamento_id", insertable = false, updatable = false)
    private Medicamento medicamento;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sucursal_id", insertable = false, updatable = false)
    private Sucursal sucursal;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", insertable = false, updatable = false)
    private Usuario usuario;

    @PrePersist
    protected void alCrear() {
        this.createdAt = OffsetDateTime.now();
    }

    // ---- Ayudas para la pantalla ----

    /** Nombre visible de cada tipo. */
    public static String nombreTipo(Short tipo) {
        if (tipo == null) return "Desconocido";
        return switch (tipo) {
            case COMPRA -> "Compra";
            case DEVOLUCION -> "Devolución";
            case VENTA -> "Venta";
            case RECLAMO -> "Reclamo";
            case AJUSTE_ENTRADA -> "Ajuste+";
            case AJUSTE_SALIDA -> "Ajuste-";
            case SALIDA_DESPACHO -> "Despacho";
            default -> "Desconocido";
        };
    }

    /** true si el tipo SUMA al stock (0, 1, 4). */
    public static boolean esEntrada(Short tipo) {
        return tipo != null && (tipo == COMPRA || tipo == DEVOLUCION || tipo == AJUSTE_ENTRADA);
    }

    public String getNombreTipo() { return nombreTipo(tipoMovimiento); }

    public boolean isEntrada() { return esEntrada(tipoMovimiento); }

    /** Fecha en hora de Guatemala (la BD la guarda en UTC). */
    public LocalDateTime getFechaLocal() {
        return createdAt == null ? null : createdAt.atZoneSameInstant(ZONA_GT).toLocalDateTime();
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
    public Medicamento getMedicamento() { return medicamento; }
    public Sucursal getSucursal() { return sucursal; }
    public Usuario getUsuario() { return usuario; }
}