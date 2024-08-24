package de.tum.in.www1.hephaestus;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.modulith.Modulithic;

import de.tum.in.www1.hephaestus.codereview.CodeReviewService;
import de.tum.in.www1.hephaestus.codereview.repository.Repository;
import de.tum.in.www1.hephaestus.codereview.repository.RepositoryService;

@SpringBootApplication
@Modulithic(systemName = "Hephaestus")
@EnableConfigurationProperties(EnvConfig.class)
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Bean
	CommandLineRunner commandLineRunner(CodeReviewService service, RepositoryService repositoryService) {
		return args -> {
			Repository repo = service.fetchHephaestus();
			System.out.println("Got repo: " + repo);
			repositoryService.saveRepository(repo);
			System.out.println("Saved repo: " + repositoryService.countRepositories());

			List<Repository> repo2 = repositoryService.getAllRepositories();
			System.out.println("Repositories: " + repo2);
		};
	}
}
