import { format } from 'date-fns';

// Repository image mapping
export const REPOSITORY_IMAGES: Record<string, string> = {
  'Hephaestus': 'https://github.com/ls1intum/Hephaestus/raw/refs/heads/develop/docs/images/hammer_bg.svg',
  'Artemis': 'https://artemis.tum.de/public/images/logo.png',
  'Athena': 'https://raw.githubusercontent.com/ls1intum/Athena/develop/playground/public/logo.png'
};

// Default organization avatar
export const DEFAULT_ORG_AVATAR = 'https://avatars.githubusercontent.com/u/11064260?v=4';

// Get image URL for a repository
export function getRepositoryImage(name: string | undefined): string {
  if (!name) return DEFAULT_ORG_AVATAR;
  
  const parts = name.split('/');
  if (parts.length < 2) return DEFAULT_ORG_AVATAR;
  
  return REPOSITORY_IMAGES[parts[1]] || DEFAULT_ORG_AVATAR;
}

// Format the first contribution date
export function formatFirstContribution(date: string | undefined): string | null {
  if (!date) return null;
  
  try {
    return format(new Date(date), "do 'of' MMMM yyyy");
  } catch (e) {
    console.error('Error formatting date:', e);
    return null;
  }
}

// Types
export interface UserInfo {
  login: string;
  name: string;
  avatarUrl: string;
  htmlUrl: string;
}

export interface RepositoryInfo {
  nameWithOwner: string;
  htmlUrl: string;
}