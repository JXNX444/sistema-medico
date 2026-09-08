package gt.edu.umg.sistemamedico.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * Tabla unica para personal interno y pacientes. El rol discrimina el tipo.
 * Tabla: his.usuario
 *
 * CU-00 (login del portal), CU-01 (mantenimiento), CU-02 (registro externo).
 * Incluye el control de bloqueo por intentos fallidos. [RN-CU00-03]
 *
 * "state" tiene 3 valores posibles:
 *   0 = Inactivo (editable desde el formulario, reversible)
 *   1 = Activo   (editable desde el formulario, reversible)
 *   2 = Eliminado (solo lo pone el boton Eliminar del CU-01, oculta
 *       al usuario del listado; no es editable desde el formulario)
 */
@Entity
@Table(name = "usuario", schema = "his")
public class Usuario extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "usuario_id")
    private Integer id;

    /** [RN-CU01-04 / RN-CU02-01] 10 a 100 caracteres. */
    @Column(name = "nombre_completo", nullable = false, length = 100)
    private String nombreCompleto;

    /** [RN-CU01-05 / RN-CU02-05] 8 a 9 caracteres alfanumericos, UNICO. */
    @Column(name = "username", nullable = false, length = 9)
    private String username;

    /** Hash BCrypt. Nunca la contrasena en claro. [RNF-015] */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /** [RN-CU02-04] UNICO, formato email. La columna es citext (case-insensitive). */
    @Column(name = "correo", nullable = false, columnDefinition = "citext")
    private String correo;

    /** [RN-GLOBAL-001] exactamente 13 digitos. */
    @Column(name = "dpi", length = 13)
    private String dpi;

    /** [RN-GLOBAL-002] 8 a 9 caracteres alfanumericos. */
    @Column(name = "nit", length = 9)
    private String nit;

    /** [RN-CU01-08 / RN-CU02-02] exactamente 8 digitos. */
    @Column(name = "telefono", length = 8)
    private String telefono;

    /** [RN-CU01-12] 5 a 50 caracteres. */
    @Column(name = "numero_seguro", length = 50)
    private String numeroSeguro;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "rol_id", nullable = false)
    private Rol rol;

    /**
     * EAGER (no LAZY): el listado de usuarios (CU-01) siempre muestra la
     * sucursal en la tabla, y Thymeleaf la lee fuera de la sesion de
     * Hibernate, asi que necesita venir ya cargada.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sucursal_id")
    private Sucursal sucursal;

    /** [RN-CU01-14] solo aplica cuando el rol es Medico. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "especialidad_id")
    private Especialidad especialidad;

    // ---- Control de acceso  [RN-CU00-03] [RN-GLOBAL-007] ----

    @Column(name = "intentos_fallidos", nullable = false)
    private Short intentosFallidos = 0;

    @Column(name = "bloqueado_hasta")
    private OffsetDateTime bloqueadoHasta;

    @Column(name = "ultimo_acceso")
    private OffsetDateTime ultimoAcceso;

    // ---- Comportamiento ----

    /** [RN-CU00-03] la cuenta esta bloqueada si el instante de desbloqueo aun no llega. */
    @Transient
    public boolean estaBloqueado() {
        return bloqueadoHasta != null && bloqueadoHasta.isAfter(OffsetDateTime.now());
    }

    @Transient
    public String getNombreRol() {
        return rol != null ? rol.getNombre() : null;
    }

    // ---- Getters / setters ----

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getNombreCompleto() { return nombreCompleto; }
    public void setNombreCompleto(String nombreCompleto) { this.nombreCompleto = nombreCompleto; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    public String getDpi() { return dpi; }
    public void setDpi(String dpi) { this.dpi = dpi; }

    public String getNit() { return nit; }
    public void setNit(String nit) { this.nit = nit; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getNumeroSeguro() { return numeroSeguro; }
    public void setNumeroSeguro(String numeroSeguro) { this.numeroSeguro = numeroSeguro; }

    public Rol getRol() { return rol; }
    public void setRol(Rol rol) { this.rol = rol; }

    public Sucursal getSucursal() { return sucursal; }
    public void setSucursal(Sucursal sucursal) { this.sucursal = sucursal; }

    public Especialidad getEspecialidad() { return especialidad; }
    public void setEspecialidad(Especialidad especialidad) { this.especialidad = especialidad; }

    public Short getIntentosFallidos() { return intentosFallidos; }
    public void setIntentosFallidos(Short intentosFallidos) { this.intentosFallidos = intentosFallidos; }

    public OffsetDateTime getBloqueadoHasta() { return bloqueadoHasta; }
    public void setBloqueadoHasta(OffsetDateTime bloqueadoHasta) { this.bloqueadoHasta = bloqueadoHasta; }

    public OffsetDateTime getUltimoAcceso() { return ultimoAcceso; }
    public void setUltimoAcceso(OffsetDateTime ultimoAcceso) { this.ultimoAcceso = ultimoAcceso; }
}