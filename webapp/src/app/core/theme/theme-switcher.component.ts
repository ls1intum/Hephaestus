import { Component, inject } from '@angular/core';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { lucideSun, lucideMoon } from '@ng-icons/lucide';
import { AppTheme, ThemeSwitcherService } from './theme-switcher.service';
import { animate, state, style, transition, trigger } from '@angular/animations';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';

@Component({
  selector: 'app-theme-switcher',
  imports: [HlmButtonModule, NgIconComponent],
  templateUrl: './theme-switcher.component.html',
  providers: [provideIcons({ lucideSun, lucideMoon })],
  animations: [
    trigger('iconTrigger', [
      state('*', style({ transform: 'rotate(0deg)' })),
      transition('light => dark', animate('0.25s ease-out', style({ transform: 'rotate(90deg)' }))),
      transition('dark => light', animate('0.25s ease-out', style({ transform: 'rotate(360deg)' })))
    ])
  ]
})
export class ThemeSwitcherComponent {
  themeSwitcherService = inject(ThemeSwitcherService);

  toggleTheme() {
    if (this.themeSwitcherService.currentTheme() === AppTheme.DARK) {
      this.themeSwitcherService.setLightTheme();
    } else {
      this.themeSwitcherService.setDarkTheme();
    }
  }
}
