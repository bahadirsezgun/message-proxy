package tr.com.tatmetal.proxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Message Proxy uygulaması.
 * <p>
 * mini_jpa bileşenlerinin (ör. @ExtendsBaseEntity aspect'i) bean olarak yüklenebilmesi için
 * mini_jpa kök paketi de taranır.
 * <p>
 * @EnableScheduling: swagger endpoint senkronizasyonu (SwaggerSyncScheduler) için.
 */
@SpringBootApplication(scanBasePackages = {"tr.com.tatmetal.proxy", "tr.com.zenergy.mini_jpa"})
@EnableFeignClients(basePackages = "tr.com.tatmetal.proxy.client")
@EnableScheduling
public class MessageProxyApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessageProxyApplication.class, args);
    }
}
