import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.jsx'
import posthog from 'posthog-js'

// Replace '<YOUR_POSTHOG_API_KEY>' with your actual PostHog API key.
// Ensure api_host matches your PostHog region (us.i.posthog.com or eu.i.posthog.com).
posthog.init('phc_BdzKEt6JWiNRi9YEgbqtMTYUgCz5N3k3YQMxo5p8RZpb', {
  api_host: 'https://us.i.posthog.com',
  person_profiles: 'identified_only' // Tracks anonymous data efficiently
})

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
