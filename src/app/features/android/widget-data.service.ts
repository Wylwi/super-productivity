import { inject, Injectable } from '@angular/core';
import { Store } from '@ngrx/store';
import { firstValueFrom } from 'rxjs';
import { createValidate } from 'typia';
import { selectTodayWidgetRows, WidgetRow } from './store/widget.selectors';
import { selectUnarchivedProjects } from '../project/store/project.selectors';
import { selectAllTags } from '../tag/store/tag.reducer';
import { androidInterface } from './android-interface';
import { DroidLog } from '../../core/log';
import { HydrationStateService } from '../../op-log/apply/hydration-state.service';
import { WidgetSnapshotV1 } from './android.model';

const _validateWidgetSnapshot = createValidate<WidgetSnapshotV1>();

export const buildWidgetSnapshot = (
  rows: WidgetRow[],
  projectsList: any[],
  tagsList: any[],
  ts: number,
): WidgetSnapshotV1 => {
  const tasks = rows.map((r) => ({
    id: r.id,
    title: r.title,
    isDone: r.isDone,
    projectId: r.projectId || null,
    tagIds: r.tagIds || [],
    isToday: r.isToday || false,
  }));

  const projects: Record<string, { title: string; color: string | null }> = {};
  for (const p of projectsList) {
    if (p && p.id) {
      projects[p.id] = {
        title: p.title || '',
        color: p.theme?.primary || null,
      };
    }
  }

  const tags: Record<string, { title: string; color: string | null }> = {};
  for (const t of tagsList) {
    if (t && t.id && t.id !== 'TODAY') {
      tags[t.id] = {
        title: t.title || '',
        color: t.color || t.theme?.primary || null,
      };
    }
  }

  return { v: 1, ts, tasks, projects, tags };
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

    const projectsList = await firstValueFrom(
      this._store.select(selectUnarchivedProjects),
    );

    const tagsList = await firstValueFrom(this._store.select(selectAllTags));

    const snapshot = buildWidgetSnapshot(rows, projectsList, tagsList, Date.now());

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
