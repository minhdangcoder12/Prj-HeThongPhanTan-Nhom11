// Single entry point for HTTP (rule R7): every call carries a fresh X-Correlation-Id
// so the action can be traced on the System Monitor event stream.
export async function api(method, path, body) {
  const correlationId = crypto.randomUUID();
  const res = await fetch(path, {
    method,
    headers: { "Content-Type": "application/json", "X-Correlation-Id": correlationId },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  let data;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = text;
  }
  return { ok: res.ok, status: res.status, correlationId, data };
}

// Polls a GET endpoint; failures show as an error object instead of hiding the problem.
export function poll(path, setter, everyMs = 2000) {
  let stopped = false;
  const tick = async () => {
    const r = await api("GET", path).catch((e) => ({ ok: false, data: String(e) }));
    if (!stopped) setter(r.ok ? r.data : { error: r.status ?? "network", detail: r.data });
  };
  tick();
  const id = setInterval(tick, everyMs);
  return () => {
    stopped = true;
    clearInterval(id);
  };
}
