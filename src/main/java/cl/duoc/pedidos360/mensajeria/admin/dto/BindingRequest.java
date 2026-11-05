package cl.duoc.pedidos360.mensajeria.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Une una cola a un exchange con una clave de ruteo (puede ir vacía en un fanout). */
public record BindingRequest(
        @NotBlank(message = "La cola es obligatoria")
        @Size(max = 200, message = "El nombre de la cola admite hasta 200 caracteres")
        String queue,
        @NotBlank(message = "El exchange es obligatorio")
        @Size(max = 200, message = "El nombre del exchange admite hasta 200 caracteres")
        String exchange,
        @Size(max = 255, message = "La clave de ruteo admite hasta 255 caracteres")
        String routingKey) {
}
