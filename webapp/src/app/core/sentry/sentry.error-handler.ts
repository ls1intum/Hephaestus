import { ErrorHandler, Injectable } from '@angular/core';
import { environment } from 'environments/environment';
import * as Sentry from "@sentry/angular";

@Injectable({ providedIn: 'root' })
export class SentryErrorHandler extends ErrorHandler {
  private environment = environment;

  constructor() {
    super();
  }

  /**
   * Initialize Sentry with environment.
   */
  async init() {
    const env = this.environment;
    if (!env || !env.version || !env.sentry?.dsn) {
      return;
    }

    Sentry.init({
      dsn: env.sentry.dsn,
      release: env.version,
      environment: env.sentry.environment,
      integrations: [Sentry.browserTracingIntegration()],
      tracesSampleRate: env.sentry.environment !== 'prod' ? 1.0 : 0.2
    });

    console.log('Sentry initialized');
  }

  /**
   * Send an HttpError to Sentry. Only if it's not in the range 400-499.
   * @param error
   */
  override handleError(error: any): void {
      if (error && error.name === 'HttpErrorResponse' && error.status < 500 && error.status >= 400) {
          super.handleError(error);
          return;
      }
      if (this.environment.sentry.environment !== 'local') {
        const exception = error.error || error.message || error.originalError || error;
        Sentry.captureException(exception);
      }
      super.handleError(error);
  }
}
