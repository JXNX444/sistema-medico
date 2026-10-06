package gt.edu.umg.sistemamedico.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Despacho de una receta en farmacia. Tabla: his.despacho  [CU-11]
 *
 * No extiende BaseEntity: la tabla no tiene updated_at, created_by ni updated_by.
 */
@Entity
@Table(name = "despacho", schema = "his")
public class Despacho {

    /** estado_despacho (ck_despacho_estado: 0 o 1) */
    public static final short DESPACHADO = 0;
    public static final short NO_ADQUIRIDO = 1;   // FA03

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "despacho_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receta_id", nullable = false)
    private Receta receta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_id", nullable = false)
    private Sucursal sucursal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "farmaceutico_id", nullable = false)
    private Usuario farmaceutico;

    @Column(name = "monto_total", nullable = false)
    private BigDecimal montoTotal = BigDecimal.ZERO;

    @Column(name = "estado_despacho", nullable = false)
    private Short estadoDespacho = DESPACHADO;

    @Column(name = "notas", length = 500)
    private String notas;

    @Column(name = "state", nullable = false)
    private Short state = 1;

    @Version
    @Column(name = "row_version", nullable = false)
    private Integer rowVersion = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "despacho", cascade = CascadeType.ALL)
    private List<DespachoDetalle> detalles = new ArrayList<>();

    @PrePersist
    protected void alCrear() {
        this.createdAt = OffsetDateTime.now();
    }

    /** Agrega un item y mantiene la relacion en ambos lados. */
    public void agregarDetalle(DespachoDetalle d) {
        d.setDespacho(this);
        detalles.add(d);
    }

    // ---- Getters / setters ----

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Receta getReceta() { return receta; }
    public void setReceta(Receta receta) { this.receta = receta; }
    public Sucursal getSucursal() { return sucursal; }
    public void setSucursal(Sucursal sucursal) { this.sucursal = sucursal; }
    public Usuario getFarmaceutico() { return farmaceutico; }
    public void setFarmaceutico(Usuario farmaceutico) { this.farmaceutico = farmaceutico; }
    public BigDecimal getMontoTotal() { return montoTotal; }
    public void setMontoTotal(BigDecimal montoTotal) { this.montoTotal = montoTotal; }
    public Short getEstadoDespacho() { return estadoDespacho; }
    public void setEstadoDespacho(Short estadoDespacho) { this.estadoDespacho = estadoDespacho; }
    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }
    public Short getState() { return state; }
    public void setState(Short state) { this.state = state; }
    public Integer getRowVersion() { return rowVersion; }
    public void setRowVersion(Integer rowVersion) { this.rowVersion = rowVersion; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public List<DespachoDetalle> getDetalles() { return detalles; }
    public void setDetalles(List<DespachoDetalle> detalles) { this.detalles = detalles; }
}