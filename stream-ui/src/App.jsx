import { useEffect, useState } from "react";
import VideoPlayer from "./VideoPlayer.jsx";

const STATUS_LABEL = {
  PENDING: "Not uploaded",
  UPLOADED: "Uploaded, waiting to encode",
  ENCODING: "Encoding",
  ENCODED: "Encoded",
  READY: "Ready to watch",
  FAILED: "Encoding failed",
};

export default function App() {
  const [movies, setMovies] = useState([]);
  const [loadState, setLoadState] = useState("loading"); // loading | ok | error
  const [activeId, setActiveId] = useState(null);

  const [stream, setStream] = useState(null); // { streamingURL, ... }
  const [streamError, setStreamError] = useState(null);
  const [streamLoading, setStreamLoading] = useState(false);

  async function loadMovies() {
    setLoadState("loading");
    try {
      const res = await fetch("/content/api/v1/movies");
      if (!res.ok) throw new Error(res.status);
      setMovies(await res.json());
      setLoadState("ok");
    } catch {
      setLoadState("error");
    }
  }

  useEffect(() => {
    loadMovies();
  }, []);

  async function play(movie) {
    setActiveId(movie.id);
    setStream(null);
    setStreamError(null);
    setStreamLoading(true);
    try {
      const res = await fetch(`/stream/api/v1/stream/${movie.id}`);
      if (res.status === 404) throw new Error("streaming-service has no playlist for this movie yet.");
      if (!res.ok) throw new Error(`streaming-service returned ${res.status}.`);
      setStream(await res.json());
    } catch (e) {
      setStreamError(e.message || "Could not reach streaming-service on port 8084.");
    } finally {
      setStreamLoading(false);
    }
  }

  const active = movies.find((m) => m.id === activeId);

  return (
    <div className="layout">
      <header className="masthead">
        <h1>Screening Room</h1>
        <p>Pick a title. The player adapts quality to your connection.</p>
      </header>

      <aside className="catalog" aria-label="Catalog">
        {loadState === "loading" && <p className="note">Loading catalog…</p>}

        {loadState === "error" && (
          <div className="note">
            <p>Can't reach content-service on port 8081.</p>
            <button onClick={loadMovies}>Try again</button>
          </div>
        )}

        {loadState === "ok" && movies.length === 0 && (
          <p className="note">
            No movies yet. Create one with <code>POST /api/v1/movies</code>, then upload a video to it.
          </p>
        )}

        <ul>
          {movies.map((m) => {
            const ready = m.videoStatus === "READY";
            return (
              <li key={m.id}>
                <button
                  className={"movie" + (m.id === activeId ? " is-active" : "")}
                  onClick={() => play(m)}
                  disabled={!ready}
                >
                  <span className="movie-title">{m.title}</span>
                  <span className="movie-meta">
                    {[m.releaseYear || null, m.genre?.replace("_", "-").toLowerCase()]
                      .filter(Boolean)
                      .join(", ")}
                  </span>
                  <span className={"status status-" + m.videoStatus}>
                    {STATUS_LABEL[m.videoStatus] ?? m.videoStatus}
                  </span>
                </button>
              </li>
            );
          })}
        </ul>
      </aside>

      <main className="stage">
        {!active && (
          <div className="empty">
            <h2>Nothing playing</h2>
            <p>Choose a title marked “Ready to watch”.</p>
          </div>
        )}

        {active && (
          <>
            {streamLoading && <div className="empty"><p>Getting stream link…</p></div>}
            {streamError && (
              <div className="empty" role="alert">
                <h2>Can't start playback</h2>
                <p>{streamError}</p>
              </div>
            )}
            {stream && <VideoPlayer key={stream.streamingURL} src={stream.streamingURL} title={active.title} />}

            <section className="details">
              <h2>{active.title}</h2>
              <p className="facts">
                {[active.releaseYear || null, active.durationMinutes ? `${active.durationMinutes} min` : null, active.director]
                  .filter(Boolean)
                  .join(", ")}
              </p>
              {active.description && <p className="blurb">{active.description}</p>}
              {active.cast && <p className="cast">Starring {active.cast}</p>}
            </section>
          </>
        )}
      </main>
    </div>
  );
}
