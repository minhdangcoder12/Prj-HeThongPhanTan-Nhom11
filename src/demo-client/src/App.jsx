import { useEffect, useState } from "react";
import { poll } from "./api.js";
import Passenger from "./Passenger.jsx";
import Driver from "./Driver.jsx";
import Chaos from "./Chaos.jsx";
import Monitor from "./Monitor.jsx";
import Flow from "./Flow.jsx";

export default function App() {
  const [passenger, setPassenger] = useState(null);
  const [driver, setDriver] = useState(null);
  const [tripId, setTripId] = useState("");
  const [tripStatus, setTripStatus] = useState(null); // status server trả về, chỉ để hiện trong thẻ Hành khách
  const [health, setHealth] = useState({});
  useEffect(() => poll("/api/monitor/health", setHealth, 3000), []);
  useEffect(() => {
    setTripStatus(null);
    return tripId ? poll(`/api/trips/${tripId}`, (t) => setTripStatus(t?.status ?? null)) : undefined;
  }, [tripId]);

  const services = health.error ? [] : Object.entries(health);
  const down = health.error ? ["gateway"] : services.filter(([, s]) => s !== "UP").map(([name]) => name);

  return (
    <div className="app">
      <header className="topbar">
        <span className="brand">
          <Mark /> RideFlow
        </span>
        <span className="scope">Hệ thống gọi xe phân tán · Nhóm 11 · DS03</span>
        <ul className="health" aria-label="Trạng thái service">
          <li>
            <span className={health.error ? "dot off" : "dot"} />
            gateway
            {health.error && <b>DOWN</b>}
          </li>
          {services.map(([svc, status]) => (
            <li key={svc} title={status}>
              <span className={status === "UP" ? "dot" : "dot off"} />
              {svc}
              {status !== "UP" && <b>{status}</b>}
            </li>
          ))}
        </ul>
      </header>

      <aside className="controls" aria-label="Thao tác demo">
        <Passenger session={passenger} onSession={setPassenger} tripId={tripId} onTrip={setTripId} tripStatus={tripStatus} />
        <Driver session={driver} onSession={setDriver} />
        <Chaos token={passenger?.token} />
      </aside>

      <main className="sheet">
        <div className="title-row">
          <h1>Hệ thống vận hành thế nào</h1>
          <p className="lede">
            {down.length
              ? `${down.join(", ")} đang DOWN: các bước liên quan dừng cho tới khi service chạy lại.`
              : `${services.length} service đang chạy. Cột trái chỉ để kích hoạt luồng; bên dưới là request và event đi qua các service.`}
          </p>
        </div>
        <Flow tripId={tripId} />
        <Monitor />
      </main>
    </div>
  );
}

// Dấu của RideFlow: điểm đón và điểm trả nối bằng một tuyến đường.
function Mark() {
  return (
    <svg width="22" height="22" viewBox="0 0 22 22" aria-hidden="true">
      <path d="M5 16 C 5 8, 17 14, 17 6" fill="none" stroke="var(--accent)" strokeWidth="2" strokeLinecap="round" />
      <circle cx="5" cy="16" r="3" fill="var(--accent)" />
      <circle cx="17" cy="6" r="3" fill="none" stroke="var(--accent)" strokeWidth="2" />
    </svg>
  );
}

// Màu chỉ để đọc nhanh trạng thái server trả về; UI không suy ra trạng thái mới (R7).
const TONE = {
  positive: ["PAID", "COMPLETED", "AVAILABLE", "SENT", "SUCCEEDED"],
  negative: ["NO_DRIVER", "PAYMENT_FAILED", "FAILED", "CANCELLED"],
  warning: ["REQUESTED", "MATCHING", "RESERVED", "PENDING", "STARTED", "CANDIDATE_SELECTED", "DRIVER_RESERVED", "COMPENSATING", "COMPENSATED"],
  accent: ["DRIVER_ASSIGNED", "ACCEPTED", "ARRIVED", "IN_PROGRESS", "ON_TRIP", "TRIP_ASSIGNED"],
};
export function Status({ value }) {
  if (!value) return <span className="dash">–</span>;
  const tone = Object.keys(TONE).find((t) => TONE[t].includes(value)) ?? "";
  return <span className={`pill ${tone}`}>{value}</span>;
}

// UUID rút gọn 8 ký tự, giữ bản đầy đủ trong tooltip.
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-/i;
export function Id({ value }) {
  if (value == null || value === "") return <span className="dash">–</span>;
  if (!UUID.test(value)) return <span className="id-raw" title={String(value)}>{String(value)}</span>;
  // ID mẫu (00000000-0000-…-0000000000d1) giống nhau ở đầu, nên hiện phần đuôi để phân biệt.
  const shown = value.startsWith("00000000-0000") ? `…${value.slice(-4)}` : value.slice(0, 8);
  return <span className="id" title={value}>{shown}</span>;
}

// Kết quả thô của một lệnh (R7: không giấu lỗi). Lỗi mở sẵn; thành công gói gọn, bấm để xem JSON.
export function Result({ r }) {
  if (!r) return null;
  const head = (
    <>
      <b>{r.status}</b>
      <span>{r.ok ? "thành công" : "lỗi"}</span>
      <span className="id">corr {r.correlationId?.slice(0, 8)}</span>
    </>
  );
  const body = typeof r.data === "string" ? r.data : JSON.stringify(r.data, null, 2);
  return r.ok ? (
    <details className="result">
      <summary>{head}</summary>
      <pre>{body}</pre>
    </details>
  ) : (
    <div className="result err" role="alert">
      <div className="result-head">{head}</div>
      <pre>{body}</pre>
    </div>
  );
}
