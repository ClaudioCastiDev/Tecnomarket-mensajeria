package cl.duoc.pedidos360.mensajeria.consumer;

import cl.duoc.pedidos360.mensajeria.RabbitTestProps;
import cl.duoc.pedidos360.mensajeria.consumer.notificaciones.NotificacionConsumer;
import cl.duoc.pedidos360.mensajeria.consumer.support.ConfirmacionManual;
import cl.duoc.pedidos360.mensajeria.consumer.ticket.TicketConsumer;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Caminos del ACK manual de los consumidores de ticket y notificaciones, sin RabbitMQ real:
 * el canal y el RabbitTemplate son mocks, así se verifica exactamente qué operación se envía.
 */
class ConsumidoresTest {

    private static final long TAG = 7L;
    private static final String PEDIDO_JSON =
            "{\"pedidoId\":1,\"producto\":\"Mouse\",\"cantidad\":2,\"clienteSub\":\"u\",\"fecha\":\"2026-11-05T10:00:00\"}";

    private final Channel channel = mock(Channel.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final ConfirmacionManual confirmacion = new ConfirmacionManual(rabbitTemplate, RabbitTestProps.rabbit());

    private final TicketConsumer ticket = new TicketConsumer(confirmacion, JsonMapper.builder().build());
    private final NotificacionConsumer notificacion = new NotificacionConsumer(confirmacion);

    private static Message mensaje(String cuerpo, String cola, Integer reintentosPrevios) {
        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(TAG);
        props.setConsumerQueue(cola);
        props.setReceivedRoutingKey("pedido.creado");
        if (reintentosPrevios != null) {
            props.setHeader(ConfirmacionManual.HEADER_REINTENTOS, reintentosPrevios);
        }
        return new Message(cuerpo.getBytes(StandardCharsets.UTF_8), props);
    }

    // ------------------------------------------------------------------ ticket

    @Test
    void ticket_pedidoValido_confirmaConAck() throws Exception {
        ticket.onTicket(mensaje(PEDIDO_JSON, "q.ticket", null), channel);

        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void ticket_jsonIlegible_vaALaDlq() throws Exception {
        ticket.onTicket(mensaje("no es json", "q.ticket", null), channel);

        verify(channel).basicNack(TAG, false, false);
    }

    @Test
    void ticket_cantidadInvalida_vaALaDlq() throws Exception {
        ticket.onTicket(mensaje("{\"pedidoId\":1,\"producto\":\"Mouse\",\"cantidad\":0}", "q.ticket", null), channel);

        verify(channel).basicNack(TAG, false, false);
        verify(rabbitTemplate, never()).send(any(String.class), any(String.class), any(Message.class));
    }

    // ------------------------------------------------------- notificaciones

    @Test
    void notificacion_conContenido_confirmaConAck() throws Exception {
        notificacion.onEvento(mensaje(PEDIDO_JSON, "q.notificaciones", null), channel);

        verify(channel).basicAck(TAG, false);
    }

    @Test
    void notificacion_vacia_vaALaDlq() throws Exception {
        notificacion.onEvento(mensaje("", "q.notificaciones", null), channel);

        verify(channel).basicNack(TAG, false, false);
    }

    // ------------------------------------------------- reintentos (helper)

    @Test
    void errorTecnico_reenviaALaMismaColaConContadorYConfirmaElOriginal() throws Exception {
        Message original = mensaje(PEDIDO_JSON, "q.ticket", null);

        confirmacion.reintentarOEnviarADlq(original, channel, new IllegalStateException("caída"));

        verify(rabbitTemplate).send(eq(""), eq("q.ticket"), any(Message.class));
        assertThat(original.getMessageProperties().<Integer>getHeader(ConfirmacionManual.HEADER_REINTENTOS))
                .isEqualTo(1);
        verify(channel).basicAck(TAG, false);
    }

    @Test
    void errorTecnico_conReintentosAgotados_vaALaDlq() throws Exception {
        confirmacion.reintentarOEnviarADlq(mensaje(PEDIDO_JSON, "q.ticket", 3), channel,
                new IllegalStateException("caída"));

        verify(channel).basicNack(TAG, false, false);
        verify(rabbitTemplate, never()).send(any(String.class), any(String.class), any(Message.class));
    }

    @Test
    void siNoSePuedeReprogramar_elOriginalVuelveALaCola() throws Exception {
        doThrow(new AmqpConnectException(new ConnectException("sin broker")))
                .when(rabbitTemplate).send(any(String.class), any(String.class), any(Message.class));

        confirmacion.reintentarOEnviarADlq(mensaje(PEDIDO_JSON, "q.ticket", null), channel,
                new IllegalStateException("caída"));

        verify(channel).basicNack(TAG, false, true);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }
}
