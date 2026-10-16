import { describe, expect, it } from "vitest";
import { shareCoverage } from "./shareCoverage";
import type { DocumentProgress, Member } from "./types";

const member = (id: string, status: Member["status"] = "ACCEPTED"): Member => ({
  id: `m-${id}`,
  studentId: status === "ACCEPTED" ? id : null,
  email: `${id}@x.org`,
  name: id,
  status,
  invitedAt: "",
  respondedAt: null,
});
const share = (id: string, removed = false) => ({ studentId: id, removedByStudent: removed }) as DocumentProgress;

describe("shareCoverage", () => {
  it("counts the current students who do not have the document", () => {
    const members = [member("a"), member("b"), member("c"), member("d", "PENDING")];
    expect(shareCoverage(members, [share("a"), share("b", true)])).toEqual({ students: 3, missing: 2 });
  });

  it("is complete when everyone has it, and empty without students", () => {
    expect(shareCoverage([member("a")], [share("a")])).toEqual({ students: 1, missing: 0 });
    expect(shareCoverage([], [])).toEqual({ students: 0, missing: 0 });
  });
});
