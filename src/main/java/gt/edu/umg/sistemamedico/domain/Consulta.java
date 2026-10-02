package gt.edu.umg.sistemamedico.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * Consulta medica de una cita. Tabla: his.consulta  [CU-08]
 *
 * No extiende BaseEntity: la tabla no tiene created_by/updated_by.
 * Una sola consulta activa por cita (indice unico ux_consulta_cita).
 *
 * estadoConsulta: 0 = En curso, 1 = Finalizada  [CU-08 paso 4]
 * La BD obliga (ck_consulta_diagnostico) a que una consulta finalizada
 * tenga diagnostico de 10 a 5000 caracteres  [FA05].
 *
 * rowVersion NO se inicializa: asi Spring Data detecta la entidad como
 * nueva (version null) y hace persist en lugar de merge.
 */
@Entity
@Table(name = "consulta", schema = "his")
public class Consulta {

    public static final short EN_CURSO = 0;
    public static final short FINALIZADA = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "consulta_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cita_id", nullable = false)
    private Cita cita;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "medico_id", nullable = false)
    private Usuario medico;

    /** [CU-08 paso 5] obligatorio. */
    @Column(name = "motivo_visita", nullable = false, length = 2000)
    private String motivoVisita;

    /** [CU-08 paso 6] */
    @Column(name = "hallazgos", columnDefinition = "text")
    private String hallazgos;

    /** [CU-08 paso 7] codigo CIE-10 seleccionado del autocompletado. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cie10_id")
    private Cie10 cie10;

    /** [CU-08 paso 8 / FA05] obligatorio para finalizar. */
    @Column(name = "diagnostico", length = 5000)
    private String diagnostico;

    @Column(name = "plan_tratamiento", columnDefinition = "text")
    private String planTratamiento;

    @Column(name = "notas", columnDefinition = "text")
    private String notas;

    @Column(name = "estado_consulta", nullable = false)
    private Short estadoConsulta = EN_CURSO;

    /** Se llena solo cuando estadoConsulta = 1 (ck_consulta_cierre). */
    @Column(name = "finalizada_en")
    private OffsetDateTime finalizadaEn;

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

    public boolean estaFinalizada() {
        return estadoConsulta != null && estadoConsulta == FINALIZADA;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Cita getCita() { return cita; }
    public void setCita(Cita cita) { this.cita = cita; }
    public Usuario getMedico() { return medico; }
    public void setMedico(Usuario medico) { this.medico = medico; }
    public String getMotivoVisita() { return motivoVisita; }
    public void setMotivoVisita(String motivoVisita) { this.motivoVisita = motivoVisita; }
    public String getHallazgos() { return hallazgos; }
    public void setHallazgos(String hallazgos) { this.hallazgos = hallazgos; }
    public Cie10 getCie10() { return cie10; }
    public void setCie10(Cie10 cie10) { this.cie10 = cie10; }
    public String getDiagnostico() { return diagnostico; }
    public void setDiagnostico(String diagnostico) { this.diagnostico = diagnostico; }
    public String getPlanTratamiento() { return planTratamiento; }
    public void setPlanTratamiento(String planTratamiento) { this.planTratamiento = planTratamiento; }
    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }
    public Short getEstadoConsulta() { return estadoConsulta; }
    public void setEstadoConsulta(Short estadoConsulta) { this.estadoConsulta = estadoConsulta; }
    public OffsetDateTime getFinalizadaEn() { return finalizadaEn; }
    public void setFinalizadaEn(OffsetDateTime finalizadaEn) { this.finalizadaEn = finalizadaEn; }
    public Short getState() { return state; }
    public void setState(Short state) { this.state = state; }
    public Integer getRowVersion() { return rowVersion; }
    public void setRowVersion(Integer rowVersion) { this.rowVersion = rowVersion; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}