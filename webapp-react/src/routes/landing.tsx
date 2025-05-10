import { createFileRoute } from '@tanstack/react-router'
import { LandingContainer } from '@/features/info/landing/LandingContainer'

export const Route = createFileRoute('/landing')({
  component: LandingContainer,
})
