package cl.duoc.pedidos360.mensajeria.admin.service;

import cl.duoc.pedidos360.mensajeria.admin.dto.BindingRequest;
import cl.duoc.pedidos360.mensajeria.admin.dto.ExchangeRequest;
import cl.duoc.pedidos360.mensajeria.admin.dto.QueueRequest;
import cl.duoc.pedidos360.mensajeria.admin.error.OperacionNoPermitidaException;
import cl.duoc.pedidos360.mensajeria.admin.error.RecursoNoEncontradoException;
import cl.duoc.pedidos360.mensajeria.config.RabbitProperties;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Toda la lógica de administración de RabbitMQ vive aquí: el controlador solo llama a estos
 * métodos y no conoce la librería de RabbitMQ. Las colas y exchanges de la topología base
 * (los configurados en application.yml) están protegidos y no se pueden eliminar.
 */
@Service
public class RabbitAdminService {

    private final AmqpAdmin admin;
    private final Set<String> protegidos;

    public RabbitAdminService(AmqpAdmin admin, RabbitProperties p) {
        this.admin = admin;
        this.protegidos = Set.copyOf(List.of(
                p.exchanges().pedidos(), p.exchanges().eventos(), p.exchanges().dlx(),
                p.queues().stock(), p.queues().ticket(), p.queues().notificaciones(),
                p.dlqs().stock(), p.dlqs().ticket(), p.dlqs().notificaciones()));
    }

    // -------------------------------------------------------------------- Colas

    public void crearCola(QueueRequest r) {
        Map<String, Object> args = new HashMap<>();
        if (StringUtils.hasText(r.deadLetterExchange())) {
            args.put("x-dead-letter-exchange", r.deadLetterExchange());
            if (StringUtils.hasText(r.deadLetterRoutingKey())) {
                args.put("x-dead-letter-routing-key", r.deadLetterRoutingKey());
            }
        }
        boolean durable = r.durable() == null || r.durable();
        boolean autoDelete = Boolean.TRUE.equals(r.autoDelete());
        admin.declareQueue(new Queue(r.name(), durable, false, autoDelete, args));
    }

    /** Incluye QUEUE_NAME, QUEUE_MESSAGE_COUNT y QUEUE_CONSUMER_COUNT. */
    public Properties detalleCola(String nombre) {
        Properties props = admin.getQueueProperties(nombre);
        if (props == null) {
            throw new RecursoNoEncontradoException("La cola no existe: " + nombre);
        }
        return props;
    }

    public void eliminarCola(String nombre) {
        detalleCola(nombre); // lanza 404 si no existe
        rechazarSiEsBase(nombre, "la cola");
        admin.deleteQueue(nombre);
    }

    // --------------------------------------------------------------- Exchanges

    public void crearExchange(ExchangeRequest r) {
        boolean durable = r.durable() == null || r.durable();
        Exchange exchange = switch (r.type()) {
            case DIRECT -> new DirectExchange(r.name(), durable, false);
            case TOPIC -> new TopicExchange(r.name(), durable, false);
            case FANOUT -> new FanoutExchange(r.name(), durable, false);
        };
        admin.declareExchange(exchange);
    }

    /** Borrar un exchange es idempotente en RabbitMQ: si no existe, no falla. */
    public void eliminarExchange(String nombre) {
        rechazarSiEsBase(nombre, "el exchange");
        admin.deleteExchange(nombre);
    }

    // ---------------------------------------------------------------- Bindings

    public void crearBinding(BindingRequest r) {
        admin.declareBinding(aBinding(r));
    }

    public void eliminarBinding(BindingRequest r) {
        if (protegidos.contains(r.queue()) && protegidos.contains(r.exchange())) {
            throw new OperacionNoPermitidaException(
                    "No se puede eliminar un binding de la topología base: " + r.queue() + " <- " + r.exchange());
        }
        admin.removeBinding(aBinding(r));
    }

    // ------------------------------------------------------------------ Helpers

    private Binding aBinding(BindingRequest r) {
        String clave = r.routingKey() == null ? "" : r.routingKey();
        return new Binding(r.queue(), Binding.DestinationType.QUEUE, r.exchange(), clave, null);
    }

    private void rechazarSiEsBase(String nombre, String tipo) {
        if (protegidos.contains(nombre)) {
            throw new OperacionNoPermitidaException(
                    "No se puede eliminar " + tipo + " '" + nombre + "': pertenece a la topología base");
        }
    }
}
