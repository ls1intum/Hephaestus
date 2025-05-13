import { createFileRoute } from '@tanstack/react-router'
import { useAuth } from '@/lib/auth/AuthContext';
import { LandingPage } from '@/features/info/landing/LandingPage'

export const Route = createFileRoute('/landing')({
  component: LandingContainer,
})

export function LandingContainer() {
  const { login, isAuthenticated } = useAuth();
  return <LandingPage onSignIn={() => login()} isSignedIn={isAuthenticated} />
}