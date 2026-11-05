package cl.duoc.pedidos360.mensajeria.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos para crear una cola. "durable" y "autoDelete" son opcionales (por defecto true y false).
 * Si se indica deadLetterExchange, los mensajes rechazados de la cola nueva van a ese exchange.
 */
public record QueueRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 200, message = "El nombre admite hasta 200 caracteres")
        @Pattern(regexp = "^(?!amq[.])[A-Za-z0-9._-]+$",
                message = "Solo letras, números, punto, guion y guion bajo; no puede empezar con 'amq.'")
        String name,
        Boolean durable,
        Boolean autoDelete,
        @Size(max = 200, message = "deadLetterExchange admite hasta 200 caracteres")
        String deadLetterExchange,
        @Size(max = 200, message = "deadLetterRoutingKey admite hasta 200 caracteres")
        String deadLetterRoutingKey) {
}
