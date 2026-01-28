package de.tum.in.www1.hephaestus;

import de.tum.in.www1.hephaestus.gitprovider.label.LabelInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import de.tum.in.www1.hephaestus.profile.dto.ProfileReviewActivityDTO;
import io.hypersistence.utils.hibernate.type.util.ClassImportIntegrator;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.jpa.boot.spi.IntegratorProvider;

public class ClassImportIntegratorIntegratorProvider implements IntegratorProvider {

    @Override
    public List<Integrator> getIntegrators() {
        // Accessible DTOs
        @SuppressWarnings("rawtypes")
        List<Class> classes = new ArrayList<>();
        classes.add(UserInfoDTO.class);
        classes.add(TeamInfoDTO.class);
        classes.add(LabelInfoDTO.class);
        classes.add(PullRequestInfoDTO.class);
        classes.add(ProfileReviewActivityDTO.class);
        classes.add(RepositoryInfoDTO.class);

        return List.of(new ClassImportIntegrator(classes));
    }
}
