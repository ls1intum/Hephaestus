package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.SecurityUtils;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Mock security helper for chat integration tests.
 * Simulates authentication with the test user.
 */
@Component
@Profile("test")
public class MockSecurityHelper {
    
    private MockedStatic<SecurityUtils> mockedSecurityUtils;
    
    /**
     * Sets up authentication for the test user.
     */
    public void mockAuthentication(String userLogin) {
        if (mockedSecurityUtils != null) {
            mockedSecurityUtils.close();
        }
        
        mockedSecurityUtils = Mockito.mockStatic(SecurityUtils.class);
        mockedSecurityUtils.when(SecurityUtils::getCurrentUserLogin)
                          .thenReturn(Optional.of(userLogin));
    }
    
    /**
     * Clears authentication mock.
     */
    public void clearAuthentication() {
        if (mockedSecurityUtils != null) {
            mockedSecurityUtils.close();
            mockedSecurityUtils = null;
        }
    }
}
