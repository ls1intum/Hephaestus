import { SelectionModel } from '@angular/cdk/collections';
import { DecimalPipe, TitleCasePipe } from '@angular/common';
import { Component, TrackByFunction, computed, effect, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
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
import { debounceTime, map } from 'rxjs';
import { AdminService, TeamDTO, UserTeamsDTO } from '@app/core/modules/openapi';
import { RouterLink } from '@angular/router';
import { GithubLabelComponent } from '@app/ui/github-label/github-label.component';
import { injectQueryClient } from '@tanstack/angular-query-experimental';
import { octNoEntry } from '@ng-icons/octicons';

const LOADING_DATA: UserTeamsDTO[] = [
  {
    id: 1,
    login: 'test1',
    name: 'Tester 1',
    url: 'https://github.com/test1',
    teams: new Set([
      {
        id: 1,
        name: 'Team A',
        color: '#FF0000'
      }
    ])
  },
  {
    id: 2,
    login: 'test2',
    name: 'Tester 2',
    url: 'https://github.com/test2',
    teams: new Set([
      {
        id: 2,
        name: 'Team B',
        color: '#00FF00'
      }
    ])
  }
];

const LOADING_TEAMS: TeamDTO[] = [
  {
    id: 1,
    name: 'Team A',
    color: '#FF0000'
  }
];

@Component({
  selector: 'app-admin-users-table',
  standalone: true,
  imports: [
    FormsModule,
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

    GithubLabelComponent
  ],
  providers: [provideIcons({ lucideChevronDown, lucideMoreHorizontal, lucideArrowUpDown, lucideRotateCw, lucideXOctagon })],
  templateUrl: './users-table.component.html'
})
export class AdminUsersTableComponent {
  protected adminService = inject(AdminService);
  protected queryClient = injectQueryClient();
  protected octNoEntry = octNoEntry;

  isLoading = input(false);
  userData = input.required<UserTeamsDTO[] | undefined>();

  _users = computed(() => this.userData() ?? LOADING_DATA);
  protected readonly _rawFilterInput = signal('');
  protected readonly _loginFilter = signal('');
  private readonly _debouncedFilter = toSignal(toObservable(this._rawFilterInput).pipe(debounceTime(300)));

  private readonly _displayedIndices = signal({ start: 0, end: 0 });
  protected readonly _availablePageSizes = [5, 10, 20, 10000];
  protected readonly _pageSize = signal(this._availablePageSizes[0]);

  private readonly _selectionModel = new SelectionModel<UserTeamsDTO>(true);
  protected readonly _isUserSelected = (user: UserTeamsDTO) => this._selectionModel.isSelected(user);
  protected readonly _selected = toSignal(this._selectionModel.changed.pipe(map((change) => change.source.selected)), {
    initialValue: []
  });

  protected readonly _brnColumnManager = useBrnColumnManager({
    name: { visible: true, label: 'Name' },
    login: { visible: true, label: 'Login' },
    teams: { visible: true, label: 'Teams' }
  });
  protected readonly _allDisplayedColumns = computed(() => ['select', ...this._brnColumnManager.displayedColumns(), 'actions']);

  private readonly _filteredLogins = computed(() => {
    const loginFilter = this._loginFilter()?.trim()?.toLowerCase();
    if (loginFilter && loginFilter.length > 0) {
      return this._users().filter((u) => u.login!.toLowerCase().includes(loginFilter));
    }
    return this._users();
  });
  private readonly _loginSort = signal<'ASC' | 'DESC' | null>(null);
  protected readonly _filteredSortedPaginatedLogins = computed(() => {
    const sort = this._loginSort();
    const start = this._displayedIndices().start;
    const end = this._displayedIndices().end + 1;
    const logins = this._filteredLogins();
    if (!sort) {
      return logins.slice(start, end);
    }
    return [...logins].sort((p1, p2) => (sort === 'ASC' ? 1 : -1) * p1.login!.localeCompare(p2.login!)).slice(start, end);
  });
  protected readonly _allFilteredPaginatedLoginsSelected = computed(() => this._filteredSortedPaginatedLogins().every((login) => this._selected().includes(login)));
  protected readonly _checkboxState = computed(() => {
    const noneSelected = this._selected().length === 0;
    const allSelectedOrIndeterminate = this._allFilteredPaginatedLoginsSelected() ? true : 'indeterminate';
    return noneSelected ? false : allSelectedOrIndeterminate;
  });

  protected readonly _trackBy: TrackByFunction<UserTeamsDTO> = (_: number, u: UserTeamsDTO) => u.id;
  protected readonly _totalElements = computed(() => this._filteredLogins().length);
  protected readonly _onStateChange = ({ startIndex, endIndex }: PaginatorState) => this._displayedIndices.set({ start: startIndex, end: endIndex });

  constructor() {
    // needed to sync the debounced filter to the name filter, but being able to override the
    // filter when loading new users without debounce
    effect(() => this._loginFilter.set(this._debouncedFilter() ?? ''), { allowSignalWrites: true });
  }

  protected toggleUser(user: UserTeamsDTO) {
    this._selectionModel.toggle(user);
  }

  protected handleHeaderCheckboxChange() {
    const previousCbState = this._checkboxState();
    if (previousCbState === 'indeterminate' || !previousCbState) {
      this._selectionModel.select(...this._filteredSortedPaginatedLogins());
    } else {
      this._selectionModel.deselect(...this._filteredSortedPaginatedLogins());
    }
  }

  protected handleLoginSortChange() {
    const sort = this._loginSort();
    if (sort === 'ASC') {
      this._loginSort.set('DESC');
    } else if (sort === 'DESC') {
      this._loginSort.set(null);
    } else {
      this._loginSort.set('ASC');
    }
  }

  protected copyLogin(element: UserTeamsDTO) {
    console.log('Copying login', element);
    navigator.clipboard.writeText(element.login!);
  }

  // handle team add / remove
  teams = input<TeamDTO[] | undefined>();
  protected readonly _availableTeams = computed(() => this.teams() ?? LOADING_TEAMS);
  protected readonly _selectedTeam = signal<TeamDTO | undefined>(undefined);

  protected addTeamToSelected() {
    for (const user of this._selected()) {
      console.log('Adding team to user', user.login, this._selectedTeam());
      this.adminService.addTeamToUser(user.login, this._selectedTeam()!.id).subscribe({
        next: () => console.log('Team added to user', user),
        error: (err) => console.error('Error adding team to user', user, err)
      });
    }
    this.invalidateUsers();
  }

  protected invalidateUsers() {
    if (this.isLoading()) {
      return;
    }
    for (const user of this._selected()) {
      this._selectionModel.deselect(user);
    }
    this.queryClient.invalidateQueries({ queryKey: ['admin', 'users'] });
  }
}
