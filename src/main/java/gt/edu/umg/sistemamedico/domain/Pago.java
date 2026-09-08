package gt.edu.umg.sistemamedico.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Pago de una cita/consulta o de una orden de laboratorio. Tabla: his.pago
 *
 * Es una sola tabla para ambos casos (comparten comprobante y metodos):
 *   - tipo_pago = 0 -> paga una CITA  (CU-04, este caso de uso)
 *   - tipo_pago = 1 -> paga una ORDEN de laboratorio (CU-10)
 *
 * [RNF-011] PCI DSS: NUNCA se guarda el numero completo de la tarjeta ni el
 * CVV. Solo se conservan los ultimos 4 digitos (columna ultimos4). Por eso
 * esta entidad no tiene campo para el CVV: no existe en la tabla.
 *
 * NO extiende BaseEntity: la tabla pago solo tiene created_at (no updated_at,
 * ni created_by/updated_by), asi que se declara standalone igual que EstadoCita.
 */
@Entity
@Table(name = "pago", schema = "his")
public class Pago {

    // ---- Constantes de los codigos smallint de la tabla (para no usar numeros sueltos) ----

    /** tipo_pago */
    public static final short TIPO_CITA = 0;
    public static final short TIPO_ORDEN_LAB = 1;

    /** metodo_pago  [RN-GLOBAL-004] */
    public static final short METODO_EFECTIVO = 0;
    public static final short METODO_VISA = 1;
    public static final short METODO_MASTERCARD = 2;
    public static final short METODO_DEBITO = 3;

    /** canal */
    public static final short CANAL_EN_LINEA = 0;   // CU-04
    public static final short CANAL_CAJA = 1;       // CU-06 / CU-10

    /** estado_pago */
    public static final short ESTADO_PENDIENTE = 0;
    public static final short ESTADO_APROBADO = 1;
    public static final short ESTADO_RECHAZADO = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pago_id")
    private Integer id;

    /** Numero visible del comprobante, unico. Ej: "TRX-2026-00001". [RN-GLOBAL-005] */
    @Column(name = "numero_transaccion", nullable = false, length = 30)
    private String numeroTransaccion;

    /** 0 = Cita/Consulta, 1 = Orden de laboratorio. En CU-04 siempre 0. */
    @Column(name = "tipo_pago", nullable = false)
    private Short tipoPago = TIPO_CITA;

    /** FK a la cita pagada (obligatoria cuando tipoPago = 0). */
    @Column(name = "cita_id")
    private Integer citaId;

    /** FK a la orden de laboratorio (solo cuando tipoPago = 1). En CU-04 va null. */
    @Column(name = "orden_id")
    private Integer ordenId;

    @Column(name = "paciente_id", nullable = false)
    private Integer pacienteId;

    @Column(name = "sucursal_id")
    private Integer sucursalId;

    /** 0=Efectivo 1=Visa 2=Mastercard 3=Debito. En linea solo 1,2,3. [RN-GLOBAL-004] */
    @Column(name = "metodo_pago", nullable = false)
    private Short metodoPago;

    /** 0 = En linea (CU-04), 1 = Caja presencial. En CU-04 siempre 0. */
    @Column(name = "canal", nullable = false)
    private Short canal = CANAL_EN_LINEA;

    /** Monto cobrado. La BD exige monto > 0 (ck_pago_monto). */
    @Column(name = "monto", nullable = false)
    private BigDecimal monto;

    /** Solo pago en efectivo (CU-06). En linea va null. */
    @Column(name = "monto_recibido")
    private BigDecimal montoRecibido;

    /** Cambio devuelto en efectivo (CU-06). En linea va null. */
    @Column(name = "cambio")
    private BigDecimal cambio;

    /** Ultimos 4 digitos de la tarjeta (enmascarado). [RNF-012] Debe cumplir ^[0-9]{4}$. */
    @Column(name = "ultimos4", length = 4)
    private String ultimos4;

    /** 0=Pendiente 1=Aprobado 2=Rechazado. */
    @Column(name = "estado_pago", nullable = false)
    private Short estadoPago = ESTADO_PENDIENTE;

    /** Motivo del rechazo devuelto por la pasarela. [RN-CU04-06] */
    @Column(name = "codigo_rechazo", length = 50)
    private String codigoRechazo;

    /** Referencia/id que devuelve la pasarela de pago. */
    @Column(name = "pasarela_ref", length = 100)
    private String pasarelaRef;

    /**
     * Llave de idempotencia. [RNF-016] La genera el navegador por cada intento
     * de pago y se envia al backend; la BD tiene indice unico sobre ella, asi
     * que si el paciente hace doble clic, el segundo INSERT choca y no se
     * cobra dos veces.
     */
    @Column(name = "idempotency_key", nullable = false)
    private UUID idempotencyKey;

    /** Cajero que cobro (solo en caja). En linea va null. */
    @Column(name = "cajero_id")
    private Integer cajeroId;

    /** 1 = activo, 0 = inactivo (borrado logico). */
    @Column(name = "state", nullable = false)
    private Short state = 1;

    @Version
    @Column(name = "row_version", nullable = false)
    private Integer rowVersion = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * Se ejecuta justo antes del INSERT: llena created_at (la columna es NOT
     * NULL) y, por seguridad, genera la llave de idempotencia si viniera nula.
     */
    @PrePersist
    protected void alCrear() {
        this.createdAt = OffsetDateTime.now();
        if (this.idempotencyKey == null) {
            this.idempotencyKey = UUID.randomUUID();
        }
    }

    /** true si el pago fue aprobado por la pasarela. */
    public boolean estaAprobado() {
        return estadoPago != null && estadoPago == ESTADO_APROBADO;
    }

    // ---- Getters / setters ----

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getNumeroTransaccion() { return numeroTransaccion; }
    public void setNumeroTransaccion(String numeroTransaccion) { this.numeroTransaccion = numeroTransaccion; }
    public Short getTipoPago() { return tipoPago; }
    public void setTipoPago(Short tipoPago) { this.tipoPago = tipoPago; }
    public Integer getCitaId() { return citaId; }
    public void setCitaId(Integer citaId) { this.citaId = citaId; }
    public Integer getOrdenId() { return ordenId; }
    public void setOrdenId(Integer ordenId) { this.ordenId = ordenId; }
    public Integer getPacienteId() { return pacienteId; }
    public void setPacienteId(Integer pacienteId) { this.pacienteId = pacienteId; }
    public Integer getSucursalId() { return sucursalId; }
    public void setSucursalId(Integer sucursalId) { this.sucursalId = sucursalId; }
    public Short getMetodoPago() { return metodoPago; }
    public void setMetodoPago(Short metodoPago) { this.metodoPago = metodoPago; }
    public Short getCanal() { return canal; }
    public void setCanal(Short canal) { this.canal = canal; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
    public BigDecimal getMontoRecibido() { return montoRecibido; }
    public void setMontoRecibido(BigDecimal montoRecibido) { this.montoRecibido = montoRecibido; }
    public BigDecimal getCambio() { return cambio; }
    public void setCambio(BigDecimal cambio) { this.cambio = cambio; }
    public String getUltimos4() { return ultimos4; }
    public void setUltimos4(String ultimos4) { this.ultimos4 = ultimos4; }
    public Short getEstadoPago() { return estadoPago; }
    public void setEstadoPago(Short estadoPago) { this.estadoPago = estadoPago; }
    public String getCodigoRechazo() { return codigoRechazo; }
    public void setCodigoRechazo(String codigoRechazo) { this.codigoRechazo = codigoRechazo; }
    public String getPasarelaRef() { return pasarelaRef; }
    public void setPasarelaRef(String pasarelaRef) { this.pasarelaRef = pasarelaRef; }
    public UUID getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(UUID idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public Integer getCajeroId() { return cajeroId; }
    public void setCajeroId(Integer cajeroId) { this.cajeroId = cajeroId; }
    public Short getState() { return state; }
    public void setState(Short state) { this.state = state; }
    public Integer getRowVersion() { return rowVersion; }
    public void setRowVersion(Integer rowVersion) { this.rowVersion = rowVersion; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}