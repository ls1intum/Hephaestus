import { Injectable } from '@angular/core';
import { environment } from 'environments/environment';

@Injectable({
  providedIn: 'root'
})
export class AnalyticsService {
  private scriptLoaded = false;

  initialize(): void {
    if (environment.umami.enabled) {
      this.loadUmamiScript();
    }
  }

  private loadUmamiScript(): void {
    if (this.scriptLoaded) {
      return;
    }

    const script = document.createElement('script');
    script.defer = true;
    script.src = environment.umami.scriptUrl;
    script.setAttribute('data-website-id', environment.umami.websiteId);
    script.setAttribute('data-domains', environment.umami.domains);

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
