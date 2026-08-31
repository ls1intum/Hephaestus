package de.tum.cit.aet.hephaestus.core.auth.consent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "consent_notice")
@Getter
@NoArgsConstructor
class ConsentNotice {

    static final String CURRENT_VERSION = "2026-08-30";

    @Id
    @Column(name = "version", nullable = false, length = 32)
    private String version;

    @Column(name = "notice_text", nullable = false, columnDefinition = "text")
    private String noticeText;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "published_at", nullable = false, updatable = false)
    private Instant publishedAt;

    ConsentNotice(String version, String noticeText, String sha256, Instant publishedAt) {
        this.version = version;
        this.noticeText = noticeText;
        this.sha256 = sha256;
        this.publishedAt = publishedAt;
    }
}
