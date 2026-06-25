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
    tagIds: null,
    isToday: false,
    ...overrides,
  });

  it('uses v=1 and the provided timestamp', () => {
    const snapshot = buildWidgetSnapshot([], [], [], 1700000000000);
    expect(snapshot.v).toBe(1);
    expect(snapshot.ts).toBe(1700000000000);
  });

  it('returns an empty tasks list and empty projects/tags maps for no data', () => {
    const snapshot = buildWidgetSnapshot([], [], [], 0);
    expect(snapshot.tasks).toEqual([]);
    expect(snapshot.projects).toEqual({});
    expect(snapshot.tags).toEqual({});
  });

  it('keeps row order intact and drops the color/projectTitle fields from tasks, defaulting tagIds', () => {
    const rows: WidgetRow[] = [
      row({
        id: 'a',
        title: 'A',
        isDone: false,
        projectId: 'p1',
        color: '#fff',
        tagIds: ['t1'],
        isToday: true,
      }),
      row({ id: 'b', title: 'B', isDone: true, tagIds: null, isToday: false }),
    ];

    const snapshot = buildWidgetSnapshot(rows, [], [], 0);

    expect(snapshot.tasks).toEqual([
      {
        id: 'a',
        title: 'A',
        isDone: false,
        projectId: 'p1',
        tagIds: ['t1'],
        isToday: true,
      },
      { id: 'b', title: 'B', isDone: true, projectId: null, tagIds: [], isToday: false },
    ]);
  });

  it('collects projects from projectsList into the projects map', () => {
    const projectsList = [
      { id: 'p1', title: 'Project One', theme: { primary: '#ff0000' } },
      { id: 'p2', title: 'Project Two', theme: {} },
    ];

    const snapshot = buildWidgetSnapshot([], projectsList, [], 0);

    expect(snapshot.projects).toEqual({
      p1: { title: 'Project One', color: '#ff0000' },
      p2: { title: 'Project Two', color: null },
    });
  });

  it('collects tags from tagsList into the tags map, excluding TODAY', () => {
    const tagsList = [
      { id: 'TODAY', title: 'Today' },
      { id: 't1', title: 'Tag One', color: '#00ff00' },
      { id: 't2', title: 'Tag Two', theme: { primary: '#0000ff' } },
    ];

    const snapshot = buildWidgetSnapshot([], [], tagsList, 0);

    expect(snapshot.tags).toEqual({
      t1: { title: 'Tag One', color: '#00ff00' },
      t2: { title: 'Tag Two', color: '#0000ff' },
    });
  });

  it('falls back to empty string when project title is missing', () => {
    const projectsList = [{ id: 'p1', theme: { primary: '#ff0000' } }];
    const snapshot = buildWidgetSnapshot([], projectsList, [], 0);
    expect(snapshot.projects.p1).toEqual({ title: '', color: '#ff0000' });
  });

  it('falls back to empty string when tag title is missing', () => {
    const tagsList = [{ id: 't1', color: '#00ff00' }];
    const snapshot = buildWidgetSnapshot([], [], tagsList, 0);
    expect(snapshot.tags!.t1).toEqual({ title: '', color: '#00ff00' });
  });
});
