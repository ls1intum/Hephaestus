import { Component, computed, input } from '@angular/core';
import { ClassValue } from 'clsx';
import { cn } from 'app/utils';

type TableHeadScope = 'col' | 'colgroup' | 'row' | 'rowgroup';

@Component({
  selector: 'app-table-head',
  standalone: true,
  template: `<ng-content />`,
  host: {
    '[class]': 'computedClass()',
    '[attr.abbr]': 'abbr()',
    '[attr.colspan]': 'colspan()',
    '[attr.headers]': 'headers()',
    '[attr.rowspan]': 'rowspan()',
    '[attr.scope]': 'scope()'
  }
})
export class TableHeadComponent {
  class = input<ClassValue>();

  abbr = input<string>();
  colspan = input<number>();
  headers = input<string>();
  rowspan = input<number>();
  scope = input<TableHeadScope>();

  computedClass = computed(() => cn('table-cell h-12 px-4 text-left align-middle font-medium text-muted-foreground [&:has([role=checkbox])]:pr-0', this.class()));
}
