import { useState } from "react";
import { api } from "./api.js";
import { Result } from "./App.jsx";

const trip = () => ({
  pickup: { lat: 10.7769, lng: 106.7009 },
  dropoff: { lat: 10.7626, lng: 106.6602 },
});

// Demo-only triggers for flows B-E. Chaos endpoints exist only with SPRING_PROFILES_ACTIVE=demo.
export default function Chaos({ token }) {
  const [result, setResult] = useState(null);
  const [rate, setRate] = useState("1");
  const run = async (method, path, body) => setResult(await api(method, path, body, token));

  async function flowB() {
    const [a, b] = await Promise.all([
      api("POST", "/api/trips", trip(), token),
      api("POST", "/api/trips", trip(), token),
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
      {!token && <p className="hint">Cần đăng nhập ở Passenger trước (ghi/chaos đều cần token).</p>}
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
      <p className="hint">Flow D: bấm “replay” trên một event ở Monitor (gửi lại cùng eventId).</p>
      <Result r={result} />
    </div>
  );
}
