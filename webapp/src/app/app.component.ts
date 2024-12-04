import { Component, inject, isDevMode } from '@angular/core';
import { AngularQueryDevtools } from '@tanstack/angular-query-devtools-experimental';
import { RouterOutlet } from '@angular/router';
import { HeaderComponent } from '@app/core/header/header.component';
import { FooterComponent } from './core/footer/footer.component';
import { SentryService } from './core/sentry/sentry.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, AngularQueryDevtools, HeaderComponent, FooterComponent],
  templateUrl: './app.component.html'
})
export class AppComponent {
  title = 'Hephaestus';
  sentry = inject(SentryService);

  isDevMode() {
    return isDevMode();
  }
}
