import { defineConfig } from "cypress";

// Demo-only fallbacks match the local seed users. Production credentials must be supplied via env.
const demoPlayerEmail = process.env.CYPRESS_DEMO_PLAYER_EMAIL ?? "demo.player1@dotaops.local";
const demoOrganizerEmail = process.env.CYPRESS_DEMO_ORGANIZER_EMAIL ?? "demo.organizer@dotaops.local";
const demoPassword = process.env.CYPRESS_DEMO_PASSWORD ?? "DotaOpsDemo123!"; // NOSONAR: local demo seed fallback, not a production credential.

export default defineConfig({
  env: {
    DEMO_ORGANIZER_EMAIL: demoOrganizerEmail,
    DEMO_PASSWORD: demoPassword,
    DEMO_PLAYER_EMAIL: demoPlayerEmail
  },
  e2e: {
    baseUrl: "http://localhost:3000",
    specPattern: "cypress/e2e/**/*.cy.ts",
    supportFile: false,
    video: false,
    screenshotOnRunFailure: true,
    defaultCommandTimeout: 10_000,
    requestTimeout: 10_000,
    responseTimeout: 20_000
  }
});
