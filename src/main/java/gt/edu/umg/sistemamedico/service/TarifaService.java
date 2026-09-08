package gt.edu.umg.sistemamedico.service;

import gt.edu.umg.sistemamedico.domain.Especialidad;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Precio de la consulta segun la especialidad. [CU-04]
 * Mapa en memoria: cero cambios en la base de Azure.
 */
@Service
public class TarifaService {

    private static final BigDecimal DEFAULT = new BigDecimal("150.00");

    private static final Map<String, BigDecimal> PRECIOS = Map.of(
            "Medicina General", new BigDecimal("150.00"),
            "Pediatria",        new BigDecimal("175.00"),
            "Odontologia",      new BigDecimal("175.00"),
            "Dermatologia",     new BigDecimal("200.00"),
            "Ginecologia",      new BigDecimal("225.00"),
            "Oftalmologia",     new BigDecimal("225.00"),
            "Traumatologia",    new BigDecimal("275.00"),
            "Cardiologia",      new BigDecimal("300.00")
    );

    /** Devuelve el precio de la especialidad, o Q150 si no esta en el mapa. */
    public BigDecimal precioConsulta(Especialidad especialidad) {
        if (especialidad == null) {
            return DEFAULT;
        }
        return PRECIOS.getOrDefault(especialidad.getNombre(), DEFAULT);
    }
}