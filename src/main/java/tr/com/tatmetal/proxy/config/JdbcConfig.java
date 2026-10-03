package tr.com.tatmetal.proxy.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.context.annotation.Primary;
import tr.com.zenergy.mini_jpa.jdbc.template.ZenergyNamedJdbcTemplate;

/**
 * Dao katmanında @Qualifier("exdbJdbcTemplate") ile kullanılan mini_jpa template bean'i.
 * DataSource, application.yml içindeki spring.datasource ayarlarından otomatik oluşturulur.
 */
@Configuration
public class JdbcConfig {

    @Bean(name = "exDb")
    @Primary
    @ConfigurationProperties(prefix = "datasource.exdb")
    public DataSource exdbDataSource() {
        return DataSourceBuilder.create().build();
    }


    @Bean(name = "exdbJdbcTemplate")
    @Primary
    public ZenergyNamedJdbcTemplate exdbJdbcTemplate(@Qualifier("exDb") DataSource ds) {
        return new ZenergyNamedJdbcTemplate(ds);
    }
}
