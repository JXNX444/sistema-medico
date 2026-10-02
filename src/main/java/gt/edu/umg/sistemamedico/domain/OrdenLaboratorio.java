package gt.edu.umg.sistemamedico.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Orden de laboratorio generada desde una consulta. Tabla: his.orden_laboratorio
 * [CU-08 FA01]  -> la procesa laboratorio en CU-09.
 *
 * No extiende BaseEntity: la tabla no tiene created_by/updated_by.
 *
 * estadoOrden (order_status): 0 = Pendiente, 1 = En proceso, 2 = Completada.
 * CU-08 solo la crea en 0.
 *
 * Los examenes van en OrdenLaboratorioDetalle y se guardan en cascada
 * junto con la orden (un solo save).
 */
@Entity
@Table(name = "orden_laboratorio", schema = "his")
public class OrdenLaboratorio {

    public static final short PENDIENTE = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "orden_id")
    private Integer id;

    /** Numero visible, ej: "LAB-2026-00001". Unico. */
    @Column(name = "numero_orden", nullable = false, length = 20)
    private String numeroOrden;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consulta_id", nullable = false)
    private Consulta consulta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Usuario paciente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medico_id", nullable = false)
    private Usuario medico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_id", nullable = false)
    private Sucursal sucursal;

    @Column(name = "order_status", nullable = false)
    private Short estadoOrden = PENDIENTE;

    @Column(name = "es_externa", nullable = false)
    private boolean esExterna = false;

    /** Suma de los montos de los detalles. */
    @Column(name = "monto_total", nullable = false)
    private BigDecimal montoTotal = BigDecimal.ZERO;

    /** [FA01 paso 4] observaciones del medico. */
    @Column(name = "notas", length = 1000)
    private String notas;

    @Column(name = "state", nullable = false)
    private Short state = 1;

    @Version
    @Column(name = "row_version", nullable = false)
    private Integer rowVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL)
    private List<OrdenLaboratorioDetalle> detalles = new ArrayList<>();

    @PrePersist
    protected void alCrear() {
        this.createdAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void alActualizar() {
        this.updatedAt = OffsetDateTime.now();
    }

    /** Agrega un examen a la orden y amarra la relacion en ambos lados. */
    public void agregarDetalle(OrdenLaboratorioDetalle detalle) {
        detalle.setOrden(this);
        this.detalles.add(detalle);
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getNumeroOrden() { return numeroOrden; }
    public void setNumeroOrden(String numeroOrden) { this.numeroOrden = numeroOrden; }
    public Consulta getConsulta() { return consulta; }
    public void setConsulta(Consulta consulta) { this.consulta = consulta; }
    public Usuario getPaciente() { return paciente; }
    public void setPaciente(Usuario paciente) { this.paciente = paciente; }
    public Usuario getMedico() { return medico; }
    public void setMedico(Usuario medico) { this.medico = medico; }
    public Sucursal getSucursal() { return sucursal; }
    public void setSucursal(Sucursal sucursal) { this.sucursal = sucursal; }
    public Short getEstadoOrden() { return estadoOrden; }
    public void setEstadoOrden(Short estadoOrden) { this.estadoOrden = estadoOrden; }
    public boolean isEsExterna() { return esExterna; }
    public void setEsExterna(boolean esExterna) { this.esExterna = esExterna; }
    public BigDecimal getMontoTotal() { return montoTotal; }
    public void setMontoTotal(BigDecimal montoTotal) { this.montoTotal = montoTotal; }
    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }
    public Short getState() { return state; }
    public void setState(Short state) { this.state = state; }
    public Integer getRowVersion() { return rowVersion; }
    public void setRowVersion(Integer rowVersion) { this.rowVersion = rowVersion; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<OrdenLaboratorioDetalle> getDetalles() { return detalles; }
    public void setDetalles(List<OrdenLaboratorioDetalle> detalles) { this.detalles = detalles; }
}