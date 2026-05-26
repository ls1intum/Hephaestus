package de.tum.cit.aet.hephaestus.integration.feedback;

import de.tum.cit.aet.hephaestus.integration.feedback.FeedbackPost.PostKind;
import de.tum.cit.aet.hephaestus.integration.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.spi.SubjectClass;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cross-vendor edit-in-place orchestration.
 *
 * <p>Agent code calls {@link #findExisting} to discover whether a prior post exists
 * for the same subject + kind; if yes, the adapter performs an in-place edit
 * (Slack {@code chat.update}, Outline {@code comments.update}, GitHub PR-comment
 * edit). On post-new, {@link #recordPost} captures the vendor-side handle.
 */
@Service
public class FeedbackPostService {

    private final FeedbackPostRepository repository;

    public FeedbackPostService(FeedbackPostRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<FeedbackPost> findExisting(Connection connection, String subjectExternalId, PostKind postKind) {
        return repository.findFirstByConnectionIdAndSubjectExternalIdAndPostKindOrderByCreatedAtDesc(
            connection.getId(), subjectExternalId, postKind);
    }

    @Transactional(readOnly = true)
    public List<FeedbackPost> listFor(Connection connection, String subjectExternalId, PostKind postKind) {
        return repository.findByConnectionIdAndSubjectExternalIdAndPostKind(
            connection.getId(), subjectExternalId, postKind);
    }

    @Transactional
    public FeedbackPost recordPost(Connection connection,
                                   String subjectExternalId,
                                   SubjectClass subjectClass,
                                   PostKind postKind,
                                   String externalPostId,
                                   String vendorMetadataJson) {
        FeedbackPost post = new FeedbackPost(
            connection, subjectExternalId, subjectClass, postKind, externalPostId, vendorMetadataJson
        );
        return repository.save(post);
    }
}
