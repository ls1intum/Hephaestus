import { createContext, useContext, useEffect, useState, useCallback } from 'react';
import type { ReactNode } from 'react';
import keycloakService from './keycloak';
import { setupAuthFunction, setupTokenRefresh } from './hey-api-auth';

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

  // Function to check auth state and update context
  const checkAuthState = useCallback(() => {
    console.log('Checking auth state...');
    const authenticated = keycloakService.isAuthenticated();
    console.log('Current auth state from Keycloak:', authenticated);
    
    setIsAuthenticated(authenticated);
    
    if (authenticated) {
      const userName = keycloakService.getUsername();
      const roles = keycloakService.getUserRoles();
      
      console.log('Setting username:', userName);
      console.log('Setting roles:', roles);
      
      setUsername(userName);
      setUserRoles(roles);
    } else {
      setUsername(undefined);
      setUserRoles([]);
    }
  }, []);

  useEffect(() => {
    const initKeycloak = async () => {
      try {
        console.log('AuthProvider: Setting up API auth...');
        // Set up API client authentication
        setupAuthFunction();
        setupTokenRefresh();
        
        console.log('AuthProvider: Initializing Keycloak...');
        // Initialize Keycloak
        const authenticated = await keycloakService.init();
        console.log('AuthProvider: Keycloak init result:', authenticated);
        
        setIsAuthenticated(authenticated);
        
        if (authenticated) {
          const userName = keycloakService.getUsername();
          const roles = keycloakService.getUserRoles();
          
          console.log('AuthProvider: Setting authenticated user:', userName);
          setUsername(userName);
          setUserRoles(roles);
        }
      } catch (error) {
        console.error('AuthProvider: Failed to initialize authentication', error);
      } finally {
        console.log('AuthProvider: Finished initialization, setting isLoading to false');
        setIsLoading(false);
      }
    };

    initKeycloak();
  }, []);

  // Add an effect to handle Keycloak token changes
  useEffect(() => {
    console.log('AuthProvider: Setting up token monitoring');
    
    const tokenCheck = setInterval(() => {
      if (keycloakService.isAuthenticated() !== isAuthenticated) {
        console.log('AuthProvider: Auth state changed, updating...');
        checkAuthState();
      }
    }, 3000);

    // Handle possible callback from Keycloak login
    if (window.location.hash?.includes('state=')) {
      console.log('AuthProvider: Detected possible auth callback, checking state');
      setTimeout(checkAuthState, 500);
    }
    
    return () => clearInterval(tokenCheck);
  }, [isAuthenticated, checkAuthState]);

  const login = async () => {
    console.log('AuthProvider: Login requested');
    await keycloakService.login();
  };

  const logout = async () => {
    console.log('AuthProvider: Logout requested');
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
    username, 
    userRoles: userRoles?.length
  });

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
}