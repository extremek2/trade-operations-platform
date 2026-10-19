const { defineConfig } = require("@playwright/test");
const webPort = process.env.E2E_WEB_PORT || "3100";
const webUrl = `http://localhost:${webPort}`;
module.exports = defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  timeout: 60000,
  reporter: "list",
  use: { baseURL: webUrl, channel: "chrome", headless: true, screenshot: "only-on-failure", actionTimeout: 15000 },
  webServer: [
    { command: "./gradlew onboardingE2eServer --no-daemon", cwd: "../api-server", url: "http://127.0.0.1:18080/_e2e/ready", timeout: 180000,
      reuseExistingServer: false, env: { E2E_WEB_URL: webUrl } },
    { command: "npm start", url: webUrl, timeout: 120000, reuseExistingServer: false,
      env: { BROWSER: "none", HOST: "localhost", PORT: webPort, REACT_APP_API_URL: "http://localhost:18080/api/v1" } },
  ],
});
