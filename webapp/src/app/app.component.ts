import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { HeaderComponent } from '@app/core/header/header.component';
import { FooterComponent } from './core/footer/footer.component';
import { SentryErrorHandler } from './core/sentry/sentry.error-handler';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, HeaderComponent, FooterComponent],
  templateUrl: './app.component.html'
})
export class AppComponent {
  title = 'Hephaestus';
  sentry = inject(SentryErrorHandler);

  constructor() {
    this.sentry.init();
  }
}
