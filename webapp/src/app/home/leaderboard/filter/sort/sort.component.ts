import { Component, computed, effect, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { BrnSelectModule } from '@spartan-ng/brain/select';
import { HlmSelectModule } from '@spartan-ng/ui-select-helm';
import { HlmLabelModule } from '@spartan-ng/ui-label-helm';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { lucideArrowDownUp } from '@ng-icons/lucide';

interface SelectOption {
  id: number;
  value: string;
  label: string;
}

@Component({
  selector: 'app-leaderboard-filter-sort',
  imports: [BrnSelectModule, HlmSelectModule, HlmLabelModule, FormsModule, NgIconComponent],
  templateUrl: './sort.component.html',
  providers: [provideIcons({ lucideArrowDownUp })],
  standalone: true
})
export class LeaderboardFilterSortComponent {
  value = signal<string>('');

  placeholder = computed(() => {
    return this.options().find((option) => option.value === this.value())?.label ?? 'Score';
  });

  options = computed(() => {
    return [
      { id: 0, value: 'score', label: 'Score' },
      { id: 1, value: 'league_points', label: 'League Points' }
    ] as SelectOption[];
  });

  constructor(private router: Router) {
    this.value.set(this.router.parseUrl(this.router.url).queryParams['sort'] ?? 'score');

    effect(() => {
      if (!this.value() || this.value() === '') return;
      const queryParams = this.router.parseUrl(this.router.url).queryParams;
      if (this.value() === 'score') {
        delete queryParams['sort'];
      } else {
        queryParams['sort'] = this.value();
      }
      setTimeout(() => {
        this.router.navigate([], {
          queryParams
        });
      });
    });
  }
}
