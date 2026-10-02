package gt.edu.umg.sistemamedico.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Un examen dentro de una orden de laboratorio. Tabla: his.orden_laboratorio_detalle
 * [CU-08 FA01] se crea con el examen y su monto.
 * Los campos de resultado (valor, fecha, publicado...) los llena CU-09.
 *
 * Ojo: la BD tiene un trigger (tr_resultado_inmutable) que bloquea cambiar
 * un resultado ya publicado.
 */
@Entity
@Table(name = "orden_laboratorio_detalle", schema = "his")
public class OrdenLaboratorioDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "detalle_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_id", nullable = false)
    private OrdenLaboratorio orden;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "examen_id", nullable = false)
    private ExamenLaboratorio examen;

    /** Copia del precio_base del examen al momento de generar la orden. */
    @Column(name = "monto", nullable = false)
    private BigDecimal monto;

    // ---------- Resultado [CU-09] ----------

    @Column(name = "valor_resultado", length = 200)
    private String valorResultado;

    @Column(name = "unidad", length = 50)
    private String unidad;

    @Column(name = "fecha_resultado")
    private LocalDate fechaResultado;

    @Column(name = "fuera_rango", nullable = false)
    private boolean fueraRango = false;

    @Column(name = "notas_resultado", length = 1000)
    private String notasResultado;

    @Column(name = "registrado_por")
    private Integer registradoPor;

    @Column(name = "is_published", nullable = false)
    private boolean publicado = false;

    @Column(name = "published_at")
    private OffsetDateTime publicadoEn;

    @Column(name = "publicado_por")
    private Integer publicadoPor;

    // ---------- Control ----------

    @Column(name = "state", nullable = false)
    private Short state = 1;

    @Version
    @Column(name = "row_version", nullable = false)
    private Integer rowVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void alCrear() {
        this.createdAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void alActualizar() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public OrdenLaboratorio getOrden() { return orden; }
    public void setOrden(OrdenLaboratorio orden) { this.orden = orden; }
    public ExamenLaboratorio getExamen() { return examen; }
    public void setExamen(ExamenLaboratorio examen) { this.examen = examen; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
    public String getValorResultado() { return valorResultado; }
    public void setValorResultado(String valorResultado) { this.valorResultado = valorResultado; }
    public String getUnidad() { return unidad; }
    public void setUnidad(String unidad) { this.unidad = unidad; }
    public LocalDate getFechaResultado() { return fechaResultado; }
    public void setFechaResultado(LocalDate fechaResultado) { this.fechaResultado = fechaResultado; }
    public boolean isFueraRango() { return fueraRango; }
    public void setFueraRango(boolean fueraRango) { this.fueraRango = fueraRango; }
    public String getNotasResultado() { return notasResultado; }
    public void setNotasResultado(String notasResultado) { this.notasResultado = notasResultado; }
    public Integer getRegistradoPor() { return registradoPor; }
    public void setRegistradoPor(Integer registradoPor) { this.registradoPor = registradoPor; }
    public boolean isPublicado() { return publicado; }
    public void setPublicado(boolean publicado) { this.publicado = publicado; }
    public OffsetDateTime getPublicadoEn() { return publicadoEn; }
    public void setPublicadoEn(OffsetDateTime publicadoEn) { this.publicadoEn = publicadoEn; }
    public Integer getPublicadoPor() { return publicadoPor; }
    public void setPublicadoPor(Integer publicadoPor) { this.publicadoPor = publicadoPor; }
    public Short getState() { return state; }
    public void setState(Short state) { this.state = state; }
    public Integer getRowVersion() { return rowVersion; }
    public void setRowVersion(Integer rowVersion) { this.rowVersion = rowVersion; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}