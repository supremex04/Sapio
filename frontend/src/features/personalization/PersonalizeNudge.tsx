import { ArrowRight } from "lucide-react";
import { useEffect, useState } from "react";
import { Emoji } from "../../shared/illustrations/Emoji";
import { ButtonLink } from "../../shared/ui/Button";
import { Card } from "../../shared/ui/Card";
import { personalizationApi } from "./api";

/** Shown on Home until the learner has personalised anything. */
export function PersonalizeNudge() {
  const [show, setShow] = useState(false);
  useEffect(() => {
    personalizationApi.get().then((p) => setShow(!p.configured), () => setShow(false));
  }, []);
  if (!show) return null;
  return (
    <Card tight className="pz-nudge">
      <Emoji name="personalize" size={40} />
      <div className="pz-nudge-text">
        <strong>Make Ved yours</strong>
        <span className="small muted">Tell us your level and how you like to learn; lessons and notes will be written for you.</span>
      </div>
      <ButtonLink to="/personalize" variant="soft" size="sm" iconRight={<ArrowRight size={16} />}>
        Personalize
      </ButtonLink>
    </Card>
  );
}
