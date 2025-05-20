import { toast } from 'ngx-sonner';
import { PullRequestBadPractice } from '@app/core/modules/openapi';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function groupBy<T, K extends keyof any>(arr: T[], key: (i: T) => K) {
  return arr.reduce(
    (groups, item) => {
      (groups[key(item)] ||= []).push(item);
      return groups;
    },
    {} as Record<K, T[]>
  );
}

export function getLeagueFromPoints(points: number) {
  return Leagues.find((league) => points >= league.minPoints && points < league.maxPoints);
}

export const Leagues = [
  {
    name: 'Bronze',
    minPoints: 0,
    maxPoints: 1250
  },
  {
    name: 'Silver',
    minPoints: 1250,
    maxPoints: 1500
  },
  {
    name: 'Gold',
    minPoints: 1500,
    maxPoints: 1750
  },
  {
    name: 'Diamond',
    minPoints: 1750,
    maxPoints: 2000
  },
  {
    name: 'Master',
    minPoints: 2000,
    maxPoints: Infinity
  }
];

export function formatTitle(title: string): string {
  return title.replace(/`([^`]+)`/g, '<code class="textCode">$1</code>');
}

// Mapping states to icons and text
export const stateConfig = {
  GOOD_PRACTICE: { icon: 'lucideRocket', text: 'Good Practice', color: 'text-github-success-foreground' },
  FIXED: { icon: 'lucideCheck', text: 'Fixed', color: 'text-github-done-foreground' },
  CRITICAL_ISSUE: { icon: 'lucideFlame', text: 'Critical Issue', color: 'text-github-danger-foreground' },
  NORMAL_ISSUE: { icon: 'lucideTriangleAlert', text: 'Normal Issue', color: 'text-github-severe-foreground' },
  MINOR_ISSUE: { icon: 'lucideBug', text: 'Minor Issue', color: 'text-github-attention-foreground' },
  WONT_FIX: { icon: 'lucideBan', text: "Won't Fix", color: 'text-github-muted-foreground' },
  WRONG: { icon: 'lucideOctagonX', text: 'Wrong', color: 'text-github-muted-foreground' }
};

export const doubleDetectionString = 'This pull request has not changed since the last detection. Try changing status or description, then run the detection again.';

export const serverErrorString = 'We encountered an error while trying to detect bad practices. Please try again later.';

export function showToast(message: string) {
  toast('Something went wrong...', {
    description: message
  });
}

export function filterGoodAndBadPractices(allBadPractices: PullRequestBadPractice[]): {
  goodPractices: PullRequestBadPractice[];
  badPractices: PullRequestBadPractice[];
  resolvedPractices: PullRequestBadPractice[];
} {
  const goodPractices = allBadPractices.filter((badPractice) => badPractice.state === PullRequestBadPractice.StateEnum.GoodPractice);
  const badPractices = allBadPractices.filter(
    (badPractice) =>
      badPractice.state === PullRequestBadPractice.StateEnum.CriticalIssue ||
      badPractice.state === PullRequestBadPractice.StateEnum.NormalIssue ||
      badPractice.state === PullRequestBadPractice.StateEnum.MinorIssue
  );
  const resolvedPractices = allBadPractices.filter(
    (badPractice) =>
      badPractice.state === PullRequestBadPractice.StateEnum.Fixed ||
      badPractice.state === PullRequestBadPractice.StateEnum.WontFix ||
      badPractice.state === PullRequestBadPractice.StateEnum.Wrong
  );

  return { goodPractices, badPractices, resolvedPractices };
}
