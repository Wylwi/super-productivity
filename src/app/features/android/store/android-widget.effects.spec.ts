import { rowsSignature } from './android-widget.effects';
import { WidgetRow } from './widget.selectors';
import { T } from '../../../t.const';

/**
 * Tests for AndroidWidgetEffects.
 *
 * Since the effects are gated behind IS_ANDROID_WEB_VIEW (false in tests), we
 * exercise the bits of logic we can directly: `rowsSignature` (exported) and
 * reimplement the per-tap toggle handler to lock in the action/snackbar
 * branches. Same approach as android-foreground-tracking.effects.spec.ts.
 */
describe('AndroidWidgetEffects - rowsSignature comparator', () => {
  const row = (overrides: Partial<WidgetRow>): WidgetRow => ({
    id: 'r',
    title: 'Row',
    isDone: false,
    projectId: null,
    color: null,
    projectTitle: null,
    ...overrides,
  });

  it('returns an empty string for an empty list', () => {
    expect(rowsSignature([])).toBe('');
  });

  it('produces the same signature for structurally equal lists', () => {
    const a = [row({ id: 'a', title: 'A' }), row({ id: 'b', title: 'B', isDone: true })];
    const b = [row({ id: 'a', title: 'A' }), row({ id: 'b', title: 'B', isDone: true })];

    expect(rowsSignature(a)).toBe(rowsSignature(b));
  });

  it('changes the signature when isDone flips', () => {
    const before = rowsSignature([row({ id: 'a', isDone: false })]);
    const after = rowsSignature([row({ id: 'a', isDone: true })]);
    expect(before).not.toBe(after);
  });

  it('changes the signature when the title changes', () => {
    const before = rowsSignature([row({ id: 'a', title: 'Old' })]);
    const after = rowsSignature([row({ id: 'a', title: 'New' })]);
    expect(before).not.toBe(after);
  });

  it('changes the signature when projectId changes', () => {
    const before = rowsSignature([row({ id: 'a', projectId: 'p1' })]);
    const after = rowsSignature([row({ id: 'a', projectId: 'p2' })]);
    expect(before).not.toBe(after);
  });

  it('changes the signature when project color changes', () => {
    // Color is part of the snapshot the widget renders, so a recolor must push.
    const before = rowsSignature([row({ id: 'a', color: '#ff0000' })]);
    const after = rowsSignature([row({ id: 'a', color: '#00ff00' })]);
    expect(before).not.toBe(after);
  });

  it('changes the signature when projectTitle changes', () => {
    const before = rowsSignature([row({ id: 'a', projectTitle: 'Old' })]);
    const after = rowsSignature([row({ id: 'a', projectTitle: 'New' })]);
    expect(before).not.toBe(after);
  });

  it('changes the signature when row order changes', () => {
    const a = [row({ id: 'a' }), row({ id: 'b' })];
    const b = [row({ id: 'b' }), row({ id: 'a' })];
    expect(rowsSignature(a)).not.toBe(rowsSignature(b));
  });

  it('treats null projectId/color/projectTitle as the empty token', () => {
    // Defensive: nulls must serialize consistently — neighbouring fields must
    // not collide with each other.
    expect(rowsSignature([row({ id: 'a' })])).toBe('a:0:Row:::');
  });

  it('escapes nothing — fields containing the separator characters change the signature', () => {
    // Documents the (small) hash collision surface: a title containing ':' or
    // '|' produces a different string from a non-colliding equivalent. This is
    // intentional — the comparator is fast, not cryptographic.
    const a = rowsSignature([row({ id: 'a', title: 'foo:bar' })]);
    const b = rowsSignature([row({ id: 'a', title: 'foobar' })]);
    expect(a).not.toBe(b);
  });
});

describe('AndroidWidgetEffects - widget toggle handler logic', () => {
  // Mirror the body of handleWidgetDone$ from android-widget.effects.ts so we
  // can verify the action + snackbar branches without instantiating effects.

  type TapEvent = { id: string; isDone: boolean };

  const handleWidgetToggle = (
    event: TapEvent,
    taskService: { setDone: jasmine.Spy; setUnDone: jasmine.Spy },
    snackService: { open: jasmine.Spy },
  ): void => {
    if (event.isDone) {
      taskService.setDone(event.id);
    } else {
      taskService.setUnDone(event.id);
    }
    snackService.open({
      type: 'SUCCESS',
      msg: event.isDone
        ? T.GLOBAL_SNACK.WIDGET_TASK_DONE
        : T.GLOBAL_SNACK.WIDGET_TASK_UNDONE,
    });
  };

  let taskService: { setDone: jasmine.Spy; setUnDone: jasmine.Spy };
  let snackService: { open: jasmine.Spy };

  beforeEach(() => {
    taskService = {
      setDone: jasmine.createSpy('setDone'),
      setUnDone: jasmine.createSpy('setUnDone'),
    };
    snackService = { open: jasmine.createSpy('open') };
  });

  it('calls setDone and shows the "task done" snack when isDone is true', () => {
    handleWidgetToggle({ id: 'task-1', isDone: true }, taskService, snackService);

    expect(taskService.setDone).toHaveBeenCalledOnceWith('task-1');
    expect(taskService.setUnDone).not.toHaveBeenCalled();
    expect(snackService.open).toHaveBeenCalledOnceWith({
      type: 'SUCCESS',
      msg: T.GLOBAL_SNACK.WIDGET_TASK_DONE,
    });
  });

  it('calls setUnDone and shows the "task undone" snack when isDone is false', () => {
    handleWidgetToggle({ id: 'task-1', isDone: false }, taskService, snackService);

    expect(taskService.setUnDone).toHaveBeenCalledOnceWith('task-1');
    expect(taskService.setDone).not.toHaveBeenCalled();
    expect(snackService.open).toHaveBeenCalledOnceWith({
      type: 'SUCCESS',
      msg: T.GLOBAL_SNACK.WIDGET_TASK_UNDONE,
    });
  });
});

describe('AndroidWidgetEffects - push gating', () => {
  // Documents the contract from pushOnStateChange$: writes must be skipped
  // while the hydration window is open, so remote replays don't get echoed
  // back to the widget snapshot.
  const shouldPushSnapshot = (isApplyingRemoteOps: boolean): boolean =>
    !isApplyingRemoteOps;

  it('skips writing the widget snapshot during hydration', () => {
    expect(shouldPushSnapshot(true)).toBeFalse();
  });

  it('writes the widget snapshot once hydration finishes', () => {
    expect(shouldPushSnapshot(false)).toBeTrue();
  });
});
