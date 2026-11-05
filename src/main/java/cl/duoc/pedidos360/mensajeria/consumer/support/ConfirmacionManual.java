package cl.duoc.pedidos360.mensajeria.consumer.support;

import cl.duoc.pedidos360.mensajeria.config.RabbitProperties;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Las tres salidas posibles de un mensaje con ACK manual, en un solo lugar para que
 * todos los consumidores de este servicio se comporten igual:
 *
 * <ul>
 *   <li>{@link #confirmar}: éxito, {@code basicAck}. El mensaje sale de la cola.</li>
 *   <li>{@link #enviarADlq}: error de negocio (no recuperable),
 *       {@code basicNack(requeue=false)}. RabbitMQ lo deriva al DLX y de ahí a la DLQ.</li>
 *   <li>{@link #reintentarOEnviarADlq}: error técnico (recuperable). Se publica una copia
 *       al final de la misma cola con el encabezado {@value #HEADER_REINTENTOS}
 *       incrementado y se confirma el original. Agotados los reintentos, va a la DLQ.</li>
 * </ul>
 *
 * Por qué no se reintenta con {@code basicNack(requeue=true)}: el mensaje vuelve de inmediato
 * al inicio de la cola sin contar intentos, y si el error persiste se forma un bucle infinito.
 */
@Component
public class ConfirmacionManual {

    public static final String HEADER_REINTENTOS = "x-reintentos";

    private static final Logger log = LoggerFactory.getLogger(ConfirmacionManual.class);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitProperties.Reintentos reintentos;

    public ConfirmacionManual(RabbitTemplate rabbitTemplate, RabbitProperties props) {
        this.rabbitTemplate = rabbitTemplate;
        this.reintentos = props.reintentos();
    }

    public void confirmar(Message mensaje, Channel channel) throws IOException {
        channel.basicAck(tag(mensaje), false);
    }

    public void enviarADlq(Message mensaje, Channel channel, String motivo) throws IOException {
        channel.basicNack(tag(mensaje), false, false);
        log.warn("Mensaje enviado a la DLQ desde {}: {} messageId={}", cola(mensaje), motivo,
                mensaje.getMessageProperties().getMessageId());
    }

    public void reintentarOEnviarADlq(Message mensaje, Channel channel, Exception causa) throws IOException {
        MessageProperties props = mensaje.getMessageProperties();
        int intento = reintentosPrevios(props) + 1;

        if (intento > reintentos.maximo()) {
            enviarADlq(mensaje, channel, "reintentos agotados (" + reintentos.maximo() + "): " + causa.getMessage());
            return;
        }

        log.warn("Error técnico en {}, reintento {}/{}: {}", cola(mensaje), intento, reintentos.maximo(),
                causa.getMessage());
        esperar(intento);
        try {
            props.setHeader(HEADER_REINTENTOS, intento);
            // Exchange por defecto ("") + nombre de la cola = publicar directo en esa cola.
            rabbitTemplate.send("", cola(mensaje), mensaje);
            channel.basicAck(tag(mensaje), false);
        } catch (RuntimeException errorAlReenviar) {
            // Si no se puede reprogramar, el original vuelve a la cola para no perderlo.
            channel.basicNack(tag(mensaje), false, true);
            log.error("No se pudo reprogramar el reintento, el mensaje vuelve a la cola: {}",
                    errorAlReenviar.getMessage());
        }
    }

    private static long tag(Message mensaje) {
        return mensaje.getMessageProperties().getDeliveryTag();
    }

    private static String cola(Message mensaje) {
        return mensaje.getMessageProperties().getConsumerQueue();
    }

    private static int reintentosPrevios(MessageProperties props) {
        Object valor = props.getHeaders().get(HEADER_REINTENTOS);
        return valor instanceof Number numero ? numero.intValue() : 0;
    }

    private void esperar(int intento) {
        long ms = reintentos.esperaMs() * intento;
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
