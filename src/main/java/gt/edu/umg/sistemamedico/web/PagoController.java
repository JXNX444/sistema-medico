package gt.edu.umg.sistemamedico.web;

import gt.edu.umg.sistemamedico.domain.Cita;
import gt.edu.umg.sistemamedico.repository.CitaRepository;
import gt.edu.umg.sistemamedico.security.UsuarioDetails;
import gt.edu.umg.sistemamedico.service.PagoService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CU-04 Pago en Linea. Pantalla de pago de una cita y procesamiento.
 *
 * Rutas (cubiertas por /citas/** -> rol Paciente en SecurityConfig):
 *   GET  /citas/pago/{citaId}          -> pantalla "Pago de Consulta"
 *   POST /citas/pago/{citaId}/procesar -> valida (FA01) y cobra
 */
@Controller
@RequestMapping("/citas")
public class PagoController {

    private final CitaRepository citaRepository;
    private final PagoService pagoService;

    private static final ZoneId ZONA_GT = ZoneId.of("America/Guatemala");

    public PagoController(CitaRepository citaRepository, PagoService pagoService) {
        this.citaRepository = citaRepository;
        this.pagoService = pagoService;
    }

    // ---------- PANTALLA DE PAGO ----------

    @GetMapping("/pago/{citaId}")
    public String pago(@PathVariable Integer citaId,
                       @AuthenticationPrincipal UsuarioDetails ud, Model model) {

        Cita cita = citaRepository.findById(citaId).orElse(null);

        // La cita debe existir y pertenecer al paciente logueado.
        if (cita == null || !cita.getPaciente().getId().equals(ud.getUsuario().getId())) {
            return "redirect:/citas/mis-citas";
        }

        // Solo se paga una cita que este pendiente de pago.
        if (!"PENDIENTE_PAGO".equals(cita.getEstadoCita().getCodigo())) {
            return "redirect:/citas/mis-citas";
        }

        model.addAttribute("cita", cita);
        model.addAttribute("montoFmt",
                cita.getMonto().setScale(2, RoundingMode.HALF_UP).toPlainString());

        // La cita se guarda como instante UTC; para mostrarla la pasamos a hora de Guatemala.
        ZonedDateTime local = cita.getFechaHora().atZoneSameInstant(ZONA_GT);
        model.addAttribute("fechaFmt", local.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        model.addAttribute("horaFmt", local.format(DateTimeFormatter.ofPattern("HH:mm")));

        model.addAttribute("expiraEn",
                cita.getReservaExpiraEn() != null ? cita.getReservaExpiraEn().toString() : "");
        return "citas/pago";
    }

    // ---------- PROCESAR EL PAGO ----------

    @PostMapping("/pago/{citaId}/procesar")
    @ResponseBody
    public Map<String, Object> procesar(@PathVariable Integer citaId,
                                        @RequestBody PagoForm form,
                                        @AuthenticationPrincipal UsuarioDetails ud) {

        // FA01: validacion de formato de los campos.
        Map<String, Object> errores = validar(form);
        if (!errores.isEmpty()) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", false);
            out.put("estado", "VALIDACION");
            out.put("errores", errores);
            return out;
        }

        // Llave de idempotencia [RNF-016]; si viene mal, generamos una.
        UUID key;
        try {
            key = UUID.fromString(form.idempotencyKey());
        } catch (Exception e) {
            key = UUID.randomUUID();
        }

        String tarjeta = form.numeroTarjeta().replaceAll("\\s", "");

        PagoService.ResultadoPago r =
                pagoService.procesarPago(citaId, ud.getUsuario().getId(), tarjeta, key);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", r.ok());
        out.put("estado", r.estado());
        out.put("mensaje", r.mensaje());
        out.put("numeroTransaccion", r.numeroTransaccion());
        out.put("codigoRechazo", r.codigoRechazo());
        return out;
    }

    // ---------- Validacion FA01 (RN-CU04-01 a 04) ----------

    private Map<String, Object> validar(PagoForm f) {
        Map<String, Object> e = new LinkedHashMap<>();

        // [RN-CU04-01] numero de tarjeta: 13-19 digitos (el Luhn lo verifica la pasarela).
        String tarjeta = f.numeroTarjeta() == null ? "" : f.numeroTarjeta().replaceAll("\\s", "");
        if (!tarjeta.matches("\\d{13,19}")) {
            e.put("numeroTarjeta", "El numero de tarjeta no es valido.");
        }

        // [RN-CU04-02] titular: 5 a 100 caracteres.
        String titular = f.titular() == null ? "" : f.titular().trim();
        if (titular.length() < 5 || titular.length() > 100) {
            e.put("titular", "El nombre del titular debe tener entre 5 y 100 caracteres.");
        }

        // [RN-CU04-03] vencimiento MM/AA y no vencida.
        String v = f.vencimiento() == null ? "" : f.vencimiento().trim();
        if (!v.matches("(0[1-9]|1[0-2])/\\d{2}")) {
            e.put("vencimiento", "Formato invalido. Use MM/AA.");
        } else if (estaVencida(v)) {
            e.put("vencimiento", "La tarjeta esta vencida.");
        }

        // [RN-CU04-04] CVV: 3 o 4 digitos.
        String cvv = f.cvv() == null ? "" : f.cvv().trim();
        if (!cvv.matches("\\d{3,4}")) {
            e.put("cvv", "El CVV debe tener 3 o 4 digitos.");
        }

        return e;
    }

    /** true si el mes de vencimiento ya paso. La tarjeta vale hasta el ultimo dia de ese mes. */
    private boolean estaVencida(String mmYY) {
        int mm = Integer.parseInt(mmYY.substring(0, 2));
        int yy = Integer.parseInt(mmYY.substring(3, 5));
        return YearMonth.of(2000 + yy, mm).isBefore(YearMonth.now());
    }
}