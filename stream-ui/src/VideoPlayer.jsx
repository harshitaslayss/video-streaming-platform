import { useEffect, useRef, useState } from "react";
import Hls from "hls.js";

/**
 * Plays an HLS stream and exposes the adaptive-bitrate ladder.
 * `src` is the master playlist URL returned by streaming-service.
 */
export default function VideoPlayer({ src, title }) {
  const videoRef = useRef(null);
  const hlsRef = useRef(null);

  const [levels, setLevels] = useState([]); // [{height, bitrate}]
  const [selected, setSelected] = useState(-1); // -1 = auto
  const [playingLevel, setPlayingLevel] = useState(-1);
  const [error, setError] = useState(null);

  useEffect(() => {
    const video = videoRef.current;
    if (!video || !src) return;

    setError(null);
    setLevels([]);
    setSelected(-1);
    setPlayingLevel(-1);

    // Safari / iOS play HLS natively.
    if (!Hls.isSupported()) {
      if (video.canPlayType("application/vnd.apple.mpegurl")) {
        video.src = src;
      } else {
        setError("This browser can't play HLS streams.");
      }
      return;
    }

    const hls = new Hls({ maxBufferLength: 30 });
    hlsRef.current = hls;

    hls.on(Hls.Events.MANIFEST_PARSED, (_, data) => {
      setLevels(data.levels.map((l) => ({ height: l.height, bitrate: l.bitrate })));
    });
    hls.on(Hls.Events.LEVEL_SWITCHED, (_, data) => setPlayingLevel(data.level));

    hls.on(Hls.Events.ERROR, (_, data) => {
      if (!data.fatal) return;
      const status = data.response?.code;
      if (data.type === Hls.ErrorTypes.NETWORK_ERROR) {
        setError(
          status === 403
            ? "The stream was refused (403). The playlist or segment URL isn't signed, or has expired."
            : "Network error while loading the stream. Check that the S3 bucket allows CORS from this origin."
        );
        hls.destroy();
      } else if (data.type === Hls.ErrorTypes.MEDIA_ERROR) {
        hls.recoverMediaError();
      } else {
        setError("The stream could not be played.");
        hls.destroy();
      }
    });

    hls.loadSource(src);
    hls.attachMedia(video);

    return () => {
      hls.destroy();
      hlsRef.current = null;
    };
  }, [src]);

  function pickLevel(e) {
    const value = Number(e.target.value);
    setSelected(value);
    if (hlsRef.current) hlsRef.current.currentLevel = value; // -1 re-enables auto
  }

  const activeHeight = playingLevel >= 0 ? levels[playingLevel]?.height : null;

  return (
    <div className="player">
      <div className="screen">
        <video ref={videoRef} controls playsInline aria-label={title} />
        {error && (
          <div className="screen-error" role="alert">
            {error}
          </div>
        )}
      </div>

      <div className="player-bar">
        <label className="quality">
          Quality
          <select value={selected} onChange={pickLevel} disabled={levels.length === 0}>
            <option value={-1}>Auto</option>
            {levels.map((l, i) => (
              <option key={i} value={i}>
                {l.height}p
              </option>
            ))}
          </select>
        </label>
        <span className="now-playing" aria-live="polite">
          {activeHeight ? `Playing at ${activeHeight}p` : "Waiting for stream"}
        </span>
      </div>
    </div>
  );
}
