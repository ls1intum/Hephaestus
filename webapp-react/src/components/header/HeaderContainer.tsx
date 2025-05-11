import { useAuth } from "@/lib/auth/AuthContext";
import environment from "@/environment";
import Header from "./Header";

export default function HeaderContainer() {
  const { isAuthenticated, isLoading, username, userProfile, login, logout, hasRole } = useAuth();
  return (
    <Header 
      version={environment.version}
      isAuthenticated={isAuthenticated}
      isLoading={isLoading}
      name={userProfile && `${userProfile.firstName} ${userProfile.lastName}`}
      username={username}
      showAdmin={hasRole('admin')}
      showMentor={hasRole('mentor_access')}
      onLogin={login}
      onLogout={logout}
    />
  );
}