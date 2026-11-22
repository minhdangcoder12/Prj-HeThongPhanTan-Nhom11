import { useEffect, useState } from "react";
import { api, poll } from "./api.js";
import { Result } from "./App.jsx";

const trip = () => ({
  pickup: { lat: 10.7769, lng: 106.7009 },
  dropoff: { lat: 10.7626, lng: 106.6602 },
});

// Kịch bản lỗi B–E. Endpoint chaos chỉ có khi SPRING_PROFILES_ACTIVE=demo.
export default function Chaos({ token }) {
  const [result, setResult] = useState(null);
  const [rate, setRate] = useState("1");
  const [dlq, setDlq] = useState({ count: 0 });
  const run = async (method, path, body) => setResult(await api(method, path, body, token));

  useEffect(() => poll("/api/notifications/dlq", (d) => d && setDlq(d), 3000), []);

  async function flowB() {
    const [a, b] = await Promise.all([api("POST", "/api/trips", trip(), token), api("POST", "/api/trips", trip(), token)]);
    setResult({
      ok: a.ok && b.ok,
      status: `${a.status} / ${b.status}`,
      correlationId: a.correlationId,
      data: { tripA: a.data, tripB: b.data },
    });
  }

  return (
    <section className="panel" aria-labelledby="chaos-title">
      <h2 id="chaos-title">Kịch bản lỗi</h2>
      {!token && <p className="hint">Cần đăng nhập ở Hành khách trước: mọi lệnh ghi đều cần token.</p>}

      <div className="scenario">
        <h3>Flow B · Hai khách cùng lúc</h3>
        <p className="panel-note">Đặt 2 chuyến song song gần D01: chỉ một chuyến giữ được tài xế (UPDATE nguyên tử).</p>
        <div className="row">
          <button onClick={flowB}>Đặt 2 chuyến cùng lúc</button>
        </div>
      </div>

      <div className="scenario">
        <h3>
          Flow C · Retry rồi vào DLQ
          <span className={dlq?.count > 0 ? "pill negative" : "pill"}>DLQ {dlq?.count ?? 0}</span>
        </h3>
        <p className="panel-note">notification-service lỗi theo tỉ lệ; thử lại 2s → 5s → 10s rồi chuyển message vào DLQ.</p>
        <div className="row">
          <input aria-label="Tỉ lệ lỗi notification (0 đến 1)" value={rate} onChange={(e) => setRate(e.target.value)} />
          <button onClick={() => run("PUT", "/api/chaos/notification/fail-rate", { rate: Number(rate) })}>Đặt tỉ lệ lỗi</button>
          <button onClick={() => run("POST", "/api/notifications/dlq/reprocess")} disabled={!dlq?.count}>
            Đẩy lại DLQ
          </button>
        </div>
      </div>

      <div className="scenario">
        <h3>Flow D · Event trùng</h3>
        <p className="panel-note">Bấm “Gửi lại” trên một event ở bảng Event: cùng eventId, inbox bỏ qua, không có thông báo thứ hai.</p>
        <div className="row">
          <button onClick={() => run("POST", "/api/monitor/test-event")}>Gửi event test</button>
        </div>
      </div>

      <div className="scenario">
        <h3>Flow E · Saga bù trừ</h3>
        <p className="panel-note">Bật lỗi gán chuyến: saga đã giữ tài xế phải nhả lại và kết thúc COMPENSATED.</p>
        <div className="row">
          <button onClick={() => run("PUT", "/api/chaos/trip/fail-assign", { enabled: true })}>Bật lỗi gán chuyến</button>
          <button onClick={() => run("PUT", "/api/chaos/trip/fail-assign", { enabled: false })}>Tắt</button>
        </div>
      </div>

      <Result r={result} />
    </section>
  );
}
