import { useEffect, useState } from "react";
import { api, poll } from "./api.js";
import { Result } from "./App.jsx";

const trip = (passengerId) => ({
  passengerId,
  pickup: { lat: 10.7769, lng: 106.7009 },
  dropoff: { lat: 10.7626, lng: 106.6602 },
});

// Demo-only triggers for flows B-E. Chaos endpoints exist only with SPRING_PROFILES_ACTIVE=demo.
export default function Chaos() {
  const [result, setResult] = useState(null);
  const [rate, setRate] = useState("1");
  const [dlq, setDlq] = useState({ count: 0 });
  const run = async (method, path, body) => setResult(await api(method, path, body));

  useEffect(() => {
    return poll("/api/notifications/dlq", (d) => d && setDlq(d), 3000);
  }, []);

  async function flowB() {
    const [a, b] = await Promise.all([
      api("POST", "/api/trips", trip(crypto.randomUUID())),
      api("POST", "/api/trips", trip(crypto.randomUUID())),
    ]);
    setResult({
      ok: a.ok && b.ok,
      status: `${a.status} / ${b.status}`,
      correlationId: a.correlationId,
      data: { passengerA: a.data, passengerB: b.data },
    });
  }

  return (
    <div className="panel">
      <h2>Kịch bản demo</h2>
      <div className="row">
        <button onClick={() => run("POST", "/api/monitor/test-event")}>Gửi event test</button>
        <button onClick={flowB}>Flow B: 2 khách cùng lúc</button>
      </div>
      <div className="row">
        Flow E
        <button onClick={() => run("PUT", "/api/chaos/trip/fail-assign", { enabled: true })}>Bật lỗi assign</button>
        <button onClick={() => run("PUT", "/api/chaos/trip/fail-assign", { enabled: false })}>Tắt</button>
      </div>
      <div className="row">
        Flow C fail-rate
        <input value={rate} onChange={(e) => setRate(e.target.value)} />
        <button onClick={() => run("PUT", "/api/chaos/notification/fail-rate", { rate: Number(rate) })}>Áp dụng</button>
      </div>
      <div className="row">
        DLQ: <b>{dlq?.count ?? 0}</b> tin nhắn
        <button onClick={() => run("POST", "/api/notifications/dlq/reprocess")} disabled={!dlq?.count}>
          Reprocess DLQ
        </button>
      </div>
      <p className="hint">Flow D: bấm “replay” trên một event ở Monitor (gửi lại cùng eventId).</p>
      <Result r={result} />
    </div>
  );
}
