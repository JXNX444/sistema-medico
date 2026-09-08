package gt.edu.umg.sistemamedico.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Envio de correos del sistema. [RN-GLOBAL-006]
 * CU-02 paso 14: correo de bienvenida al paciente recien registrado (HTML).
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

    /**
     * Correo de bienvenida (CU-02 paso 14). Tolerante a fallos:
     * si el SMTP no esta configurado o falla, el registro NO debe romperse.
     */
    public void enviarBienvenida(String correoDestino, String nombrePaciente, String username) {

        if (remitente == null || remitente.isBlank()) {
            log.warn("Correo SMTP no configurado (MAIL_USER vacio). Se omite bienvenida a {}", correoDestino);
            return;
        }

        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, "UTF-8");

            helper.setFrom(remitente);
            helper.setTo(correoDestino);
            helper.setSubject("Bienvenido al Sistema de Citas - " + nombreHospital);
            helper.setText(construirHtml(nombrePaciente, username, correoDestino), true); // true = HTML

            mailSender.send(mensaje);
            log.info("Correo de bienvenida enviado a {}", correoDestino);

        } catch (MessagingException e) {
            log.error("No se pudo enviar la bienvenida a {}: {}", correoDestino, e.getMessage());
        }
    }

    /**
     * Comprobante de pago (CU-04 paso 14, RN-CU04-05). Tolerante a fallos:
     * si el SMTP no esta configurado o falla, el pago NO debe romperse.
     */
    public void enviarComprobantePago(String correoDestino, String nombrePaciente,
                                      String numeroTransaccion, String medico,
                                      String especialidad, String sucursal,
                                      String fechaHora, String monto) {

        if (remitente == null || remitente.isBlank()) {
            log.warn("Correo SMTP no configurado (MAIL_USER vacio). Se omite comprobante a {}", correoDestino);
            return;
        }

        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, "UTF-8");

            helper.setFrom(remitente);
            helper.setTo(correoDestino);
            helper.setSubject("Comprobante de pago " + numeroTransaccion + " - " + nombreHospital);
            helper.setText(construirHtmlComprobante(nombrePaciente, numeroTransaccion,
                    medico, especialidad, sucursal, fechaHora, monto), true);

            mailSender.send(mensaje);
            log.info("Comprobante de pago enviado a {}", correoDestino);

        } catch (MessagingException e) {
            log.error("No se pudo enviar el comprobante a {}: {}", correoDestino, e.getMessage());
        }
    }

    private String construirHtmlComprobante(String nombre, String numeroTransaccion,
                                            String medico, String especialidad,
                                            String sucursal, String fechaHora, String monto) {
        String primerNombre = nombre.trim().split("\\s+")[0];

        String fila = """
            <tr><td style="padding:12px 18px;text-align:left;font-size:14px;color:#04342C;
                     border-top:1px solid #eee7db;">
              <span style="color:#0F6E56;">{{ETIQUETA}}</span>
              <span style="float:right;font-weight:bold;">{{VALOR}}</span>
            </td></tr>""";

        String filas =
                fila.replace("{{ETIQUETA}}", "&#128104;&#8205;&#9877; Medico").replace("{{VALOR}}", medico) +
                        fila.replace("{{ETIQUETA}}", "&#129658; Especialidad").replace("{{VALOR}}", especialidad) +
                        fila.replace("{{ETIQUETA}}", "&#127973; Sede").replace("{{VALOR}}", sucursal) +
                        fila.replace("{{ETIQUETA}}", "&#128197; Fecha y hora").replace("{{VALOR}}", fechaHora);

        String html = """
            <div style="background:#e9e7e1;padding:24px 0;font-family:Arial,sans-serif;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0">
                <tr><td align="center">
                  <table role="presentation" width="600" cellpadding="0" cellspacing="0"
                         style="max-width:600px;width:100%;background:#faf8f3;border-radius:10px;overflow:hidden;">
                    <tr><td style="background:#0F6E56;border-bottom:4px solid #EF9F27;padding:22px 24px;
                             text-align:center;color:#ffffff;font-size:20px;font-weight:bold;
                             font-family:Georgia,serif;">
                      &#9829; Hospital {{HOSPITAL}}
                    </td></tr>
                    <tr><td style="padding:32px 40px;text-align:center;color:#04342C;">
                      <div style="width:56px;height:56px;line-height:56px;margin:0 auto 16px;
                           border-radius:50%;background:#d7ece4;color:#0F6E56;font-size:26px;">&#10003;</div>
                      <h1 style="margin:0 0 6px;font-size:24px;color:#0F6E56;font-family:Georgia,serif;">
                        ¡Pago realizado exitosamente!
                      </h1>
                      <p style="margin:0 0 20px;font-size:15px;line-height:1.6;color:#3a4a44;">
                        {{SALUDO}}, su cita ha sido confirmada. Este es su comprobante de pago.
                      </p>
                      <table role="presentation" width="100%" cellpadding="0" cellspacing="0"
                             style="border:1px solid #e2ddd3;border-radius:8px;margin:0 0 20px;">
                        <tr><td style="padding:14px 18px;text-align:left;font-size:11px;letter-spacing:1px;
                                 color:#8a8577;border-bottom:1px solid #eee7db;">COMPROBANTE DE PAGO</td></tr>
                        <tr><td style="padding:12px 18px;text-align:left;font-size:14px;color:#04342C;">
                          <span style="color:#0F6E56;">&#128179; No. de transaccion</span>
                          <span style="float:right;font-weight:bold;">{{TRX}}</span>
                        </td></tr>
                        {{FILAS}}
                        <tr><td style="padding:14px 18px;text-align:left;font-size:16px;color:#04342C;
                                 border-top:2px solid #0F6E56;background:#f0ede5;">
                          <span style="color:#0F6E56;font-weight:bold;">TOTAL PAGADO</span>
                          <span style="float:right;font-weight:bold;color:#0F6E56;">Q {{MONTO}}</span>
                        </td></tr>
                      </table>
                      <p style="margin:0;font-size:12px;color:#8a8577;">
                        Conserve este comprobante. Presentelo en recepcion el dia de su cita.
                      </p>
                    </td></tr>
                    <tr><td style="background:#f0ede5;padding:18px 24px;text-align:center;font-size:12px;
                             color:#8a8577;border-top:1px solid #e2ddd3;">
                      Hospital {{HOSPITAL}}<br>
                      <span style="color:#b0aa9c;">Este es un correo automático, por favor no responda.</span>
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </div>
            """;

        return html
                .replace("{{HOSPITAL}}", nombreHospital)
                .replace("{{SALUDO}}", "Estimado(a) " + primerNombre)
                .replace("{{TRX}}", numeroTransaccion)
                .replace("{{FILAS}}", filas)
                .replace("{{MONTO}}", monto);
    }

    private String construirHtml(String nombre, String username, String correo) {
        String primerNombre = nombre.trim().split("\\s+")[0];

        String html = """
            <div style="background:#e9e7e1;padding:24px 0;font-family:Arial,sans-serif;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0">
                <tr><td align="center">
                  <table role="presentation" width="600" cellpadding="0" cellspacing="0"
                         style="max-width:600px;width:100%;background:#faf8f3;border-radius:10px;overflow:hidden;">
                    <tr><td style="background:#0F6E56;border-bottom:4px solid #EF9F27;padding:22px 24px;
                             text-align:center;color:#ffffff;font-size:20px;font-weight:bold;
                             font-family:Georgia,serif;">
                      &#9829; Hospital {{HOSPITAL}}
                    </td></tr>
                    <tr><td style="padding:32px 40px;text-align:center;color:#04342C;">
                      <div style="width:56px;height:56px;line-height:56px;margin:0 auto 16px;
                           border-radius:50%;background:#d7ece4;color:#0F6E56;font-size:26px;">&#10003;</div>
                      <h1 style="margin:0 0 12px;font-size:24px;color:#0F6E56;font-family:Georgia,serif;">
                        ¡Bienvenido, {{SALUDO}}!
                      </h1>
                      <p style="margin:0 0 24px;font-size:15px;line-height:1.6;color:#3a4a44;">
                        Estimado(a) {{NOMBRE}}, su registro ha sido completado exitosamente.
                        Ya puede agendar sus citas médicas a través de nuestro portal.
                      </p>
                      <table role="presentation" width="100%" cellpadding="0" cellspacing="0"
                             style="border:1px solid #e2ddd3;border-radius:8px;margin:0 0 24px;">
                        <tr><td style="padding:14px 18px;text-align:left;font-size:11px;letter-spacing:1px;
                                 color:#8a8577;border-bottom:1px solid #eee7db;">DATOS DE TU CUENTA</td></tr>
                        <tr><td style="padding:12px 18px;text-align:left;font-size:14px;color:#04342C;">
                          <span style="color:#0F6E56;">&#128100; Usuario</span>
                          <span style="float:right;font-weight:bold;">{{USUARIO}}</span>
                        </td></tr>
                        <tr><td style="padding:12px 18px;text-align:left;font-size:14px;color:#04342C;
                                 border-top:1px solid #eee7db;">
                          <span style="color:#0F6E56;">&#9993; Correo</span>
                          <span style="float:right;font-weight:bold;">{{CORREO}}</span>
                        </td></tr>
                      </table>
                      <a href="{{URL}}" style="display:inline-block;background:#EF9F27;color:#ffffff;
                         text-decoration:none;padding:13px 32px;border-radius:6px;font-size:15px;
                         font-weight:bold;">Iniciar sesión</a>
                      <p style="margin:24px 0 0;font-size:12px;color:#8a8577;">
                        Si usted no creó esta cuenta, ignore este mensaje o contacte a recepción.
                      </p>
                    </td></tr>
                    <tr><td style="background:#f0ede5;padding:18px 24px;text-align:center;font-size:12px;
                             color:#8a8577;border-top:1px solid #e2ddd3;">
                      Hospital {{HOSPITAL}}<br>
                      <span style="color:#b0aa9c;">Este es un correo automático, por favor no responda.</span>
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </div>
            """;

        return html
                .replace("{{HOSPITAL}}", nombreHospital)
                .replace("{{SALUDO}}", primerNombre)
                .replace("{{NOMBRE}}", nombre)
                .replace("{{USUARIO}}", username)
                .replace("{{CORREO}}", correo)
                .replace("{{URL}}", urlLogin);
    }
}