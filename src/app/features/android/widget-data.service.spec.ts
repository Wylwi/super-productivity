import { buildWidgetSnapshot } from './widget-data.service';
import { WidgetRow } from './store/widget.selectors';

describe('buildWidgetSnapshot', () => {
  const row = (overrides: Partial<WidgetRow>): WidgetRow => ({
    id: 'r',
    title: 'Row',
    isDone: false,
    projectId: null,
    color: null,
    projectTitle: null,
    ...overrides,
  });

  it('uses v=1 and the provided timestamp', () => {
    const snapshot = buildWidgetSnapshot([], 1700000000000);
    expect(snapshot.v).toBe(1);
    expect(snapshot.ts).toBe(1700000000000);
  });

  it('returns an empty tasks list and empty project map for no rows', () => {
    const snapshot = buildWidgetSnapshot([], 0);
    expect(snapshot.tasks).toEqual([]);
    expect(snapshot.projects).toEqual({});
  });

  it('keeps row order intact and drops the color/projectTitle fields from tasks', () => {
    const rows: WidgetRow[] = [
      row({ id: 'a', title: 'A', isDone: false, projectId: 'p1', color: '#fff' }),
      row({ id: 'b', title: 'B', isDone: true }),
    ];

    const snapshot = buildWidgetSnapshot(rows, 0);

    expect(snapshot.tasks).toEqual([
      { id: 'a', title: 'A', isDone: false, projectId: 'p1' },
      { id: 'b', title: 'B', isDone: true, projectId: null },
    ]);
  });

  it('collects each referenced project once into the projects map', () => {
    const rows: WidgetRow[] = [
      row({
        id: 'a',
        projectId: 'p1',
        projectTitle: 'Project One',
        color: '#ff0000',
      }),
      row({ id: 'b', projectId: 'p1', projectTitle: 'Project One', color: '#ff0000' }),
      row({
        id: 'c',
        projectId: 'p2',
        projectTitle: 'Project Two',
        color: '#00ff00',
      }),
    ];

    const snapshot = buildWidgetSnapshot(rows, 0);

    expect(snapshot.projects).toEqual({
      p1: { title: 'Project One', color: '#ff0000' },
      p2: { title: 'Project Two', color: '#00ff00' },
    });
  });

  it('keeps the first project entry when later rows have different metadata', () => {
    // Defensive: input projectTitle/color should be consistent per id, but
    // if the upstream selector races, the snapshot should not flip mid-build.
    const rows: WidgetRow[] = [
      row({ id: 'a', projectId: 'p1', projectTitle: 'First', color: '#111' }),
      row({ id: 'b', projectId: 'p1', projectTitle: 'Later', color: '#222' }),
    ];

    const snapshot = buildWidgetSnapshot(rows, 0);

    expect(snapshot.projects.p1).toEqual({ title: 'First', color: '#111' });
  });

  it('falls back to an empty title when projectTitle is null', () => {
    const rows: WidgetRow[] = [
      row({ id: 'a', projectId: 'p1', projectTitle: null, color: '#abc' }),
    ];

    const snapshot = buildWidgetSnapshot(rows, 0);

    expect(snapshot.projects.p1).toEqual({ title: '', color: '#abc' });
  });

  it('preserves null color in the project map', () => {
    const rows: WidgetRow[] = [
      row({ id: 'a', projectId: 'p1', projectTitle: 'P1', color: null }),
    ];

    const snapshot = buildWidgetSnapshot(rows, 0);

    expect(snapshot.projects.p1).toEqual({ title: 'P1', color: null });
  });

  it('does not emit an entry for rows without a projectId', () => {
    const rows: WidgetRow[] = [row({ id: 'a', projectId: null })];

    const snapshot = buildWidgetSnapshot(rows, 0);

    expect(snapshot.projects).toEqual({});
  });
});
