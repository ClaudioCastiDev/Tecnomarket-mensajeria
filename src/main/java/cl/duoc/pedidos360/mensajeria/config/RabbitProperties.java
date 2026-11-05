package cl.duoc.pedidos360.mensajeria.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nombres de exchanges, colas, DLQs y claves de ruteo, leídos desde application.yml
 * (prefijo "tecnomarket.rabbit"). Ningún otro punto del código escribe estos nombres.
 */
@ConfigurationProperties(prefix = "tecnomarket.rabbit")
public record RabbitProperties(Exchanges exchanges, Names queues, Names dlqs, RoutingKeys routingKeys,
                               Reintentos reintentos) {

    public record Exchanges(String pedidos, String eventos, String dlx) {
    }

    /** Un nombre por caso de uso: stock, ticket y notificaciones. */
    public record Names(String stock, String ticket, String notificaciones) {
    }

    public record RoutingKeys(String stock, String ticket, String pedidoCreado,
                              String pedidoTodos, String productoTodos, String productoEliminado) {
    }

    /**
     * Errores técnicos: cuántas veces se reintenta un mensaje antes de enviarlo a la DLQ
     * y cuánto se espera antes de cada intento (esperaMs x número de intento).
     */
    public record Reintentos(int maximo, long esperaMs) {
    }
}
