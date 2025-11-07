package de.tum.in.www1.hephaestus.workspace.exception;

public class InvalidWorkspaceSlugException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvalidWorkspaceSlugException(String slug) {
    super("Invalid workspace slug: " + slug);
  }
}
