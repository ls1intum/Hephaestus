package de.tum.in.www1.hephaestus.mentor;

/**
 * Document kinds supported by the application.
 */
public enum DocumentKind {
    TEXT("text");

    private final String value;

    DocumentKind(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static DocumentKind fromValue(String value) {
        for (DocumentKind kind : DocumentKind.values()) {
            if (kind.value.equals(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown document kind: " + value);
    }
}
