import { createSelector } from '@ngrx/store';
import { selectTodayTaskIds } from '../../work-context/store/work-context.selectors';
import { selectAllTasks } from '../../tasks/store/task.selectors';
import { selectAllProjectColorsAndTitles } from '../../project/store/project.selectors';

export interface WidgetRow {
  id: string;
  title: string;
  isDone: boolean;
  projectId: string | null;
  color: string | null;
  projectTitle: string | null;
  tagIds: string[] | null;
  isToday: boolean;
}

type ProjectColorsAndTitles = Record<string, { color?: string | null; title?: string }>;

export const selectTodayWidgetRows = createSelector(
  selectTodayTaskIds,
  selectAllTasks,
  selectAllProjectColorsAndTitles,
  (todayIds, allTasks, projects): WidgetRow[] => {
    const projectMap = projects as ProjectColorsAndTitles;
    const todaySet = new Set(todayIds);
    const undone: WidgetRow[] = [];
    const done: WidgetRow[] = [];
    for (const t of allTasks) {
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
        tagIds: t.tagIds || [],
        isToday: todaySet.has(t.id),
      };
      (t.isDone ? done : undone).push(row);
    }
    return [...undone, ...done];
  },
);
