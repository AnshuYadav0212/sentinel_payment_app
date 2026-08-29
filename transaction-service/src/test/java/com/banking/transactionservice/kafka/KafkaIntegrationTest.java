package com.banking.transactionservice.kafka;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig
@EmbeddedKafka(
        partitions = 1,
        topics = {"transaction.initiated.test", "fraud.detected.test", "verification.required.test"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class KafkaIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    private KafkaTemplate<String, Object> kafkaTemplate;
    private Consumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        Map<String, Object> producerProps =
                KafkaTestUtils.producerProps(embeddedKafka);

        DefaultKafkaProducerFactory<String, Object> producerFactory =
                new DefaultKafkaProducerFactory<>(
                        producerProps,
                        new StringSerializer(),
                        new JsonSerializer<>()
                );

        kafkaTemplate = new KafkaTemplate<>(producerFactory);

        Map<String, Object> consumerProps =
                KafkaTestUtils.consumerProps(
                        "kafka-integration-test-" + System.nanoTime(),
                        "false",
                        embeddedKafka
                );
        consumerProps.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"
        );

        consumer = new DefaultKafkaConsumerFactory<>(
                consumerProps,
                new StringDeserializer(),
                new StringDeserializer()
        ).createConsumer();
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    void transactionInitiatedEvent_shouldRoundTripThroughKafka() throws Exception {
        String topic = "transaction.initiated.test";
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, topic);

        kafkaTemplate.send(
                topic,
                "tx-1",
                Map.of(
                        "transactionId", "tx-1",
                        "senderAccountNumber", "123456789012",
                        "receiverAccountNumber", "987654321012",
                        "amount", new BigDecimal("500")
                )
        ).get();

        ConsumerRecord<String, String> record =
                KafkaTestUtils.getSingleRecord(consumer, topic);

        assertThat(record.key()).isEqualTo("tx-1");
        assertThat(record.value()).contains("\"transactionId\":\"tx-1\"");
        assertThat(record.value()).contains("\"amount\":500");
    }

    @Test
    void fraudDetectedEvent_shouldRoundTripThroughKafka() throws Exception {
        String topic = "fraud.detected.test";
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, topic);

        kafkaTemplate.send(
                topic,
                "123456789012",
                Map.of(
                        "transactionId", "tx-2",
                        "accountNumber", "123456789012",
                        "reason", "high-risk transaction"
                )
        ).get();

        ConsumerRecord<String, String> record =
                KafkaTestUtils.getSingleRecord(consumer, topic);

        assertThat(record.key()).isEqualTo("123456789012");
        assertThat(record.value()).contains("high-risk transaction");
    }

    @Test
    void verificationRequiredEvent_shouldRoundTripThroughKafka() throws Exception {
        String topic = "verification.required.test";
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, topic);

        kafkaTemplate.send(
                topic,
                "tx-3",
                Map.of(
                        "transactionId", "tx-3",
                        "accountNumber", "123456789012",
                        "amount", new BigDecimal("1600"),
                        "reason", "suspicious activity"
                )
        ).get();

        ConsumerRecord<String, String> record =
                KafkaTestUtils.getSingleRecord(consumer, topic);

        assertThat(record.key()).isEqualTo("tx-3");
        assertThat(record.value()).contains("suspicious activity");
    }
}
