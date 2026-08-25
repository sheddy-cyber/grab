import { Download, Share2, Copy } from "lucide-react";
import posthog from "posthog-js";
import "./index.css";

function App() {
  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        minHeight: "100vh",
        padding: "2rem",
        alignItems: "center",
      }}
    >
      {/* Navbar/Header */}
      <header
        style={{
          width: "100%",
          maxWidth: "1200px",
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          paddingBottom: "4rem",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: "1rem" }}>
          <img
            src="/app_logo.png"
            alt="grab am logo"
            style={{ width: "48px", height: "48px", borderRadius: "12px" }}
          />
          <h1
            style={{
              fontSize: "1.5rem",
              fontWeight: "800",
              letterSpacing: "-0.5px",
            }}
          >
            grab am
          </h1>
        </div>
      </header>

      {/* Main Content */}
      <main
        style={{
          flex: 1,
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          width: "100%",
          maxWidth: "800px",
          textAlign: "center",
        }}
      >
        <h2
          style={{
            fontSize: "4rem",
            fontWeight: "800",
            lineHeight: "1.1",
            marginBottom: "1.5rem",
            letterSpacing: "-1.5px",
          }}
        >
          The fastest way to <br />
          <span className="text-gradient">grab your videos.</span>
        </h2>

        <p
          style={{
            fontSize: "1.25rem",
            color: "var(--text-secondary)",
            marginBottom: "3rem",
            maxWidth: "600px",
            lineHeight: "1.6",
          }}
        >
          Download stunning content directly to your Android device from all
          your favourite social platforms in just one tap.
        </p>

        {/* Action Cards */}
        <div
          style={{
            display: "flex",
            flexDirection: "column",
            gap: "2rem",
            width: "100%",
            maxWidth: "500px",
          }}
        >
          {/* Android Download Card */}
          <div
            className="glass-card"
            style={{
              display: "flex",
              flexDirection: "column",
              gap: "1.5rem",
              alignItems: "center",
            }}
          >
            <h3 style={{ fontSize: "1.5rem", fontWeight: "700" }}>
              Get the Android App
            </h3>
            <p style={{ color: "var(--text-secondary)", fontSize: "0.95rem" }}>
              Experience the full power of <em>grab am</em> on your phone.
            </p>
            <a 
              href="/grab_am.apk" 
              download="grab_am.apk" 
              className="btn-gradient" 
              style={{ width: '100%', textDecoration: 'none' }}
              onClick={() => posthog.capture('download_apk_clicked')}
            >
              Download APK
            </a>
          </div>

          {/* Web Downloader Teaser Card */}
          <div className="glass-card" style={{ padding: "2rem" }}>
            <h3
              style={{
                fontSize: "1.25rem",
                fontWeight: "700",
                marginBottom: "1rem",
                textAlign: "left",
              }}
            >
              Web Downloader{" "}
              <span
                style={{
                  fontSize: "0.75rem",
                  backgroundColor: "rgba(29, 161, 242, 0.2)",
                  color: "var(--brand-twitter)",
                  padding: "4px 8px",
                  borderRadius: "12px",
                  marginLeft: "8px",
                  verticalAlign: "middle",
                }}
              >
                Coming Soon
              </span>
            </h3>
            <div style={{ position: "relative" }}>
              <input
                type="text"
                className="input-field"
                placeholder="Paste video link here..."
                disabled
                title="Web downloading is coming soon!"
              />
              <button
                disabled
                style={{
                  position: "absolute",
                  right: "8px",
                  top: "8px",
                  bottom: "8px",
                  background: "var(--surface-alt)",
                  border: "none",
                  borderRadius: "10px",
                  color: "var(--text-secondary)",
                  padding: "0 16px",
                  fontWeight: "700",
                  cursor: "not-allowed",
                }}
              >
                Grab
              </button>
            </div>
          </div>
        </div>

        {/* Instructions Section */}
        <div
          style={{
            marginTop: "6rem",
            width: "100%",
            maxWidth: "1000px",
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
          }}
        >
          <h2
            style={{
              fontSize: "2.5rem",
              fontWeight: "800",
              marginBottom: "1rem",
              letterSpacing: "-0.5px",
            }}
          >
            How it Works
          </h2>
          <p
            style={{
              color: "var(--text-secondary)",
              fontSize: "1.1rem",
              marginBottom: "1rem",
              textAlign: "center",
            }}
          >
            Two incredibly simple ways to get your videos.
          </p>

          <div className="steps-grid">
            <div className="step-card">
              <h3
                style={{
                  fontSize: "1.25rem",
                  fontWeight: "700",
                  marginBottom: "0.5rem",
                  display: "flex",
                  alignItems: "center",
                  gap: "0.5rem",
                }}
              >
                <Download size={24} color="var(--brand-twitter)" /> Install the
                App
              </h3>
              <p style={{ color: "var(--text-secondary)", lineHeight: "1.5" }}>
                Download and install the <em>grab am</em> APK directly onto your Android
                device.
              </p>
            </div>

            <div className="step-card">
              <h3
                style={{
                  fontSize: "1.25rem",
                  fontWeight: "700",
                  marginBottom: "0.5rem",
                  display: "flex",
                  alignItems: "center",
                  gap: "0.5rem",
                }}
              >
                <Share2 size={24} color="var(--brand-instagram-start)" /> Share
                to Download
              </h3>
              <p style={{ color: "var(--text-secondary)", lineHeight: "1.5" }}>
                While watching a video on any social app, simply tap "Share" and
                select <em>grab am</em> to download instantly.
              </p>
            </div>

            <div className="step-card">
              <h3
                style={{
                  fontSize: "1.25rem",
                  fontWeight: "700",
                  marginBottom: "0.5rem",
                  display: "flex",
                  alignItems: "center",
                  gap: "0.5rem",
                }}
              >
                <Copy size={24} color="var(--brand-facebook)" /> Copy & Paste
              </h3>
              <p style={{ color: "var(--text-secondary)", lineHeight: "1.5" }}>
                Prefer manual control? Copy any video link, open <em>grab am</em>, and tap "Paste Link" to download.
              </p>
            </div>
          </div>
        </div>

        {/* Supported Platforms */}
        <div
          style={{
            marginTop: "6rem",
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            gap: "1.5rem",
            width: "100%",
          }}
        >
          <p
            style={{
              color: "var(--text-secondary)",
              fontSize: "0.9rem",
              textTransform: "uppercase",
              letterSpacing: "2px",
              fontWeight: "700",
            }}
          >
            Supported Platforms
          </p>
          <div className="platforms-container">
            <span style={{ color: "var(--brand-youtube)", padding: "0 1rem" }}>
              YouTube
            </span>
            <span style={{ color: "var(--brand-twitter)", padding: "0 1rem" }}>
              X
            </span>
            <span
              style={{
                color: "var(--brand-instagram-start)",
                padding: "0 1rem",
              }}
            >
              Instagram
            </span>
            <span style={{ color: "var(--brand-facebook)", padding: "0 1rem" }}>
              Facebook
            </span>
          </div>
        </div>
      </main>

      <footer
        style={{
          marginTop: "4rem",
          color: "var(--text-secondary)",
          fontSize: "0.875rem",
        }}
      >
        &copy; 2026 grab am. All rights reserved.
      </footer>
    </div>
  );
}

export default App;
