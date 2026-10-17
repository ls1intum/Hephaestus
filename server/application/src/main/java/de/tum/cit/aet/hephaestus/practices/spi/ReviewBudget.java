package de.tum.cit.aet.hephaestus.practices.spi;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

public interface ReviewBudget {
    boolean canRequest(long workspaceId, ReviewKind kind, @Nullable BigDecimal budgetUsd);
}
