import { Component, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import dayjs from 'dayjs';
import weekOfYear from 'dayjs/plugin/weekOfYear';
import { BrnSelectModule } from '@spartan-ng/ui-select-brain';
import { HlmSelectModule } from '@spartan-ng/ui-select-helm';
import { HlmLabelModule } from '@spartan-ng/ui-label-helm';
import { ListFilter, LucideAngularModule } from 'lucide-angular';

interface SelectOption {
  id: number;
  value: string;
  label: string;
}

dayjs.extend(weekOfYear);

function formatLabel(startDate: dayjs.Dayjs, endDate: dayjs.Dayjs | undefined) {
  const calendarWeek = startDate.week();
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
  imports: [RouterLink, LucideAngularModule, BrnSelectModule, HlmSelectModule, HlmLabelModule],
  templateUrl: './filter.component.html'
})
export class LeaderboardFilterComponent {
  protected ListFilter = ListFilter;
  after = input<string>();
  before = input<string>();

  options = signal<SelectOption[]>([]);
  placeholder = signal<string>('Select a timeframe');

  constructor(private router: Router) {
    // get monday - sunday of last 4 weeks
    const options = new Array<SelectOption>();
    const now = dayjs();
    let currentDate = dayjs().day(1);
    options.push({
      id: now.unix(),
      value: `${currentDate.format('YYYY-MM-DD')}.${now.format('YYYY-MM-DD')}`,
      label: formatLabel(currentDate, undefined)
    });

    this.placeholder.set(formatLabel(currentDate, undefined));

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
    this.options.set(options);
  }

  onSelectChange(event: Event) {
    const value = (event.target as HTMLSelectElement).value;
    const dates = value.split('.');
    // change query params
    this.router.navigate([], {
      queryParams: {
        after: dates[0],
        before: dates[1]
      }
    });
  }
}
