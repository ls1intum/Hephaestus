package de.tum.in.www1.hephaestus.leaderboard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LeaguePointsCalculationService {
    private final Logger logger = LoggerFactory.getLogger(LeaguePointsCalculationService.class);

    public int calculateNewPoints(int oldPoints, LeaderboardEntryDTO entry) {
        // Base decay - the higher the points, the more you lose
        int decay = calculateDecay(oldPoints);
        
        // Activity bonus based on leaderboard score
        int activityBonus = calculateActivityBonus(entry.score());
        
        // Additional bonus for review diversity
        int diversityBonus = calculateDiversityBonus(
            entry.numberOfApprovals(),
            entry.numberOfChangeRequests(),
            entry.numberOfComments(),
            entry.numberOfCodeComments()
        );
        
        // Calculate final point change
        int pointChange = activityBonus + diversityBonus - decay;
        
        // Apply minimum change to prevent extreme swings
        int newPoints = Math.max(0, oldPoints + pointChange);
        
        logger.info("Points calculation: old={}, decay={}, activity={}, diversity={}, new={}", 
            oldPoints, decay, activityBonus, diversityBonus, newPoints);
            
        return newPoints;
    }
    
    private int calculateDecay(int currentPoints) {
        // 5% decay of current points, minimum 10 points if they have any points
        return currentPoints > 0 ? Math.max(10, (int)(currentPoints * 0.05)) : 0;
    }
    
    private int calculateActivityBonus(int score) {
        // Convert leaderboard score directly to points with diminishing returns
        return (int)(Math.sqrt(score) * 10);
    }
    
    private int calculateDiversityBonus(int approvals, int changes, int comments, int codeComments) {
        // Reward diverse review activity
        int totalInteractions = approvals + changes + comments;
        if (totalInteractions == 0) return 0;
        
        // Calculate how evenly distributed the review types are
        double distribution = (double)(Math.min(approvals, Math.min(changes, comments))) / Math.max(approvals, Math.max(changes, comments));
        
        // Bonus for code comments
        int codeCommentBonus = Math.min(50, codeComments * 2);
        
        return (int)(distribution * 30) + codeCommentBonus;
    }
}
