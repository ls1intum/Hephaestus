export interface User {
  id: string;
  email: string;
  name: string;
  username: string;
  anonymous: boolean;
  bearer: string;
  roles: string[];
}

export const ANONYMOUS_USER: User = {
  id: '',
  email: 'nomail',
  name: 'no user',
  username: 'anon',
  anonymous: true,
  bearer: '',
  roles: []
};

export interface SecurityState {
  loaded: boolean;
  user: User | undefined;
}
