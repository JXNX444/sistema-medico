package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.service.TipoReporte;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Ejecuta el SQL de cualquier reporte del enum TipoReporte.
 *
 * Usa NamedParameterJdbcTemplate (SQL nativo con parametros :nombre) en vez de
 * JPA porque los reportes son agregaciones (GROUP BY / COUNT / SUM) y no
 * entidades. Spring Boot lo configura solo con el starter data-jpa.
 */
@Repository
public class ReporteRepository {

    /** Una fila tal como sale de la BD: etiqueta, detalle (puede ser null) y valor. */
    public record FilaBD(String etiqueta, String detalle, BigDecimal valor) { }

    private final NamedParameterJdbcTemplate jdbc;

    public ReporteRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param tipo        reporte a ejecutar (trae su SQL)
     * @param desde       fecha inicial, incluida
     * @param hasta       fecha final, incluida
     * @param sucursalId  id de la sede; 0 = todas las sedes
     * @param top         cuantas filas devolver (solo lo usan los reportes con LIMIT :top)
     */
    public List<FilaBD> ejecutar(TipoReporte tipo, LocalDate desde, LocalDate hasta,
                                 int sucursalId, int top) {
        MapSqlParameterSource parametros = new MapSqlParameterSource()
                .addValue("desde", desde)
                .addValue("hasta", hasta)
                .addValue("sucursal", sucursalId)
                .addValue("top", top);

        return jdbc.query(tipo.getSql(), parametros, (rs, numFila) -> new FilaBD(
                rs.getString("etiqueta"),
                rs.getString("detalle"),
                rs.getBigDecimal("valor")
        ));
    }
}