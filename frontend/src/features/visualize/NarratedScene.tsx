import { Pause, Play, RotateCcw, Volume2, VolumeX } from "lucide-react";
import { useCallback, useEffect, useRef, useState, type CSSProperties, type ReactNode } from "react";
import { beatAt, videoLength } from "./progress";
import type { Beat, Timing } from "./types";

/** What a viewer needs from the clock: the beat playing, how far into it (0..1), and whether time runs. */
export interface SceneClock {
  beat: number;
  progress: number;
  playing: boolean;
  /** The narration has played to its end. */
  ended: boolean;
}

/**
 * A molecule, protein or program shown in time with its narration: one audio track, with each beat starting where
 * the timings say. The viewer draws the beat (highlights, focus, the step of the program); the learner can pause,
 * seek and still turn the 3D view. Without a voice (speech unavailable) the same timings run on a silent clock.
 * When the browser will not start sound on its own (no click yet), it waits with a play button.
 */
export function NarratedScene({ audioUrl, timing, beats, children }: { audioUrl: string | null; timing: Timing[]; beats: Beat[]; children: (clock: SceneClock) => ReactNode }) {
  const audio = useRef<HTMLAudioElement>(null);
  const frame = useRef(0);
  const silentStart = useRef<number | null>(null);
  const silentOffset = useRef(0);
  const last = timing[timing.length - 1];
  const duration = last ? last.start + last.seconds : 0;
  const [time, setTime] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [muted, setMuted] = useState(false);
  /** The browser would not start sound without a click: wait for one (a muted narration says nothing). */
  const [blocked, setBlocked] = useState(false);

  const now = useCallback(() => {
    if (audio.current && audioUrl) return audio.current.ended ? duration : audio.current.currentTime;
    if (silentStart.current == null) return silentOffset.current;
    return Math.min(duration, silentOffset.current + (performance.now() - silentStart.current) / 1000);
  }, [audioUrl, duration]);

  const stopSilent = useCallback(() => {
    silentOffset.current = now();
    silentStart.current = null;
  }, [now]);

  // Follow the clock every frame while playing.
  useEffect(() => {
    if (!playing) return;
    const tick = () => {
      const t = now();
      setTime(t);
      if (!audioUrl && t >= duration) {
        stopSilent();
        setPlaying(false);
        return;
      }
      frame.current = requestAnimationFrame(tick);
    };
    frame.current = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame.current);
  }, [playing, now, audioUrl, duration, stopSilent]);

  const play = useCallback(() => {
    if (audioUrl && audio.current) {
      const a = audio.current;
      if (a.ended) a.currentTime = 0;
      a.play().then(
        () => {
          setBlocked(false);
          setPlaying(true);
        },
        () => setBlocked(true),
      );
    } else {
      if (silentOffset.current >= duration) silentOffset.current = 0;
      silentStart.current = performance.now();
      setPlaying(true);
    }
  }, [audioUrl, duration]);

  const pause = useCallback(() => {
    if (audioUrl) audio.current?.pause();
    else stopSilent();
    setPlaying(false);
    setTime(now());
  }, [audioUrl, now, stopSilent]);

  useEffect(() => {
    play();
    const a = audio.current;
    return () => a?.pause();
  }, [play]);

  const seek = (to: number) => {
    setBlocked(false); // the learner is using the controls: the prompt has done its job
    if (audioUrl && audio.current) audio.current.currentTime = to;
    else {
      silentOffset.current = to;
      if (silentStart.current != null) silentStart.current = performance.now();
    }
    setTime(to);
  };

  const ended = !playing && duration > 0 && time >= duration - 0.05;
  const beat = beatAt(timing, time);
  const span = timing[beat];
  const progress = span && span.seconds > 0 ? Math.min(1, Math.max(0, (time - span.start) / span.seconds)) : 0;
  const label = ended ? "Play again" : playing ? "Pause" : "Play";

  return (
    <div className="viz-scene">
      {audioUrl && (
        <audio
          ref={audio}
          src={audioUrl}
          preload="auto"
          onEnded={() => {
            setPlaying(false);
            setTime(duration);
          }}
          onPause={() => setPlaying(false)}
        />
      )}
      <div className="viz-scene-view">
        {children({ beat, progress, playing, ended })}
        {beats[beat]?.says && <p className="viz-subtitle">{beats[beat].says}</p>}
        {blocked && !playing && (
          <button type="button" className="viz-start" onClick={play}>
            <Play size={22} aria-hidden /> Play with narration
          </button>
        )}
      </div>
      <div className="viz-controls viz-scene-controls">
        <button type="button" className="viz-control" onClick={() => (playing ? pause() : play())} aria-label={label} title={label}>
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
          value={Math.min(time, duration)}
          onChange={(e) => seek(Number(e.target.value))}
          aria-label="Position in the narration"
          aria-valuetext={`${videoLength(time)} of ${videoLength(duration)}`}
          style={{ "--fill": `${duration ? (time / duration) * 100 : 0}%` } as CSSProperties}
        />
        {audioUrl && (
          <button
            type="button"
            className="viz-control"
            onClick={() => {
              const a = audio.current;
              if (!a) return;
              a.muted = !a.muted;
              setMuted(a.muted);
            }}
            aria-label={muted ? "Turn sound on" : "Mute"}
            aria-pressed={muted}
            title={muted ? "Turn sound on" : "Mute"}
          >
            {muted ? <VolumeX size={18} /> : <Volume2 size={18} />}
          </button>
        )}
      </div>
    </div>
  );
}
