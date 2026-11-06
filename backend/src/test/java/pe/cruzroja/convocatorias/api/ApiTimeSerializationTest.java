package pe.cruzroja.convocatorias.api;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import pe.cruzroja.convocatorias.shared.web.WebSerializationConfiguration;

class ApiTimeSerializationTest {
    @Test void webAdapterPreservesPeruOffsetWithoutChangingDomainDates() throws Exception {
        var builder=new Jackson2ObjectMapperBuilder(); new WebSerializationConfiguration().limaDateTimes().customize(builder);
        var mapper=builder.build(); var local=LocalDateTime.of(2030,1,5,8,0);
        var json=mapper.readTree(mapper.writeValueAsString(Map.of("start_at",local)));
        var exposed=OffsetDateTime.parse(json.get("start_at").asText());
        assertEquals(local,exposed.toLocalDateTime()); assertEquals(ZoneOffset.ofHours(-5),exposed.getOffset());
        assertEquals(Instant.parse("2030-01-05T13:00:00Z"),exposed.toInstant());
    }
}
