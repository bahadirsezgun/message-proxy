package tr.com.tatmetal.proxy.config;

import org.springframework.context.annotation.Bean;

import feign.Logger;
import feign.Retryer;

/**
 * ProxyForwardClient'a özel Feign ayarları.
 * Bilerek @Configuration DEĞİLDİR; aksi halde tüm Feign client'lara global uygulanır.
 */
public class ProxyFeignConfig {

    /** Proxy iletiminde otomatik tekrar denemesi yapılmaz (POST gibi idempotent olmayan çağrılar çift gitmesin). */
    @Bean
    public Retryer proxyForwardRetryer() {
        return Retryer.NEVER_RETRY;
    }

    @Bean
    public Logger.Level proxyForwardLoggerLevel() {
        return Logger.Level.BASIC;
    }
}
