package cl.duoc.pedidos360.mensajeria.event;

import java.time.LocalDateTime;

/**
 * Evento que publica el microservicio de pedidos al crear un pedido. Es una copia local:
 * los microservicios no comparten código, solo el contrato JSON.
 */
public record PedidoCreadoEvent(Long pedidoId, String producto, Integer cantidad,
                                String clienteSub, LocalDateTime fecha) {
}
