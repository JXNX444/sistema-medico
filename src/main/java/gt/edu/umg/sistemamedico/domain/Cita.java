package gt.edu.umg.sistemamedico.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Cita medica. Tabla: his.cita
 *
 * Extiende BaseEntity: state (0=Inactivo,1=Activo,2=Eliminado, aunque
 * el ciclo de vida real de una cita lo maneja "estadoCita", no state),
 * row_version, created_at/by, updated_at/by ya vienen heredados.
 *
 * Las relaciones son EAGER (igual que Usuario.rol/sucursal) porque el
 * listado de citas casi siempre necesita mostrar junto paciente, medico,
 * especialidad y sucursal en la misma pantalla.
 */
@Entity
@Table(name = "cita", schema = "his")
public class Cita extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cita_id")
    private Integer id;

    /** Numero visible de la cita, ej: "CITA-2026-00001". Se genera al confirmar. */
    @Column(name = "numero_cita", nullable = false)
    private String numeroCita;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Usuario paciente;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "medico_id", nullable = false)
    private Usuario medico;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "especialidad_id", nullable = false)
    private Especialidad especialidad;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sucursal_id", nullable = false)
    private Sucursal sucursal;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "estado_cita_id", nullable = false)
    private EstadoCita estadoCita;

    /** [RN-CU03-05] fecha y hora exacta de la cita; debe ser futura al crearla. */
    @Column(name = "fecha_hora", nullable = false)
    private OffsetDateTime fechaHora;

    @Column(name = "duracion_min", nullable = false)
    private Short duracionMin;

    /** [RN-CU03-03] 10 a 2000 caracteres. */
    @Column(name = "motivo", nullable = false)
    private String motivo;

    @Column(name = "monto", nullable = false)
    private BigDecimal monto;

    @Column(name = "es_emergencia", nullable = false)
    private boolean esEmergencia = false;

    @Column(name = "prioridad", nullable = false)
    private Short prioridad = 0;

    /** Se llena cuando el paciente llega fisicamente (CU-06 Recepcion). */
    @Column(name = "hora_llegada")
    private OffsetDateTime horaLlegada;

    /** [CU-03 FA03] instante en que expira la reserva de 5 minutos si no se paga. */
    @Column(name = "reserva_expira_en")
    private OffsetDateTime reservaExpiraEn;

    /** Canal de origen de la cita (ej: 0=Portal web, 1=Recepcion). */
    @Column(name = "origen", nullable = false)
    private Short origen;

    @Column(name = "es_seguimiento", nullable = false)
    private boolean esSeguimiento = false;

    /** Tipo de seguimiento, solo si esSeguimiento=true. */
    @Column(name = "follow_up_type")
    private Short followUpType;

    /** Id de la consulta original de la que esta cita da seguimiento (CU-13). */
    @Column(name = "parent_consulta_id")
    private Integer parentConsultaId;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getNumeroCita() { return numeroCita; }
    public void setNumeroCita(String numeroCita) { this.numeroCita = numeroCita; }
    public Usuario getPaciente() { return paciente; }
    public void setPaciente(Usuario paciente) { this.paciente = paciente; }
    public Usuario getMedico() { return medico; }
    public void setMedico(Usuario medico) { this.medico = medico; }
    public Especialidad getEspecialidad() { return especialidad; }
    public void setEspecialidad(Especialidad especialidad) { this.especialidad = especialidad; }
    public Sucursal getSucursal() { return sucursal; }
    public void setSucursal(Sucursal sucursal) { this.sucursal = sucursal; }
    public EstadoCita getEstadoCita() { return estadoCita; }
    public void setEstadoCita(EstadoCita estadoCita) { this.estadoCita = estadoCita; }
    public OffsetDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(OffsetDateTime fechaHora) { this.fechaHora = fechaHora; }
    public Short getDuracionMin() { return duracionMin; }
    public void setDuracionMin(Short duracionMin) { this.duracionMin = duracionMin; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
    public boolean isEsEmergencia() { return esEmergencia; }
    public void setEsEmergencia(boolean esEmergencia) { this.esEmergencia = esEmergencia; }
    public Short getPrioridad() { return prioridad; }
    public void setPrioridad(Short prioridad) { this.prioridad = prioridad; }
    public OffsetDateTime getHoraLlegada() { return horaLlegada; }
    public void setHoraLlegada(OffsetDateTime horaLlegada) { this.horaLlegada = horaLlegada; }
    public OffsetDateTime getReservaExpiraEn() { return reservaExpiraEn; }
    public void setReservaExpiraEn(OffsetDateTime reservaExpiraEn) { this.reservaExpiraEn = reservaExpiraEn; }
    public Short getOrigen() { return origen; }
    public void setOrigen(Short origen) { this.origen = origen; }
    public boolean isEsSeguimiento() { return esSeguimiento; }
    public void setEsSeguimiento(boolean esSeguimiento) { this.esSeguimiento = esSeguimiento; }
    public Short getFollowUpType() { return followUpType; }
    public void setFollowUpType(Short followUpType) { this.followUpType = followUpType; }
    public Integer getParentConsultaId() { return parentConsultaId; }
    public void setParentConsultaId(Integer parentConsultaId) { this.parentConsultaId = parentConsultaId; }

    /** Fecha/hora de la cita en zona de Guatemala, para mostrar en vistas. */
    @Transient
    public java.time.ZonedDateTime getFechaLocal() {
        return fechaHora == null ? null
                : fechaHora.atZoneSameInstant(java.time.ZoneId.of("America/Guatemala"));
    }
}