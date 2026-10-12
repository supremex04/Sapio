import { describe, expect, it } from "vitest";
import { repairTables, splitRow } from "./tables";

describe("repairTables", () => {
  it("keeps cell line breaks inside the row", () => {
    const md = "|Strategy|How the boundary is<br>chosen|\n|---|---|\n|By business<br>capability|What the business<br>_does_|";
    expect(repairTables(md)).toBe("|Strategy|How the boundary is chosen|\n|---|---|\n|By business capability|What the business _does_|");
  });

  it("merges the wrapped tail of a cell back into its row", () => {
    const md = [
      "|Model|Isolation|Cost|Notes|",
      "|---|---|---|---|",
      "|Shared schema,|Logical|Lowest|Every query must filter by tenant;|",
      "|`tenant_id`column|||enforce with Hibernate filters or|",
      "||||PostgreSQL Row-Level Security|",
      "|Schema per tenant|Stronger|Medium|Migrations run N times|",
    ].join("\n");
    expect(repairTables(md).split("\n")).toEqual([
      "|Model|Isolation|Cost|Notes|",
      "|---|---|---|---|",
      "|Shared schema, `tenant_id`column|Logical|Lowest|Every query must filter by tenant; enforce with Hibernate filters or PostgreSQL Row-Level Security|",
      "|Schema per tenant|Stronger|Medium|Migrations run N times|",
    ]);
  });

  it("leaves tables that genuinely have empty cells alone", () => {
    const md = "|A|B|\n|---|---|\n|x||\n|y||";
    expect(repairTables(md)).toBe(md);
  });

  it("does not touch code blocks or non-table text", () => {
    const md = "```\n|a|b|\n|---|---|\n|c||\n```\nText with a | pipe";
    expect(repairTables(md)).toBe(md);
  });

  it("joins a table continued after a page break", () => {
    const md = "|A|B|\n|---|---|\n|1|2|\n\n---\n\n|A|B|\n|---|---|\n|3|4|\n\nAfter.";
    expect(repairTables(md)).toBe("|A|B|\n|---|---|\n|1|2|\n|3|4|\n\nAfter.");
  });

  it("keeps tables with different headers apart", () => {
    const md = "|A|B|\n|---|---|\n|1|2|\n\n|C|D|\n|---|---|\n|3|4|";
    expect(repairTables(md)).toBe(md);
  });

  it("splits on pipes outside inline code only", () => {
    expect(splitRow("|`a|b`|c|")).toEqual(["`a|b`", "c"]);
  });
});
