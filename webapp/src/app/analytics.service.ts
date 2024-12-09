import { inject, Injectable } from '@angular/core';
import { EnvironmentService } from './environment.service';

@Injectable({
  providedIn: 'root'
})
export class AnalyticsService {
  private environmentService = inject(EnvironmentService);

  private scriptLoaded = false;

  initialize(): void {
    if (this.environmentService.env.umami.enabled) {
      this.loadUmamiScript();
    }
  }

  private loadUmamiScript(): void {
    if (this.scriptLoaded) {
      return;
    }

    const script = document.createElement('script');
    script.defer = true;
    script.src = this.environmentService.env.umami.scriptUrl;
    script.setAttribute('data-website-id', this.environmentService.env.umami.websiteId);
    script.setAttribute('data-domains', this.environmentService.env.umami.domains);

    script.onload = () => {
      console.log('Umami analytics script loaded successfully.');
    };

    script.onerror = () => {
      console.error('Failed to load Umami analytics script.');
    };

    document.head.appendChild(script);
    this.scriptLoaded = true;
  }
}
