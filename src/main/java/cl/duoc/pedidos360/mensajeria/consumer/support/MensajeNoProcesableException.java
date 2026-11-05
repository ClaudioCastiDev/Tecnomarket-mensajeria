package cl.duoc.pedidos360.mensajeria.consumer.support;

/**
 * Error que reintentar no arregla (mensaje ilegible o con datos inválidos).
 * El consumidor que la lanza envía el mensaje directo a la DLQ.
 */
public class MensajeNoProcesableException extends RuntimeException {

    public MensajeNoProcesableException(String mensaje) {
        super(mensaje);
    }
}
