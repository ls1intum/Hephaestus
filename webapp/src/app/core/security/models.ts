export interface User {
  id: string;
  email: string;
  name: string;
  anonymous: boolean;
  bearer: string;
  roles: string[];
}

export const ANONYMOUS_USER: User = {
  id: '',
  email: 'nomail',
  name: 'no user',
  anonymous: true,
  bearer: '',
  roles: []
};

export interface SecurityState {
  loaded: boolean;
  user: User | undefined;
}
