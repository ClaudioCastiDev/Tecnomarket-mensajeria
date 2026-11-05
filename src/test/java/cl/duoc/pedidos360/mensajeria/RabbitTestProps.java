package cl.duoc.pedidos360.mensajeria;

import cl.duoc.pedidos360.mensajeria.config.RabbitProperties;

/** Propiedades de RabbitMQ de ejemplo para tests unitarios (sin levantar Spring). */
public final class RabbitTestProps {

    private RabbitTestProps() {
    }

    public static RabbitProperties rabbit() {
        return new RabbitProperties(
                new RabbitProperties.Exchanges("tecnomarket.pedidos.direct", "tecnomarket.eventos.topic",
                        "tecnomarket.dlx"),
                new RabbitProperties.Names("q.stock", "q.ticket", "q.notificaciones"),
                new RabbitProperties.Names("q.stock.dlq", "q.ticket.dlq", "q.notificaciones.dlq"),
                new RabbitProperties.RoutingKeys("pedido.stock", "pedido.ticket", "pedido.creado",
                        "pedido.#", "producto.#", "producto.eliminado"),
                new RabbitProperties.Reintentos(3, 0));
    }
}
