package cl.duoc.pedidos360.mensajeria;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** El contexto arranca sin necesitar un RabbitMQ real (listeners apagados). */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "management.health.rabbit.enabled=false"})
class MensajeriaApplicationTests {

    @Test
    void contextLoads() {
    }
}
