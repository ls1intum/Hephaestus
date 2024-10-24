package de.tum.in.www1.hephaestus.codereview.team;

import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Set;

import org.springframework.lang.NonNull;

import de.tum.in.www1.hephaestus.codereview.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "team")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class Team {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String name;

    @NonNull
    private String color;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "TEAM_MEMBERS", joinColumns = @JoinColumn(name = "team_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "user_id", referencedColumnName = "id"))
    @ToString.Exclude
    private Set<User> members = new HashSet<>();

    public void addMember(User user) {
        members.add(user);
        user.addTeam(this);
    }

    public void removeMember(User user) {
        members.remove(user);
        user.removeTeam(this);
    }
}
