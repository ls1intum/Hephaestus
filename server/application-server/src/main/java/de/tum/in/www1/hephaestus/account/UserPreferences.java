package de.tum.in.www1.hephaestus.account;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Stores Hephaestus platform preferences for a user.
 * <p>
 * This entity is separate from {@link User} to maintain domain isolation.
 * The gitprovider module should only contain data from the Git provider (GitHub),
 * while platform-specific preferences belong in the account module.
 * <p>
 * Preferences include:
 * <ul>
 *   <li>{@link #notificationsEnabled} - Whether the user wants to receive platform notifications</li>
 *   <li>{@link #participateInResearch} - Whether the user consents to analytics/research data collection</li>
 * </ul>
 *
 * @see AccountService
 */
@Entity
@Table(name = "user_preferences")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class UserPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /**
     * The user these preferences belong to.
     * Uses a one-to-one relationship with the User entity.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "user_id",
        nullable = false,
        unique = true,
        foreignKey = @ForeignKey(name = "fk_user_preferences_user")
    )
    @ToString.Exclude
    private User user;

    /**
     * Whether the user wants to receive platform notifications.
     * Defaults to true for new users.
     */
    @Column(name = "notifications_enabled", nullable = false)
    private boolean notificationsEnabled = true;

    /**
     * Whether the user consents to analytics and research data collection.
     * Defaults to true for new users.
     * When changed from true to false, analytics data should be deleted.
     */
    @Column(name = "participate_in_research", nullable = false)
    private boolean participateInResearch = true;

    /**
     * Creates a new UserPreferences instance for the given user with default settings.
     *
     * @param user the user these preferences belong to
     */
    public UserPreferences(User user) {
        this.user = user;
    }
}
