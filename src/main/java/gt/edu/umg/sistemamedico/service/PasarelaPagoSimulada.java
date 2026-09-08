package gt.edu.umg.sistemamedico.service;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Pasarela de pago SIMULADA. [CU-04]
 * No hay pasarela real: el resultado se decide por el ultimo digito de la
 * tarjeta, para poder demostrar cada flujo alterno (FA03) a voluntad.
 */
@Service
public class PasarelaPagoSimulada {

    /** Resultado que devuelve la pasarela. */
    public record Resultado(
            boolean aprobado,
            String codigoRechazo,   // null si aprobado
            String mensaje,         // texto para mostrar al paciente
            String pasarelaRef      // id de referencia de la transaccion
    ) {}

    /**
     * Procesa el cobro. numeroTarjeta viene SIN espacios (solo digitos).
     */
    public Resultado procesar(String numeroTarjeta) {

        // 1) Validacion Luhn: si no pasa, datos invalidos.
        if (!pasaLuhn(numeroTarjeta)) {
            return new Resultado(false, "DATOS_INVALIDOS",
                    "El numero de tarjeta no es valido.", null);
        }

        String ref = "PAS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        char ultimo = numeroTarjeta.charAt(numeroTarjeta.length() - 1);

        // 2) Reglas de simulacion segun el ultimo digito.
        switch (ultimo) {
            case '0':
                return new Resultado(false, "RECHAZO_BANCARIO",
                        "La transaccion con tarjeta fue rechazada por el banco. " +
                                "Por favor, verifique los datos de su tarjeta o intente con una tarjeta diferente.",
                        ref);
            case '1':
                return new Resultado(false, "ERROR_COMUNICACION",
                        "Error de comunicacion con la pasarela de pago. Intente nuevamente en unos minutos.",
                        ref);
            case '2':
                return new Resultado(false, "ERROR_PROCESAMIENTO",
                        "El pago no pudo ser procesado. Por favor, intente nuevamente o utilice otra tarjeta.",
                        ref);
            default:
                return new Resultado(true, null, "Pago aprobado.", ref);
        }
    }

    /** Algoritmo de Luhn. [RN-CU04-01] */
    private boolean pasaLuhn(String numero) {
        if (numero == null || !numero.matches("\\d{13,19}")) {
            return false;
        }
        int suma = 0;
        boolean alternar = false;
        for (int i = numero.length() - 1; i >= 0; i--) {
            int d = numero.charAt(i) - '0';
            if (alternar) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            suma += d;
            alternar = !alternar;
        }
        return suma % 10 == 0;
    }
}