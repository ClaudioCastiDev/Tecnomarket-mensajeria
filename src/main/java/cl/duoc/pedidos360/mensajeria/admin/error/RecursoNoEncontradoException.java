package cl.duoc.pedidos360.mensajeria.admin.error;

/** El recurso de RabbitMQ pedido (por ejemplo, una cola) no existe: se responde 404. */
public class RecursoNoEncontradoException extends RuntimeException {
    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
