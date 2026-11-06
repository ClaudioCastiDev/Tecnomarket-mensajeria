# Tecnomarket – Mensajería (RabbitMQ)

Microservicio de mensajería de Tecnomarket (Evaluación Parcial N° 3, DSY1107 Desarrollo Cloud Native I).
Reúne tres cosas:

1. **Clúster RabbitMQ de 2 nodos** con `docker-compose`.
2. **Administrador REST de RabbitMQ**: crea y elimina colas, exchanges y bindings (solo grupo `admin` de Cognito).
3. **Consumidores** de los dominios *ticket* y *notificaciones*.

Los otros repos: `Tecnomarket-backend` (pedidos, productor), `Tecnomarket-productos` (consumidor de stock) y `Tecnomarket-frontend`.

## Arquitectura de mensajería

```
                       ┌─ tecnomarket.pedidos.direct ─┬─► q.stock ───────► productos
pedidos ──pedido.*─────┤                              └─► q.ticket ──────► mensajeria
                       └─ tecnomarket.eventos.topic ───► q.notificaciones ► mensajeria

Cada cola tiene DLQ vía tecnomarket.dlx:  q.stock.dlq · q.ticket.dlq · q.notificaciones.dlq
```

| Exchange | Tipo | Cola | Clave de ruteo |
|---|---|---|---|
| `tecnomarket.pedidos.direct` | direct | `q.stock` | `pedido.stock` |
| `tecnomarket.pedidos.direct` | direct | `q.ticket` | `pedido.ticket` |
| `tecnomarket.eventos.topic` | topic | `q.notificaciones` | `pedido.#` y `producto.#` |
| `tecnomarket.dlx` | direct | `*.dlq` | nombre de la propia DLQ |

Todos los nombres están centralizados en `application.yml` (`tecnomarket.rabbit.*`) y se leen con `RabbitProperties`.
La topología se declara en `RabbitTopologyConfig` (beans `Queue`, `Exchange` y `Binding`).

### Manejo de errores en los consumidores (ACK manual)

Los dos consumidores usan `ConfirmacionManual`, así todos se comportan igual:

| Situación | Tipo | Acción |
|---|---|---|
| Procesado correctamente | — | `basicAck` |
| JSON ilegible o datos inválidos | Negocio (no recuperable) | `basicNack(requeue=false)` → DLQ |
| Cualquier otro error | Técnico (recuperable) | Hasta 3 reintentos (1 s, 2 s, 3 s) → luego DLQ |

El reintento publica una copia en la misma cola con el encabezado `x-reintentos`
incrementado y confirma el original. No se usa `basicNack(requeue=true)`, porque
devuelve el mensaje de inmediato y sin contar intentos: si el error persiste se forma
un bucle infinito.

Los consumidores reciben el mensaje crudo y convierten el JSON ellos mismos. Si Spring
hiciera la conversión y fallara, con ACK manual el mensaje quedaría sin confirmar ni
rechazar, bloqueado en la cola.

Los rechazos se registran en el log y el mensaje queda en la DLQ para poder revisarlo.

## Levantar RabbitMQ (clúster de 2 nodos)

```bash
cp .env.example .env        # editar usuario, clave y cookie
docker compose up -d
docker exec rabbit1 rabbitmqctl cluster_status   # deben aparecer rabbit@rabbit1 y rabbit@rabbit2
```

Consola web: http://localhost:15672 (nodo 1) y http://localhost:15673 (nodo 2).
Opcional, para replicar las colas en ambos nodos (RabbitMQ 3.13):

```bash
docker exec rabbit1 rabbitmqctl set_policy ha-all "^q[.]" '{"ha-mode":"all"}' --apply-to queues
```

## Ejecutar el microservicio

```bash
./mvnw spring-boot:run     # puerto 8083
./mvnw test
```

Variables de entorno (ver `application.yml`):

| Variable | Descripción | Por defecto |
|---|---|---|
| `RABBIT_ADDRESSES` | Nodos del clúster | `localhost:5672,localhost:5673` |
| `RABBIT_USER` / `RABBIT_PASS` | Credenciales de RabbitMQ | `guest` / `guest` |
| `COGNITO_ISSUER_URI` / `COGNITO_CLIENT_ID` | User pool y app client de Cognito | los del proyecto |

## API del administrador

Requiere un access token de Cognito de un usuario del grupo **admin**. Errores en JSON: `{"status","error","message"}`.

| Método | Ruta | Descripción | Respuestas |
|---|---|---|---|
| POST | `/api/queues` | Crea una cola | 201, 400, 401, 403 |
| GET | `/api/queues/{name}` | Mensajes y consumidores de la cola | 200, 404 |
| DELETE | `/api/queues/{name}` | Elimina una cola | 204, 404, 409 |
| POST | `/api/exchanges` | Crea un exchange (DIRECT, TOPIC o FANOUT) | 201, 400 |
| DELETE | `/api/exchanges/{name}` | Elimina un exchange | 204, 409 |
| POST | `/api/bindings` | Crea un binding cola ↔ exchange | 201, 400 |
| DELETE | `/api/bindings` | Elimina un binding | 204, 409 |

Las colas, exchanges y bindings de la topología base están protegidos: intentar eliminarlos responde **409**.

Ejemplos de cuerpo:

```json
POST /api/queues      {"name":"q.prueba","durable":true,"autoDelete":false,"deadLetterExchange":"tecnomarket.dlx","deadLetterRoutingKey":"q.stock.dlq"}
POST /api/exchanges   {"name":"ex.prueba","type":"TOPIC","durable":true}
POST /api/bindings    {"queue":"q.prueba","exchange":"ex.prueba","routingKey":"prueba.#"}
```

Validaciones: el nombre es obligatorio, de hasta 200 caracteres, solo letras, números, `.`, `-` y `_`, y no puede empezar con `amq.`.

## Probar el flujo de DLQ

1. En la consola de RabbitMQ: *Exchanges → tecnomarket.pedidos.direct → Publish message*.
2. Routing key `pedido.ticket`, propiedad `content_type` = `application/json` y payload `{"pedidoId":1,"producto":"Teclado mecánico","cantidad":0}`.
3. El ticket se rechaza (cantidad inválida) y `q.ticket.dlq` muestra 1 mensaje; el motivo queda en el log del servicio.

## Estructura

```
config/          RabbitProperties, RabbitTopologyConfig, seguridad Cognito
event/           PedidoCreadoEvent (contrato JSON compartido con pedidos)
consumer/        ticket/TicketConsumer, notificaciones/NotificacionConsumer,
                 support/ConfirmacionManual (ACK, DLQ y reintentos comunes)
admin/           controller, service (RabbitAdminService), dto, error
```

## Despliegue en la nube

1. En la máquina con Docker: copiar `docker-compose.yml`, `rabbitmq.conf` y `.env`, y ejecutar `docker compose up -d`.
2. Abrir los puertos 5672-5673 (AMQP) solo hacia los microservicios, y 15672-15673 (consola) solo hacia la IP del equipo.
3. Desplegar el `.jar` con `RABBIT_ADDRESSES`, `RABBIT_USER` y `RABBIT_PASS` como variables de entorno.
4. En API Gateway, registrar `/api/queues/{proxy+}`, `/api/exchanges/{proxy+}` y `/api/bindings` hacia este servicio con el mismo authorizer de Cognito.
