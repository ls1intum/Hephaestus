import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { lastValueFrom } from 'rxjs';

export interface Environment {
  clientUrl: string;
  serverUrl: string;
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
  private config!: Environment;

  constructor(private http: HttpClient) {}

  loadEnv() {
    return lastValueFrom(this.http.get<Environment>('/environment.json'))
      .then((config) => {
        this.config = config;
        console.log('Config loaded successfully.', this.config);
      })
      .catch((error) => {
        console.error('Error loading config.', error);
      });
  }

  get env(): Environment {
    return this.config;
  }
}
