import { Component, computed } from '@angular/core';
import { stateConfig } from '@app/utils';
import { HlmCardImports } from '@spartan-ng/ui-card-helm';

@Component({
  selector: 'app-bad-practice-legend-card',
  imports: [HlmCardImports],
  templateUrl: './bad-practice-legend-card.component.html',
  styles: ``
})
export class BadPracticeLegendCardComponent {
  protected readonly stateConfig = stateConfig;

  stateList = computed(() => {
    return Object.values(stateConfig);
  });
}
