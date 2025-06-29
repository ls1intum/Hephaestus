package de.tum.in.www1.hephaestus.mentor;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.NonNull;
import java.util.List;

/**
 * DTO for grouped chat threads.
 * Used for organizing threads by time periods (today, yesterday, etc.).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatThreadGroupDTO {
    
    /**
     * Group name (e.g., "Today", "Yesterday", "Last 7 Days", "Last 30 Days")
     */
    @NonNull
    private String groupName;
    
    /**
     * List of thread summaries in this group
     */
    @NonNull
    private List<ChatThreadSummaryDTO> threads;
}
