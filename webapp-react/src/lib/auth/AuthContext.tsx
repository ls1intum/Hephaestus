import { createContext, useContext, useEffect, useState, useCallback, useRef } from 'react';
import type { ReactNode } from 'react';
import keycloakService from './keycloak';
import type { UserProfile } from './keycloak';

// Create a global state to prevent multiple initializations across remounts
// This helps with React StrictMode double-rendering and hot module reloading
const globalState = {
  initialized: false,
  authCallbackProcessed: false
};

interface AuthContextType {
  isAuthenticated: boolean;
  isLoading: boolean;
  username: string | undefined;
  userRoles: string[];
  userProfile: UserProfile | undefined;
  login: () => Promise<void>;
  logout: () => Promise<void>;
  hasRole: (role: string) => boolean;
  checkAuthState: () => void;
  isCurrentUser: (login?: string) => boolean;
  getUserId: () => string | undefined;
  getUserGithubId: () => string | undefined;
  getUserGithubProfilePictureUrl: () => string;
  getUserGithubProfileUrl: () => string;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}

interface AuthProviderProps {
  children: ReactNode;
}

export function AuthProvider({ children }: AuthProviderProps) {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [username, setUsername] = useState<string | undefined>(undefined);
  const [userRoles, setUserRoles] = useState<string[]>([]);
  const [userProfile, setUserProfile] = useState<UserProfile | undefined>(undefined);
  const initRef = useRef(globalState.initialized);
  const authCallbackRef = useRef(globalState.authCallbackProcessed);
  
  // Function to check auth state and update context
  const checkAuthState = useCallback(() => {
    if (isLoading) return; // Don't check while still loading
    
    console.log('Checking auth state...');
    const authenticated = keycloakService.isAuthenticated();
    console.log('Current auth state from Keycloak:', authenticated);
    
    setIsAuthenticated(authenticated);
    
    if (authenticated) {
      const userName = keycloakService.getUsername();
      const roles = keycloakService.getUserRoles();
      const profile = keycloakService.getUserProfile();
      
      console.log('Setting username:', userName);
      console.log('Setting roles:', roles);
      
      setUsername(userName);
      setUserRoles(roles);
      setUserProfile(profile);
    } else {
      setUsername(undefined);
      setUserRoles([]);
      setUserProfile(undefined);
    }
  }, [isLoading]);

  // Clean the URL from authentication parameters
  // This is crucial to prevent infinite loops with router navigation
  const cleanUrlFromAuthParams = useCallback(() => {
    if (window.location.hash && 
       (window.location.hash.includes('state=') || 
        window.location.hash.includes('session_state=') || 
        window.location.hash.includes('code='))
    ) {
      // Get the base route without the hash and authentication parameters
      const baseUrl = window.location.pathname;
      console.log('Cleaning URL from auth params, redirecting to:', baseUrl);
      
      // Use history API to replace the current URL without auth parameters
      if (window.history && window.history.replaceState) {
        window.history.replaceState(null, '', baseUrl);
        return true;
      }
    }
    return false;
  }, []);

  useEffect(() => {
    // Skip initialization if already done (prevents duplicate init in StrictMode)
    if (initRef.current) {
      console.log('AuthProvider: Already initialized, skipping');
      setIsLoading(false);
      return;
    }

    const initKeycloak = async () => {
      try {
        // First, clean URL from any potential auth parameters to prevent loops
        cleanUrlFromAuthParams();
        
        console.log('AuthProvider: Initializing Keycloak...');
        // Initialize Keycloak
        const authenticated = await keycloakService.init();
        console.log('AuthProvider: Keycloak init result:', authenticated);
        
        if (authenticated) {
          const userName = keycloakService.getUsername();
          const roles = keycloakService.getUserRoles();
          const profile = keycloakService.getUserProfile();
          
          console.log('AuthProvider: Setting authenticated user:', userName);
          setUsername(userName);
          setUserRoles(roles);
          setUserProfile(profile);
        }
        
        setIsAuthenticated(authenticated);
        
        // Mark as initialized both locally and globally
        initRef.current = true;
        globalState.initialized = true;
      } catch (error) {
        console.error('AuthProvider: Failed to initialize authentication', error);
      } finally {
        console.log('AuthProvider: Finished initialization, setting isLoading to false');
        setIsLoading(false);
      }
    };

    initKeycloak();
  }, [cleanUrlFromAuthParams]);

  // Add an effect to handle Keycloak token changes - run only once after initialization
  useEffect(() => {
    if (!initRef.current || isLoading) return;
    
    console.log('AuthProvider: Setting up token monitoring');
    
    const tokenCheck = setInterval(() => {
      if (keycloakService.isAuthenticated() !== isAuthenticated) {
        console.log('AuthProvider: Auth state changed, updating...');
        checkAuthState();
      }
    }, 3000);
    
    return () => clearInterval(tokenCheck);
  }, [isAuthenticated, checkAuthState, isLoading]);

  // Handle Keycloak callback separately with protection against loops
  useEffect(() => {
    // Skip if still loading or already processed
    if (isLoading || authCallbackRef.current) return;
    
    // Check for authentication callback parameters in URL
    const hasAuthParams = 
      window.location.hash?.includes('state=') || 
      window.location.search?.includes('code=');
    
    if (hasAuthParams && !authCallbackRef.current) {
      console.log('AuthProvider: Detected auth callback, processing once');
      
      // Mark as processed both locally and globally
      authCallbackRef.current = true;
      globalState.authCallbackProcessed = true;
      
      // Clean the URL from auth parameters
      cleanUrlFromAuthParams();
      
      // Check auth state once after cleanup
      setTimeout(checkAuthState, 300);
    }
  }, [checkAuthState, isLoading, cleanUrlFromAuthParams]);

  const login = async () => {
    console.log('AuthProvider: Login requested');
    await keycloakService.login();
  };

  const logout = async () => {
    console.log('AuthProvider: Logout requested');
    // Reset our initialization state on logout
    initRef.current = false;
    globalState.initialized = false;
    authCallbackRef.current = false;
    globalState.authCallbackProcessed = false;
    
    await keycloakService.logout();
  };

  const hasRole = (role: string) => {
    return keycloakService.hasRole(role);
  };

  const isCurrentUser = (login?: string) => {
    return keycloakService.isCurrentUser(login);
  };

  const getUserId = () => {
    return keycloakService.getUserId();
  };

  const getUserGithubId = () => {
    return keycloakService.getUserGithubId();
  };

  const getUserGithubProfilePictureUrl = () => {
    return keycloakService.getUserGithubProfilePictureUrl();
  };

  const getUserGithubProfileUrl = () => {
    return keycloakService.getUserGithubProfileUrl();
  };

  const value = {
    isAuthenticated,
    isLoading,
    username,
    userRoles,
    userProfile,
    login,
    logout,
    hasRole,
    checkAuthState,
    isCurrentUser,
    getUserId,
    getUserGithubId,
    getUserGithubProfilePictureUrl,
    getUserGithubProfileUrl
  };

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
}