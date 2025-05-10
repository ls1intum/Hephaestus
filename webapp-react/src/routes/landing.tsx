import { createFileRoute } from '@tanstack/react-router'
import { LandingPageContainer } from '@/features/landing/LandingPageContainer';

export const Route = createFileRoute('/landing')({
  component: LandingPageContainer,
})
