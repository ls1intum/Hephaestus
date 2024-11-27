import { Component, computed, effect, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { BrnSelectModule } from '@spartan-ng/ui-select-brain';
import { HlmSelectModule } from '@spartan-ng/ui-select-helm';
import { HlmLabelModule } from '@spartan-ng/ui-label-helm';

interface SelectOption {
  id: number;
  value: string;
  label: string;
}

@Component({
  selector: 'app-leaderboard-filter-team',
  standalone: true,
  imports: [BrnSelectModule, HlmSelectModule, HlmLabelModule, FormsModule],
  templateUrl: './team.component.html'
})
export class LeaderboardFilterTeamComponent {
  teams = input.required<string[]>();
  value = signal<string>('');

  placeholder = computed(() => {
    return this.teams().find((option) => option === this.value()) ?? 'All';
  });

  options = computed(() => {
    const options: SelectOption[] = this.teams().map((name, index) => {
      return {
        id: index + 1,
        value: name,
        label: name
      };
    });
    options.unshift({
      id: 0,
      value: 'all',
      label: 'All'
    });
    return options;
  });

  constructor(private router: Router) {
    this.value.set(this.router.parseUrl(this.router.url).queryParams['team'] ?? 'all');

    effect(() => {
      if (!this.value() || this.value() === '') return;
      const queryParams = this.router.parseUrl(this.router.url).queryParams;
      if (this.value() === 'all') {
        delete queryParams['team'];
      } else {
        queryParams['team'] = this.value();
      }
      this.router.navigate([], {
        queryParams
      });
    });
  }
}
