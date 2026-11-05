package cl.duoc.pedidos360.mensajeria.consumer.notificaciones;

import cl.duoc.pedidos360.mensajeria.consumer.support.ConfirmacionManual;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Dominio "notificaciones": recibe todos los eventos del exchange topic (patrones pedido.#
 * y producto.#) y los notifica. Se recibe el mensaje crudo porque llegan eventos de
 * distintos tipos; la routing key indica cuál es.
 *
 * <p>ACK manual (ver {@link ConfirmacionManual}): éxito, basicAck; mensaje vacío, DLQ;
 * cualquier otro error, hasta 3 reintentos y luego q.notificaciones.dlq.
 */
@Component
public class NotificacionConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificacionConsumer.class);

    private final ConfirmacionManual confirmacion;

    public NotificacionConsumer(ConfirmacionManual confirmacion) {
        this.confirmacion = confirmacion;
    }

    @RabbitListener(id = "notificacion-consumer", queues = "${tecnomarket.rabbit.queues.notificaciones}",
            ackMode = "MANUAL")
    public void onEvento(Message mensaje, Channel channel) throws IOException {
        String clave = mensaje.getMessageProperties().getReceivedRoutingKey();
        try {
            if (mensaje.getBody() == null || mensaje.getBody().length == 0) {
                confirmacion.enviarADlq(mensaje, channel, "evento [" + clave + "] sin contenido");
                return;
            }
            String cuerpo = new String(mensaje.getBody(), StandardCharsets.UTF_8);
            log.info("Notificación [{}]: {}", clave, cuerpo);
            confirmacion.confirmar(mensaje, channel);
        } catch (RuntimeException e) {
            confirmacion.reintentarOEnviarADlq(mensaje, channel, e);
        }
    }
}
