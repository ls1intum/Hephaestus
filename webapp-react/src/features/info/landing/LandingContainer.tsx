import { useAuth } from '@/lib/auth/AuthContext';
import { LandingPage } from './LandingPage';

export function LandingContainer() {
  const { login } = useAuth();
  return <LandingPage onSignIn={() => login()} />
}