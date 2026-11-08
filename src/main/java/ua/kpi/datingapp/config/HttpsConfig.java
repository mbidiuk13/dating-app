package ua.kpi.datingapp.config;

import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Захист з'єднання через HTTPS (НФВ безпеки, ЛР1).
 * Основний порт (server.port, 8443) працює по HTTPS. Додатковий HTTP-порт (8080)
 * лише перенаправляє всі запити на HTTPS, тож дані ніколи не передаються відкритим текстом.
 * Вимикається властивістю SSL_ENABLED=false (наприклад, якщо сертифікат ще не створено).
 */
@Configuration
@ConditionalOnProperty(name = "server.ssl.enabled", havingValue = "true")
public class HttpsConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> httpToHttpsRedirect(
            @Value("${server.port}") int httpsPort,
            @Value("${app.http-port:8080}") int httpPort) {
        return factory -> {
            // Будь-яка адреса вимагає захищеного з'єднання (CONFIDENTIAL)
            factory.addContextCustomizers(context -> {
                SecurityConstraint constraint = new SecurityConstraint();
                constraint.setUserConstraint("CONFIDENTIAL");
                SecurityCollection collection = new SecurityCollection();
                collection.addPattern("/*");
                constraint.addCollection(collection);
                context.addConstraint(constraint);
            });

            // HTTP-конектор, який відповідає лише перенаправленням на HTTPS
            Connector httpConnector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            httpConnector.setScheme("http");
            httpConnector.setPort(httpPort);
            httpConnector.setSecure(false);
            httpConnector.setRedirectPort(httpsPort);
            factory.addAdditionalTomcatConnectors(httpConnector);
        };
    }
}
