package de.tum.in.www1.hephaestus.leaderboard;

import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LeaderboardService {

    private static final Logger logger = LoggerFactory.getLogger(LeaderboardService.class);

    private final UserRepository userRepository;
    private final PullRequestReviewRepository pullRequestReviewRepository;
    private final IssueCommentRepository issueCommentRepository;
    private final ScoringService scoringService;
    private final TeamRepository teamRepository;
    private final LeaguePointsCalculationService leaguePointsCalculationService;

    public LeaderboardService(
        UserRepository userRepository,
        PullRequestReviewRepository pullRequestReviewRepository,
        IssueCommentRepository issueCommentRepository,
        ScoringService scoringService,
        TeamRepository teamRepository,
        LeaguePointsCalculationService leaguePointsCalculationService
    ) {
        this.userRepository = userRepository;
        this.pullRequestReviewRepository = pullRequestReviewRepository;
        this.issueCommentRepository = issueCommentRepository;
        this.scoringService = scoringService;
        this.teamRepository = teamRepository;
        this.leaguePointsCalculationService = leaguePointsCalculationService;
    }

    @Transactional
    public List<LeaderboardEntryDTO> createLeaderboard(Instant after, Instant before, String team, LeaderboardSortType sort, LeaderboardMode mode) {
        if (mode == LeaderboardMode.INDIVIDUAL) {
            Team resolvedTeam = "all".equals(team) ? null : resolveTeamByPath(team);
            return createIndividualLeaderboard(after, before, resolvedTeam, sort);
        } else {
            // Team mode aggregates across all visible teams; the team filter is ignored on purpose (documented in OpenAPI).
            return createTeamLeaderboard(after, before, sort);
        }
    }

    public List<LeaderboardEntryDTO> createLeaderboard(Instant after, Instant before, Optional<String> team, Optional<LeaderboardSortType> sort, Optional<LeaderboardMode> mode) {
        return createLeaderboard(
            after,
            before,
            team.orElse(null),
            sort.orElse(LeaderboardSortType.SCORE),
            mode.orElse(LeaderboardMode.INDIVIDUAL)
        );
    }

    private List<LeaderboardEntryDTO> createIndividualLeaderboard(Instant after, Instant before, Team team, LeaderboardSortType sort) {
        return createIndividualLeaderboard(after, before, team, sort, null);
    }

    private List<LeaderboardEntryDTO> createIndividualLeaderboard(
        Instant after,
        Instant before,
        Team team,
        LeaderboardSortType sort,
        Map<Long, List<Team>> teamHierarchy
    ) {
        logger.info("Creating leaderboard dataset with timeframe: {} - {} and team: {}", after, before, team == null ? "all" : team.getName());

        List<PullRequestReview> reviews;
        List<IssueComment> issueComments;

        Set<Long> teamIds;
        if (team != null) {
            Map<Long, List<Team>> hierarchy = teamHierarchy == null ? buildTeamHierarchy() : teamHierarchy;
            teamIds = collectTeamAndDescendantIds(team, hierarchy);
        } else {
            teamIds = Collections.emptySet();
        }

        if (team != null) {
            reviews = pullRequestReviewRepository.findAllInTimeframeOfTeams(after, before, teamIds);
            issueComments = issueCommentRepository.findAllInTimeframeOfTeams(after, before, teamIds, true);
        } else {
            reviews = pullRequestReviewRepository.findAllInTimeframe(after, before);
            issueComments = issueCommentRepository.findAllInTimeframe(after, before, true);
        }

        Map<Long, User> usersById = reviews.stream()
            .map(PullRequestReview::getAuthor)
            .filter(Objects::nonNull)
            .filter(u -> u.getId() != null)
            .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a, HashMap::new));

        issueComments.stream()
            .map(IssueComment::getAuthor)
            .filter(Objects::nonNull)
            .filter(u -> u.getId() != null)
            .forEach(u -> usersById.putIfAbsent(u.getId(), u));

        if (team != null) {
            if (!teamIds.isEmpty()) {
                userRepository.findAllByTeamIds(teamIds).forEach(u -> {
                    if (u.getId() != null) {
                        usersById.putIfAbsent(u.getId(), u);
                    }
                });
            }
        } else {
            userRepository.findAllHumanInTeams().forEach(u -> {
                if (u.getId() != null) {
                    usersById.putIfAbsent(u.getId(), u);
                }
            });
        }

        Map<Long, List<PullRequestReview>> reviewsByUserId = reviews.stream()
            .filter(r -> r.getAuthor() != null && r.getAuthor().getId() != null)
            .collect(Collectors.groupingBy(r -> r.getAuthor().getId()));

        Map<Long, List<IssueComment>> issueCommentsByUserId = issueComments.stream()
            .filter(c -> c.getAuthor() != null && c.getAuthor().getId() != null)
            .collect(Collectors.groupingBy(c -> c.getAuthor().getId()));

        Map<Long, Integer> scoresByUserId = reviewsByUserId.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> calculateTotalScore(e.getValue(), issueCommentsByUserId.getOrDefault(e.getKey(), Collections.emptyList()))
            ));

        // ensure all discovered users are present with at least score 0
        usersById.keySet().forEach(id -> scoresByUserId.putIfAbsent(id, 0));

        List<Long> ranking = scoresByUserId.entrySet().stream()
            .sorted(comparatorFor(sort, usersById, reviewsByUserId, issueCommentsByUserId))
            .map(Map.Entry::getKey)
            .toList();

        List<LeaderboardEntryDTO> result = new ArrayList<>();
        for (int index = 0; index < ranking.size(); index++) {
            Long userId = ranking.get(index);
            int score = scoresByUserId.getOrDefault(userId, 0);
            UserInfoDTO userDto = Optional.ofNullable(usersById.get(userId)).map(UserInfoDTO::fromUser).orElse(null);
            List<PullRequestReview> userReviews = reviewsByUserId.getOrDefault(userId, Collections.emptyList());
            List<IssueComment> userIssueComments = issueCommentsByUserId.getOrDefault(userId, Collections.emptyList());

            List<PullRequestInfoDTO> reviewedPullRequests = userReviews.stream()
                .map(PullRequestReview::getPullRequest)
                .filter(Objects::nonNull)
                .filter(pr -> pr.getAuthor() == null || !Objects.equals(pr.getAuthor().getId(), userId))
                .collect(Collectors.toMap(PullRequest::getId, pr -> pr, (a, b) -> a))
                .values()
                .stream()
                .map(PullRequestInfoDTO::fromPullRequest)
                .collect(Collectors.toList());

            int numberOfReviewedPRs = (int) userReviews.stream()
                .map(PullRequestReview::getPullRequest)
                .filter(Objects::nonNull)
                .map(PullRequest::getId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

            int numberOfApprovals = (int) userReviews.stream()
                .filter(prr -> prr.getState() == PullRequestReview.State.APPROVED)
                .count();

            int numberOfChangeRequests = (int) userReviews.stream()
                .filter(prr -> prr.getState() == PullRequestReview.State.CHANGES_REQUESTED)
                .count();

            int numberOfComments = (int) userReviews.stream()
                .filter(prr -> prr.getState() == PullRequestReview.State.COMMENTED && prr.getBody() != null)
                .count() + userIssueComments.size();

            int numberOfUnknowns = (int) userReviews.stream()
                .filter(prr -> prr.getState() == PullRequestReview.State.UNKNOWN && prr.getBody() != null)
                .count();

            int numberOfCodeComments = userReviews.stream()
                .mapToInt(prr -> prr.getComments() == null ? 0 : prr.getComments().size())
                .sum();

            LeaderboardEntryDTO entry = new LeaderboardEntryDTO(
                index + 1,
                score,
                userDto,
                null,
                reviewedPullRequests,
                numberOfReviewedPRs,
                numberOfApprovals,
                numberOfChangeRequests,
                numberOfComments,
                numberOfUnknowns,
                numberOfCodeComments
            );
            result.add(entry);
        }

        return result;
    }

    private List<LeaderboardEntryDTO> createTeamLeaderboard(Instant after, Instant before, LeaderboardSortType sort) {
        logger.info("Creating team leaderboard dataset with timeframe: {} - {}", after, before);

        List<Team> allTeams = teamRepository.findAll();
        LinkedHashMap<Long, List<Team>> teamHierarchy = allTeams.stream().collect(Collectors.groupingBy(Team::getParentId, LinkedHashMap::new, Collectors.toList()));
        List<Team> targetTeams = allTeams.stream().filter(t -> !t.isHidden()).toList();

        if (targetTeams.isEmpty()) {
            logger.info("❌ No teams found for team leaderboard");
            return Collections.emptyList();
        }

        Map<Team, TeamStats> teamStatsById = targetTeams.stream()
            .collect(Collectors.toMap(
                teamEntity -> teamEntity,
                teamEntity -> {
                    List<LeaderboardEntryDTO> entries = createIndividualLeaderboard(after, before, teamEntity, LeaderboardSortType.SCORE, teamHierarchy);
                    return aggregateTeamStats(entries);
                }
            ));

        List<Map.Entry<Team, TeamStats>> sorted = teamStatsById.entrySet().stream()
            .sorted((e1, e2) -> {
                int cmp;
                if (sort == LeaderboardSortType.SCORE) {
                    cmp = Integer.compare(e2.getValue().score(), e1.getValue().score());
                } else {
                    cmp = Integer.compare(e2.getValue().leaguePoints(), e1.getValue().leaguePoints());
                }
                if (cmp != 0) {
                    return cmp;
                }
                return e1.getKey().getName().compareTo(e2.getKey().getName());
            })
            .toList();

        List<LeaderboardEntryDTO> result = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Team teamEntity = sorted.get(i).getKey();
            TeamStats stats = sorted.get(i).getValue();
            int score = sort == LeaderboardSortType.SCORE ? stats.score() : stats.leaguePoints();
            LeaderboardEntryDTO entry = new LeaderboardEntryDTO(
                i + 1,
                score,
                null,
                TeamInfoDTO.fromTeam(teamEntity),
                stats.reviewedPullRequests(),
                stats.numberOfReviewedPRs(),
                stats.numberOfApprovals(),
                stats.numberOfChangeRequests(),
                stats.numberOfComments(),
                stats.numberOfUnknowns(),
                stats.numberOfCodeComments()
            );
            result.add(entry);
        }
        return result;
    }

    private Comparator<Map.Entry<Long, Integer>> comparatorFor(
        LeaderboardSortType sortType,
        Map<Long, User> usersById,
        Map<Long, List<PullRequestReview>> reviewsByUserId,
        Map<Long, List<IssueComment>> issueCommentsByUserId
    ) {
        if (sortType == LeaderboardSortType.LEAGUE_POINTS) {
            return compareByLeaguePoints(usersById, reviewsByUserId, issueCommentsByUserId);
        } else {
            return compareByScore(reviewsByUserId, issueCommentsByUserId);
        }
    }

    private Comparator<Map.Entry<Long, Integer>> compareByLeaguePoints(
        Map<Long, User> usersById,
        Map<Long, List<PullRequestReview>> reviewsByUserId,
        Map<Long, List<IssueComment>> issueCommentsByUserId
    ) {
        return (e1, e2) -> {
            int e1LeaguePoints = Optional.ofNullable(usersById.get(e1.getKey())).map(User::getLeaguePoints).orElse(0);
            int e2LeaguePoints = Optional.ofNullable(usersById.get(e2.getKey())).map(User::getLeaguePoints).orElse(0);
            int leagueCompare = Integer.compare(e2LeaguePoints, e1LeaguePoints);
            if (leagueCompare != 0) {
                return leagueCompare;
            }
            return compareByScore(reviewsByUserId, issueCommentsByUserId).compare(e1, e2);
        };
    }

    private Comparator<Map.Entry<Long, Integer>> compareByScore(
        Map<Long, List<PullRequestReview>> reviewsByUserId,
        Map<Long, List<IssueComment>> issueCommentsByUserId
    ) {
        return (e1, e2) -> {
            int scoreCompare = Integer.compare(e2.getValue(), e1.getValue());
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            int e1ReviewComments = reviewsByUserId.getOrDefault(e1.getKey(), Collections.emptyList()).stream()
                .mapToInt(r -> r.getComments() == null ? 0 : r.getComments().size()).sum();
            int e2ReviewComments = reviewsByUserId.getOrDefault(e2.getKey(), Collections.emptyList()).stream()
                .mapToInt(r -> r.getComments() == null ? 0 : r.getComments().size()).sum();
            int e1IssueComments = issueCommentsByUserId.getOrDefault(e1.getKey(), Collections.emptyList()).size();
            int e2IssueComments = issueCommentsByUserId.getOrDefault(e2.getKey(), Collections.emptyList()).size();
            int e1TotalComments = e1ReviewComments + e1IssueComments;
            int e2TotalComments = e2ReviewComments + e2IssueComments;
            return Integer.compare(e2TotalComments, e1TotalComments);
        };
    }

    private int calculateTotalScore(List<PullRequestReview> reviews, List<IssueComment> issueComments) {
        long numberOfIssueComments = issueComments.stream()
            .filter(issueComment -> {
                Long issueAuthorId = issueComment.getIssue() == null ? null : issueComment.getIssue().getAuthor() == null ? null : issueComment.getIssue().getAuthor().getId();
                Long commentAuthorId = issueComment.getAuthor() == null ? null : issueComment.getAuthor().getId();
                return issueAuthorId != null && commentAuthorId != null && !issueAuthorId.equals(commentAuthorId);
            })
            .count();

        Map<Long, List<PullRequestReview>> reviewsByPullRequest = reviews.stream()
            .filter(r -> r.getPullRequest() != null && r.getPullRequest().getId() != null)
            .collect(Collectors.groupingBy(r -> r.getPullRequest().getId()));

        double totalScore = reviewsByPullRequest.values().stream()
            .mapToDouble(pullRequestReviews -> scoringService.calculateReviewScore(pullRequestReviews, (int) numberOfIssueComments))
            .sum();

        return (int) Math.ceil(totalScore);
    }

    @Transactional
    public LeagueChangeDTO getUserLeagueStats(String login, LeaderboardEntryDTO entry) {
        User user = userRepository.findByLogin(login).orElseThrow(() -> new IllegalArgumentException("User not found with login: " + login));
        int currentLeaguePoints = user.getLeaguePoints();
        int projectedNewPoints = leaguePointsCalculationService.calculateNewPoints(user, entry);
        return new LeagueChangeDTO(user.getLogin(), projectedNewPoints - currentLeaguePoints);
    }

    private Team resolveTeamByPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }

        String[] parts = path.split(" / ");
        String leaf = parts[parts.length - 1];
        List<Team> candidates = teamRepository.findAllByName(leaf);
        if (candidates.isEmpty()) {
            return null;
        }

        Map<Long, Team> cache = new HashMap<>();
        Map<Long, Team> currentByCandidate = new HashMap<>();

        for (Team candidate : candidates) {
            Long candidateId = candidate.getId();
            if (candidateId == null) {
                continue;
            }
            cache.put(candidateId, candidate);
            currentByCandidate.put(candidateId, candidate);
        }

        if (currentByCandidate.isEmpty()) {
            return null;
        }

        if (parts.length == 1) {
            Long sole = currentByCandidate.keySet().stream().findFirst().get();
            return cache.get(sole);
        }

        for (int index = parts.length - 2; index >= 0; index--) {
            if (currentByCandidate.size() == 1) {
                break;
            }

            String expected = parts[index];
            Map<Long, Team> nextVisibleByCandidate = new HashMap<>();
            boolean pendingResolution = true;

            while (pendingResolution) {
                pendingResolution = false;
                Set<Long> missingIds = new HashSet<>();

                for (Map.Entry<Long, Team> entry : currentByCandidate.entrySet()) {
                    Long candidateId = entry.getKey();
                    Team cursor = entry.getValue();
                    if (nextVisibleByCandidate.containsKey(candidateId)) {
                        continue;
                    }

                    Long parentId = cursor.getParentId();
                    while (parentId != null) {
                        Team parent = cache.get(parentId);
                        if (parent == null) {
                            missingIds.add(parentId);
                            break;
                        }
                        if (!parent.isHidden()) {
                            nextVisibleByCandidate.put(candidateId, parent);
                            break;
                        }
                        parentId = parent.getParentId();
                    }

                    if (parentId == null && !nextVisibleByCandidate.containsKey(candidateId)) {
                        nextVisibleByCandidate.put(candidateId, null);
                    }
                }

                if (!missingIds.isEmpty()) {
                    teamRepository.findAllById(missingIds).forEach(parent -> {
                        Long parentId = parent.getId();
                        if (parentId != null) {
                            cache.putIfAbsent(parentId, parent);
                        }
                    });
                    pendingResolution = true;
                }
            }

            Map<Long, Team> filtered = new HashMap<>();
            for (Map.Entry<Long, Team> e : currentByCandidate.entrySet()) {
                Long candidateId = e.getKey();
                Team nextVisible = nextVisibleByCandidate.get(candidateId);
                if (nextVisible != null && expected.equals(nextVisible.getName())) {
                    filtered.put(candidateId, nextVisible);
                    if (nextVisible.getId() != null) {
                        cache.putIfAbsent(nextVisible.getId(), nextVisible);
                    }
                }
            }

            currentByCandidate = filtered;

            if (currentByCandidate.isEmpty()) {
                return null;
            }

            if (currentByCandidate.size() == 1) {
                Long onlyId = currentByCandidate.keySet().iterator().next();
                return cache.get(onlyId);
            }
        }

        if (currentByCandidate.size() > 1) {
            preloadAncestors(currentByCandidate.values(), cache);
            for (Long candidateId : currentByCandidate.keySet()) {
                Team candidate = cache.get(candidateId);
                if (candidate != null && equalsVisiblePath(candidate, parts, cache)) {
                    return candidate;
                }
            }
            logger.warn("Ambiguous team path '{}' resolved to multiple candidates; picking first.", sanitizeForLog(path));
        }

        Long anyId = currentByCandidate.keySet().stream().findFirst().orElse(null);
        return anyId == null ? null : cache.get(anyId);
    }

    private boolean equalsVisiblePath(Team team, String[] parts, Map<Long, Team> cache) {
        int index = parts.length - 1;
        Team current = team;
        while (current != null) {
            if (!current.isHidden()) {
                if (index < 0 || !parts[index].equals(current.getName())) {
                    return false;
                }
                index--;
            }
            Long parentId = current.getParentId();
            current = parentId == null ? null : cache.get(parentId);
        }
        return index < 0;
    }

    private boolean equalsVisiblePath(Team team, List<String> partsList, Map<Long, Team> cache) {
        return equalsVisiblePath(team, partsList.toArray(new String[0]), cache);
    }

    private void preloadAncestors(Collection<Team> teams, Map<Long, Team> cache) {
        Set<Long> pending = teams.stream()
            .map(Team::getParentId)
            .filter(Objects::nonNull)
            .filter(id -> !cache.containsKey(id))
            .collect(Collectors.toSet());

        while (!pending.isEmpty()) {
            Set<Long> nextRound = new HashSet<>();
            teamRepository.findAllById(pending).forEach(parent -> {
                Long parentId = parent.getId();
                if (parentId == null) {
                    return;
                }
                if (!cache.containsKey(parentId)) {
                    cache.put(parentId, parent);
                }
                Long ancestorId = parent.getParentId();
                if (ancestorId != null && !cache.containsKey(ancestorId)) {
                    nextRound.add(ancestorId);
                }
            });
            pending = nextRound;
        }
    }

    private String sanitizeForLog(String input) {
        return input == null ? null : input.replaceAll("[\\r\\n]", "");
    }

    private TeamStats aggregateTeamStats(List<LeaderboardEntryDTO> entries) {
        List<PullRequestInfoDTO> reviewedPullRequests = new ArrayList<>(entries.stream()
            .flatMap(e -> e.reviewedPullRequests().stream())
            .collect(Collectors.toMap(PullRequestInfoDTO::id, p -> p, (a, b) -> a))
            .values());

        int score = entries.stream().mapToInt(LeaderboardEntryDTO::score).sum();
        int leaguePoints = entries.stream().mapToInt(e -> e.user() == null ? 0 : e.user().leaguePoints()).sum();
        int numberOfReviewedPRs = entries.stream().mapToInt(LeaderboardEntryDTO::numberOfReviewedPRs).sum();
        int numberOfApprovals = entries.stream().mapToInt(LeaderboardEntryDTO::numberOfApprovals).sum();
        int numberOfChangeRequests = entries.stream().mapToInt(LeaderboardEntryDTO::numberOfChangeRequests).sum();
        int numberOfComments = entries.stream().mapToInt(LeaderboardEntryDTO::numberOfComments).sum();
        int numberOfUnknowns = entries.stream().mapToInt(LeaderboardEntryDTO::numberOfUnknowns).sum();
        int numberOfCodeComments = entries.stream().mapToInt(LeaderboardEntryDTO::numberOfCodeComments).sum();

        return new TeamStats(score, leaguePoints, reviewedPullRequests, numberOfReviewedPRs, numberOfApprovals, numberOfChangeRequests, numberOfComments, numberOfUnknowns, numberOfCodeComments);
    }

    private HashMap<Long, List<Team>> buildTeamHierarchy() {
        List<Team> all = teamRepository.findAll();
        return all.stream().collect(Collectors.groupingBy(t -> Optional.ofNullable(t.getParentId()).orElse(0L), HashMap::new, Collectors.toList()));
    }

    private Set<Long> collectTeamAndDescendantIds(Team team, Map<Long, List<Team>> hierarchy) {
        Set<Long> result = new HashSet<>();
        ArrayDeque<Team> queue = new ArrayDeque<>();
        queue.add(team);

        while (!queue.isEmpty()) {
            Team current = queue.removeFirst();
            Long currentId = current.getId();
            if (currentId == null) {
                continue;
            }
            if (result.add(currentId)) {
                List<Team> children = hierarchy.getOrDefault(currentId, Collections.emptyList());
                queue.addAll(children);
            }
        }
        return result;
    }

    private record TeamStats(
        int score,
        int leaguePoints,
        List<PullRequestInfoDTO> reviewedPullRequests,
        int numberOfReviewedPRs,
        int numberOfApprovals,
        int numberOfChangeRequests,
        int numberOfComments,
        int numberOfUnknowns,
        int numberOfCodeComments
    ) {
    }
}