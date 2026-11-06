package pe.cruzroja.convocatorias.shared.web;

import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.*;

/** Conserva el contrato temporal anterior: los instantes expuestos a la web llevan el offset de Perú. */
@Configuration
public class WebSerializationConfiguration {
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer limaDateTimes() {
        return builder -> builder.serializers(new JsonSerializer<LocalDateTime>() {
            @Override public Class<LocalDateTime> handledType() { return LocalDateTime.class; }
            @Override public void serialize(LocalDateTime value,JsonGenerator generator,SerializerProvider provider) throws IOException {
                generator.writeString(value.atZone(ZoneId.of("America/Lima")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            }
        });
    }
}
