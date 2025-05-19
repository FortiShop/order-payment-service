package org.fortishop.orderpaymentservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.fortishop.orderpaymentservice.domain.Order;
import org.fortishop.orderpaymentservice.domain.OrderStatus;
import org.fortishop.orderpaymentservice.domain.Payment;
import org.fortishop.orderpaymentservice.dto.event.InventoryFailedEvent;
import org.fortishop.orderpaymentservice.dto.request.OrderItemRequest;
import org.fortishop.orderpaymentservice.dto.request.OrderRequest;
import org.fortishop.orderpaymentservice.dto.request.PaymentRequest;
import org.fortishop.orderpaymentservice.dto.response.OrderResponse;
import org.fortishop.orderpaymentservice.dto.response.PaymentResponse;
import org.fortishop.orderpaymentservice.respository.OrderRepository;
import org.fortishop.orderpaymentservice.respository.PaymentRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.location=classpath:/application-test.yml",
                "spring.profiles.active=test"
        }
)
@Testcontainers
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class OrderPaymentServiceIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    PaymentRepository paymentRepository;

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("fortishop")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> zookeeper = new GenericContainer<>(DockerImageName.parse("bitnami/zookeeper:3.8.1"))
            .withExposedPorts(2181)
            .withEnv("ALLOW_ANONYMOUS_LOGIN", "yes")
            .withNetwork(Network.SHARED)
            .withNetworkAliases("zookeeper");

    @Container
    static GenericContainer<?> kafka = new GenericContainer<>(DockerImageName.parse("bitnami/kafka:3.6.0"))
            .withExposedPorts(9092, 9093)
            .withNetwork(Network.SHARED)
            .withNetworkAliases("kafka")
            .withCreateContainerCmdModifier(cmd -> cmd.withHostName("kafka").withHostConfig(
                    Objects.requireNonNull(cmd.getHostConfig())
                            .withPortBindings(
                                    new PortBinding(Ports.Binding.bindPort(9092), new ExposedPort(9092)),
                                    new PortBinding(Ports.Binding.bindPort(9093), new ExposedPort(9093))
                            )
            ))
            .withEnv("KAFKA_BROKER_ID", "1")
            .withEnv("ALLOW_PLAINTEXT_LISTENER", "yes")
            .withEnv("KAFKA_CFG_ZOOKEEPER_CONNECT", "zookeeper:2181")
            .withEnv("KAFKA_CFG_LISTENERS", "PLAINTEXT://0.0.0.0:9092,EXTERNAL://0.0.0.0:9093")
            .withEnv("KAFKA_CFG_ADVERTISED_LISTENERS", "PLAINTEXT://kafka:9092,EXTERNAL://localhost:9093")
            .withEnv("KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP", "PLAINTEXT:PLAINTEXT,EXTERNAL:PLAINTEXT")
            .withEnv("KAFKA_INTER_BROKER_LISTENER_NAME", "PLAINTEXT")
            .withEnv("KAFKA_CFG_AUTO_CREATE_TOPICS_ENABLE", "true")
            .waitingFor(Wait.forLogMessage(".*\\[KafkaServer id=\\d+] started.*\\n", 1));

    @Container
    static GenericContainer<?> kafkaUi = new GenericContainer<>(DockerImageName.parse("provectuslabs/kafka-ui:latest"))
            .withExposedPorts(8080)
            .withEnv("KAFKA_CLUSTERS_0_NAME", "fortishop-cluster")
            .withEnv("KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS", "PLAINTEXT://kafka:9092")
            .withEnv("KAFKA_CLUSTERS_0_ZOOKEEPER", "zookeeper:2181")
            .withNetwork(Network.SHARED)
            .withNetworkAliases("kafka-ui");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        mysql.start();
        zookeeper.start();
        kafka.start();
        kafkaUi.start();

        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> kafka.getHost() + ":" + kafka.getMappedPort(9093));
    }

    private static boolean topicCreated = false;

    @BeforeAll
    void initKafkaTopics() throws Exception {
        System.out.println("Kafka UI is available at: http://" + kafkaUi.getHost() + ":" + kafkaUi.getMappedPort(8080));
        String bootstrapServers = kafka.getHost() + ":" + kafka.getMappedPort(9093);
        List<String> topics = List.of("order.created", "payment.completed", "point.changed", "delivery.started",
                "payment.failed", "inventory.reserved");
        if (!topicCreated) {
            for (String topic : topics) {
                createTopicIfNotExists(topic, bootstrapServers);
            }
            topicCreated = true;
        }
    }

    private static void createTopicIfNotExists(String topic, String bootstrapServers) {
        Properties config = new Properties();
        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        try (AdminClient admin = AdminClient.create(config)) {
            Set<String> existingTopics = admin.listTopics().names().get(3, TimeUnit.SECONDS);
            if (!existingTopics.contains(topic)) {
                try {
                    admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1)))
                            .all().get(3, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof org.apache.kafka.common.errors.TopicExistsException) {
                        System.out.println("Topic already exists: " + topic);
                    } else {
                        throw e;
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to check or create topic: " + topic, e);
        }
    }

    @BeforeEach
    void cleanDb() {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
    }

    private String getBaseUrl(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    @DisplayName("주문 생성 API 호출 시 주문이 생성되고 orderId가 반환된다")
    void createOrder_success() {
        // given
        OrderRequest request = new OrderRequest(
                1L,
                List.of(new OrderItemRequest(10L, 2, BigDecimal.valueOf(10000))),
                BigDecimal.valueOf(20000)
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<OrderRequest> entity = new HttpEntity<>(request, headers);

        // when
        ResponseEntity<Long> res = restTemplate.exchange(
                getBaseUrl("/api/orders"),
                HttpMethod.POST,
                entity,
                Long.class
        );

        // then
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        Long orderId = res.getBody();
        assertThat(orderId).isNotNull();

        Optional<Order> saved = orderRepository.findById(orderId);
        assertThat(saved).isPresent();
        assertThat(saved.get().getMemberId()).isEqualTo(1L);
        assertThat(saved.get().getTotalPrice()).isEqualByComparingTo(BigDecimal.valueOf(20000));
    }

    @Test
    @DisplayName("주문 상세 조회 API는 주문 정보를 정확히 반환한다")
    void getOrder_success() {
        // given
        Order order = orderRepository.save(Order.builder()
                .memberId(2L)
                .totalPrice(BigDecimal.valueOf(15000))
                .status(OrderStatus.ORDERED)
                .createdAt(LocalDateTime.now())
                .build());

        // when
        ResponseEntity<OrderResponse> res = restTemplate.getForEntity(
                getBaseUrl("/api/orders/" + order.getId()), OrderResponse.class
        );

        // then
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getOrderId()).isEqualTo(order.getId());
        assertThat(res.getBody().getMemberId()).isEqualTo(2L);
        assertThat(res.getBody().getStatus()).isEqualTo("ORDERED");
    }

    @Test
    @DisplayName("주문 생성 시 Kafka order.created 이벤트가 발행된다")
    void orderCreated_kafkaPublished() throws Exception {
        // given
        OrderRequest request = new OrderRequest(
                3L,
                List.of(new OrderItemRequest(10L, 1, BigDecimal.valueOf(5000))),
                BigDecimal.valueOf(5000)
        );

        HttpEntity<OrderRequest> entity = new HttpEntity<>(request);

        ResponseEntity<Long> response = restTemplate.postForEntity(
                getBaseUrl("/api/orders"), entity, Long.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Long orderId = response.getBody();
        assertThat(orderId).isNotNull();

        // then: Kafka 토픽 존재 여부로 발행 확인
        String bootstrapServers = kafka.getHost() + ":" + kafka.getMappedPort(9093);
        try (AdminClient admin = AdminClient.create(
                Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers))) {
            await()
                    .atMost(Duration.ofSeconds(5))
                    .pollInterval(Duration.ofMillis(500))
                    .until(() -> admin.listTopics().names().get().contains("order.created"));
        }
    }

    @Test
    @DisplayName("주문 생성 → 주문 상세 조회 → Kafka 이벤트 발행까지 성공한다")
    void createOrder_and_verifyKafka_and_fetchOrder() {
        // given
        long memberId = 1L;
        BigDecimal price = BigDecimal.valueOf(15000);
        List<OrderItemRequest> items = List.of(
                new OrderItemRequest(10L, 2, price)
        );
        OrderRequest request = new OrderRequest(memberId, items, price.multiply(BigDecimal.valueOf(2)));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<OrderRequest> entity = new HttpEntity<>(request, headers);

        // when
        ResponseEntity<Long> response = restTemplate.exchange(
                getBaseUrl("/api/orders"),
                HttpMethod.POST,
                entity,
                Long.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Long orderId = response.getBody();
        assertThat(orderId).isNotNull();

        // 주문 상세 조회
        ResponseEntity<OrderResponse> orderRes = restTemplate.getForEntity(
                getBaseUrl("/api/orders/" + orderId), OrderResponse.class
        );

        assertThat(orderRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(orderRes.getBody()).isNotNull();
        assertThat(orderRes.getBody().getOrderId()).isEqualTo(orderId);
        assertThat(orderRes.getBody().getMemberId()).isEqualTo(memberId);
        assertThat(orderRes.getBody().getItems()).hasSize(1);
        assertThat(orderRes.getBody().getItems().get(0).getProductId()).isEqualTo(10L);

        // Kafka 이벤트 발행 여부 확인
        String bootstrapServers = kafka.getHost() + ":" + kafka.getMappedPort(9093);
        try (AdminClient admin = AdminClient.create(
                Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers))) {
            await()
                    .atMost(Duration.ofSeconds(10))
                    .pollInterval(Duration.ofMillis(500))
                    .until(() -> admin.listTopics().names().get().contains("order.created"));
        }
    }

    @Test
    @DisplayName("inventory.reserved 이벤트 수신 시 주문 상태는 그대로 유지된다")
    void handleInventoryReservedEvent_doesNotChangeStatus() throws Exception {
        // given: 주문 생성
        OrderRequest request = new OrderRequest(
                1L,
                List.of(new OrderItemRequest(10L, 1, BigDecimal.valueOf(5000))),
                BigDecimal.valueOf(5000)
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<OrderRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Long> res = restTemplate.exchange(
                getBaseUrl("/api/orders"), HttpMethod.POST, entity, Long.class
        );
        Long orderId = res.getBody();

        // when: Kafka로 inventory.reserved 이벤트 발행
        Map<String, Object> event = Map.of(
                "orderId", orderId,
                "reserved", true,
                "timestamp", LocalDateTime.now().toString(),
                "traceId", UUID.randomUUID().toString()
        );

        String bootstrapServers = kafka.getHost() + ":" + kafka.getMappedPort(9093);

        KafkaProducer<String, Object> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                org.springframework.kafka.support.serializer.JsonSerializer.class
        ));

        producer.send(new ProducerRecord<>("inventory.reserved", orderId.toString(), event));
        producer.flush();
        producer.close();

        // then: 상태가 그대로 ORDERED 인지 확인
        await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    Order order = orderRepository.findById(orderId).orElseThrow();
                    assertThat(order.getStatus()).isEqualTo(OrderStatus.ORDERED);
                });
    }

    @Test
    @DisplayName("inventory.failed 이벤트 수신 시 주문 상태가 FAILED로 변경된다")
    void handleInventoryFailedEvent_updatesStatusToFailed() throws Exception {
        // given: 주문 생성
        OrderRequest request = new OrderRequest(
                1L,
                List.of(new OrderItemRequest(10L, 1, BigDecimal.valueOf(5000))),
                BigDecimal.valueOf(5000)
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<OrderRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Long> res = restTemplate.exchange(
                getBaseUrl("/api/orders"),
                HttpMethod.POST,
                entity,
                Long.class
        );

        Long orderId = res.getBody();

        InventoryFailedEvent event = InventoryFailedEvent.builder()
                .orderId(orderId)
                .productId(10L)
                .reason("재고 부족")
                .timestamp(LocalDateTime.now().toString())
                .traceId(UUID.randomUUID().toString())
                .build();

        String bootstrapServers = kafka.getHost() + ":" + kafka.getMappedPort(9093);

        KafkaProducer<String, Object> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                org.springframework.kafka.support.serializer.JsonSerializer.class
        ));

        producer.send(new ProducerRecord<>("inventory.failed", orderId.toString(), event));
        producer.flush();
        producer.close();

        // then: 상태가 FAILED로 변경되었는지 확인
        await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    Order order = orderRepository.findById(orderId).orElseThrow();
                    assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
                });
    }

    @Test
    @DisplayName("결제 요청 시 주문 상태가 PAID로 변경되고 Kafka 이벤트가 발행된다")
    void manualPayment_success_kafkaEmitted() {
        // given: 주문 생성
        OrderRequest orderRequest = new OrderRequest(
                1L,
                List.of(new OrderItemRequest(100L, 1, BigDecimal.valueOf(5000))),
                BigDecimal.valueOf(5000)
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<OrderRequest> orderEntity = new HttpEntity<>(orderRequest, headers);

        ResponseEntity<Long> res = restTemplate.exchange(
                getBaseUrl("/api/orders"), HttpMethod.POST, orderEntity, Long.class
        );
        Long orderId = res.getBody();

        // when: 결제 요청
        PaymentRequest paymentRequest = new PaymentRequest("CARD");
        HttpEntity<PaymentRequest> paymentEntity = new HttpEntity<>(paymentRequest, headers);
        ResponseEntity<String> paymentRes = restTemplate.exchange(
                getBaseUrl("/api/payments/" + orderId),
                HttpMethod.POST,
                paymentEntity,
                String.class
        );

        // then: 응답 성공
        assertThat(paymentRes.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 주문 상태 변경 확인
        await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    Order order = orderRepository.findById(orderId).orElseThrow();
                    assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
                });

        // 결제 DB 저장 확인
        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    assertThat(paymentRepository.findByOrderId(orderId)).isPresent();
                });

        // Kafka 토픽 존재 여부 확인 (발행 여부는 외부 Kafka UI에서 확인 가능)
        String bootstrap = kafka.getHost() + ":" + kafka.getMappedPort(9093);
        try (AdminClient admin = AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap))) {
            await()
                    .atMost(Duration.ofSeconds(5))
                    .until(() -> admin.listTopics().names().get().containsAll(
                            List.of("payment.completed", "point.changed", "delivery.started")));
        }
    }

    @Test
    @DisplayName("결제 상세 조회에 성공한다")
    void getPayment_success() {
        Long orderId = createOrderAndPay();
        Long paymentId = paymentRepository.findByOrderId(orderId).orElseThrow().getId();

        ResponseEntity<PaymentResponse> res = restTemplate.getForEntity(
                getBaseUrl("/api/payments/" + paymentId), PaymentResponse.class
        );

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getOrderId()).isEqualTo(orderId);
        assertThat(res.getBody().getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("주문 ID로 결제 상태 조회에 성공한다")
    void getPaymentStatus_success() {
        Long orderId = createOrderAndPay();

        ResponseEntity<String> res = restTemplate.exchange(
                getBaseUrl("/api/payments/order/" + orderId + "/status"),
                HttpMethod.GET,
                null,
                String.class
        );

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("결제를 취소하면 상태가 FAILED로 변경된다")
    void cancelPayment_success() {
        Long orderId = createOrderAndPay();
        Long paymentId = paymentRepository.findByOrderId(orderId).orElseThrow().getId();

        ResponseEntity<String> res = restTemplate.postForEntity(
                getBaseUrl("/api/payments/" + paymentId + "/cancel"),
                null,
                String.class
        );

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(payment.getPaymentStatus().name()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("실패한 결제를 재시도하면 상태가 SUCCESS로 변경된다")
    void retryPayment_success() {
        Long orderId = createOrderAndPay();
        Long paymentId = paymentRepository.findByOrderId(orderId).orElseThrow().getId();

        // 먼저 취소 상태로 만들기
        restTemplate.postForEntity(getBaseUrl("/api/payments/" + paymentId + "/cancel"), null, String.class);

        ResponseEntity<String> res = restTemplate.postForEntity(
                getBaseUrl("/api/payments/" + paymentId + "/retry"),
                null,
                String.class
        );

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(payment.getPaymentStatus().name()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("이미 성공한 결제를 재시도하면 예외가 발생한다")
    void retryPayment_fail() {
        Long orderId = createOrderAndPay();
        Long paymentId = paymentRepository.findByOrderId(orderId).orElseThrow().getId();

        ResponseEntity<String> res = restTemplate.postForEntity(
                getBaseUrl("/api/payments/" + paymentId + "/retry"),
                null,
                String.class
        );

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private Long createOrderAndPay() {
        OrderRequest orderRequest = new OrderRequest(
                1L,
                List.of(new OrderItemRequest(100L, 1, BigDecimal.valueOf(5000))),
                BigDecimal.valueOf(5000)
        );
        HttpEntity<OrderRequest> orderEntity = new HttpEntity<>(orderRequest);
        ResponseEntity<Long> res = restTemplate.exchange(
                getBaseUrl("/api/orders"),
                HttpMethod.POST,
                orderEntity,
                Long.class
        );
        Long orderId = res.getBody();

        PaymentRequest paymentRequest = new PaymentRequest("CARD");
        HttpEntity<PaymentRequest> payEntity = new HttpEntity<>(paymentRequest);
        restTemplate.postForEntity(getBaseUrl("/api/payments/" + orderId), payEntity, String.class);

        return orderId;
    }
}
