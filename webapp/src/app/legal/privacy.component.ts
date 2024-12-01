import { Component, inject } from '@angular/core';
import { EnvironmentService } from '@app/environment.service';

@Component({
  selector: 'app-privacy',
  standalone: true,
  template: `
    <div class="container prose dark:prose-invert">
      <h1>Privacy</h1>
      <div [innerHTML]="imprintHtml"></div>
    </div>
  `
})
export class PrivacyComponent {
  private environmentService = inject(EnvironmentService);
  imprintHtml = this.environmentService.env.legal.privacyHtml;
}
