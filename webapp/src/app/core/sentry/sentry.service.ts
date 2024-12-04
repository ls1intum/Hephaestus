import { Injectable } from '@angular/core';
import { environment } from 'environments/environment';
import * as Sentry from "@sentry/angular";

@Injectable({ providedIn: 'root' })
export class SentryService {
  private environment = environment;

  constructor() {
    this.init();
  }

  /**
   * Initialize Sentry with environment.
   */
  async init() {
    console.log('Initializing Sentry');
    const env = this.environment;
    if (!env || !env.version || !env.sentry?.dsn) {
      return;
    }

    Sentry.init({
      dsn: env.sentry.dsn,
      release: env.version,
      environment: env.sentry.environment,
      integrations: [Sentry.browserTracingIntegration(), Sentry.replayIntegration()],
      tracesSampleRate: env.sentry.environment !== 'prod' ? 1.0 : 0.2
    });
  }
}
