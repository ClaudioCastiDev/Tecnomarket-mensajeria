package cl.duoc.pedidos360.mensajeria.admin.error;

/** La operación se rechaza por regla de negocio (por ejemplo, borrar la topología base): 409. */
public class OperacionNoPermitidaException extends RuntimeException {
    public OperacionNoPermitidaException(String mensaje) {
        super(mensaje);
    }
}
