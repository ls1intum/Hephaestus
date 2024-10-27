import { SelectionModel } from '@angular/cdk/collections';
import { DecimalPipe, TitleCasePipe } from '@angular/common';
import { Component, TrackByFunction, computed, effect, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { lucideArrowUpDown, lucideChevronDown, lucideMoreHorizontal, lucideRotateCw, lucideXOctagon } from '@ng-icons/lucide';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { HlmCheckboxCheckIconComponent, HlmCheckboxComponent } from '@spartan-ng/ui-checkbox-helm';
import { HlmIconComponent, provideIcons } from '@spartan-ng/ui-icon-helm';
import { HlmInputDirective } from '@spartan-ng/ui-input-helm';
import { BrnMenuTriggerDirective } from '@spartan-ng/ui-menu-brain';
import { HlmMenuModule } from '@spartan-ng/ui-menu-helm';
import { BrnTableModule, PaginatorState, useBrnColumnManager } from '@spartan-ng/ui-table-brain';
import { HlmTableModule } from '@spartan-ng/ui-table-helm';
import { BrnSelectModule } from '@spartan-ng/ui-select-brain';
import { HlmSelectModule } from '@spartan-ng/ui-select-helm';
import { HlmSkeletonModule } from '@spartan-ng/ui-skeleton-helm';
import { HlmCardModule } from '@spartan-ng/ui-card-helm';
import { debounceTime, map } from 'rxjs';
import { AdminService, TeamInfo } from '@app/core/modules/openapi';
import { RouterLink } from '@angular/router';
import { injectQueryClient } from '@tanstack/angular-query-experimental';
import { octNoEntry } from '@ng-icons/octicons';

const LOADING_TEAMS: TeamInfo[] = [
  {
    id: 1,
    name: 'Team A',
    color: '#FF0000'
  },
  {
    id: 2,
    name: 'Team B',
    color: '#00FF00'
  }
];

@Component({
  selector: 'app-admin-teams-table',
  standalone: true,
  imports: [
    FormsModule,
    ReactiveFormsModule,
    RouterLink,

    BrnMenuTriggerDirective,
    HlmMenuModule,

    BrnTableModule,
    HlmTableModule,

    HlmButtonModule,

    DecimalPipe,
    TitleCasePipe,
    HlmIconComponent,
    HlmInputDirective,

    HlmCheckboxCheckIconComponent,
    HlmCheckboxComponent,

    BrnSelectModule,
    HlmSelectModule,

    HlmSkeletonModule,
    HlmCardModule
  ],
  providers: [provideIcons({ lucideChevronDown, lucideMoreHorizontal, lucideArrowUpDown, lucideRotateCw, lucideXOctagon })],
  templateUrl: './teams-table.component.html'
})
export class AdminTeamsTableComponent {
  protected adminService = inject(AdminService);
  protected queryClient = injectQueryClient();
  protected octNoEntry = octNoEntry;

  isLoading = input(false);
  teamData = input.required<TeamInfo[] | undefined>();

  _teams = computed(() => this.teamData() ?? LOADING_TEAMS);
  protected readonly _rawFilterInput = signal('');
  protected readonly _nameFilter = signal('');
  private readonly _debouncedFilter = toSignal(toObservable(this._rawFilterInput).pipe(debounceTime(300)));

  private readonly _displayedIndices = signal({ start: 0, end: 0 });
  protected readonly _availablePageSizes = [5, 10, 20, 10000];
  protected readonly _pageSize = signal(this._availablePageSizes[0]);

  private readonly _selectionModel = new SelectionModel<TeamInfo>(true);
  protected readonly _isUserSelected = (user: TeamInfo) => this._selectionModel.isSelected(user);
  protected readonly _selected = toSignal(this._selectionModel.changed.pipe(map((change) => change.source.selected)), {
    initialValue: []
  });

  protected readonly _brnColumnManager = useBrnColumnManager({
    name: { visible: true, label: 'Name' },
    color: { visible: true, label: 'Color' }
  });
  protected readonly _allDisplayedColumns = computed(() => ['select', ...this._brnColumnManager.displayedColumns(), 'actions']);

  private readonly _filteredNames = computed(() => {
    const nameFilter = this._nameFilter()?.trim()?.toLowerCase();
    if (nameFilter && nameFilter.length > 0) {
      return this._teams().filter((u) => u.name!.toLowerCase().includes(nameFilter));
    }
    return this._teams();
  });
  private readonly _nameSort = signal<'ASC' | 'DESC' | null>(null);
  protected readonly _filteredSortedPaginatedTeams = computed(() => {
    const sort = this._nameSort();
    const start = this._displayedIndices().start;
    const end = this._displayedIndices().end + 1;
    const names = this._filteredNames();
    if (!sort) {
      return names.slice(start, end);
    }
    return [...names].sort((p1, p2) => (sort === 'ASC' ? 1 : -1) * p1.name.localeCompare(p2.name)).slice(start, end);
  });
  protected readonly _allFilteredPaginatedTeamsSelected = computed(() => this._filteredSortedPaginatedTeams().every((team) => this._selected().includes(team)));
  protected readonly _checkboxState = computed(() => {
    const noneSelected = this._selected().length === 0;
    const allSelectedOrIndeterminate = this._allFilteredPaginatedTeamsSelected() ? true : 'indeterminate';
    return noneSelected ? false : allSelectedOrIndeterminate;
  });

  protected readonly _trackBy: TrackByFunction<TeamInfo> = (_: number, u: TeamInfo) => u.id;
  protected readonly _totalElements = computed(() => this._filteredNames().length);
  protected readonly _onStateChange = ({ startIndex, endIndex }: PaginatorState) => this._displayedIndices.set({ start: startIndex, end: endIndex });

  constructor() {
    // needed to sync the debounced filter to the name filter, but being able to override the
    // filter when loading new users without debounce
    effect(() => this._nameFilter.set(this._debouncedFilter() ?? ''), { allowSignalWrites: true });
  }

  protected toggleTeam(team: TeamInfo) {
    this._selectionModel.toggle(team);
  }

  protected handleHeaderCheckboxChange() {
    const previousCbState = this._checkboxState();
    if (previousCbState === 'indeterminate' || !previousCbState) {
      this._selectionModel.select(...this._filteredSortedPaginatedTeams());
    } else {
      this._selectionModel.deselect(...this._filteredSortedPaginatedTeams());
    }
  }

  protected handleNameSortChange() {
    const sort = this._nameSort();
    if (sort === 'ASC') {
      this._nameSort.set('DESC');
    } else if (sort === 'DESC') {
      this._nameSort.set(null);
    } else {
      this._nameSort.set('ASC');
    }
  }

  protected deleteTeam(team: TeamInfo) {
    if (this.isLoading()) {
      return;
    }
    this.adminService.deleteTeam(team.id!);
    this.invalidateTeams();
  }

  protected copyName(element: TeamInfo) {
    console.log('Copying name', element);
    navigator.clipboard.writeText(element.name!);
  }

  _newTeamName = new FormControl('');
  _newTeamColor = new FormControl('');

  protected createTeam() {
    if (this.isLoading() || !this._newTeamName.value || !this._newTeamColor.value) {
      return;
    }
    const newTeam = {
      name: this._newTeamName.value,
      color: this._newTeamColor.value
    } as TeamInfo;
    this.adminService.createTeam(newTeam).subscribe({
      next: () => console.log('Team created'),
      error: (err) => console.error('Error creating team', err)
    });
    this.invalidateTeams();
  }

  protected invalidateTeams() {
    if (this.isLoading()) {
      return;
    }
    for (const team of this._selected()) {
      this._selectionModel.deselect(team);
    }
    this.queryClient.invalidateQueries({ queryKey: ['admin', 'teams'] });
  }
}
