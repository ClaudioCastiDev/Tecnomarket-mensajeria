package cl.duoc.pedidos360.mensajeria.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Datos para crear un exchange. "durable" es opcional (por defecto true). */
public record ExchangeRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 200, message = "El nombre admite hasta 200 caracteres")
        @Pattern(regexp = "^(?!amq[.])[A-Za-z0-9._-]+$",
                message = "Solo letras, números, punto, guion y guion bajo; no puede empezar con 'amq.'")
        String name,
        @NotNull(message = "El tipo es obligatorio (DIRECT, TOPIC o FANOUT)")
        TipoExchange type,
        Boolean durable) {
}
