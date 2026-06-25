import { Task } from '../../tasks/task.model';
import { selectTodayWidgetRows } from './widget.selectors';

describe('selectTodayWidgetRows', () => {
  const task = (overrides: Partial<Task>): Task =>
    ({
      id: 'T',
      title: 'Task',
      isDone: false,
      projectId: undefined,
      ...overrides,
    }) as Task;

  it('returns empty list when there are no today task ids', () => {
    expect(selectTodayWidgetRows.projector([], [], {})).toEqual([]);
  });

  it('puts undone rows first, then done rows, preserving relative order', () => {
    const tasks = [
      task({ id: 'a', title: 'A', isDone: true }),
      task({ id: 'b', title: 'B', isDone: false }),
      task({ id: 'c', title: 'C', isDone: true }),
      task({ id: 'd', title: 'D', isDone: false }),
    ];

    const result = selectTodayWidgetRows.projector(['a', 'b', 'c', 'd'], tasks, {});

    expect(result.map((r) => r.id)).toEqual(['b', 'd', 'a', 'c']);
  });

  it('hydrates project color and title from the project map', () => {
    const tasks = [task({ id: 't1', projectId: 'p1' })];
    const result = selectTodayWidgetRows.projector(['t1'], tasks, {
      p1: { color: '#ff0000', title: 'Project One' },
    });

    expect(result).toEqual([
      {
        id: 't1',
        title: 'Task',
        isDone: false,
        projectId: 'p1',
        color: '#ff0000',
        projectTitle: 'Project One',
        tagIds: [],
        isToday: true,
      },
    ]);
  });

  it('returns null project fields when the task has no projectId', () => {
    const tasks = [task({ id: 't1', projectId: undefined })];
    const result = selectTodayWidgetRows.projector(['t1'], tasks, {});

    expect(result[0].projectId).toBeNull();
    expect(result[0].color).toBeNull();
    expect(result[0].projectTitle).toBeNull();
  });

  it('returns null project fields when the task projectId is missing from the project map', () => {
    // The map may lag the task store by a tick; the selector must not crash.
    const tasks = [task({ id: 't1', projectId: 'gone' })];
    const result = selectTodayWidgetRows.projector(['t1'], tasks, {});

    expect(result[0].projectId).toBe('gone');
    expect(result[0].color).toBeNull();
    expect(result[0].projectTitle).toBeNull();
  });

  it('skips task ids that have no matching entity', () => {
    const tasks = [task({ id: 'a' })];
    const result = selectTodayWidgetRows.projector(['missing', 'a'], tasks, {});

    expect(result.map((r) => r.id)).toEqual(['a']);
    expect(result[0].isToday).toBe(true);
  });

  it('falls back to null color/title when the project entry omits them', () => {
    const tasks = [task({ id: 't1', projectId: 'p1' })];
    const result = selectTodayWidgetRows.projector(['t1'], tasks, {
      p1: {},
    });

    expect(result[0].color).toBeNull();
    expect(result[0].projectTitle).toBeNull();
  });
});
