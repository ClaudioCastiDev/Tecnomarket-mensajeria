package cl.duoc.pedidos360.mensajeria.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología de mensajería: exchanges, colas con su DLQ y bindings, un grupo por caso de uso.
 * Esta clase es la misma en pedidos, productos y mensajería: RabbitMQ acepta la declaración
 * repetida si es idéntica, así que no importa qué servicio arranque primero.
 *
 * <pre>
 *  pedidos.direct --pedido.stock--&gt; q.stock
 *  pedidos.direct --pedido.ticket-&gt; q.ticket
 *  eventos.topic  --pedido.# / producto.#--&gt; q.notificaciones
 *  Cada cola rechazada (nack sin requeue) viaja por tecnomarket.dlx a su DLQ.
 * </pre>
 */
@Configuration
@EnableConfigurationProperties(RabbitProperties.class)
public class RabbitTopologyConfig {

    private final RabbitProperties p;

    public RabbitTopologyConfig(RabbitProperties p) {
        this.p = p;
    }

    // ---------------------------------------------------------------- Exchanges

    @Bean
    public DirectExchange pedidosExchange() {
        return new DirectExchange(p.exchanges().pedidos(), true, false);
    }

    @Bean
    public TopicExchange eventosExchange() {
        return new TopicExchange(p.exchanges().eventos(), true, false);
    }

    /** Dead Letter Exchange: recibe los mensajes rechazados de todas las colas. */
    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(p.exchanges().dlx(), true, false);
    }

    // ------------------------------------------- Caso de uso 1: stock (direct)

    @Bean
    public Queue stockQueue() {
        return conDlq(p.queues().stock(), p.dlqs().stock());
    }

    @Bean
    public Queue stockDlq() {
        return QueueBuilder.durable(p.dlqs().stock()).build();
    }

    @Bean
    public Binding stockBinding() {
        return BindingBuilder.bind(stockQueue()).to(pedidosExchange()).with(p.routingKeys().stock());
    }

    @Bean
    public Binding stockDlqBinding() {
        return BindingBuilder.bind(stockDlq()).to(dlxExchange()).with(p.dlqs().stock());
    }

    // ------------------------------------------ Caso de uso 2: ticket (direct)

    @Bean
    public Queue ticketQueue() {
        return conDlq(p.queues().ticket(), p.dlqs().ticket());
    }

    @Bean
    public Queue ticketDlq() {
        return QueueBuilder.durable(p.dlqs().ticket()).build();
    }

    @Bean
    public Binding ticketBinding() {
        return BindingBuilder.bind(ticketQueue()).to(pedidosExchange()).with(p.routingKeys().ticket());
    }

    @Bean
    public Binding ticketDlqBinding() {
        return BindingBuilder.bind(ticketDlq()).to(dlxExchange()).with(p.dlqs().ticket());
    }

    // ------------------------ Caso de uso 3: notificaciones (topic, 2 bindings)

    @Bean
    public Queue notificacionesQueue() {
        return conDlq(p.queues().notificaciones(), p.dlqs().notificaciones());
    }

    @Bean
    public Queue notificacionesDlq() {
        return QueueBuilder.durable(p.dlqs().notificaciones()).build();
    }

    @Bean
    public Binding notifPedidosBinding() {
        return BindingBuilder.bind(notificacionesQueue()).to(eventosExchange()).with(p.routingKeys().pedidoTodos());
    }

    @Bean
    public Binding notifProductosBinding() {
        return BindingBuilder.bind(notificacionesQueue()).to(eventosExchange()).with(p.routingKeys().productoTodos());
    }

    @Bean
    public Binding notifDlqBinding() {
        return BindingBuilder.bind(notificacionesDlq()).to(dlxExchange()).with(p.dlqs().notificaciones());
    }

    /** Cola durable cuyos mensajes rechazados van al DLX con la clave de su DLQ. */
    private Queue conDlq(String nombre, String dlqNombre) {
        return QueueBuilder.durable(nombre)
                .deadLetterExchange(p.exchanges().dlx())
                .deadLetterRoutingKey(dlqNombre)
                .build();
    }
}
