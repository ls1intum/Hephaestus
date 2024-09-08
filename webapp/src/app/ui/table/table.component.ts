import { ColumnDef, ColumnFiltersState, createAngularTable, FlexRenderDirective, getCoreRowModel, PaginationState, SortingState } from '@tanstack/angular-table';
import { ChangeDetectionStrategy, Component, input, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { combineLatest, of, switchMap } from 'rxjs';
import { Leaderboard } from 'app/@types/leaderboard';

const defaultColumns: ColumnDef<Leaderboard.Entry>[] = [
  {
    accessorKey: 'githubName',
    header: () => 'Github',
    cell: (info) => `
    <div class="flex justify-center items-center">
      <img src="https://github.com/${info.getValue<string>()}.png" width="24" height="24">
    </div>
    `
  },
  {
    accessorKey: 'name',
    header: () => 'Name',
    cell: (info) => `
    <div>
      ${info.getValue<string>()}
    </div>
    `
  },
  {
    accessorKey: 'score',
    header: () => `Score`,
    enableSorting: true
  },
  {
    accessorKey: 'total',
    header: () => 'Total'
  },
  {
    accessorKey: 'changes_requested',
    header: () => 'Changes requested'
  },
  {
    accessorKey: 'approvals',
    header: () => 'Approvals'
  },
  {
    accessorKey: 'comments',
    header: () => 'Comments'
  }
];

@Component({
  selector: 'app-table',
  templateUrl: './table.component.html',
  standalone: true,
  imports: [RouterOutlet, FlexRenderDirective],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TableComponent {
  columnFilters = signal<ColumnFiltersState>([]);
  readonly sorting = signal<SortingState>([
    {
      id: 'score',
      desc: true
    }
  ]);
  pagination = signal<PaginationState>({
    pageIndex: 0,
    pageSize: 15
  });
  // fetchData = signal<(filters: ColumnFiltersState, sorting: SortingState, pagination: PaginationState) => Leaderboard.Entry[]>((...args) => {
  //   return this.initData();
  // });
  // initData = signal<Leaderboard.Entry[]>(defaultData);
  // data$ = combineLatest({
  //   filters: toObservable(this.columnFilters),
  //   sorting: toObservable(this.sorting),
  //   pagination: toObservable(this.pagination)
  // }).pipe(switchMap(({ filters, sorting, pagination }) => of({ filters, sorting, pagination }, this.fetchData()(filters, sorting, pagination))));
  // data = toSignal(this.data$);
  data = input.required<Leaderboard.Entry[]>();
  columns = signal<ColumnDef<Leaderboard.Entry>[]>(defaultColumns);

  table = createAngularTable(() => ({
    // @ts-ignore
    data: this.data() ?? defaultData,
    columns: this.columns(),
    getCoreRowModel: getCoreRowModel(),
    debugTable: true,
    state: {
      columnFilters: this.columnFilters(), // pass controlled state back to the table (overrides internal state)
      sorting: this.sorting(),
      pagination: this.pagination()
    },
    onColumnFiltersChange: (updater) => {
      // hoist columnFilters state into our own state management
      updater instanceof Function ? this.columnFilters.update(updater) : this.columnFilters.set(updater);
    },
    onSortingChange: (updater) => {
      updater instanceof Function ? this.sorting.update(updater) : this.sorting.set(updater);
    },
    onPaginationChange: (updater) => {
      updater instanceof Function ? this.pagination.update(updater) : this.pagination.set(updater);
    }
  }));
}
