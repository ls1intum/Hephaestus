package de.tum.in.www1.hephaestus;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.integrator.spi.Integrator;
import org.hibernate.jpa.boot.spi.IntegratorProvider;

import de.tum.in.www1.hephaestus.gitprovider.issue.dto.IssueInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.dto.IssueCommentInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.label.dto.LabelInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.milestone.dto.MilestoneInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.dto.PullRequestInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.dto.PullRequestReviewInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.dto.RepositoryInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.dto.UserInfoDTO;
import io.hypersistence.utils.hibernate.type.util.ClassImportIntegrator;

public class ClassImportIntegratorIntegratorProvider implements IntegratorProvider {

    @Override
    public List<Integrator> getIntegrators() {
        // Accessible DTOs
        @SuppressWarnings("rawtypes")
        List<Class> classes = new ArrayList<>();
        classes.add(UserInfoDTO.class);
        classes.add(IssueInfoDTO.class);
        classes.add(LabelInfoDTO.class);
        classes.add(MilestoneInfoDTO.class);
        classes.add(PullRequestInfoDTO.class);
        classes.add(IssueCommentInfoDTO.class);
        classes.add(PullRequestReviewInfoDTO.class);
        classes.add(RepositoryInfoDTO.class);

        return List.of(new ClassImportIntegrator(classes));
    }
}
