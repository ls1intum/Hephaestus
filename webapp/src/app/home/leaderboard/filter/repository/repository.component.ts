import { Component, computed, effect, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { BrnSelectModule } from '@spartan-ng/ui-select-brain';
import { HlmSelectModule } from '@spartan-ng/ui-select-helm';
import { HlmLabelModule } from '@spartan-ng/ui-label-helm';

interface SelectOption {
  id: number;
  value: string;
  label: string;
}

@Component({
  selector: 'app-leaderboard-filter-repository',
  standalone: true,
  imports: [RouterLink, BrnSelectModule, HlmSelectModule, HlmLabelModule, FormsModule],
  templateUrl: './repository.component.html'
})
export class LeaderboardFilterRepositoryComponent {
  repositories = input.required<string[]>();
  value = signal<string>('');

  placeholder = computed(() => {
    return this.repositories().find((option) => option === this.value()) ?? 'All';
  });

  options = computed(() => {
    const options: SelectOption[] = !this.repositories()
      ? []
      : this.repositories()!.map((name, index) => {
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
    this.value.set(this.router.parseUrl(this.router.url).queryParams['repository'] ?? 'all');

    effect(() => {
      if (!this.value() || this.value() === '') return;
      const queryParams = this.router.parseUrl(this.router.url).queryParams;
      if (this.value() === 'all') {
        delete queryParams['repository'];
      } else {
        queryParams['repository'] = this.value();
      }
      this.router.navigate([], {
        queryParams
      });
    });
  }
}
