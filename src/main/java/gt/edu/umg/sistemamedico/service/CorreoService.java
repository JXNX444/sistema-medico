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
 * CU-04 paso 14: comprobante de pago.
 * CU-08 FA02 paso 4: aviso de cita de seguimiento (estetica nueva navy + cian).
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

    /**
     * Aviso de cita de seguimiento (CU-08 FA02 paso 4). Tolerante a fallos:
     * si el SMTP no esta configurado o falla, la cita ya agendada NO debe
     * deshacerse. Atrapa Exception (no solo MessagingException) porque
     * mailSender.send() lanza MailException, que es de tipo runtime y
     * haria rollback de la transaccion que agendo la cita.
     */
    public void enviarCitaSeguimiento(String correoDestino, String nombrePaciente,
                                      String numeroCita, String tipoSeguimiento,
                                      String medico, String especialidad, String sucursal,
                                      String fechaHora, String monto) {

        if (remitente == null || remitente.isBlank()) {
            log.warn("Correo SMTP no configurado (MAIL_USER vacio). Se omite aviso de seguimiento a {}", correoDestino);
            return;
        }

        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, "UTF-8");

            helper.setFrom(remitente);
            helper.setTo(correoDestino);
            helper.setSubject("Cita de seguimiento " + numeroCita + " - " + nombreHospital);
            helper.setText(construirHtmlSeguimiento(nombrePaciente, numeroCita, tipoSeguimiento,
                    medico, especialidad, sucursal, fechaHora, monto), true);

            mailSender.send(mensaje);
            log.info("Aviso de cita de seguimiento enviado a {}", correoDestino);

        } catch (Exception e) {
            log.error("No se pudo enviar el aviso de seguimiento a {}: {}", correoDestino, e.getMessage());
        }
    }

    /**
     * Plantilla con la estetica actual del sistema (theme.css):
     * navy #0F172A, cian #06B6D4 / #0891B2, grises slate, bordes #E2E8F0,
     * fuente Plus Jakarta Sans. Todo con estilos en linea y tablas, que es
     * lo unico que respetan Gmail/Outlook.
     */
    private String construirHtmlSeguimiento(String nombre, String numeroCita, String tipoSeguimiento,
                                            String medico, String especialidad, String sucursal,
                                            String fechaHora, String monto) {
        String primerNombre = nombre.trim().split("\\s+")[0];

        String fila = """
            <tr>
              <td style="padding:14px 20px;border-top:1px solid #F1F5F9;font-size:13px;color:#64748B;">{{ETIQUETA}}</td>
              <td align="right" style="padding:14px 20px;border-top:1px solid #F1F5F9;font-size:14px;
                  font-weight:700;color:#0F172A;">{{VALOR}}</td>
            </tr>""";

        String filas =
                fila.replace("{{ETIQUETA}}", "No. de cita").replace("{{VALOR}}", numeroCita) +
                        fila.replace("{{ETIQUETA}}", "Tipo").replace("{{VALOR}}", tipoSeguimiento) +
                        fila.replace("{{ETIQUETA}}", "Médico").replace("{{VALOR}}", medico) +
                        fila.replace("{{ETIQUETA}}", "Especialidad").replace("{{VALOR}}", especialidad) +
                        fila.replace("{{ETIQUETA}}", "Sede").replace("{{VALOR}}", sucursal);

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

                    <!-- Hero navy -->
                    <tr><td style="background:#0F172A;padding:36px 32px;">
                      <div style="display:inline-block;padding:4px 12px;border-radius:20px;background:rgba(34,211,238,0.15);
                                  color:#22D3EE;font-size:11px;font-weight:700;letter-spacing:1px;">CITA DE SEGUIMIENTO</div>
                      <div style="margin-top:14px;font-size:26px;font-weight:800;color:#FFFFFF;line-height:1.25;">
                        Hola, {{SALUDO}}
                      </div>
                      <div style="margin-top:6px;font-size:15px;color:#94A3B8;line-height:1.6;">
                        Su médico le agendó una cita de seguimiento. Estos son los detalles.
                      </div>
                    </td></tr>

                    <!-- Fecha destacada -->
                    <tr><td style="padding:28px 32px 8px;">
                      <table role="presentation" width="100%" cellpadding="0" cellspacing="0"
                             style="background:#ECFEFF;border:1px solid #E0F7FA;border-radius:14px;">
                        <tr><td style="padding:18px 20px;">
                          <div style="font-size:12px;color:#0891B2;font-weight:700;letter-spacing:.5px;">FECHA Y HORA</div>
                          <div style="margin-top:4px;font-size:20px;font-weight:800;color:#0F172A;">{{FECHA}}</div>
                        </td></tr>
                      </table>
                    </td></tr>

                    <!-- Detalle -->
                    <tr><td style="padding:16px 32px 8px;">
                      <table role="presentation" width="100%" cellpadding="0" cellspacing="0"
                             style="border:1px solid #E2E8F0;border-radius:14px;border-collapse:separate;">
                        <tr><td colspan="2" style="padding:14px 20px;font-size:11px;font-weight:700;letter-spacing:1px;
                                 color:#94A3B8;">DETALLE DE LA CITA</td></tr>
                        {{FILAS}}
                        <tr>
                          <td style="padding:16px 20px;border-top:1px solid #E2E8F0;background:#F8FAFC;font-size:13px;
                                     font-weight:700;color:#0F172A;border-radius:0 0 0 14px;">Monto a pagar</td>
                          <td align="right" style="padding:16px 20px;border-top:1px solid #E2E8F0;background:#F8FAFC;
                                     font-size:18px;font-weight:800;color:#0891B2;border-radius:0 0 14px 0;">Q {{MONTO}}</td>
                        </tr>
                      </table>
                    </td></tr>

                    <!-- Aviso + boton -->
                    <tr><td style="padding:16px 32px 32px;">
                      <div style="padding:14px 16px;border-radius:10px;background:#FEF3C7;color:#92400E;
                                  font-size:13px;line-height:1.6;">
                        La cita queda <b>pendiente de pago</b>. Puede pagarla en línea desde <b>Mis Citas</b>
                        o directamente en caja el día de su cita.
                      </div>
                      <div style="margin-top:24px;text-align:center;">
                        <a href="{{URL}}" style="display:inline-block;background:#06B6D4;color:#FFFFFF;text-decoration:none;
                           padding:14px 34px;border-radius:10px;font-size:15px;font-weight:700;">Ver mis citas</a>
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
                .replace("{{SALUDO}}", primerNombre)
                .replace("{{FECHA}}", fechaHora)
                .replace("{{FILAS}}", filas)
                .replace("{{MONTO}}", monto)
                .replace("{{URL}}", urlLogin);
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