import { Download, ExternalLink } from "lucide-react";
import posthog from 'posthog-js';
import "./index.css";

function App() {
  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        minHeight: "100vh",
        padding: "1.5rem 1.5rem 3rem",
        alignItems: "center",
        maxWidth: "1280px",
        margin: "0 auto",
        width: "100%",
      }}
    >
      {/* Header / Navbar */}
      <header
        style={{
          width: "100%",
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          padding: "1rem 0 3.5rem",
        }}
      >
        <a href="/" className="brand-badge">
          <img
            src="/app_logo.png"
            alt="grab logo"
            className="brand-emblem"
          />
          <span className="brand-title">grab</span>
        </a>
      </header>

      {/* Main Content */}
      <main
        style={{
          flex: 1,
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          width: "100%",
          textAlign: "center",
        }}
      >
        {/* Hero Section */}
        <section
          style={{
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            maxWidth: "840px",
            marginBottom: "3.5rem",
          }}
        >
          <h1
            style={{
              fontSize: "clamp(2rem, 5vw, 3.25rem)",
              fontWeight: "700",
              lineHeight: "1.08",
              letterSpacing: "-0.02em",
              marginBottom: "1.5rem",
            }}
          >
            Download videos from social media.
          </h1>

          <p
            style={{
              fontSize: "clamp(1.05rem, 2vw, 1.25rem)",
              color: "var(--text-secondary)",
              maxWidth: "620px",
              lineHeight: "1.6",
            }}
          >
            Save videos from YouTube, X, Instagram, and Facebook directly to your device.
          </p>
        </section>

        {/* Action Cards Grid */}
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 320px), 1fr))",
            gap: "1.75rem",
            width: "100%",
            maxWidth: "920px",
            marginBottom: "6rem",
          }}
        >
          {/* Android Download Card */}
          <div
            className="apex-card"
            style={{
              display: "flex",
              flexDirection: "column",
              alignItems: "flex-start",
              textAlign: "left",
              justifyContent: "space-between",
            }}
          >
            <div>
              <div style={{ display: "flex", alignItems: "center", gap: "10px", marginBottom: "1rem" }}>
                <h2 style={{ fontSize: "1.4rem", fontWeight: "700" }}>
                  Android App
                </h2>
              </div>
              <p style={{ color: "var(--text-secondary)", fontSize: "0.95rem", lineHeight: "1.6", marginBottom: "2rem" }}>
                Download the APK to get share sheet integration and background downloads.
              </p>
            </div>

            <div style={{ width: "100%" }}>
              <a
                href="/grab.apk"
                download="grab.apk"
                className="btn-titanium"
                style={{ width: "100%", boxSizing: "border-box" }}
                onClick={() => posthog.capture('download_apk_clicked')}
              >
                <Download size={20} />
                <span>Download APK</span>
              </a>
              <p
                style={{
                  fontSize: "0.8rem",
                  color: "var(--text-muted)",
                  textAlign: "center",
                  marginTop: "0.75rem",
                }}
              >
                Android 8.0+ &bull; Free &bull; 3 MB
              </p>
            </div>
          </div>

          {/* Web Downloader Card */}
          <div
            className="apex-card"
            style={{
              display: "flex",
              flexDirection: "column",
              alignItems: "flex-start",
              textAlign: "left",
              justifyContent: "space-between",
            }}
          >
            <div>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", width: "100%", marginBottom: "1rem" }}>
                <h2 style={{ fontSize: "1.4rem", fontWeight: "700" }}>
                  Web Downloader
                </h2>
              </div>
              <p style={{ color: "var(--text-secondary)", fontSize: "0.95rem", lineHeight: "1.6", marginBottom: "2rem" }}>
                Browser-based video extraction is in development. Use the Android app for now.
              </p>
            </div>

            <div style={{ width: "100%", position: "relative" }}>
              <input
                type="text"
                className="input-field"
                placeholder="Paste video link here..."
                disabled
                title="Web downloading is coming soon!"
                style={{ paddingRight: "100px" }}
              />
              <button
                disabled
                style={{
                  position: "absolute",
                  right: "8px",
                  top: "8px",
                  bottom: "8px",
                  background: "var(--surface-alt)",
                  border: "1px solid var(--surface-border)",
                  borderRadius: "10px",
                  color: "var(--text-muted)",
                  padding: "0 16px",
                  fontWeight: "700",
                  fontFamily: "var(--font-body)",
                  cursor: "not-allowed",
                  fontSize: "0.875rem",
                }}
              >
                grab
              </button>
            </div>
          </div>
        </div>

        {/* How It Works Section */}
        <section
          style={{
            width: "100%",
            maxWidth: "1040px",
            marginBottom: "6rem",
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
          }}
        >
          <h2
            style={{
              fontSize: "2.25rem",
              fontWeight: "700",
              letterSpacing: "-0.03em",
              marginBottom: "0.75rem",
            }}
          >
            How It Works
          </h2>
          <p
            style={{
              color: "var(--text-secondary)",
              fontSize: "1.05rem",
              marginBottom: "2.5rem",
            }}
          >
            Two effortless ways to capture your media.
          </p>

          <div className="steps-grid">
            <div className="step-card">
              <div className="step-badge">01</div>
              <h3
                style={{
                  fontSize: "1.2rem",
                  fontWeight: "700",
                  marginBottom: "0.75rem",
                  display: "flex",
                  alignItems: "center",
                  gap: "0.6rem",
                }}
              >
                Share
              </h3>
              <p style={{ color: "var(--text-secondary)", lineHeight: "1.6", fontSize: "0.95rem" }}>
                Tap the share button on the video you want to download and select the grab app on the share sheet to start downloading.
              </p>
            </div>

            <div className="step-card">
              <div className="step-badge">02</div>
              <h3
                style={{
                  fontSize: "1.2rem",
                  fontWeight: "700",
                  marginBottom: "0.75rem",
                  display: "flex",
                  alignItems: "center",
                  gap: "0.6rem",
                }}
              >
                Direct Paste
              </h3>
              <p style={{ color: "var(--text-secondary)", lineHeight: "1.6", fontSize: "0.95rem" }}>
                Or if you prefer manual download, open the app directly, paste a video link, and tap download.
              </p>
            </div>
          </div>
        </section>

        {/* Supported Platforms */}
        <section
          style={{
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            gap: "1.5rem",
            width: "100%",
            maxWidth: "800px",
            marginBottom: "4rem",
          }}
        >
          <p
            style={{
              color: "var(--text-muted)",
              fontSize: "0.8125rem",
              textTransform: "uppercase",
              letterSpacing: "0.15em",
              fontWeight: "700",
            }}
          >
            Supported Platforms
          </p>

          <div
            style={{
              display: "flex",
              flexWrap: "wrap",
              justifyContent: "center",
              gap: "1rem",
            }}
          >
            <div className="platform-chip">
              <span style={{ width: "8px", height: "8px", borderRadius: "50%", background: "var(--brand-youtube)" }}></span>
              <span>YouTube</span>
            </div>
            <div className="platform-chip">
              <span style={{ width: "8px", height: "8px", borderRadius: "50%", background: "var(--brand-twitter)" }}></span>
              <span>X (Twitter)</span>
            </div>
            <div className="platform-chip">
              <span style={{ width: "8px", height: "8px", borderRadius: "50%", background: "var(--brand-instagram)" }}></span>
              <span>Instagram</span>
            </div>
            <div className="platform-chip">
              <span style={{ width: "8px", height: "8px", borderRadius: "50%", background: "var(--brand-facebook)" }}></span>
              <span>Facebook</span>
            </div>
          </div>
        </section>

        {/* Tips Section */}
        <section
          style={{
            width: "100%",
            maxWidth: "1040px",
            marginBottom: "6rem",
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
          }}
        >
          <div className="apex-card" style={{ width: "100%", maxWidth: "800px", textAlign: "left" }}>
            <h2
              style={{
                fontSize: "1.4rem",
                fontWeight: "700",
                marginBottom: "1.25rem",
              }}
            >
              Few tips:
            </h2>
            <ol
              style={{
                color: "var(--text-secondary)",
                fontSize: "0.95rem",
                lineHeight: "1.7",
                paddingLeft: "1.5rem",
                display: "flex",
                flexDirection: "column",
                gap: "0.75rem",
              }}
            >
              <li>
                A video may fail to download on the first try (due to server cold starts). Retry it immediately and it should work.
              </li>
              <li>
                Downloading from Twitter using the share menu is a bit buggy. Don't scroll right away after sharing&mdash;wait until the download has progressed significantly.
              </li>
            </ol>
          </div>
        </section>
      </main>

      {/* Footer */}
      <footer
        style={{
          width: "100%",
          paddingTop: "2rem",
          borderTop: "1px solid var(--surface-border)",
          display: "flex",
          flexWrap: "wrap",
          justifyContent: "space-between",
          alignItems: "center",
          gap: "1rem",
          color: "var(--text-muted)",
          fontSize: "0.875rem",
        }}
      >
        <div>
          &copy; 2026 grab
        </div>
        <div>
          <a
            href="https://krisshedrach.dev"
            target="_blank"
            rel="noopener noreferrer"
            style={{
              color: "var(--text-secondary)",
              textDecoration: "none",
              display: "inline-flex",
              alignItems: "center",
              gap: "4px",
            }}
          >
            <span>Created by Kris Shedrach</span>
            <ExternalLink size={13} />
          </a>
        </div>
      </footer>
    </div>
  );
}

export default App;
