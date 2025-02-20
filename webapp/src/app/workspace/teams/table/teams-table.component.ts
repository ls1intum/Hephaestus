import { SelectionModel } from '@angular/cdk/collections';
import { Component, TrackByFunction, computed, effect, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { lucideArrowUpDown, lucideChevronDown, lucideGripHorizontal, lucideRotateCw, lucideOctagonX, lucidePlus, lucideCheck, lucideTrash2 } from '@ng-icons/lucide';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { HlmIconComponent, provideIcons } from '@spartan-ng/ui-icon-helm';
import { HlmInputDirective } from '@spartan-ng/ui-input-helm';
import { BrnMenuTriggerDirective } from '@spartan-ng/brain/menu';
import { HlmMenuModule } from '@spartan-ng/ui-menu-helm';
import { BrnTableModule, PaginatorState } from '@spartan-ng/brain/table';
import { HlmTableModule } from '@spartan-ng/ui-table-helm';
import { BrnSelectModule } from '@spartan-ng/brain/select';
import { HlmSelectModule } from '@spartan-ng/ui-select-helm';
import { HlmSkeletonModule } from '@spartan-ng/ui-skeleton-helm';
import { HlmCardModule } from '@spartan-ng/ui-card-helm';
import { debounceTime, lastValueFrom, map } from 'rxjs';
import { WorkspaceService, TeamInfo } from '@app/core/modules/openapi';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { octNoEntry } from '@ng-icons/octicons';
import { HlmPopoverModule } from '@spartan-ng/ui-popover-helm';
import { BrnPopoverComponent, BrnPopoverContentDirective, BrnPopoverTriggerDirective } from '@spartan-ng/brain/popover';
import { GithubLabelComponent } from '@app/ui/github-label/github-label.component';
import { HlmScrollAreaComponent } from '@spartan-ng/ui-scrollarea-helm';
import { groupBy } from '@app/utils';

const LOADING_TEAMS: TeamInfo[] = [
  {
    id: 1,
    name: 'Team A',
    color: '#FF0000',
    repositories: [],
    labels: []
  },
  {
    id: 2,
    name: 'Team B',
    color: '#00FF00',
    repositories: [],
    labels: []
  }
];

@Component({
  selector: 'app-workspace-teams-table',
  imports: [
    FormsModule,
    ReactiveFormsModule,
    BrnMenuTriggerDirective,
    HlmMenuModule,
    BrnTableModule,
    HlmTableModule,
    HlmButtonModule,
    HlmIconComponent,
    HlmInputDirective,
    HlmScrollAreaComponent,
    BrnSelectModule,
    HlmSelectModule,
    HlmSkeletonModule,
    HlmCardModule,
    HlmPopoverModule,
    BrnPopoverComponent,
    BrnPopoverContentDirective,
    BrnPopoverTriggerDirective,
    GithubLabelComponent
  ],
  providers: [provideIcons({ lucideChevronDown, lucideGripHorizontal, lucideArrowUpDown, lucideRotateCw, lucideOctagonX, lucidePlus, lucideCheck, lucideTrash2 })],
  templateUrl: './teams-table.component.html'
})
export class WorkspaceTeamsTableComponent {
  protected workspaceService = inject(WorkspaceService);
  protected queryClient = inject(QueryClient);
  protected octNoEntry = octNoEntry;

  isLoading = input(false);
  teamData = input.required<TeamInfo[] | undefined>();
  allRepositories = input.required<Array<string> | undefined>();

  // Controls for mutations
  _newLabelName = new FormControl('');
  _newTeamName = new FormControl('');
  _newTeamColor = new FormControl('#000000');

  displayLabelAlert = signal(false);

  _teams = computed(() => (this.isLoading() ? LOADING_TEAMS : (this.teamData() ?? [])));
  // Filters
  protected readonly _rawFilterInput = signal('');
  protected readonly _nameFilter = signal('');
  private readonly _debouncedFilter = toSignal(toObservable(this._rawFilterInput).pipe(debounceTime(300)));
  // Pagination
  private readonly _displayedIndices = signal({ start: 0, end: 0 });
  protected readonly _availablePageSizes = [5, 10, 20, 10000];
  protected readonly _pageSize = signal(this._availablePageSizes[2]);
  // Selection
  private readonly _selectionModel = new SelectionModel<TeamInfo>(true);
  protected readonly _isUserSelected = (user: TeamInfo) => this._selectionModel.isSelected(user);
  protected readonly _selected = toSignal(this._selectionModel.changed.pipe(map((change) => change.source.selected)), {
    initialValue: []
  });

  protected readonly _allDisplayedColumns = ['name', 'color', 'repositories', 'actions'];

  // Table state logic properties
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

  groupLabelsByRepository = (team: TeamInfo) => {
    const group = Object.entries(groupBy(team.labels, (l) => l.repository!.nameWithOwner)).map(([repository, labels]) => ({
      repository,
      labels,
      repositoryId: labels[0].repository!.id
    }));
    // add repos without labels
    const filteredRepos = team.repositories.filter((r) => !group.map((g) => g.repository).includes(r.nameWithOwner));
    let result = group.concat(filteredRepos.map((r) => ({ repository: r.nameWithOwner, labels: [], repositoryId: r.id }))).sort((a, b) => a.repository.localeCompare(b.repository));
    // Sort labels
    result = result.map((r) => ({ ...r, labels: r.labels.sort((a, b) => a.name.localeCompare(b.name)) }));
    return result;
  };

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

  deleteTeam = injectMutation(() => ({
    mutationFn: (team: TeamInfo) => lastValueFrom(this.workspaceService.deleteTeam(team.id)),
    queryKey: ['workspace', 'team', 'delete'],
    onSettled: () => this.invalidateTeams()
  }));

  addLabelToTeam = injectMutation(() => ({
    mutationFn: ({ teamId, repositoryId, label }: { teamId: number; repositoryId: number; label: string }) =>
      lastValueFrom(this.workspaceService.addLabelToTeam(teamId, repositoryId, label)),
    onError: () => {
      this.displayLabelAlert.set(true);
    },
    onSettled: () => {
      this.displayLabelAlert.set(false);
      this._newLabelName.reset();
      this.invalidateTeams();
    }
  }));

  removeLabelFromTeam = injectMutation(() => ({
    mutationFn: ({ teamId, labelId }: { teamId: number; labelId: number }) => lastValueFrom(this.workspaceService.removeLabelFromTeam(teamId, labelId)),
    onError: () => {
      this.displayLabelAlert.set(true);
    },
    onSettled: () => {
      this.displayLabelAlert.set(false);
      this._newLabelName.reset();
      this.invalidateTeams();
    }
  }));

  createTeam = injectMutation(() => ({
    mutationFn: () =>
      lastValueFrom(
        this.workspaceService.createTeam({
          name: this._newTeamName.value,
          color: this._newTeamColor.value ?? '#000000'
        } as TeamInfo)
      ),
    queryKey: ['workspace', 'team', 'create'],
    onSettled: () => this.invalidateTeams()
  }));

  removeRepositoryFromTeam = injectMutation(() => ({
    mutationFn: ({ teamId, owner, repo }: { teamId: number; owner: string; repo: string }) => lastValueFrom(this.workspaceService.removeRepositoryFromTeam(teamId, owner, repo)),
    queryKey: ['workspace', 'team', 'repository', 'remove'],
    onSettled: () => this.invalidateTeams()
  }));

  addRepositoryToTeam = injectMutation(() => ({
    mutationFn: ({ teamId, owner, repo }: { teamId: number; owner: string; repo: string }) => lastValueFrom(this.workspaceService.addRepositoryToTeam(teamId, owner, repo)),
    queryKey: ['workspace', 'team', 'repository', 'add'],
    onSettled: () => this.invalidateTeams()
  }));

  protected copyName(element: TeamInfo) {
    console.log('Copying name', element);
    navigator.clipboard.writeText(element.name!);
  }

  protected invalidateTeams() {
    this.queryClient.invalidateQueries({ queryKey: ['workspace', 'teams'] });
  }

  protected isRepositoryInTeam(team: TeamInfo, repository: string) {
    return team.repositories.some((r) => r.nameWithOwner === repository);
  }

  protected toggleRepository(team: TeamInfo, repository: string, checked: boolean) {
    const [owner, repo] = repository.split('/');
    if (checked) {
      this.removeRepositoryFromTeam.mutate({ teamId: team.id, owner, repo });
    } else {
      this.addRepositoryToTeam.mutate({ teamId: team.id, owner, repo });
    }
  }
}
