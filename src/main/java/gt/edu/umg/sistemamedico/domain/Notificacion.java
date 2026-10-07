package gt.edu.umg.sistemamedico.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * Cola de correos del sistema. Tabla: his.notificacion
 * [CU-12 RN-CU11-04, RN-CU11-05, RNF-020, RNF-032]
 *
 * Cada fila es UN correo por enviar. No se manda en el momento:
 * se guarda aqui con su fecha programada (programado_para) y una
 * tarea programada (NotificacionScheduler) lo envia cuando llega la hora.
 * Como todo queda en la BD, si la app se reinicia los pendientes
 * se siguen enviando (RNF-020).
 *
 * No extiende BaseEntity: la tabla no tiene state, row_version ni
 * created_by/updated_by.
 *
 * La BD no documenta los valores de tipo y estado_envio; se definen aqui:
 *   tipo (0-4):         0 = Cita de seguimiento agendada  [RN-CU11-04]
 *                       1 = Recordatorio de seguimiento   [RN-CU11-05]
 *                       2, 3, 4 = libres para otros CU
 *   estado_envio (0-2): 0 = Pendiente, 1 = Enviado, 2 = Fallido o cancelado
 */
@Entity
@Table(name = "notificacion", schema = "his")
public class Notificacion {

    // ---------- tipo ----------
    public static final short TIPO_SEGUIMIENTO_AGENDADO = 0;
    public static final short TIPO_RECORDATORIO_SEGUIMIENTO = 1;

    // ---------- estado_envio ----------
    public static final short PENDIENTE = 0;
    public static final short ENVIADO = 1;
    public static final short FALLIDO = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notificacion_id")
    private Long id;

    /** Correo del paciente. */
    @Column(name = "destinatario", nullable = false)
    private String destinatario;

    @Column(name = "asunto", nullable = false)
    private String asunto;

    /** HTML completo del correo, ya armado al momento de programarlo. */
    @Column(name = "cuerpo", nullable = false, columnDefinition = "text")
    private String cuerpo;

    @Column(name = "tipo", nullable = false)
    private Short tipo;

    /** Paciente al que va dirigido (solo el id, no se navega). */
    @Column(name = "usuario_id")
    private Integer usuarioId;

    /** Cita de la que trata el correo (para no enviar recordatorios de citas canceladas). */
    @Column(name = "cita_id")
    private Integer citaId;

    @Column(name = "estado_envio", nullable = false)
    private Short estadoEnvio = PENDIENTE;

    /** Cuantas veces se intento enviar y fallo. */
    @Column(name = "intentos", nullable = false)
    private Short intentos = 0;

    /** Mensaje del ultimo error de envio, para revisarlo en DBeaver. */
    @Column(name = "ultimo_error")
    private String ultimoError;

    /** A partir de cuando se puede enviar. */
    @Column(name = "programado_para", nullable = false)
    private OffsetDateTime programadoPara;

    /** Cuando salio de verdad (null mientras este pendiente). */
    @Column(name = "enviado_en")
    private OffsetDateTime enviadoEn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void alCrear() {
        this.createdAt = OffsetDateTime.now();
        if (this.programadoPara == null) {
            this.programadoPara = this.createdAt;   // sin fecha = enviar ya
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDestinatario() { return destinatario; }
    public void setDestinatario(String destinatario) { this.destinatario = destinatario; }
    public String getAsunto() { return asunto; }
    public void setAsunto(String asunto) { this.asunto = asunto; }
    public String getCuerpo() { return cuerpo; }
    public void setCuerpo(String cuerpo) { this.cuerpo = cuerpo; }
    public Short getTipo() { return tipo; }
    public void setTipo(Short tipo) { this.tipo = tipo; }
    public Integer getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Integer usuarioId) { this.usuarioId = usuarioId; }
    public Integer getCitaId() { return citaId; }
    public void setCitaId(Integer citaId) { this.citaId = citaId; }
    public Short getEstadoEnvio() { return estadoEnvio; }
    public void setEstadoEnvio(Short estadoEnvio) { this.estadoEnvio = estadoEnvio; }
    public Short getIntentos() { return intentos; }
    public void setIntentos(Short intentos) { this.intentos = intentos; }
    public String getUltimoError() { return ultimoError; }
    public void setUltimoError(String ultimoError) { this.ultimoError = ultimoError; }
    public OffsetDateTime getProgramadoPara() { return programadoPara; }
    public void setProgramadoPara(OffsetDateTime programadoPara) { this.programadoPara = programadoPara; }
    public OffsetDateTime getEnviadoEn() { return enviadoEn; }
    public void setEnviadoEn(OffsetDateTime enviadoEn) { this.enviadoEn = enviadoEn; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}