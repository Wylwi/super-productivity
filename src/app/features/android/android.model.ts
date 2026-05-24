export interface WidgetSnapshotV1 {
  v: 1;
  ts: number;
  tasks: {
    id: string;
    title: string;
    isDone: boolean;
    projectId: string | null;
  }[];
  projects: Record<string, { title: string; color: string | null }>;
}
