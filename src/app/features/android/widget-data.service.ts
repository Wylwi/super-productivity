import { inject, Injectable } from '@angular/core';
import { Store } from '@ngrx/store';
import { firstValueFrom } from 'rxjs';
import { createValidate } from 'typia';
import { selectTodayWidgetRows, WidgetRow } from './store/widget.selectors';
import { androidInterface } from './android-interface';
import { DroidLog } from '../../core/log';
import { HydrationStateService } from '../../op-log/apply/hydration-state.service';
import { WidgetSnapshotV1 } from './android.model';

const _validateWidgetSnapshot = createValidate<WidgetSnapshotV1>();

export const buildWidgetSnapshot = (rows: WidgetRow[], ts: number): WidgetSnapshotV1 => {
  const tasks = rows.map((r) => ({
    id: r.id,
    title: r.title,
    isDone: r.isDone,
    projectId: r.projectId,
  }));

  const projects: Record<string, { title: string; color: string | null }> = {};
  for (const r of rows) {
    if (r.projectId && !projects[r.projectId]) {
      projects[r.projectId] = {
        title: r.projectTitle ?? '',
        color: r.color,
      };
    }
  }

  return { v: 1, ts, tasks, projects };
};

@Injectable({ providedIn: 'root' })
export class WidgetDataService {
  private _store = inject(Store);
  private _hydrationState = inject(HydrationStateService);

  async serialize(): Promise<void> {
    if (this._hydrationState.isApplyingRemoteOps()) {
      return;
    }

    const rows: WidgetRow[] = await firstValueFrom(
      this._store.select(selectTodayWidgetRows),
    );

    const snapshot = buildWidgetSnapshot(rows, Date.now());

    const validation = _validateWidgetSnapshot(snapshot);
    if (!validation.success) {
      DroidLog.err('Widget snapshot validation failed', validation.errors);
      return;
    }

    try {
      await androidInterface.saveToDbWrapped('widget_data', JSON.stringify(snapshot));
      androidInterface.updateWidget?.();
    } catch (e) {
      DroidLog.err('Failed to push widget data', e);
    }
  }
}
