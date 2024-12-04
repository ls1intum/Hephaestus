package de.tum.in.www1.hephaestus;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.integrator.spi.Integrator;
import org.hibernate.jpa.boot.spi.IntegratorProvider;

import de.tum.in.www1.hephaestus.gitprovider.issue.IssueInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueCommentInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import de.tum.in.www1.hephaestus.mentor.message.MessageDTO;
import de.tum.in.www1.hephaestus.mentor.session.SessionDTO;
import io.hypersistence.utils.hibernate.type.util.ClassImportIntegrator;

public class ClassImportIntegratorIntegratorProvider implements IntegratorProvider {

        @Override
        public List<Integrator> getIntegrators() {
                // Accessible DTOs
                @SuppressWarnings("rawtypes")
                List<Class> classes = new ArrayList<>();
                classes.add(UserInfoDTO.class);
                classes.add(TeamInfoDTO.class);
                classes.add(IssueInfoDTO.class);
                classes.add(LabelInfoDTO.class);
                classes.add(MilestoneInfoDTO.class);
                classes.add(PullRequestInfoDTO.class);
                classes.add(IssueCommentInfoDTO.class);
                classes.add(PullRequestReviewInfoDTO.class);
                classes.add(RepositoryInfoDTO.class);
                classes.add(MessageDTO.class);
                classes.add(SessionDTO.class);

                return List.of(new ClassImportIntegrator(classes));
        }
}
