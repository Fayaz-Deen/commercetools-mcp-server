package io.github.fayazdeen.ctmcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CommercetoolsMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommercetoolsMcpServerApplication.class, args);
    }
}
