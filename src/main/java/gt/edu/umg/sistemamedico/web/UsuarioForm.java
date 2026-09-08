package gt.edu.umg.sistemamedico.web;

/** Datos que vienen del formulario de crear/editar usuario. */
public class UsuarioForm {

    private Integer id;
    private String nombreCompleto;
    private String username;
    private String password;
    private String correo;
    private String dpi;
    private String telefono;
    private Integer rolId;
    private String nit;
    private String numeroSeguro;
    private Integer sucursalId;
    private Integer especialidadId;
    private Short state;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getNombreCompleto() { return nombreCompleto; }
    public void setNombreCompleto(String nombreCompleto) { this.nombreCompleto = nombreCompleto; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
    public String getDpi() { return dpi; }
    public void setDpi(String dpi) { this.dpi = dpi; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public Integer getRolId() { return rolId; }
    public void setRolId(Integer rolId) { this.rolId = rolId; }
    public String getNit() { return nit; }
    public void setNit(String nit) { this.nit = nit; }
    public String getNumeroSeguro() { return numeroSeguro; }
    public void setNumeroSeguro(String numeroSeguro) { this.numeroSeguro = numeroSeguro; }
    public Integer getSucursalId() { return sucursalId; }
    public void setSucursalId(Integer sucursalId) { this.sucursalId = sucursalId; }
    public Integer getEspecialidadId() { return especialidadId; }
    public void setEspecialidadId(Integer especialidadId) { this.especialidadId = especialidadId; }
    public Short getState() { return state; }
    public void setState(Short state) { this.state = state; }
}