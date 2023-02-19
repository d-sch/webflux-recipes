package io.github.d_sch.webfluxcached;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.reactive.config.EnableWebFlux;

@SpringBootApplication
@EnableWebFlux
public class TestApplication {
    public static void main(String[] args) {
		SpringApplication.run(TestApplication.class, args);
	}
}
