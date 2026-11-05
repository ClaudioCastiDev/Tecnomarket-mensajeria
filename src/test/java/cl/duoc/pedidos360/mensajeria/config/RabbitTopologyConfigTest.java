package cl.duoc.pedidos360.mensajeria.config;

import cl.duoc.pedidos360.mensajeria.RabbitTestProps;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Verifica la topología sin levantar Spring ni RabbitMQ. */
class RabbitTopologyConfigTest {

    private final RabbitTopologyConfig config = new RabbitTopologyConfig(RabbitTestProps.rabbit());

    @Test
    void exchangesSonDirectYTopic() {
        assertEquals("direct", config.pedidosExchange().getType());
        assertEquals("topic", config.eventosExchange().getType());
        assertEquals("direct", config.dlxExchange().getType());
    }

    @Test
    void cadaColaApuntaASuDlq() {
        assertDlq(config.stockQueue(), "q.stock", "q.stock.dlq");
        assertDlq(config.ticketQueue(), "q.ticket", "q.ticket.dlq");
        assertDlq(config.notificacionesQueue(), "q.notificaciones", "q.notificaciones.dlq");
    }

    @Test
    void lasDlqNoTienenDlq() {
        assertNull(config.stockDlq().getArguments().get("x-dead-letter-exchange"));
        assertEquals("q.stock.dlq", config.stockDlq().getName());
    }

    @Test
    void bindingsUsanLasClavesConfiguradas() {
        assertBinding(config.stockBinding(), "q.stock", "tecnomarket.pedidos.direct", "pedido.stock");
        assertBinding(config.ticketBinding(), "q.ticket", "tecnomarket.pedidos.direct", "pedido.ticket");
        assertBinding(config.notifPedidosBinding(), "q.notificaciones", "tecnomarket.eventos.topic", "pedido.#");
        assertBinding(config.notifProductosBinding(), "q.notificaciones", "tecnomarket.eventos.topic", "producto.#");
        assertBinding(config.stockDlqBinding(), "q.stock.dlq", "tecnomarket.dlx", "q.stock.dlq");
    }

    private void assertDlq(Queue cola, String nombre, String dlq) {
        assertEquals(nombre, cola.getName());
        assertEquals("tecnomarket.dlx", cola.getArguments().get("x-dead-letter-exchange"));
        assertEquals(dlq, cola.getArguments().get("x-dead-letter-routing-key"));
    }

    private void assertBinding(Binding b, String destino, String exchange, String clave) {
        assertEquals(destino, b.getDestination());
        assertEquals(exchange, b.getExchange());
        assertEquals(clave, b.getRoutingKey());
    }
}
