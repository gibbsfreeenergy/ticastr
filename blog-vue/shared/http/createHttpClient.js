import { getSafeErrorMessage, normalizeHttpError } from "../api/error";

/**
 * Shared HTTP infrastructure for both Vue applications.
 *
 * The factory deliberately accepts axios as a dependency.  The shared layer
 * therefore stays independent from either application's UI framework while
 * both applications still own their toast, router and store policies.
 */
export const DEFAULT_HTTP_OPTIONS = {
  timeout: 15000,
  withCredentials: true,
  xsrfCookieName: "XSRF-TOKEN",
  xsrfHeaderName: "X-XSRF-TOKEN",
  headers: { "X-Requested-With": "XMLHttpRequest" }
};

export function getErrorMessage(error) {
  return getSafeErrorMessage(error);
}

export function createHttpClient({ axios, onBusinessResponse, onHttpError, onAuthExpired, options = {} }) {
  if (!axios || typeof axios.create !== "function") {
    throw new TypeError("createHttpClient requires an axios-compatible dependency");
  }

  const client = axios.create({ ...DEFAULT_HTTP_OPTIONS, ...options });
  let authExpiredNotified = false;
  client.interceptors.response.use(
    response => {
      authExpiredNotified = false;
      onBusinessResponse?.({
        code: response.data?.code,
        message: response.data?.message,
        response
      });
      return response;
    },
    error => {
      const normalized = normalizeHttpError(error);
      if (normalized.kind === "auth-expired" && !authExpiredNotified) {
        authExpiredNotified = true;
        onAuthExpired?.(normalized);
      }
      onHttpError?.({
        error,
        ...normalized
      });
      return Promise.reject(error);
    }
  );
  return client;
}
