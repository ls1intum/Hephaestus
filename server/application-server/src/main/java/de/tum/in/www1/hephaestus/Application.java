package de.tum.in.www1.hephaestus;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.modulith.Modulithic;

import de.tum.in.www1.hephaestus.codereview.CodeReviewService;
import de.tum.in.www1.hephaestus.codereview.repository.Repository;

@SpringBootApplication
@Modulithic(systemName = "Hephaestus")
@EnableConfigurationProperties(EnvConfig.class)
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Bean
	CommandLineRunner commandLineRunner(CodeReviewService service) {
		return args -> {
			Repository repo = service.fetchRepository("ls1intum/hephaestus");
			System.out.println("Got repo: " + repo);
		};
	}
}
