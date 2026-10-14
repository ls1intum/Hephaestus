package de.tum.cit.aet.hephaestus.core.auth.domain;

import org.springframework.modulith.NamedInterface;

@NamedInterface("auth-types")
public enum AiChoice {
    NO_AI,
    IN_HOUSE_ONLY,
    CLOUD,
}
