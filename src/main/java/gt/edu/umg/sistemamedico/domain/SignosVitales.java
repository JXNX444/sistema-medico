package gt.edu.umg.sistemamedico.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Signos vitales de un paciente, tomados por enfermeria antes de la
 * consulta medica. [CU-07]
 *
 * No extiende BaseEntity: la tabla no tiene row_version ni
 * updated_at/by (es un registro clinico, no se edita despues de
 * creado) - mismo patron que CitaHistorialEstado.
 */
@Entity
@Table(name = "signos_vitales", schema = "his")
public class SignosVitales {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "signos_id")
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "cita_id", nullable = false)
    private Cita cita;

    @ManyToOne
    @JoinColumn(name = "enfermero_id", nullable = false)
    private Usuario enfermero;

    /** [RN-CU07-01] 60-250 mmHg. */
    @Column(name = "presion_sistolica", nullable = false)
    private Short presionSistolica;

    /** [RN-CU07-01] 40-150 mmHg. */
    @Column(name = "presion_diastolica", nullable = false)
    private Short presionDiastolica;

    /** [RN-CU07-02] 34.0-42.0 C, 1 decimal. */
    @Column(name = "temperatura", nullable = false)
    private BigDecimal temperatura;

    /** [RN-CU07-03] 0.5-300 kg, 2 decimales. */
    @Column(name = "peso_kg", nullable = false)
    private BigDecimal pesoKg;

    /** [RN-CU07-04] 30-250 cm, 2 decimales. */
    @Column(name = "talla_cm", nullable = false)
    private BigDecimal tallaCm;

    /** [RN-CU07-05] 30-220 lpm. */
    @Column(name = "frecuencia_cardiaca", nullable = false)
    private Short frecuenciaCardiaca;

    /** [CU-07 FA01] true si se marco como emergencia al registrar. */
    @Column(name = "is_emergency", nullable = false)
    private boolean esEmergencia;

    /** [RN-CU07-06] true si algun valor quedo fuera del rango clinico normal. */
    @Column(name = "tiene_alertas", nullable = false)
    private boolean tieneAlertas;

    /** [RN-CU07-06] Detalle de las alertas, guardado como JSON. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "alertas", columnDefinition = "jsonb")
    private String alertas;

    @Column(name = "state", nullable = false)
    private Short state = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void alCrear() {
        this.createdAt = OffsetDateTime.now();
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Cita getCita() { return cita; }
    public void setCita(Cita cita) { this.cita = cita; }

    public Usuario getEnfermero() { return enfermero; }
    public void setEnfermero(Usuario enfermero) { this.enfermero = enfermero; }

    public Short getPresionSistolica() { return presionSistolica; }
    public void setPresionSistolica(Short presionSistolica) { this.presionSistolica = presionSistolica; }

    public Short getPresionDiastolica() { return presionDiastolica; }
    public void setPresionDiastolica(Short presionDiastolica) { this.presionDiastolica = presionDiastolica; }

    public BigDecimal getTemperatura() { return temperatura; }
    public void setTemperatura(BigDecimal temperatura) { this.temperatura = temperatura; }

    public BigDecimal getPesoKg() { return pesoKg; }
    public void setPesoKg(BigDecimal pesoKg) { this.pesoKg = pesoKg; }

    public BigDecimal getTallaCm() { return tallaCm; }
    public void setTallaCm(BigDecimal tallaCm) { this.tallaCm = tallaCm; }

    public Short getFrecuenciaCardiaca() { return frecuenciaCardiaca; }
    public void setFrecuenciaCardiaca(Short frecuenciaCardiaca) { this.frecuenciaCardiaca = frecuenciaCardiaca; }

    public boolean isEsEmergencia() { return esEmergencia; }
    public void setEsEmergencia(boolean esEmergencia) { this.esEmergencia = esEmergencia; }

    public boolean isTieneAlertas() { return tieneAlertas; }
    public void setTieneAlertas(boolean tieneAlertas) { this.tieneAlertas = tieneAlertas; }

    public String getAlertas() { return alertas; }
    public void setAlertas(String alertas) { this.alertas = alertas; }

    public Short getState() { return state; }
    public void setState(Short state) { this.state = state; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}