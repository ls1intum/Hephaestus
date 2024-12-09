import { ErrorHandler, inject, Injectable } from '@angular/core';
import { EnvironmentService } from '@app/environment.service';
import * as Sentry from '@sentry/angular';

@Injectable({ providedIn: 'root' })
export class SentryErrorHandler extends ErrorHandler {
  private environmentService = inject(EnvironmentService);

  constructor() {
    super();
  }

  /**
   * Initialize Sentry with environment.
   */
  async init() {
    const env = this.environmentService.env;
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
  }

  /**
   * Send an HttpError to Sentry. Only if it's not in the range 400-499.
   * @param error
   */
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  override handleError(error: any): void {
    if (error && error.name === 'HttpErrorResponse' && error.status < 500 && error.status >= 400) {
      super.handleError(error);
      return;
    }
    if (this.environmentService.env.sentry.environment !== 'local') {
      const exception = error.error || error.message || error.originalError || error;
      Sentry.captureException(exception);
    }
    super.handleError(error);
  }
}
