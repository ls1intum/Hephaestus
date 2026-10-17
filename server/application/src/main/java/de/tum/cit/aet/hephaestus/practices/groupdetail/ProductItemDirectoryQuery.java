package de.tum.cit.aet.hephaestus.practices.groupdetail;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.api.ItemIndexService;
import de.tum.cit.aet.hephaestus.integration.core.api.ItemRefDTO;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.practices.review.ProductItemRepository;
import de.tum.cit.aet.hephaestus.practices.review.ProductReferenceQuery;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnServerRole
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductItemDirectoryQuery {
    private final ProductItemRepository items;
    private final ItemIndexService index;
    private final ProductReferenceQuery references;

    public LegacyFeedbackItemDTO legacyFeedback(long workspaceId, String kind, long sourceId) {
        if (sourceId <= 0 || (!kind.equals("scm.pull_request") && !kind.equals("scm.issue")))
            throw new EntityNotFoundException("Item", Long.toString(sourceId));
        var item = index.findSource(workspaceId, kind, sourceId)
                .orElseThrow(() -> new EntityNotFoundException("Item", Long.toString(sourceId)));
        return new LegacyFeedbackItemDTO(Long.toString(item.itemId()));
    }

    public Page<ItemRefDTO> list(long workspaceId, @Nullable String kind, Pageable pageable) {
        var selected = kind == null ? null : new ItemKind(kind).value();
        var page = items.directory(workspaceId, selected, pageable);
        var refs = references.items(workspaceId, page.getContent());
        return page.map(id -> Objects.requireNonNull(refs.get(id)));
    }
}
