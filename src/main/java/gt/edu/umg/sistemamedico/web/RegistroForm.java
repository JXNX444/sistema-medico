package gt.edu.umg.sistemamedico.web;

/** Datos del formulario de registro de pacientes externos. CU-02. */
public class RegistroForm {

    private String nombreCompleto;
    private String dpi;
    private String nit;
    private String telefono;
    private String numeroSeguro;
    private String correo;
    private String username;
    private String password;

    public String getNombreCompleto() { return nombreCompleto; }
    public void setNombreCompleto(String nombreCompleto) { this.nombreCompleto = nombreCompleto; }

    public String getDpi() { return dpi; }
    public void setDpi(String dpi) { this.dpi = dpi; }

    public String getNit() { return nit; }
    public void setNit(String nit) { this.nit = nit; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getNumeroSeguro() { return numeroSeguro; }
    public void setNumeroSeguro(String numeroSeguro) { this.numeroSeguro = numeroSeguro; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}