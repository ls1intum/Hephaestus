import { Component, inject, OnInit } from '@angular/core';
import { RouterOutlet, Router, Event, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';
import { Observable } from 'rxjs';
import posthog from 'posthog-js';
import { HlmToasterComponent } from '@spartan-ng/ui-sonner-helm';
import { HeaderComponent } from '@app/core/header/header.component';
import { FooterComponent } from './core/footer/footer.component';
import { SentryErrorHandler } from './core/sentry/sentry.error-handler';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, HeaderComponent, FooterComponent, HlmToasterComponent],
  templateUrl: './app.component.html'
})
export class AppComponent implements OnInit {
  title = 'Hephaestus';
  sentry = inject(SentryErrorHandler);
  navigationEnd: Observable<NavigationEnd>;

  constructor(public router: Router) {
    this.sentry.init();
    this.navigationEnd = router.events.pipe(filter((event: Event) => event instanceof NavigationEnd)) as Observable<NavigationEnd>;
  }

  ngOnInit() {
    this.navigationEnd.subscribe(() => {
      posthog.capture('$pageview');
    });
  }
}
