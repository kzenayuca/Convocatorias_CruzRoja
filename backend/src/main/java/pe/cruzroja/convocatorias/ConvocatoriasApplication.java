package pe.cruzroja.convocatorias;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ConvocatoriasApplication {
    @jakarta.annotation.PostConstruct
    void useLocalTime() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Lima"));
    }

    public static void main(String[] args) {
        SpringApplication.run(ConvocatoriasApplication.class, args);
    }
}
