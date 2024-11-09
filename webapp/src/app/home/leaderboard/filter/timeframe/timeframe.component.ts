import { Component, computed, effect, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import dayjs from 'dayjs';
import isoWeek from 'dayjs/plugin/isoWeek';
import { BrnSelectModule } from '@spartan-ng/ui-select-brain';
import { HlmSelectModule } from '@spartan-ng/ui-select-helm';
import { HlmLabelModule } from '@spartan-ng/ui-label-helm';
import { BrnTooltipContentDirective } from '@spartan-ng/ui-tooltip-brain';
import { HlmTooltipComponent, HlmTooltipTriggerDirective } from '@spartan-ng/ui-tooltip-helm';
import { HlmIconComponent } from '@spartan-ng/ui-icon-helm';
import { provideIcons } from '@spartan-ng/ui-icon-helm';
import { lucideHelpCircle } from '@ng-icons/lucide';

interface SelectOption {
  id: number;
  value: string;
  label: string;
}

dayjs.extend(isoWeek);

function formatLabel(weekIndex: number) {
  if (weekIndex === 0) {
    return 'Current week';
  }
  if (weekIndex === 1) {
    return 'Last week';
  }
  return `${weekIndex} weeks ago`;
}

@Component({
  selector: 'app-leaderboard-filter-timeframe',
  standalone: true,
  imports: [BrnSelectModule, HlmSelectModule, HlmLabelModule, FormsModule, HlmTooltipComponent, HlmTooltipTriggerDirective, BrnTooltipContentDirective, HlmIconComponent],
  providers: [provideIcons({ lucideHelpCircle })],
  templateUrl: './timeframe.component.html'
})
export class LeaderboardFilterTimeframeComponent {
  after = signal<string>('');
  before = signal<string>('');
  value = signal<string>(`${this.after()}.${this.before()}`);

  placeholder = computed(() => {
    return formatLabel(dayjs(dayjs()).diff(this.after(), 'week'));
  });

  options = computed(() => {
    const now = dayjs();
    let currentDate = dayjs().isoWeekday(2).startOf('hour').hour(9);
    const options: SelectOption[] = [
      {
        id: now.unix(),
        value: `${currentDate.format()}.${now.format()}`,
        label: formatLabel(0)
      }
    ];

    for (let i = 0; i < 4; i++) {
      const startDate = currentDate.subtract(7, 'day');
      options.push({
        id: startDate.unix(),
        value: `${startDate.format()}.${currentDate.format()}`,
        label: formatLabel(i + 1)
      });
      currentDate = startDate;
    }

    return options;
  });

  constructor(private router: Router) {
    // init params
    const queryParams = this.router.parseUrl(this.router.url).queryParams;
    this.after.set(queryParams['after'] ?? dayjs().isoWeekday(2).hour(9).format());
    this.before.set(queryParams['before'] ?? dayjs().format());

    // persist changes in url
    effect(() => {
      if (this.value().length === 1) return;

      const queryParams = this.router.parseUrl(this.router.url).queryParams;
      const dates = this.value().split('.');
      queryParams['after'] = dates[0];
      queryParams['before'] = dates[1];

      this.router.navigate([], {
        queryParams
      });
    });
  }
}
