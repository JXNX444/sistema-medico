package gt.edu.umg.sistemamedico.web;

/**
 * Datos editables por el paciente en "Mi Perfil".
 * La contrasena es opcional: solo se cambia si el paciente escribe una nueva.
 */
public class PerfilForm {

    private String correo;
    private String telefono;
    private String passwordActual;
    private String passwordNueva;

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getPasswordActual() { return passwordActual; }
    public void setPasswordActual(String passwordActual) { this.passwordActual = passwordActual; }
    public String getPasswordNueva() { return passwordNueva; }
    public void setPasswordNueva(String passwordNueva) { this.passwordNueva = passwordNueva; }
}