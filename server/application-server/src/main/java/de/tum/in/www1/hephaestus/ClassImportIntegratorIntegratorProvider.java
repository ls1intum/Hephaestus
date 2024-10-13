package de.tum.in.www1.hephaestus;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.integrator.spi.Integrator;
import org.hibernate.jpa.boot.spi.IntegratorProvider;

import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueCommentDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserDTO;
import io.hypersistence.utils.hibernate.type.util.ClassImportIntegrator;

public class ClassImportIntegratorIntegratorProvider implements IntegratorProvider {

    @Override
    public List<Integrator> getIntegrators() {
        // Accessible DTOs
        @SuppressWarnings("rawtypes")
        List<Class> classes = new ArrayList<>();
        classes.add(UserDTO.class);
        classes.add(PullRequestDTO.class);
        classes.add(IssueCommentDTO.class);
        classes.add(RepositoryDTO.class);

        return List.of(new ClassImportIntegrator(classes));
    }
}
