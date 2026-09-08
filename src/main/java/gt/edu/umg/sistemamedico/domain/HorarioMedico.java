package gt.edu.umg.sistemamedico.domain;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * Horario de atencion de un medico en una sucursal especifica.
 * Tabla: his.horario_medico
 *
 * Define en que dias de la semana y en que rango de horas atiende
 * un medico, y cada cuanto se dividen los slots (duracion_slot).
 * El CU-03 cruza esto con la tabla "cita" para saber que horarios
 * especificos ya estan ocupados.
 */
@Entity
@Table(name = "horario_medico", schema = "his")
public class HorarioMedico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "horario_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "medico_id", nullable = false)
    private Usuario medico;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sucursal_id", nullable = false)
    private Sucursal sucursal;

    /** 1 = Lunes ... 7 = Domingo (o el criterio que ya tengan sembrado). */
    @Column(name = "dia_semana", nullable = false)
    private Short diaSemana;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    /** Duracion de cada cita en minutos, ej: 30. Define el tamano de cada slot. */
    @Column(name = "duracion_slot", nullable = false)
    private Short duracionSlot;

    /** Desde cuando aplica este horario. */
    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde;

    /** Hasta cuando aplica (null = indefinido). */
    @Column(name = "vigente_hasta")
    private LocalDate vigenteHasta;

    /** 1 = activo, 0 = inactivo. */
    @Column(name = "state", nullable = false)
    private Short state = 1;

    @Column(name = "row_version", nullable = false)
    private Integer rowVersion = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void alCrear() {
        this.createdAt = OffsetDateTime.now();
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Usuario getMedico() { return medico; }
    public void setMedico(Usuario medico) { this.medico = medico; }
    public Sucursal getSucursal() { return sucursal; }
    public void setSucursal(Sucursal sucursal) { this.sucursal = sucursal; }
    public Short getDiaSemana() { return diaSemana; }
    public void setDiaSemana(Short diaSemana) { this.diaSemana = diaSemana; }
    public LocalTime getHoraInicio() { return horaInicio; }
    public void setHoraInicio(LocalTime horaInicio) { this.horaInicio = horaInicio; }
    public LocalTime getHoraFin() { return horaFin; }
    public void setHoraFin(LocalTime horaFin) { this.horaFin = horaFin; }
    public Short getDuracionSlot() { return duracionSlot; }
    public void setDuracionSlot(Short duracionSlot) { this.duracionSlot = duracionSlot; }
    public LocalDate getVigenteDesde() { return vigenteDesde; }
    public void setVigenteDesde(LocalDate vigenteDesde) { this.vigenteDesde = vigenteDesde; }
    public LocalDate getVigenteHasta() { return vigenteHasta; }
    public void setVigenteHasta(LocalDate vigenteHasta) { this.vigenteHasta = vigenteHasta; }
    public Short getState() { return state; }
    public void setState(Short state) { this.state = state; }
    public Integer getRowVersion() { return rowVersion; }
    public void setRowVersion(Integer rowVersion) { this.rowVersion = rowVersion; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}