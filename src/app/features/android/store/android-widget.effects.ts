import { combineLatest } from 'rxjs';
import { inject, Injectable } from '@angular/core';
import { createEffect } from '@ngrx/effects';
import { Store } from '@ngrx/store';
import { debounceTime, distinctUntilChanged, filter, tap } from 'rxjs/operators';
import { IS_ANDROID_WEB_VIEW } from '../../../util/is-android-web-view';
import { androidInterface, drainWidgetDoneQueue } from '../android-interface';
import { WidgetDataService } from '../widget-data.service';
import { TaskService } from '../../tasks/task.service';
import { SnackService } from '../../../core/snack/snack.service';
import { DroidLog } from '../../../core/log';
import { HydrationStateService } from '../../../op-log/apply/hydration-state.service';
import { selectTodayWidgetRows, WidgetRow } from './widget.selectors';
import { selectUnarchivedProjects } from '../../project/store/project.selectors';
import { selectAllTags } from '../../tag/store/tag.reducer';
import { T } from '../../../t.const';

export const rowsSignature = (rows: WidgetRow[]): string =>
  rows
    .map(
      (r) =>
        `${r.id}:${r.isDone ? 1 : 0}:${r.title}:${r.projectId ?? ''}:${r.color ?? ''}:${r.projectTitle ?? ''}:${r.isToday ? 1 : 0}`,
    )
    .join('|');

@Injectable()
export class AndroidWidgetEffects {
  private _store = inject(Store);
  private _widgetDataService = inject(WidgetDataService);
  private _taskService = inject(TaskService);
  private _snackService = inject(SnackService);
  private _hydrationState = inject(HydrationStateService);

  // Selector-based effect (exception to CLAUDE.md #8) because we need to react to
  // state shape, not specific actions — many different actions can change a
  // today task's title/isDone/projectId, or the project/tag lists. Guarded with
  // isApplyingRemoteOps() so hydration replay does not write. The single post-hydration
  // emission is intentional: it pushes a fresh widget snapshot after a remote sync.
  pushOnStateChange$ =
    IS_ANDROID_WEB_VIEW &&
    createEffect(
      () =>
        combineLatest([
          this._store.select(selectTodayWidgetRows),
          this._store.select(selectUnarchivedProjects),
          this._store.select(selectAllTags),
        ]).pipe(
          filter(() => !this._hydrationState.isApplyingRemoteOps()),
          distinctUntilChanged(
            (a, b) =>
              a[0].length === b[0].length &&
              rowsSignature(a[0]) === rowsSignature(b[0]) &&
              a[1] === b[1] &&
              a[2] === b[2],
          ),
          debounceTime(500),
          tap(() => {
            this._widgetDataService.serialize();
          }),
        ),
      { dispatch: false },
    );

  pushOnPause$ =
    IS_ANDROID_WEB_VIEW &&
    createEffect(
      () =>
        androidInterface.onPause$.pipe(
          tap(() => {
            this._widgetDataService.serialize();
          }),
        ),
      { dispatch: false },
    );

  // Queue is the single source of truth — broadcast carries no task ID.
  drainWidgetDoneQueueOnResume$ =
    IS_ANDROID_WEB_VIEW &&
    createEffect(
      () => androidInterface.onResume$.pipe(tap(() => drainWidgetDoneQueue())),
      { dispatch: false },
    );

  drainWidgetDoneQueueOnRequest$ =
    IS_ANDROID_WEB_VIEW &&
    createEffect(
      () =>
        androidInterface.onWidgetDoneDrainRequest$.pipe(
          tap(() => drainWidgetDoneQueue()),
        ),
      { dispatch: false },
    );

  handleWidgetDone$ =
    IS_ANDROID_WEB_VIEW &&
    createEffect(
      () =>
        androidInterface.onWidgetDone$.pipe(
          tap(({ id, isDone }) => {
            DroidLog.log('Widget toggle for task', { id, isDone });
            if (isDone) {
              this._taskService.setDone(id);
            } else {
              this._taskService.setUnDone(id);
            }
            this._snackService.open({
              type: 'SUCCESS',
              msg: isDone
                ? T.GLOBAL_SNACK.WIDGET_TASK_DONE
                : T.GLOBAL_SNACK.WIDGET_TASK_UNDONE,
            });
          }),
        ),
      { dispatch: false },
    );
}
