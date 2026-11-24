package pe.cruzroja.convocatorias.membership.web;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pe.cruzroja.convocatorias.membership.application.*;

@Configuration(proxyBeanMethods = false)
class MembershipSerializationConfiguration {
    @Bean
    Jackson2ObjectMapperBuilderCustomizer membershipJsonNames() {
        return builder ->
                builder.mixIn(VolunteerProfile.class, SnakeCase.class)
                        .mixIn(PendingProfileChange.class, SnakeCase.class)
                        .mixIn(ProfileChangeRequest.class, SnakeCase.class)
                        .mixIn(AnnualEnrollmentDetail.class, SnakeCase.class)
                        .mixIn(AnnualEnrollmentSummary.class, SnakeCase.class);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    private interface SnakeCase {}
}
