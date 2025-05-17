import { createFileRoute } from '@tanstack/react-router'
import { useAuth } from '@/integrations/auth/AuthContext';
import { LandingPage } from '@/components/info/LandingPage'

export const Route = createFileRoute('/landing')({
  component: LandingContainer,
})

export function LandingContainer() {
  const { login, isAuthenticated } = useAuth();
  return <LandingPage onSignIn={() => login()} isSignedIn={isAuthenticated} />
}