package org.cruzroja;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Punto de entrada de la gestión de inscripciones. */
@SpringBootApplication(scanBasePackages = {
        "org.cruzroja.bc1_usuarios", "org.cruzroja.bc2_convocatorias", "org.cruzroja.bc3_inscripciones"
})
public class Main {
    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

    @Bean
    WebMvcConfigurer redireccionPaginaPrincipal() {
        return new WebMvcConfigurer() {
            @Override
            public void addViewControllers(ViewControllerRegistry registro) {
                registro.addRedirectViewController("/", "/main.html");
            }
        };
    }
}
