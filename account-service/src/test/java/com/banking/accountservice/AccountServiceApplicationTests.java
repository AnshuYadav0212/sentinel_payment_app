package com.banking.accountservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "ACCOUNT_SERVICE_SERVER_PORT=8081",
        "ACCOUNT_SERVICE_DATASOURCE_URL=jdbc:postgresql://localhost:5432/account_service_db",
        "ACCOUNT_SERVICE_DATASOURCE_USERNAME=myuser",
        "ACCOUNT_SERVICE_DATASOURCE_PASSWORD=mypassword",
        "ACCOUNT_SERVICE_KAFKA_BOOTSTRAP_SERVER=localhost:9092"

})
class AccountServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
