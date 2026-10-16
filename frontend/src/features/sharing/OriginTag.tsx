import { Link } from "react-router";
import { Badge } from "../../shared/ui/Badge";
import { Emoji } from "../../shared/illustrations/Emoji";
import type { DocumentDto } from "../documents/types";

/** "Imported" tag on a copy from Explore: who shared the original, linking to the listing while it exists. */
export function OriginTag({ origin, compact }: { origin: NonNullable<DocumentDto["origin"]>; compact?: boolean }) {
  if (origin.kind === "CLASS") {
    return (
      <span className="origin-tag" title={`Shared by your teacher ${origin.sharedBy}`}>
        <Badge tone="primary" icon={<Emoji name="teacher" size={16} />}>
          From your teacher
        </Badge>
        {!compact && <span className="xs muted break">{origin.sharedBy}</span>}
      </span>
    );
  }
  const label = `Imported from ${origin.sharedBy}`;
  return (
    <span className="origin-tag" title={label}>
      <Badge tone="accent" icon={<Emoji name="inbox-tray" size={16} />}>
        Imported
      </Badge>
      {!compact && (
        <span className="xs muted break">
          from{" "}
          {origin.shareId ? (
            <Link to={`/explore/${origin.shareId}`} className="origin-link">
              {origin.sharedBy}
            </Link>
          ) : (
            origin.sharedBy
          )}
        </span>
      )}
    </span>
  );
}
