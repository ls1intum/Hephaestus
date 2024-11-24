package de.tum.in.www1.hephaestus.workspace;

import org.springframework.context.ApplicationEvent;

public class WorkspaceChangedEvent extends ApplicationEvent {
    private final Workspace workspace;

    public WorkspaceChangedEvent(Object source, Workspace workspace) {
        super(source);
        this.workspace = workspace;
    }

    public Workspace getWorkspace() {
        return workspace;
    }
}
