import { Component, computed, effect, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import dayjs from 'dayjs';
import isoWeek from 'dayjs/plugin/isoWeek';
import { BrnSelectModule } from '@spartan-ng/ui-select-brain';
import { HlmSelectModule } from '@spartan-ng/ui-select-helm';
import { HlmLabelModule } from '@spartan-ng/ui-label-helm';
import { ListFilter, LucideAngularModule } from 'lucide-angular';

interface SelectOption {
  id: number;
  value: string;
  label: string;
}

dayjs.extend(isoWeek);

function formatLabel(startDate: dayjs.Dayjs, endDate: dayjs.Dayjs | undefined) {
  const calendarWeek = startDate.isoWeek();
  if (!endDate) {
    return `CW\xa0${calendarWeek}:\xa0${startDate.format('MMM D')}\xa0-\xa0Today`;
  }

  const sameMonth = startDate.month() === endDate.month();
  if (sameMonth) {
    return `CW\xa0${calendarWeek}:\xa0${startDate.format('MMM D')}\xa0-\xa0${endDate.format('D')}`;
  } else {
    return `CW\xa0${calendarWeek}:\xa0${startDate.format('MMM D')}\xa0-\xa0${endDate.format('MMM D')}`;
  }
}

@Component({
  selector: 'app-leaderboard-filter',
  standalone: true,
  imports: [RouterLink, LucideAngularModule, BrnSelectModule, HlmSelectModule, HlmLabelModule, FormsModule],
  templateUrl: './filter.component.html'
})
export class LeaderboardFilterComponent {
  protected ListFilter = ListFilter;
  after = input<string>('');
  before = input<string>('');

  value = signal<string>(`${this.after()}.${this.before()}`);

  placeholder = computed(() => {
    const beforeIsToday = this.before() === dayjs().format('YYYY-MM-DD');
    return formatLabel(this.after() ? dayjs(this.after()) : dayjs().isoWeekday(1), beforeIsToday ? undefined : dayjs(this.before()));
  });

  options = computed(() => {
    const now = dayjs();
    let currentDate = dayjs().isoWeekday(1);
    const options: SelectOption[] = [
      {
        id: now.unix(),
        value: `${currentDate.format('YYYY-MM-DD')}.${now.format('YYYY-MM-DD')}`,
        label: formatLabel(currentDate, undefined)
      }
    ];

    for (let i = 0; i < 4; i++) {
      const startDate = currentDate.subtract(7, 'day');
      const endDate = currentDate.subtract(1, 'day');
      options.push({
        id: startDate.unix(),
        value: `${startDate.format('YYYY-MM-DD')}.${endDate.format('YYYY-MM-DD')}`,
        label: formatLabel(startDate, endDate)
      });
      currentDate = startDate;
    }

    return options;
  });

  constructor(private router: Router) {
    effect(() => {
      if (this.value().length === 1) return;
      const dates = this.value().split('.');
      // change query params
      this.router.navigate([], {
        queryParams: {
          after: dates[0],
          before: dates[1]
        }
      });
    });
  }
}
