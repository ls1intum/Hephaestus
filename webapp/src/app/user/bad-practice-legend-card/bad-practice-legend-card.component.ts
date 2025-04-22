import { Component, computed } from '@angular/core';
import { stateConfig } from '@app/utils';
import { HlmCardImports } from '@spartan-ng/ui-card-helm';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideRocket, lucideCheck, lucideFlame, lucideTriangleAlert, lucideOctagonX, lucideBan, lucideCircleHelp, lucideBug } from '@ng-icons/lucide';

@Component({
  selector: 'app-bad-practice-legend-card',
  imports: [HlmCardImports, NgIcon],
  templateUrl: './bad-practice-legend-card.component.html',
  providers: [provideIcons({ lucideRocket, lucideCheck, lucideFlame, lucideTriangleAlert, lucideBug, lucideOctagonX, lucideBan, lucideCircleHelp })]
})
export class BadPracticeLegendCardComponent {
  stateList = computed(() => {
    return Object.values(stateConfig);
  });
}
