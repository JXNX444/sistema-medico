package gt.edu.umg.sistemamedico.domain;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Receta medica generada desde una consulta. Tabla: his.receta
 * [CU-08 FA04]  -> la despacha farmacia en CU-10.
 *
 * No extiende BaseEntity: la tabla no tiene created_by ni updated_at/by.
 *
 * vence_el es una columna CALCULADA por la BD (fecha_emision + 7 dias),
 * por eso va con insertable/updatable = false: Hibernate solo la lee.
 *
 * Los medicamentos van en RecetaDetalle y se guardan en cascada.
 */
@Entity
@Table(name = "receta", schema = "his")
public class Receta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "receta_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consulta_id", nullable = false)
    private Consulta consulta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medico_id", nullable = false)
    private Usuario medico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Usuario paciente;

    @Column(name = "fecha_emision", nullable = false)
    private LocalDate fechaEmision = LocalDate.now();

    @Column(name = "notas", length = 1000)
    private String notas;

    @Column(name = "state", nullable = false)
    private Short state = 1;

    @Version
    @Column(name = "row_version", nullable = false)
    private Integer rowVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** Calculada por la BD: fecha_emision + 7. Solo lectura. */
    @Column(name = "vence_el", insertable = false, updatable = false)
    private LocalDate venceEl;

    @OneToMany(mappedBy = "receta", cascade = CascadeType.ALL)
    private List<RecetaDetalle> detalles = new ArrayList<>();

    @PrePersist
    protected void alCrear() {
        this.createdAt = OffsetDateTime.now();
    }

    /** Agrega un medicamento a la receta y amarra la relacion en ambos lados. */
    public void agregarDetalle(RecetaDetalle detalle) {
        detalle.setReceta(this);
        this.detalles.add(detalle);
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Consulta getConsulta() { return consulta; }
    public void setConsulta(Consulta consulta) { this.consulta = consulta; }
    public Usuario getMedico() { return medico; }
    public void setMedico(Usuario medico) { this.medico = medico; }
    public Usuario getPaciente() { return paciente; }
    public void setPaciente(Usuario paciente) { this.paciente = paciente; }
    public LocalDate getFechaEmision() { return fechaEmision; }
    public void setFechaEmision(LocalDate fechaEmision) { this.fechaEmision = fechaEmision; }
    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }
    public Short getState() { return state; }
    public void setState(Short state) { this.state = state; }
    public Integer getRowVersion() { return rowVersion; }
    public void setRowVersion(Integer rowVersion) { this.rowVersion = rowVersion; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDate getVenceEl() { return venceEl; }
    public List<RecetaDetalle> getDetalles() { return detalles; }
    public void setDetalles(List<RecetaDetalle> detalles) { this.detalles = detalles; }
}