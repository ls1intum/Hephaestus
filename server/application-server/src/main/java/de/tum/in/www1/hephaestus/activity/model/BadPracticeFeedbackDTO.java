package de.tum.in.www1.hephaestus.activity.model;

import org.springframework.lang.NonNull;

public record BadPracticeFeedbackDTO(@NonNull String type, @NonNull String explanation){}
