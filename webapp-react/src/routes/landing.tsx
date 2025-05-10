import { createFileRoute } from '@tanstack/react-router'
import { LandingPage } from '../features/landing/LandingPage'
import { useAuth } from '../lib/auth/AuthContext'

export const Route = createFileRoute('/landing')({
  component: LandingRoute,
})

function LandingRoute() {
  const { login } = useAuth();
  
  return <LandingPage onSignIn={() => login()} />;
}
