import { useAuth } from '@/lib/auth/AuthContext';
import { LandingPage } from './LandingPage';

export function LandingPageContainer() {
  const { login } = useAuth();
  return <LandingPage onSignIn={() => login()} />
}