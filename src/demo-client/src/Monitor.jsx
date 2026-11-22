import { useEffect, useState } from "react";
import { api, poll } from "./api.js";
import { Id, Status } from "./App.jsx";

const ROWS = 8; // số dòng hiện mặc định; "Xem tất cả" mở hết
const time = (v) => (v ? <span title={v}>{v.slice(5, 10)} {v.slice(11, 19)}</span> : <span className="dash">–</span>);
const num = (unit, digits = 0) => (v) =>
  v == null ? <span className="dash">–</span> : (
    <>
      {Number(v).toLocaleString("vi-VN", { minimumFractionDigits: digits, maximumFractionDigits: digits })}
      {unit && <span className="unit">{unit}</span>}
    </>
  );
const text = (v) => (v == null || v === "" ? <span className="dash">–</span> : v);
const id = (v) => <Id value={v} />;
const status = (v) => <Status value={v} />;

// Mỗi cột: [nhãn, trường, cách hiển thị, căn phải?]
const TABLES = [
  { key: "trips", title: "Chuyến", path: "/api/trips", cols: [["Chuyến", "id", id], ["Trạng thái", "status", status], ["Tài xế", "driverId", id], ["Hành khách", "passengerId", id], ["Cước", "fare", num("đ"), true], ["Hệ số", "surgeMultiplier", num("×", 2), true], ["Cập nhật", "updatedAt", time, true]] },
  { key: "drivers", title: "Tài xế", path: "/api/drivers", cols: [["Tài xế", "fullName", text], ["Trạng thái", "status", status], ["Chuyến đang giữ", "currentTripId", id], ["Vĩ độ", "lat", num("", 4), true], ["Kinh độ", "lng", num("", 4), true]] },
  { key: "sagas", title: "Saga ghép tài xế", path: "/api/matching/sagas", cols: [["Chuyến", "tripId", id], ["Trạng thái", "status", status], ["Bước", "currentStep", text], ["Tài xế", "driverId", id], ["Lỗi gần nhất", "lastError", text]] },
  { key: "payments", title: "Thanh toán", path: "/api/payments", cols: [["Chuyến", "tripId", id], ["Trạng thái", "status", status], ["Số tiền", "amount", num("đ"), true], ["Lần thử", "attempts", num(), true]] },
  { key: "notifications", title: "Thông báo", path: "/api/notifications", cols: [["Event", "type", text], ["Trạng thái", "status", status], ["Lần thử", "attempts", num(), true], ["Chuyến", "tripId", id]] },
];

function Table({ cols, rows, all }) {
  return (
    <div className="scroll">
      <table>
        <thead>
          <tr>{cols.map(([label, , , right]) => <th key={label} className={right ? "num" : undefined}>{label}</th>)}</tr>
        </thead>
        <tbody>
          {(all ? rows : rows.slice(0, ROWS)).map((r, i) => (
            <tr key={r.id ?? i}>
              {cols.map(([label, field, show, right]) => <td key={label} className={right ? "num" : undefined}>{show(r[field])}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

// Trạng thái hiện tại của từng service, mỗi service một tab (tất cả đều được poll để đếm số dòng).
function StateTabs() {
  const [tab, setTab] = useState("trips");
  const [data, setData] = useState({});
  const [all, setAll] = useState(false);
  useEffect(() => {
    const stops = TABLES.map((t) => poll(t.path, (rows) => setData((d) => ({ ...d, [t.key]: rows }))));
    return () => stops.forEach((stop) => stop());
  }, []);
  const current = TABLES.find((t) => t.key === tab);
  const rows = data[tab];
  return (
    <section className="block" aria-labelledby="state-title">
      <div className="block-head">
        <h2 id="state-title">Dữ liệu từng service</h2>
        {Array.isArray(rows) && rows.length > ROWS && (
          <span className="more">
            {all ? rows.length : `${ROWS} / ${rows.length}`} dòng
            <button className="ghost" onClick={() => setAll(!all)}>{all ? "Thu gọn" : "Xem tất cả"}</button>
          </span>
        )}
      </div>
      <div className="tabs" role="tablist">
        {TABLES.map((t) => (
          <button key={t.key} role="tab" aria-selected={t.key === tab} onClick={() => { setTab(t.key); setAll(false); }}>
            {t.title}
            {Array.isArray(data[t.key]) && <span className="count">{data[t.key].length}</span>}
          </button>
        ))}
      </div>
      {Array.isArray(rows) ? (
        rows.length ? <Table cols={current.cols} rows={rows} all={all} /> : <p className="empty">Chưa có dòng nào trong bảng {current.title.toLowerCase()}.</p>
      ) : rows ? (
        <pre className="result err">{JSON.stringify(rows, null, 2)}</pre>
      ) : (
        <p className="empty">Đang tải…</p>
      )}
    </section>
  );
}

// Mọi message trên rideflow.events và rideflow.dlx, gateway nghe lén và đẩy qua SSE.
function EventLog() {
  const [events, setEvents] = useState([]);
  const [filter, setFilter] = useState("");
  const [connected, setConnected] = useState(false);
  const [replayed, setReplayed] = useState(null);

  useEffect(() => {
    const source = new EventSource("/api/monitor/events");
    source.onopen = () => setConnected(true);
    source.onerror = () => setConnected(false);
    source.onmessage = (m) => setEvents((prev) => [JSON.parse(m.data), ...prev].slice(0, 300));
    return () => source.close();
  }, []);

  const shown = events.filter((e) => !filter || JSON.stringify(e.body).includes(filter));
  const replay = async (eventId) => setReplayed(await api("POST", `/api/monitor/replay/${eventId}`));

  return (
    <section className="block" aria-labelledby="events-title">
      <div className="block-head">
        <span className="row">
          <h2 id="events-title">Event trên RabbitMQ</h2>
          <span className={connected ? "pill positive" : "pill negative"}>{connected ? "đang nhận trực tiếp" : "mất kết nối"}</span>
        </span>
        <input className="filter" aria-label="Lọc event" placeholder="Lọc theo correlationId, tripId hoặc tên event" value={filter} onChange={(e) => setFilter(e.target.value)} />
      </div>
      {replayed && (
        <p className={replayed.ok ? "hint" : "result err"}>
          Gửi lại: {replayed.status} {JSON.stringify(replayed.data)}
        </p>
      )}
      {shown.length ? (
        <div className="scroll">
          <table>
            <thead>
              <tr>
                <th className="num">Lúc</th>
                <th>Event</th>
                <th>Đối tượng</th>
                <th>Correlation (bấm để lọc)</th>
                <th>eventId</th>
                <th aria-label="Thao tác" />
              </tr>
            </thead>
            <tbody>
              {shown.map((e, i) => (
                <tr key={i} className={e.deadLettered ? "dlq" : ""}>
                  <td className="num">{e.receivedAt?.slice(11, 19)}</td>
                  <td>
                    {e.deadLettered && <span className="pill negative">DLQ</span>} {e.routingKey}
                  </td>
                  <td>{id(e.body?.aggregateId)}</td>
                  <td>
                    <button className="ghost id" onClick={() => setFilter(e.body?.correlationId ?? "")}>{e.body?.correlationId?.slice(0, 8) ?? "–"}</button>
                  </td>
                  <td>{id(e.body?.eventId)}</td>
                  <td>{!e.deadLettered && e.body?.eventId && <button className="ghost" onClick={() => replay(e.body.eventId)}>Gửi lại</button>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <p className="empty">{filter ? "Không có event nào khớp bộ lọc." : "Chưa có event nào. Đặt một chuyến hoặc bấm Gửi event test."}</p>
      )}
    </section>
  );
}

export default function Monitor() {
  return (
    <>
      <EventLog />
      <StateTabs />
    </>
  );
}
