import { createContext, useContext, useEffect, useState, useCallback, useRef } from 'react';
import type { ReactNode } from 'react';
import keycloakService from './keycloak';
import { setupAuthFunction, setupTokenRefresh, resetAuthConfig } from './hey-api-auth';

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
  login: () => Promise<void>;
  logout: () => Promise<void>;
  hasRole: (role: string) => boolean;
  checkAuthState: () => void;
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
      
      setUsername(userName);
      setUserRoles(roles);
    } else {
      setUsername(undefined);
      setUserRoles([]);
    }
  }, [isLoading]);

  useEffect(() => {
    // Skip initialization if already done (prevents duplicate init in StrictMode)
    if (initRef.current) {
      console.log('AuthProvider: Already initialized, skipping');
      setIsLoading(false);
      return;
    }

    const initKeycloak = async () => {
      try {
        console.log('AuthProvider: Setting up API auth...');
        // Set up API client authentication first
        setupAuthFunction();
        
        console.log('AuthProvider: Initializing Keycloak...');
        // Initialize Keycloak
        const authenticated = await keycloakService.init();
        console.log('AuthProvider: Keycloak init result:', authenticated);
        
        // Only setup token refresh after successful initialization
        if (authenticated) {
          setupTokenRefresh();
          
          const userName = keycloakService.getUsername();
          const roles = keycloakService.getUserRoles();
          
          console.log('AuthProvider: Setting authenticated user:', userName);
          setUsername(userName);
          setUserRoles(roles);
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
  }, []);

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
    
    // Handle possible callback from Keycloak login only once
    if (window.location.hash?.includes('state=') && !authCallbackRef.current) {
      console.log('AuthProvider: Detected auth callback, processing once');
      
      // Mark as processed both locally and globally
      authCallbackRef.current = true;
      globalState.authCallbackProcessed = true;
      
      // Replace the URL to remove the fragment to prevent further callback processing
      if (window.history && window.history.replaceState) {
        const cleanUrl = window.location.href.split('#')[0];
        window.history.replaceState(null, '', cleanUrl);
      }
      
      // Check auth state once
      setTimeout(checkAuthState, 500);
    }
  }, [checkAuthState, isLoading]);

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
    
    // Reset API auth configuration
    resetAuthConfig();
    
    await keycloakService.logout();
  };

  const hasRole = (role: string) => {
    return keycloakService.hasRole(role);
  };

  const value = {
    isAuthenticated,
    isLoading,
    username,
    userRoles,
    login,
    logout,
    hasRole,
    checkAuthState
  };

  console.log('AuthProvider: Current state:', { 
    isAuthenticated, 
    isLoading, 
    userRoles: userRoles.length
  });

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
}