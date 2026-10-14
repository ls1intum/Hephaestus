package de.tum.cit.aet.hephaestus.account.userview;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.core.auth.dto.AccountRefDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record UserViewAccountDTO(
        @NonNull @Schema(pattern = "^[1-9][0-9]*$") String accountId,
        @NonNull AccountRefDTO account,
        @Nullable String providerLogin) {}
