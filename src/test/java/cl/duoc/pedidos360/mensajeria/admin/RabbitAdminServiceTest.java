package cl.duoc.pedidos360.mensajeria.admin;

import cl.duoc.pedidos360.mensajeria.RabbitTestProps;
import cl.duoc.pedidos360.mensajeria.admin.dto.BindingRequest;
import cl.duoc.pedidos360.mensajeria.admin.dto.ExchangeRequest;
import cl.duoc.pedidos360.mensajeria.admin.dto.QueueRequest;
import cl.duoc.pedidos360.mensajeria.admin.dto.TipoExchange;
import cl.duoc.pedidos360.mensajeria.admin.error.OperacionNoPermitidaException;
import cl.duoc.pedidos360.mensajeria.admin.error.RecursoNoEncontradoException;
import cl.duoc.pedidos360.mensajeria.admin.service.RabbitAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RabbitAdminServiceTest {

    private AmqpAdmin admin;
    private RabbitAdminService service;

    @BeforeEach
    void setUp() {
        admin = mock(AmqpAdmin.class);
        service = new RabbitAdminService(admin, RabbitTestProps.rabbit());
    }

    @Test
    void crearCola_porDefectoEsDurableSinAutoDelete() {
        service.crearCola(new QueueRequest("q.prueba", null, null, null, null));

        ArgumentCaptor<Queue> captor = ArgumentCaptor.forClass(Queue.class);
        verify(admin).declareQueue(captor.capture());
        Queue cola = captor.getValue();
        assertEquals("q.prueba", cola.getName());
        assertTrue(cola.isDurable());
        assertFalse(cola.isAutoDelete());
        assertTrue(cola.getArguments().isEmpty());
    }

    @Test
    void crearCola_conDlqAgregaLosArgumentos() {
        service.crearCola(new QueueRequest("q.prueba", true, false, "tecnomarket.dlx", "q.prueba.dlq"));

        ArgumentCaptor<Queue> captor = ArgumentCaptor.forClass(Queue.class);
        verify(admin).declareQueue(captor.capture());
        assertEquals("tecnomarket.dlx", captor.getValue().getArguments().get("x-dead-letter-exchange"));
        assertEquals("q.prueba.dlq", captor.getValue().getArguments().get("x-dead-letter-routing-key"));
    }

    @Test
    void detalleCola_inexistenteLanza404() {
        when(admin.getQueueProperties("nada")).thenReturn(null);
        assertThrows(RecursoNoEncontradoException.class, () -> service.detalleCola("nada"));
    }

    @Test
    void eliminarCola_existenteLaBorra() {
        when(admin.getQueueProperties("q.prueba")).thenReturn(new Properties());
        service.eliminarCola("q.prueba");
        verify(admin).deleteQueue("q.prueba");
    }

    @Test
    void eliminarCola_inexistenteNoBorraNada() {
        when(admin.getQueueProperties("nada")).thenReturn(null);
        assertThrows(RecursoNoEncontradoException.class, () -> service.eliminarCola("nada"));
        verify(admin, never()).deleteQueue(anyString());
    }

    @Test
    void eliminarCola_deLaTopologiaBaseSeRechaza() {
        when(admin.getQueueProperties("q.stock")).thenReturn(new Properties());
        assertThrows(OperacionNoPermitidaException.class, () -> service.eliminarCola("q.stock"));
        verify(admin, never()).deleteQueue(anyString());
    }

    @Test
    void crearExchange_topicCreaUnTopicExchange() {
        service.crearExchange(new ExchangeRequest("ex.prueba", TipoExchange.TOPIC, null));

        ArgumentCaptor<Exchange> captor = ArgumentCaptor.forClass(Exchange.class);
        verify(admin).declareExchange(captor.capture());
        assertEquals("topic", captor.getValue().getType());
        assertEquals("ex.prueba", captor.getValue().getName());
    }

    @Test
    void eliminarExchange_deLaTopologiaBaseSeRechaza() {
        assertThrows(OperacionNoPermitidaException.class,
                () -> service.eliminarExchange("tecnomarket.pedidos.direct"));
        verify(admin, never()).deleteExchange(anyString());
    }

    @Test
    void crearBinding_sinRoutingKeyUsaCadenaVacia() {
        service.crearBinding(new BindingRequest("q.prueba", "ex.prueba", null));

        ArgumentCaptor<Binding> captor = ArgumentCaptor.forClass(Binding.class);
        verify(admin).declareBinding(captor.capture());
        assertEquals("", captor.getValue().getRoutingKey());
        assertEquals("q.prueba", captor.getValue().getDestination());
    }

    @Test
    void eliminarBinding_deLaTopologiaBaseSeRechaza() {
        assertThrows(OperacionNoPermitidaException.class, () -> service.eliminarBinding(
                new BindingRequest("q.stock", "tecnomarket.pedidos.direct", "pedido.stock")));
        verify(admin, never()).removeBinding(any(Binding.class));
    }

    @Test
    void eliminarBinding_propioSeElimina() {
        service.eliminarBinding(new BindingRequest("q.prueba", "ex.prueba", "k"));
        verify(admin).removeBinding(any(Binding.class));
    }
}
