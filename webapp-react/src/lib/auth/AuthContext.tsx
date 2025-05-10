import { createContext, useContext, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import keycloakService from './keycloak';

interface AuthContextType {
  isAuthenticated: boolean;
  isLoading: boolean;
  username: string | undefined;
  userRoles: string[];
  login: () => Promise<void>;
  logout: () => Promise<void>;
  hasRole: (role: string) => boolean;
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

  useEffect(() => {
    const initKeycloak = async () => {
      try {
        const authenticated = await keycloakService.init();
        setIsAuthenticated(authenticated);
        
        if (authenticated) {
          setUsername(keycloakService.getUsername());
          setUserRoles(keycloakService.getUserRoles());
        }
      } catch (error) {
        console.error('Failed to initialize authentication', error);
      } finally {
        setIsLoading(false);
      }
    };

    initKeycloak();
  }, []);

  const login = async () => {
    await keycloakService.login();
    // The page will reload after successful login due to the redirect
  };

  const logout = async () => {
    await keycloakService.logout();
    // The page will reload after logout due to the redirect
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
  };

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
}