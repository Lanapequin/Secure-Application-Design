/**
 * config.js — Central configuration for the async HTML/JS client.
 *
 * In production, replace the placeholder hostnames with your actual
 * AWS EC2 public DNS or Elastic IP addresses.
 *
 * These values can also be injected at deploy time by your CI/CD pipeline
 * (e.g. sed-replacing __LOGIN_HOST__ and __BACKEND_HOST__ before serving).
 *
 * 12-factor principle III: Store config in the environment.
 * For a static JS client the "environment" is this config file,
 * which is the only file that changes between dev/staging/production.
 */
const CONFIG = Object.freeze({

  /**
   * Login Service — Spring server on EC2 instance 1.
   * Handles /api/auth/login and /api/auth/register.
   * Change __LOGIN_HOST__ to your actual EC2 public IP or domain.
   */
  LOGIN_SERVICE_URL: "https://__LOGIN_HOST__:5000",

  /**
   * Backend Service — Spring server on EC2 instance 2.
   * Handles /api/hello, /api/data, /api/secure-info.
   * Change __BACKEND_HOST__ to your actual EC2 public IP or domain.
   */
  BACKEND_SERVICE_URL: "https://__BACKEND_HOST__:6000",

  /**
   * Local / development overrides.
   * Uncomment these and comment the ones above when running locally.
   */
  // LOGIN_SERVICE_URL:   "https://localhost:5000",
  // BACKEND_SERVICE_URL: "https://localhost:6000",

  /** Token storage key in sessionStorage */
  TOKEN_KEY: "eci_secure_token",
  USER_KEY:  "eci_secure_user",

  /** Request timeout in milliseconds */
  TIMEOUT_MS: 10000,
});
