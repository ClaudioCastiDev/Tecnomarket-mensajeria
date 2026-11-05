package cl.duoc.pedidos360.mensajeria.admin.controller;

import cl.duoc.pedidos360.mensajeria.admin.dto.BindingRequest;
import cl.duoc.pedidos360.mensajeria.admin.dto.ExchangeRequest;
import cl.duoc.pedidos360.mensajeria.admin.dto.QueueRequest;
import cl.duoc.pedidos360.mensajeria.admin.service.RabbitAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Properties;

/**
 * API REST del administrador de RabbitMQ (solo grupo admin).
 *
 *  POST   /api/queues          crea una cola                 201
 *  GET    /api/queues/{name}   detalle (mensajes/consumidores) 200 | 404
 *  DELETE /api/queues/{name}   elimina una cola              204 | 404 | 409
 *  POST   /api/exchanges       crea un exchange              201
 *  DELETE /api/exchanges/{name} elimina un exchange          204 | 409
 *  POST   /api/bindings        crea un binding               201
 *  DELETE /api/bindings        elimina un binding            204 | 409
 */
@RestController
@RequestMapping("/api")
public class RabbitAdminController {

    private final RabbitAdminService service;

    public RabbitAdminController(RabbitAdminService service) {
        this.service = service;
    }

    @PostMapping("/queues")
    public ResponseEntity<Void> crearCola(@Valid @RequestBody QueueRequest request) {
        service.crearCola(request);
        return ResponseEntity.created(URI.create("/api/queues/" + request.name())).build();
    }

    @GetMapping("/queues/{name}")
    public Properties detalleCola(@PathVariable String name) {
        return service.detalleCola(name);
    }

    @DeleteMapping("/queues/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarCola(@PathVariable String name) {
        service.eliminarCola(name);
    }

    @PostMapping("/exchanges")
    public ResponseEntity<Void> crearExchange(@Valid @RequestBody ExchangeRequest request) {
        service.crearExchange(request);
        return ResponseEntity.created(URI.create("/api/exchanges/" + request.name())).build();
    }

    @DeleteMapping("/exchanges/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarExchange(@PathVariable String name) {
        service.eliminarExchange(name);
    }

    @PostMapping("/bindings")
    @ResponseStatus(HttpStatus.CREATED)
    public void crearBinding(@Valid @RequestBody BindingRequest request) {
        service.crearBinding(request);
    }

    @DeleteMapping("/bindings")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarBinding(@Valid @RequestBody BindingRequest request) {
        service.eliminarBinding(request);
    }
}
