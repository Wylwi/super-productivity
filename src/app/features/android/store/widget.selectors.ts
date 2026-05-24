import { createSelector } from '@ngrx/store';
import { selectTodayTaskIds } from '../../work-context/store/work-context.selectors';
import { selectTaskEntities } from '../../tasks/store/task.selectors';
import { selectAllProjectColorsAndTitles } from '../../project/store/project.selectors';

export interface WidgetRow {
  id: string;
  title: string;
  isDone: boolean;
  projectId: string | null;
  color: string | null;
  projectTitle: string | null;
}

type ProjectColorsAndTitles = Record<string, { color?: string | null; title?: string }>;

export const selectTodayWidgetRows = createSelector(
  selectTodayTaskIds,
  selectTaskEntities,
  selectAllProjectColorsAndTitles,
  (todayIds, entities, projects): WidgetRow[] => {
    const projectMap = projects as ProjectColorsAndTitles;
    const undone: WidgetRow[] = [];
    const done: WidgetRow[] = [];
    for (const id of todayIds) {
      const t = entities[id];
      if (!t) continue;
      const projectId = t.projectId || null;
      const info = projectId ? projectMap[projectId] : undefined;
      const row: WidgetRow = {
        id: t.id,
        title: t.title,
        isDone: t.isDone,
        projectId,
        color: info?.color ?? null,
        projectTitle: info?.title ?? null,
      };
      (t.isDone ? done : undone).push(row);
    }
    return [...undone, ...done];
  },
);
