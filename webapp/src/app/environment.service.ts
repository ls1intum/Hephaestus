import { Injectable, isDevMode } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { lastValueFrom } from 'rxjs';

export interface Environment {
  version: string;
  clientUrl: string;
  serverUrl: string;
  sentry: {
    dsn: string;
    environment: string;
  };
  keycloak: {
    url: string;
    realm: string;
    clientId: string;
    skipLoginPage: boolean;
  };
  umami: {
    enabled: boolean;
    scriptUrl: string;
    websiteId: string;
    domains: string;
  };
  legal: {
    imprintHtml: string;
    privacyHtml: string;
  };
}

@Injectable({
  providedIn: 'root'
})
export class EnvironmentService {
  private environment!: Environment;

  constructor(private http: HttpClient) {}

  loadEnv() {
    if (isDevMode()) {
      // eslint-disable-next-line @typescript-eslint/no-require-imports
      const environment = require('../../public/environment.json') as Environment;
      this.environment = environment;
      return Promise.resolve();
    } else {
      return lastValueFrom(this.http.get<Environment>('/environment.json'))
        .then((environment) => {
          this.environment = environment;
          console.log('Environment loaded successfully.', this.environment);
        })
        .catch((error) => {
          console.error('Error loading environment.', error);
        });
    }
  }

  get env(): Environment {
    return this.environment;
  }
}
