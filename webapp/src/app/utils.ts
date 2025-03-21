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

// Mapping states to emojis and Tailwind styles
export const stateConfig = {
  GOOD_PRACTICE: { emoji: '🚀', text: 'Good Practice' },
  FIXED: { emoji: '✅', text: 'Fixed' },
  CRITICAL_ISSUE: { emoji: '🔥', text: 'Critical Issue' },
  NORMAL_ISSUE: { emoji: '⚠️', text: 'Normal Issue' },
  MINOR_ISSUE: { emoji: '🟡', text: 'Minor Issue' },
  WONT_FIX: { emoji: '🚫', text: "Won't Fix" }
};
