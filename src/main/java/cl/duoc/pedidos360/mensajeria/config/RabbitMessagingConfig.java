package cl.duoc.pedidos360.mensajeria.config;

import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Los mensajes que publica este servicio viajan como JSON (Jackson 3, el que trae
 * Spring Boot 4). Los consumidores leen el cuerpo crudo y lo convierten ellos mismos,
 * para poder enviar a la DLQ un mensaje ilegible (ver ConfirmacionManual).
 */
@Configuration
public class RabbitMessagingConfig {

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
