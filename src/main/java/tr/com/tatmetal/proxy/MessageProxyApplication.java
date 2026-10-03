package tr.com.tatmetal.proxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Message Proxy uygulaması.
 * <p>
 * mini_jpa bileşenlerinin (ör. @ExtendsBaseEntity aspect'i) bean olarak yüklenebilmesi için
 * mini_jpa kök paketi de taranır.
 */
@SpringBootApplication(scanBasePackages = {"tr.com.tatmetal.proxy", "tr.com.zenergy.exss", "tr.com.zenergy.mini_jpa"})
@EnableFeignClients(basePackages = "tr.com.tatmetal.proxy.client")
public class MessageProxyApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessageProxyApplication.class, args);
    }
}
