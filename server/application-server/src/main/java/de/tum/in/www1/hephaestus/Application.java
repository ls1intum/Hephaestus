package de.tum.in.www1.hephaestus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@SpringBootApplication
@Modulithic(systemName = "Hephaestus")
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
}
