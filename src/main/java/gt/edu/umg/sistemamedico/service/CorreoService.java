package gt.edu.umg.sistemamedico.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Envio de correos del sistema. [RN-GLOBAL-006]
 * CU-02 paso 14: correo de bienvenida al paciente recien registrado.
 * CU-04 paso 14: comprobante de pago.
 * CU-08 FA02 paso 4: aviso de cita de seguimiento.
 *
 * Los tres usan la misma plantilla (plantilla()) con la estetica actual
 * del sistema (theme.css): navy #0F172A, cian #06B6D4 / #0891B2, grises
 * slate y bordes #E2E8F0. Todo con estilos en linea y tablas, que es lo
 * unico que respetan Gmail/Outlook.
 *
 * Todos son tolerantes a fallos: atrapan Exception (no solo
 * MessagingException) porque mailSender.send() lanza MailException, que
 * es de tipo runtime; asi un fallo de SMTP nunca deshace el registro,
 * el pago ni la cita que lo disparo.
 */
@Service
public class CorreoService {

    private static final Logger log = LoggerFactory.getLogger(CorreoService.class);

    private final JavaMailSender mailSender;

    @Value("${his.hospital.nombre}")
    private String nombreHospital;

    @Value("${spring.mail.username:}")
    private String remitente;

    @Value("${his.app.url-login:http://localhost:8080/login}")
    private String urlLogin;

    public CorreoService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // =====================================================================
    // CU-02: bienvenida
    // =====================================================================

    public void enviarBienvenida(String correoDestino, String nombrePaciente, String username) {
        enviar(correoDestino,
                "Bienvenido al Sistema de Citas - " + nombreHospital,
                construirHtml(nombrePaciente, username, correoDestino),
                "bienvenida");
    }

    // =====================================================================
    // CU-04: comprobante de pago
    // =====================================================================

    public void enviarComprobantePago(String correoDestino, String nombrePaciente,
                                      String numeroTransaccion, String medico,
                                      String especialidad, String sucursal,
                                      String fechaHora, String monto) {
        enviar(correoDestino,
                "Comprobante de pago " + numeroTransaccion + " - " + nombreHospital,
                construirHtmlComprobante(nombrePaciente, numeroTransaccion,
                        medico, especialidad, sucursal, fechaHora, monto),
                "comprobante de pago");
    }

    // =====================================================================
    // CU-08 FA02: cita de seguimiento
    // =====================================================================

    public void enviarCitaSeguimiento(String correoDestino, String nombrePaciente,
                                      String numeroCita, String tipoSeguimiento,
                                      String medico, String especialidad, String sucursal,
                                      String fechaHora, String monto) {
        enviar(correoDestino,
                "Cita de seguimiento " + numeroCita + " - " + nombreHospital,
                construirHtmlSeguimiento(nombrePaciente, numeroCita, tipoSeguimiento,
                        medico, especialidad, sucursal, fechaHora, monto),
                "aviso de seguimiento");
    }

    // =====================================================================
    // Envio comun
    // =====================================================================

    private void enviar(String correoDestino, String asunto, String html, String tipo) {
        if (remitente == null || remitente.isBlank()) {
            log.warn("Correo SMTP no configurado (MAIL_USER vacio). Se omite {} a {}", tipo, correoDestino);
            return;
        }
        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, "UTF-8");

            helper.setFrom(remitente);
            helper.setTo(correoDestino);
            helper.setSubject(asunto);
            helper.setText(html, true); // true = HTML

            mailSender.send(mensaje);
            log.info("Correo de {} enviado a {}", tipo, correoDestino);

        } catch (Exception e) {
            log.error("No se pudo enviar el correo de {} a {}: {}", tipo, correoDestino, e.getMessage());
        }
    }

    // =====================================================================
    // Contenido de cada correo
    // =====================================================================

    private String construirHtml(String nombre, String username, String correo) {
        String filas = fila("Usuario", username) + fila("Correo", correo);
        return plantilla(
                "BIENVENIDA",
                "Bienvenido, " + primerNombre(nombre),
                "Su registro ha sido completado exitosamente. Ya puede agendar sus citas médicas a través de nuestro portal.",
                null, null,
                "DATOS DE SU CUENTA", filas,
                null, null,
                aviso("#ECFEFF", "#0E7490",
                        "Si usted no creó esta cuenta, ignore este mensaje o contacte a recepción."),
                "Iniciar sesión");
    }

    private String construirHtmlComprobante(String nombre, String numeroTransaccion,
                                            String medico, String especialidad,
                                            String sucursal, String fechaHora, String monto) {
        String filas = fila("No. de transacción", numeroTransaccion)
                + fila("Médico", medico)
                + fila("Especialidad", especialidad)
                + fila("Sede", sucursal);
        return plantilla(
                "PAGO CONFIRMADO",
                "Hola, " + primerNombre(nombre),
                "Su pago fue realizado exitosamente y su cita ha sido confirmada. Este es su comprobante.",
                "FECHA Y HORA DE LA CITA", fechaHora,
                "COMPROBANTE DE PAGO", filas,
                "Total pagado", "Q " + monto,
                aviso("#DCFCE7", "#15803D",
                        "Conserve este comprobante y preséntelo en recepción el día de su cita."),
                "Ver mis citas");
    }

    private String construirHtmlSeguimiento(String nombre, String numeroCita, String tipoSeguimiento,
                                            String medico, String especialidad, String sucursal,
                                            String fechaHora, String monto) {
        String filas = fila("No. de cita", numeroCita)
                + fila("Tipo", tipoSeguimiento)
                + fila("Médico", medico)
                + fila("Especialidad", especialidad)
                + fila("Sede", sucursal);
        return plantilla(
                "CITA DE SEGUIMIENTO",
                "Hola, " + primerNombre(nombre),
                "Su médico le agendó una cita de seguimiento. Estos son los detalles.",
                "FECHA Y HORA", fechaHora,
                "DETALLE DE LA CITA", filas,
                "Monto a pagar", "Q " + monto,
                aviso("#FEF3C7", "#92400E",
                        "La cita queda <b>pendiente de pago</b>. Puede pagarla en línea desde "
                                + "<b>Mis Citas</b> o directamente en caja el día de su cita."),
                "Ver mis citas");
    }

    // =====================================================================
    // Plantilla comun (estetica navy + cian)
    // =====================================================================

    /**
     * @param etiqueta       pastilla cian del encabezado, ej: "PAGO CONFIRMADO"
     * @param titulo         titulo grande en la franja navy
     * @param subtitulo      texto gris bajo el titulo
     * @param destacadoTit   titulo del recuadro cian claro (null = sin recuadro)
     * @param destacadoValor valor grande del recuadro cian claro
     * @param tablaTitulo    titulo de la tabla de detalle
     * @param filas          filas de la tabla (usar fila())
     * @param totalEtiqueta  fila final resaltada (null = sin total)
     * @param totalValor     valor de la fila final, en cian
     * @param avisoHtml      caja de aviso (usar aviso()), o null
     * @param botonTexto     texto del boton cian que lleva al login
     */
    private String plantilla(String etiqueta, String titulo, String subtitulo,
                             String destacadoTit, String destacadoValor,
                             String tablaTitulo, String filas,
                             String totalEtiqueta, String totalValor,
                             String avisoHtml, String botonTexto) {

        String destacado = destacadoTit == null ? "" : """
            <tr><td style="padding:28px 32px 0;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0"
                     style="background:#ECFEFF;border:1px solid #E0F7FA;border-radius:14px;">
                <tr><td style="padding:18px 20px;">
                  <div style="font-size:12px;color:#0891B2;font-weight:700;letter-spacing:.5px;">{{D_TIT}}</div>
                  <div style="margin-top:4px;font-size:20px;font-weight:800;color:#0F172A;">{{D_VAL}}</div>
                </td></tr>
              </table>
            </td></tr>"""
                .replace("{{D_TIT}}", destacadoTit)
                .replace("{{D_VAL}}", destacadoValor);

        String total = totalEtiqueta == null ? "" : """
            <tr>
              <td style="padding:16px 20px;border-top:1px solid #E2E8F0;background:#F8FAFC;font-size:13px;
                         font-weight:700;color:#0F172A;border-radius:0 0 0 14px;">{{T_ETQ}}</td>
              <td align="right" style="padding:16px 20px;border-top:1px solid #E2E8F0;background:#F8FAFC;
                         font-size:18px;font-weight:800;color:#0891B2;border-radius:0 0 14px 0;">{{T_VAL}}</td>
            </tr>"""
                .replace("{{T_ETQ}}", totalEtiqueta)
                .replace("{{T_VAL}}", totalValor);

        String html = """
            <div style="margin:0;padding:32px 0;background:#F1F5F9;
                        font-family:'Plus Jakarta Sans',Arial,Helvetica,sans-serif;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0">
                <tr><td align="center" style="padding:0 16px;">
                  <table role="presentation" width="600" cellpadding="0" cellspacing="0"
                         style="max-width:600px;width:100%;background:#FFFFFF;border-radius:20px;overflow:hidden;
                                box-shadow:0 20px 40px -12px rgba(15,23,42,0.15);">

                    <!-- Marca -->
                    <tr><td style="padding:22px 32px;border-bottom:1px solid #F1F5F9;">
                      <table role="presentation" cellpadding="0" cellspacing="0"><tr>
                        <td style="width:36px;height:36px;border-radius:50%;background:#06B6D4;text-align:center;
                                   vertical-align:middle;color:#FFFFFF;font-size:16px;font-weight:800;">&#9829;</td>
                        <td style="padding-left:10px;font-size:15px;font-weight:800;color:#0F172A;letter-spacing:.3px;">
                          {{HOSPITAL}}
                        </td>
                      </tr></table>
                    </td></tr>

                    <!-- Franja navy -->
                    <tr><td style="background:#0F172A;padding:36px 32px;">
                      <div style="display:inline-block;padding:4px 12px;border-radius:20px;background:rgba(34,211,238,0.15);
                                  color:#22D3EE;font-size:11px;font-weight:700;letter-spacing:1px;">{{ETIQUETA}}</div>
                      <div style="margin-top:14px;font-size:26px;font-weight:800;color:#FFFFFF;line-height:1.25;">
                        {{TITULO}}
                      </div>
                      <div style="margin-top:6px;font-size:15px;color:#94A3B8;line-height:1.6;">
                        {{SUBTITULO}}
                      </div>
                    </td></tr>

                    {{DESTACADO}}

                    <!-- Detalle -->
                    <tr><td style="padding:20px 32px 8px;">
                      <table role="presentation" width="100%" cellpadding="0" cellspacing="0"
                             style="border:1px solid #E2E8F0;border-radius:14px;border-collapse:separate;">
                        <tr><td colspan="2" style="padding:14px 20px;font-size:11px;font-weight:700;letter-spacing:1px;
                                 color:#94A3B8;">{{TABLA_TITULO}}</td></tr>
                        {{FILAS}}
                        {{TOTAL}}
                      </table>
                    </td></tr>

                    <!-- Aviso + boton -->
                    <tr><td style="padding:16px 32px 32px;">
                      {{AVISO}}
                      <div style="margin-top:24px;text-align:center;">
                        <a href="{{URL}}" style="display:inline-block;background:#06B6D4;color:#FFFFFF;text-decoration:none;
                           padding:14px 34px;border-radius:10px;font-size:15px;font-weight:700;">{{BOTON}}</a>
                      </div>
                    </td></tr>

                    <!-- Pie -->
                    <tr><td style="background:#F8FAFC;padding:20px 32px;text-align:center;border-top:1px solid #F1F5F9;">
                      <div style="font-size:12px;font-weight:700;color:#475569;">{{HOSPITAL}}</div>
                      <div style="margin-top:4px;font-size:11px;color:#94A3B8;">
                        Este es un correo automático, por favor no responda.
                      </div>
                    </td></tr>

                  </table>
                </td></tr>
              </table>
            </div>
            """;

        return html
                .replace("{{HOSPITAL}}", nombreHospital.toUpperCase())
                .replace("{{ETIQUETA}}", etiqueta)
                .replace("{{TITULO}}", titulo)
                .replace("{{SUBTITULO}}", subtitulo)
                .replace("{{DESTACADO}}", destacado)
                .replace("{{TABLA_TITULO}}", tablaTitulo)
                .replace("{{FILAS}}", filas)
                .replace("{{TOTAL}}", total)
                .replace("{{AVISO}}", avisoHtml == null ? "" : avisoHtml)
                .replace("{{URL}}", urlLogin)
                .replace("{{BOTON}}", botonTexto);
    }

    /** Una fila etiqueta (gris) / valor (negrita) de la tabla de detalle. */
    private String fila(String etiqueta, String valor) {
        return """
            <tr>
              <td style="padding:14px 20px;border-top:1px solid #F1F5F9;font-size:13px;color:#64748B;">{{E}}</td>
              <td align="right" style="padding:14px 20px;border-top:1px solid #F1F5F9;font-size:14px;
                  font-weight:700;color:#0F172A;">{{V}}</td>
            </tr>"""
                .replace("{{E}}", etiqueta)
                .replace("{{V}}", valor == null ? "" : valor);
    }

    /** Caja de aviso de color suave (fondo + color de texto). */
    private String aviso(String fondo, String color, String textoHtml) {
        return """
            <div style="padding:14px 16px;border-radius:10px;background:{{F}};color:{{C}};
                        font-size:13px;line-height:1.6;">{{T}}</div>"""
                .replace("{{F}}", fondo)
                .replace("{{C}}", color)
                .replace("{{T}}", textoHtml);
    }

    private String primerNombre(String nombre) {
        return nombre.trim().split("\\s+")[0];
    }
}