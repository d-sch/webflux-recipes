package io.github.d_sch.webfluxcustomjacksonstream;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.reactive.config.EnableWebFlux;

@SpringBootApplication
@EnableWebFlux
public class WebfluxCustomJacksonStreamApplication {

	public static void main(String[] args) {
		SpringApplication.run(WebfluxCustomJacksonStreamApplication.class, args);
	}

}
