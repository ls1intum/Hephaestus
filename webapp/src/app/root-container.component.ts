import { Component, inject } from '@angular/core';
import { SecurityStore } from '@app/core/security/security-store.service';
import { HomeComponent } from '@app/home/home.component';
import { LandingComponent } from '@app/landing/landing.component';

@Component({
  selector: 'app-root-container',
  imports: [HomeComponent, LandingComponent],
  template: `
    @if (loaded()) {
      @if (signedIn()) {
        <app-home />
      } @else {
        <app-landing />
      }
    }
  `
})
export class RootContainerComponent {
  securityStore = inject(SecurityStore);
  loaded = this.securityStore.loaded;
  signedIn = this.securityStore.signedIn;
}
