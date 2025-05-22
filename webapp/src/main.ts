import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';
import { environment } from 'environments/environment';
import posthog from 'posthog-js';

if (environment.posthog.projectApiKey) {
  posthog.init(environment.posthog.projectApiKey, {
    api_host: environment.posthog.apiHost,
    capture_pageview: false,
    capture_pageleave: true,
    cross_subdomain_cookie: false
  });
}

bootstrapApplication(AppComponent, appConfig).catch((err) => console.error(err));
