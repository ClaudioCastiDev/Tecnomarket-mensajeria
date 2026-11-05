package cl.duoc.pedidos360.mensajeria.admin;

import cl.duoc.pedidos360.mensajeria.admin.error.OperacionNoPermitidaException;
import cl.duoc.pedidos360.mensajeria.admin.error.RecursoNoEncontradoException;
import cl.duoc.pedidos360.mensajeria.admin.service.RabbitAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Properties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Matriz 201 / 204 / 400 / 401 / 403 / 404 / 409 de la API de administración.
 * jwt() simula un token ya validado (se prueba la AUTORIZACIÓN); el servicio va con mock para
 * no necesitar un RabbitMQ real.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "management.health.rabbit.enabled=false"})
class RabbitAdminControllerTest {

    private static final GrantedAuthority ADMIN = new SimpleGrantedAuthority("ROLE_ADMIN");
    private static final GrantedAuthority CLIENTE = new SimpleGrantedAuthority("ROLE_CLIENTE");
    private static final String COLA_VALIDA = "{\"name\":\"q.prueba\",\"durable\":true,\"autoDelete\":false}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RabbitAdminService service;

    @Test
    void sinToken_devuelve401() throws Exception {
        mvc.perform(post("/api/queues").contentType(MediaType.APPLICATION_JSON).content(COLA_VALIDA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(service);
    }

    @Test
    void tokenSinRolAdmin_devuelve403() throws Exception {
        mvc.perform(post("/api/queues").with(jwt().authorities(CLIENTE))
                .contentType(MediaType.APPLICATION_JSON).content(COLA_VALIDA))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void admin_creaCola201ConLocation() throws Exception {
        mvc.perform(post("/api/queues").with(jwt().authorities(ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content(COLA_VALIDA))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/queues/q.prueba"));
        verify(service).crearCola(any());
    }

    @Test
    void nombreVacio_devuelve400ConElCampo() throws Exception {
        mvc.perform(post("/api/queues").with(jwt().authorities(ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Datos inválidos"))
                .andExpect(jsonPath("$.campos.name").exists());
        verifyNoInteractions(service);
    }

    @Test
    void nombreReservadoAmq_devuelve400() throws Exception {
        mvc.perform(post("/api/queues").with(jwt().authorities(ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"amq.prueba\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.name").exists());
    }

    @Test
    void nombreConCaracteresInvalidos_devuelve400() throws Exception {
        mvc.perform(post("/api/queues").with(jwt().authorities(ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"cola con espacios\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exchangeSinTipo_devuelve400() throws Exception {
        mvc.perform(post("/api/exchanges").with(jwt().authorities(ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"ex.prueba\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.type").exists());
    }

    @Test
    void exchangeConTipoInvalido_devuelve400() throws Exception {
        mvc.perform(post("/api/exchanges").with(jwt().authorities(ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"ex.prueba\",\"type\":\"HEADERS\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exchangeValido_devuelve201() throws Exception {
        mvc.perform(post("/api/exchanges").with(jwt().authorities(ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"ex.prueba\",\"type\":\"TOPIC\"}"))
                .andExpect(status().isCreated());
        verify(service).crearExchange(any());
    }

    @Test
    void bindingSinColaNiExchange_devuelve400() throws Exception {
        mvc.perform(post("/api/bindings").with(jwt().authorities(ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("{\"routingKey\":\"k\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.queue").exists())
                .andExpect(jsonPath("$.campos.exchange").exists());
    }

    @Test
    void bindingValido_devuelve201() throws Exception {
        mvc.perform(post("/api/bindings").with(jwt().authorities(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"queue\":\"q.prueba\",\"exchange\":\"ex.prueba\",\"routingKey\":\"k\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void detalleDeColaExistente_devuelve200() throws Exception {
        Properties props = new Properties();
        props.put("QUEUE_NAME", "q.prueba");
        props.put("QUEUE_MESSAGE_COUNT", 3);
        props.put("QUEUE_CONSUMER_COUNT", 1);
        when(service.detalleCola("q.prueba")).thenReturn(props);

        mvc.perform(get("/api/queues/q.prueba").with(jwt().authorities(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.QUEUE_NAME").value("q.prueba"))
                .andExpect(jsonPath("$.QUEUE_MESSAGE_COUNT").value(3));
    }

    @Test
    void colaInexistente_devuelve404() throws Exception {
        when(service.detalleCola("nada")).thenThrow(new RecursoNoEncontradoException("La cola no existe: nada"));

        mvc.perform(get("/api/queues/nada").with(jwt().authorities(ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("La cola no existe: nada"));
    }

    @Test
    void eliminarCola_devuelve204() throws Exception {
        mvc.perform(delete("/api/queues/q.prueba").with(jwt().authorities(ADMIN)))
                .andExpect(status().isNoContent());
        verify(service).eliminarCola("q.prueba");
    }

    @Test
    void eliminarColaBase_devuelve409() throws Exception {
        doThrow(new OperacionNoPermitidaException("No se puede eliminar la cola 'q.stock'"))
                .when(service).eliminarCola("q.stock");

        mvc.perform(delete("/api/queues/q.stock").with(jwt().authorities(ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }
}
