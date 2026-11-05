package cl.duoc.pedidos360.mensajeria.consumer.ticket;

import cl.duoc.pedidos360.mensajeria.consumer.support.ConfirmacionManual;
import cl.duoc.pedidos360.mensajeria.consumer.support.MensajeNoProcesableException;
import cl.duoc.pedidos360.mensajeria.event.PedidoCreadoEvent;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * Dominio "ticket": genera el comprobante de cada pedido nuevo (cola q.ticket, que recibe
 * la routing key pedido.ticket del exchange direct de pedidos).
 *
 * <p>ACK manual (ver {@link ConfirmacionManual}):
 * <ul>
 *   <li>Ticket generado: basicAck.</li>
 *   <li>JSON ilegible o pedido sin producto/cantidad: error de negocio, va directo a
 *       q.ticket.dlq.</li>
 *   <li>Cualquier otro error: técnico, se reintenta hasta 3 veces y luego va a la DLQ.</li>
 * </ul>
 *
 * Se recibe el mensaje crudo y se convierte aquí: si Spring hiciera la conversión y fallara,
 * con ACK manual el mensaje quedaría sin confirmar ni rechazar (bloqueado en la cola).
 */
@Component
public class TicketConsumer {

    private static final Logger log = LoggerFactory.getLogger(TicketConsumer.class);

    private final ConfirmacionManual confirmacion;
    private final JsonMapper jsonMapper;

    public TicketConsumer(ConfirmacionManual confirmacion, JsonMapper jsonMapper) {
        this.confirmacion = confirmacion;
        this.jsonMapper = jsonMapper;
    }

    @RabbitListener(id = "ticket-consumer", queues = "${tecnomarket.rabbit.queues.ticket}", ackMode = "MANUAL")
    public void onTicket(Message mensaje, Channel channel) throws IOException {
        try {
            PedidoCreadoEvent evento = leer(mensaje);
            generarTicket(evento);
            confirmacion.confirmar(mensaje, channel);
        } catch (MensajeNoProcesableException e) {
            confirmacion.enviarADlq(mensaje, channel, e.getMessage());
        } catch (RuntimeException e) {
            confirmacion.reintentarOEnviarADlq(mensaje, channel, e);
        }
    }

    private PedidoCreadoEvent leer(Message mensaje) {
        PedidoCreadoEvent e;
        try {
            e = jsonMapper.readValue(mensaje.getBody(), PedidoCreadoEvent.class);
        } catch (JacksonException ex) {
            throw new MensajeNoProcesableException("el cuerpo no es un PedidoCreadoEvent en JSON");
        }
        if (e == null || e.pedidoId() == null || e.cantidad() == null || e.cantidad() < 1
                || e.producto() == null || e.producto().isBlank()) {
            throw new MensajeNoProcesableException("pedido inválido: " + e);
        }
        return e;
    }

    private void generarTicket(PedidoCreadoEvent e) {
        log.info("Ticket generado: pedido #{} - {} x {} (cliente {})",
                e.pedidoId(), e.cantidad(), e.producto(), e.clienteSub());
    }
}
