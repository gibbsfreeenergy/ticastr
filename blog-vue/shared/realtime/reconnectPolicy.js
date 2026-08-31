export function nextReconnectDelay(attempt, {
  baseDelayMs = 500,
  maxDelayMs = 30000,
  jitterRatio = 0.2,
  random = Math.random
} = {}) {
  const safeAttempt = Math.max(0, Number(attempt) || 0);
  const exponential = Math.min(maxDelayMs, baseDelayMs * (2 ** safeAttempt));
  const jitter = exponential * Math.max(0, Math.min(1, jitterRatio));
  return Math.max(0, Math.round(exponential - jitter + random() * jitter * 2));
}

export function canReconnect(attempt, maxAttempts) {
  return maxAttempts == null || attempt < maxAttempts;
}
