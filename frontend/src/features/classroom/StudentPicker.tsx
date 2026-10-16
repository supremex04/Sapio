import { useEffect, useState } from "react";
import { Link } from "react-router";
import { SkeletonText } from "../../shared/ui/Skeleton";
import { classroomApi } from "./api";
import type { Member } from "./types";

/**
 * Choose students: everyone (the default) or a selection. ``done``: students who already have it (shown, not
 * selectable). Calls onChange with [] for "everyone".
 */
export function StudentPicker({ done = [], onChange }: { done?: string[]; onChange: (ids: string[], count: number) => void }) {
  const [members, setMembers] = useState<Member[] | null>(null);
  const [everyone, setEveryone] = useState(true);
  const [picked, setPicked] = useState<string[]>([]);

  useEffect(() => {
    classroomApi.roster().then((m) => setMembers(m.filter((x) => x.status === "ACCEPTED")), () => setMembers([]));
  }, []);
  const open = (members ?? []).filter((m) => m.studentId && !done.includes(m.studentId));

  useEffect(() => {
    onChange(everyone ? [] : picked, everyone ? open.length : picked.length);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [everyone, picked, members]);

  if (!members) return <SkeletonText lines={3} />;
  if (members.length === 0) {
    return (
      <p className="small muted">
        You have no students yet. <Link to="/students">Invite them first.</Link>
      </p>
    );
  }
  return (
    <div className="stack-sm cls-picker">
      <label className="row small">
        <input type="checkbox" checked={everyone} onChange={(e) => setEveryone(e.target.checked)} />
        <strong>All my students</strong>
        <span className="muted">({open.length} without it yet)</span>
      </label>
      {!everyone && (
        <ul className="cls-picker-list">
          {members.map((m) => {
            const has = !!m.studentId && done.includes(m.studentId);
            return (
              <li key={m.id}>
                <label className="row small">
                  <input
                    type="checkbox"
                    disabled={has}
                    checked={has || picked.includes(m.studentId!)}
                    onChange={(e) => setPicked((p) => (e.target.checked ? [...p, m.studentId!] : p.filter((x) => x !== m.studentId)))}
                  />
                  {m.name} <span className="muted">{has ? "· has it" : m.email}</span>
                </label>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
