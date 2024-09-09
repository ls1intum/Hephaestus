package de.tum.in.www1.hephaestus.leaderboard;

public class LeaderboardEntry {
    private final String githubName;
    private final String name;
    private final int score;
    private final int total;
    private final int changesRequested;
    private final int approvals;
    private final int comments;

    public LeaderboardEntry(String githubName, String name, int score, int total, int changesRequested, int approvals,
            int comments) {
        this.githubName = githubName;
        this.name = name;
        this.score = score;
        this.total = total;
        this.changesRequested = changesRequested;
        this.approvals = approvals;
        this.comments = comments;
    }

    public String getGithubName() {
        return githubName;
    }

    public String getName() {
        return name;
    }

    public int getScore() {
        return score;
    }

    public int getTotal() {
        return total;
    }

    public int getChangesRequested() {
        return changesRequested;
    }

    public int getApprovals() {
        return approvals;
    }

    public int getComments() {
        return comments;
    }
}
