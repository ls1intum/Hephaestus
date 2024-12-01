import { Component, inject } from '@angular/core';
import { EnvironmentService } from '@app/environment.service';

@Component({
  selector: 'app-imprint',
  standalone: true,
  template: `
    <div class="container prose dark:prose-invert">
      <h1>Imprint</h1>
      <div [innerHTML]="imprintHtml"></div>
    </div>
  `
})
export class ImprintComponent {
  private environmentService = inject(EnvironmentService);
  imprintHtml = this.environmentService.env.legal.imprintHtml;
}
