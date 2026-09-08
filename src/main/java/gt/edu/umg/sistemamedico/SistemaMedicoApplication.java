package gt.edu.umg.sistemamedico;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class SistemaMedicoApplication {

    public static void main(String[] args) {
        /*
         * Fija la zona horaria de TODA la aplicacion a Guatemala, sin
         * importar en que servidor corra (local, Azure, etc). Sin esto,
         * ZoneId.systemDefault() usa la zona del sistema operativo del
         * servidor (Azure normalmente corre en UTC), causando un desfase
         * de horas entre lo que el paciente selecciona y lo que se
         * guarda/muestra despues.
         */
        TimeZone.setDefault(TimeZone.getTimeZone("America/Guatemala"));

        SpringApplication.run(SistemaMedicoApplication.class, args);
    }

}