import type { DocumentProgress, Member } from "./types";

/**
 * Who of the teacher's current students has this document. A student who removed their copy counts as missing
 * (sharing again gives them a new one); pending invitations do not count at all.
 */
export function shareCoverage(members: Member[], shared: DocumentProgress[]): { students: number; missing: number } {
  const students = members.filter((m) => m.status === "ACCEPTED" && m.studentId).map((m) => m.studentId!);
  const have = new Set(shared.filter((s) => !s.removedByStudent).map((s) => s.studentId));
  return { students: students.length, missing: students.filter((id) => !have.has(id)).length };
}
