import { describe, expect, it } from "vitest";
import { isCollapsed } from "./sidebarState";

describe("isCollapsed", () => {
  it("follows the screen width until the user chooses", () => {
    expect(isCollapsed(null, 1280)).toBe(true);
    expect(isCollapsed(null, 1920)).toBe(false);
  });

  it("keeps the user's choice at any width", () => {
    expect(isCollapsed("expanded", 1280)).toBe(false);
    expect(isCollapsed("collapsed", 1920)).toBe(true);
  });
});
