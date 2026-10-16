import { Maximize, Pause, Play, RotateCcw } from "lucide-react";
import { useEffect, useRef, useState, type CSSProperties } from "react";
import { videoLength } from "./progress";

/**
 * The visualization player. Our own controls instead of the browser's: Chrome stops redrawing its native timeline
 * while the controls are hidden during playback and does not catch up when the video ends, so the bar stopped
 * short of the end. Here the bar follows the video's own clock, every frame while playing.
 */
export function VideoPlayer({ src, poster }: { src: string; poster?: string }) {
  const video = useRef<HTMLVideoElement>(null);
  const frame = useRef(0);
  const [time, setTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [ended, setEnded] = useState(false);

  useEffect(() => {
    const v = video.current;
    if (!v) return;
    const sync = () => {
      setTime(v.ended ? v.duration : v.currentTime);
      setEnded(v.ended);
    };
    const tick = () => {
      sync();
      frame.current = requestAnimationFrame(tick);
    };
    const onPlay = () => {
      setPlaying(true);
      cancelAnimationFrame(frame.current);
      frame.current = requestAnimationFrame(tick);
    };
    const onStop = () => {
      setPlaying(false);
      cancelAnimationFrame(frame.current);
      sync();
    };
    const onMeta = () => setDuration(Number.isFinite(v.duration) ? v.duration : 0);
    v.addEventListener("play", onPlay);
    v.addEventListener("pause", onStop);
    v.addEventListener("ended", onStop);
    v.addEventListener("seeked", sync);
    v.addEventListener("loadedmetadata", onMeta);
    v.addEventListener("durationchange", onMeta);
    onMeta();
    return () => {
      cancelAnimationFrame(frame.current);
      v.removeEventListener("play", onPlay);
      v.removeEventListener("pause", onStop);
      v.removeEventListener("ended", onStop);
      v.removeEventListener("seeked", sync);
      v.removeEventListener("loadedmetadata", onMeta);
      v.removeEventListener("durationchange", onMeta);
    };
  }, [src]);

  const toggle = () => {
    const v = video.current;
    if (!v) return;
    if (v.paused || v.ended) void v.play().catch(() => undefined);
    else v.pause();
  };

  const seek = (to: number) => {
    const v = video.current;
    if (!v) return;
    v.currentTime = to;
    setTime(to);
  };

  const fraction = duration > 0 ? Math.min(1, time / duration) : 0;
  const label = ended ? "Play again" : playing ? "Pause" : "Play";

  return (
    <div className="viz-player" data-playing={playing || undefined}>
      <video
        ref={video}
        className="viz-video"
        src={src}
        poster={poster}
        autoPlay
        muted
        playsInline
        onClick={toggle}
        onKeyDown={(e) => {
          if (e.key === " " || e.key === "k") {
            e.preventDefault();
            toggle();
          }
        }}
      />
      <div className="viz-controls">
        <button type="button" className="viz-control" onClick={toggle} aria-label={label} title={label}>
          {ended ? <RotateCcw size={18} /> : playing ? <Pause size={18} /> : <Play size={18} />}
        </button>
        <span className="viz-time" aria-hidden>
          {videoLength(time)} / {videoLength(duration)}
        </span>
        <input
          type="range"
          className="viz-seek"
          min={0}
          max={duration || 0}
          step={0.01}
          value={Math.min(time, duration || 0)}
          onChange={(e) => seek(Number(e.target.value))}
          aria-label="Position in the video"
          aria-valuetext={`${videoLength(time)} of ${videoLength(duration)}`}
          style={{ "--fill": `${fraction * 100}%` } as CSSProperties}
        />
        <button
          type="button"
          className="viz-control"
          onClick={() => void video.current?.requestFullscreen?.().catch(() => undefined)}
          aria-label="Full screen"
          title="Full screen"
        >
          <Maximize size={18} />
        </button>
      </div>
    </div>
  );
}
