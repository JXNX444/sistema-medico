package gt.edu.umg.sistemamedico.web;

import java.math.BigDecimal;

/**
 * Datos que envia la pantalla de caja al registrar un cobro. [CU-06]
 *
 * @param citaId         cita a cobrar (debe estar PENDIENTE_PAGO).
 * @param metodoPago     0=Efectivo, 1=Visa, 2=Mastercard, 3=Debito.
 * @param montoRecibido  solo efectivo: lo que entrego el paciente.
 * @param ultimos4       solo tarjeta: ultimos 4 digitos como referencia.
 * @param idempotencyKey llave [RNF-016] que genera el navegador por intento.
 */
public record CobroForm(
        Integer citaId,
        Short metodoPago,
        BigDecimal montoRecibido,
        String ultimos4,
        String idempotencyKey
) {}