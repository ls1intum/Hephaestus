import { type ClassValue, clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

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
