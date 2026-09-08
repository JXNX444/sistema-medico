package gt.edu.umg.sistemamedico.web;

/**
 * Datos que envia el formulario de pago (CU-04). [FA01]
 * El CVV llega para validarse en el momento, pero NUNCA se persiste. [RN-CU04-04]
 */
public record PagoForm(
        String numeroTarjeta,
        String titular,
        String vencimiento,   // MM/AA
        String cvv,
        String idempotencyKey // UUID generado por el navegador [RNF-016]
) {}