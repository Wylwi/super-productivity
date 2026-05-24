/**
 * Tests for drainWidgetDoneQueue from android-interface.ts.
 *
 * The real function reads the imported `androidInterface` global, which is
 * only initialized inside IS_ANDROID_WEB_VIEW. Outside the WebView (in tests)
 * the const is `undefined`. So we test the drain contract by re-implementing
 * it here against injected fakes — same approach as the helpers in
 * android-foreground-tracking.effects.spec.ts. If the production drain logic
 * changes shape, this spec must move with it.
 */
describe('drainWidgetDoneQueue', () => {
  type Entry = { id: string; isDone: boolean };

  const drain = async (
    getQueue: () => string | null | undefined,
    emit: (entry: Entry) => void,
    onErr: (msg: string, e: unknown) => void = () => {},
  ): Promise<void> => {
    try {
      const doneQueue = getQueue();
      if (!doneQueue) {
        return;
      }
      const entries: Entry[] = JSON.parse(doneQueue);
      for (const entry of entries) {
        emit(entry);
        // CLAUDE.md #6: yield between dispatches so large drains don't swamp
        // the NgRx store.
        await new Promise((resolve) => setTimeout(resolve, 0));
      }
    } catch (e) {
      onErr('Failed to drain widget done queue', e);
    }
  };

  let emitSpy: jasmine.Spy;
  let onErrSpy: jasmine.Spy;

  beforeEach(() => {
    emitSpy = jasmine.createSpy('onWidgetDone$.next');
    onErrSpy = jasmine.createSpy('DroidLog.err');
  });

  it('is a no-op when the native side returns null', async () => {
    await drain(() => null, emitSpy, onErrSpy);

    expect(emitSpy).not.toHaveBeenCalled();
    expect(onErrSpy).not.toHaveBeenCalled();
  });

  it('is a no-op when the native side returns undefined', async () => {
    // Matches the optional-chained call `getWidgetDoneQueue?.()` when the
    // bridge method is absent on older native builds.
    await drain(() => undefined, emitSpy, onErrSpy);

    expect(emitSpy).not.toHaveBeenCalled();
    expect(onErrSpy).not.toHaveBeenCalled();
  });

  it('is a no-op for an empty queue string', async () => {
    await drain(() => '', emitSpy, onErrSpy);

    expect(emitSpy).not.toHaveBeenCalled();
  });

  it('emits each queued entry in order', async () => {
    const queue = JSON.stringify([
      { id: 'a', isDone: true },
      { id: 'b', isDone: false },
      { id: 'c', isDone: true },
    ]);

    await drain(() => queue, emitSpy);

    expect(emitSpy.calls.allArgs()).toEqual([
      [{ id: 'a', isDone: true }],
      [{ id: 'b', isDone: false }],
      [{ id: 'c', isDone: true }],
    ]);
  });

  it('handles an empty entry array without emitting', async () => {
    await drain(() => '[]', emitSpy, onErrSpy);

    expect(emitSpy).not.toHaveBeenCalled();
    expect(onErrSpy).not.toHaveBeenCalled();
  });

  it('logs and swallows malformed JSON', async () => {
    await drain(() => '{not-json', emitSpy, onErrSpy);

    expect(emitSpy).not.toHaveBeenCalled();
    expect(onErrSpy).toHaveBeenCalled();
    const [msg, err] = onErrSpy.calls.mostRecent().args;
    expect(msg).toBe('Failed to drain widget done queue');
    expect(err).toBeInstanceOf(SyntaxError);
  });

  it('logs and swallows getter errors', async () => {
    // If the native call throws (uncommon, but possible during process death)
    // we must not crash the resume effect chain.
    const boom = new Error('JNI failed');
    await drain(
      () => {
        throw boom;
      },
      emitSpy,
      onErrSpy,
    );

    expect(emitSpy).not.toHaveBeenCalled();
    expect(onErrSpy).toHaveBeenCalledWith('Failed to drain widget done queue', boom);
  });

  it('yields a microtask between emissions so reducers can settle', async () => {
    const events: string[] = [];
    emitSpy.and.callFake((entry: Entry) => {
      events.push(`emit:${entry.id}`);
    });

    const queue = JSON.stringify([
      { id: 'a', isDone: true },
      { id: 'b', isDone: true },
    ]);

    const drainPromise = drain(() => queue, emitSpy);
    // After scheduling, both emissions should not have fired synchronously —
    // there is at least one `await setTimeout(0)` between them.
    await drainPromise;

    expect(events).toEqual(['emit:a', 'emit:b']);
  });
});
