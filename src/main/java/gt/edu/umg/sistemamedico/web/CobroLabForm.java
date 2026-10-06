package gt.edu.umg.sistemamedico.web;

import java.math.BigDecimal;

/**
 * Datos que envia la pantalla de caja al cobrar una orden de laboratorio. [CU-10]
 *
 * @param ordenId        orden a cobrar (debe estar Pendiente y no ser externa).
 * @param metodoPago     0=Efectivo, 1=Visa, 2=Mastercard, 3=Debito.
 * @param montoRecibido  solo efectivo: lo que entrego el paciente.
 * @param ultimos4       solo tarjeta: ultimos 4 digitos (FA03).
 * @param idempotencyKey llave [RNF-016] que genera el navegador por intento.
 */
public record CobroLabForm(
        Integer ordenId,
        Short metodoPago,
        BigDecimal montoRecibido,
        String ultimos4,
        String idempotencyKey
) {}