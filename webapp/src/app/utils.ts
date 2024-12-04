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
  if (points < 1250) {
    return 'bronze';
  } else if (points < 1500) {
    return 'silver';
  } else if (points < 1750) {
    return 'gold';
  } else if (points < 2000) {
    return 'diamond';
  } else {
    return 'master';
  }
}
