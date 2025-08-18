import { createFileRoute } from '@tanstack/react-router';
import { TeamLeaderboardPage } from '@/components/team-leaderboard/TeamLeaderboardPage';

export const Route = createFileRoute('/_authenticated/team-leaderboard')({
  component: RouteComponent,
})

function RouteComponent() {
  return (
    <TeamLeaderboardPage />
  );
}
