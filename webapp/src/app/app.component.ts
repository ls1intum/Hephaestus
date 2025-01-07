import { Component, inject, isDevMode } from '@angular/core';
import { AngularQueryDevtools } from '@tanstack/angular-query-devtools-experimental';
import { RouterOutlet } from '@angular/router';
import { HeaderComponent } from '@app/core/header/header.component';
import { FooterComponent } from './core/footer/footer.component';
import { SentryErrorHandler } from './core/sentry/sentry.error-handler';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, AngularQueryDevtools, HeaderComponent, FooterComponent],
  templateUrl: './app.component.html'
})
export class AppComponent {
  title = 'Hephaestus';
  sentry = inject(SentryErrorHandler);

  isDevMode() {
    return isDevMode();
  }

  constructor() {
    this.sentry.init();
  }
}
