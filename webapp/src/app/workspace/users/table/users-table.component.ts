import { SelectionModel } from '@angular/cdk/collections';
import { Component, TrackByFunction, computed, effect, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { lucideArrowUpDown, lucideChevronDown, lucideGripHorizontal, lucideRotateCw, lucideOctagonX } from '@ng-icons/lucide';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { HlmCheckboxComponent } from '@spartan-ng/ui-checkbox-helm';
import { HlmIconDirective } from '@spartan-ng/ui-icon-helm';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { HlmInputDirective } from '@spartan-ng/ui-input-helm';
import { BrnMenuTriggerDirective } from '@spartan-ng/brain/menu';
import { HlmMenuModule } from '@spartan-ng/ui-menu-helm';
import { BrnTableModule, PaginatorState, useBrnColumnManager } from '@spartan-ng/brain/table';
import { HlmTableModule } from '@spartan-ng/ui-table-helm';
import { BrnSelectModule } from '@spartan-ng/brain/select';
import { HlmSelectModule } from '@spartan-ng/ui-select-helm';
import { HlmSkeletonModule } from '@spartan-ng/ui-skeleton-helm';
import { debounceTime, lastValueFrom, map } from 'rxjs';
import { WorkspaceService, TeamInfo, UserTeams } from '@app/core/modules/openapi';
import { RouterLink } from '@angular/router';
import { GithubLabelComponent } from '@app/ui/github-label/github-label.component';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { octNoEntry } from '@ng-icons/octicons';

const LOADING_DATA: UserTeams[] = [
  {
    id: 1,
    login: 'test1',
    name: 'Tester 1',
    url: 'https://github.com/test1',
    teams: new Set([
      {
        id: 1,
        name: 'Team A',
        color: '#FF0000',
        repositories: [],
        labels: [],
        members: []
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
        color: '#00FF00',
        repositories: [],
        labels: [],
        members: []
      }
    ])
  }
];

@Component({
  selector: 'app-workspace-users-table',
  imports: [
    FormsModule,
    RouterLink,
    BrnMenuTriggerDirective,
    HlmMenuModule,
    BrnTableModule,
    HlmTableModule,
    HlmButtonModule,
    HlmIconDirective,
    HlmInputDirective,
    HlmCheckboxComponent,
    BrnSelectModule,
    HlmSelectModule,
    HlmSkeletonModule,
    NgIconComponent,
    GithubLabelComponent
  ],
  providers: [provideIcons({ lucideChevronDown, lucideGripHorizontal, lucideArrowUpDown, lucideRotateCw, lucideOctagonX })],
  templateUrl: './users-table.component.html'
})
export class WorkspaceUsersTableComponent {
  protected workspaceService = inject(WorkspaceService);
  protected queryClient = inject(QueryClient);
  protected octNoEntry = octNoEntry;

  isLoading = input(false);
  userData = input.required<UserTeams[] | undefined>();

  _users = computed(() => (this.isLoading() ? LOADING_DATA : (this.userData() ?? [])));
  // Filters
  protected readonly _rawFilterInput = signal('');
  protected readonly _loginFilter = signal('');
  private readonly _debouncedFilter = toSignal(toObservable(this._rawFilterInput).pipe(debounceTime(300)));
  // Pagination
  private readonly _displayedIndices = signal({ start: 0, end: 0 });
  protected readonly _availablePageSizes = [5, 10, 20, 10000];
  protected readonly _pageSize = signal(this._availablePageSizes[0]);
  // Selection
  private readonly _selectionModel = new SelectionModel<UserTeams>(true);
  protected readonly _isUserSelected = (user: UserTeams) => this._selectionModel.isSelected(user);
  protected readonly _selected = toSignal(this._selectionModel.changed.pipe(map((change) => change.source.selected)), {
    initialValue: []
  });
  // Manage columns
  protected readonly _brnColumnManager = useBrnColumnManager({
    name: { visible: true, label: 'Name' },
    login: { visible: true, label: 'Login' },
    teams: { visible: true, label: 'Teams' }
  });
  protected readonly _allDisplayedColumns = computed(() => ['select', ...this._brnColumnManager.displayedColumns(), 'actions']);

  // Table state logic properties
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

  protected readonly _trackBy: TrackByFunction<UserTeams> = (_: number, u: UserTeams) => u.id;
  protected readonly _totalElements = computed(() => this._filteredLogins().length);
  protected readonly _onStateChange = ({ startIndex, endIndex }: PaginatorState) => this._displayedIndices.set({ start: startIndex, end: endIndex });

  constructor() {
    // needed to sync the debounced filter to the name filter, but being able to override the
    // filter when loading new users without debounce
    effect(() => this._loginFilter.set(this._debouncedFilter() ?? ''), { allowSignalWrites: true });
  }

  protected toggleUser(user: UserTeams) {
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

  protected copyLogin(element: UserTeams) {
    console.log('Copying login', element);
    navigator.clipboard.writeText(element.login!);
  }

  // handle team add / remove
  teams = input<TeamInfo[] | undefined>();
  protected readonly _availableTeams = computed(() => this.teams() ?? []);
  protected readonly _selectedTeam = signal<TeamInfo | undefined>(undefined);

  addTeamToUser = injectMutation(() => ({
    mutationFn: (user: UserTeams) => lastValueFrom(this.workspaceService.addTeamToUser(user.login, this._selectedTeam()!.id)),
    queryKey: ['workspace', 'user', 'team', 'add'],
    onSettled: () => this.invalidateUsers()
  }));
  protected addTeamToSelected() {
    for (const user of this._selected()) {
      this.addTeamToUser.mutate(user);
    }
  }

  removeTeamFromUser = injectMutation(() => ({
    mutationFn: (user: UserTeams) => lastValueFrom(this.workspaceService.removeUserFromTeam(user.login, this._selectedTeam()!.id)),
    queryKey: ['workspace', 'user', 'team', 'remove'],
    onSettled: () => this.invalidateUsers()
  }));
  protected removeTeamFromSelected() {
    for (const user of this._selected()) {
      this.removeTeamFromUser.mutate(user);
    }
  }

  automaticallyAssignUsers = injectMutation(() => ({
    mutationFn: () => lastValueFrom(this.workspaceService.automaticallyAssignTeams()),
    onSettled: () => this.invalidateUsers()
  }));

  protected invalidateUsers() {
    for (const user of this._selected()) {
      this._selectionModel.deselect(user);
    }
    this.queryClient.invalidateQueries({ queryKey: ['workspace', 'users'] });
  }
}
