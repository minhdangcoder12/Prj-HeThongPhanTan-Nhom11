import { useEffect, useState } from "react";
import { api, poll } from "./api.js";

// Shorten UUIDs only; names and statuses stay readable.
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-/i;
const short = (v) => (typeof v === "string" && UUID.test(v) ? v.slice(0, 8) : v ?? "-");

const TABLES = [
  { title: "Trips", path: "/api/trips", cols: ["id", "status", "driverId", "passengerId", "updatedAt"] },
  { title: "Drivers", path: "/api/drivers", cols: ["fullName", "status", "currentTripId", "lat", "lng"] },
  { title: "Matching sagas", path: "/api/matching/sagas", cols: ["tripId", "status", "currentStep", "driverId", "lastError"] },
  { title: "Payments", path: "/api/payments", cols: ["tripId", "status", "amount", "attempts"] },
  { title: "Notifications", path: "/api/notifications", cols: ["type", "status", "attempts", "tripId"] },
];

function StateTable({ title, path, cols }) {
  const [rows, setRows] = useState([]);
  useEffect(() => poll(path, setRows), [path]);
  return (
    <div className="panel">
      <h3>{title}</h3>
      {Array.isArray(rows) ? (
        <table>
          <thead>
            <tr>{cols.map((c) => <th key={c}>{c}</th>)}</tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.id}>{cols.map((c) => <td key={c}>{short(r[c])}</td>)}</tr>
            ))}
          </tbody>
        </table>
      ) : (
        <p className="down">{JSON.stringify(rows)}</p>
      )}
    </div>
  );
}

// Live feed of every message on rideflow.events and rideflow.dlx, tapped by the gateway.
function EventLog() {
  const [events, setEvents] = useState([]);
  const [filter, setFilter] = useState("");
  const [connected, setConnected] = useState(false);
  const [replayed, setReplayed] = useState(null);
  const [dlq, setDlq] = useState({ count: 0, queue: "" });
  const [reprocessing, setReprocessing] = useState(false);

  useEffect(() => {
    const source = new EventSource("/api/monitor/events");
    source.onopen = () => setConnected(true);
    source.onerror = () => setConnected(false);
    source.onmessage = (m) => setEvents((prev) => [JSON.parse(m.data), ...prev].slice(0, 300));
    const stopPoll = poll("/api/notifications/dlq", (d) => d && setDlq(d), 3000);
    return () => {
      source.close();
      stopPoll();
    };
  }, []);

  const shown = events.filter((e) => !filter || JSON.stringify(e.body).includes(filter));
  const replay = async (eventId) => setReplayed(await api("POST", `/api/monitor/replay/${eventId}`));

  const reprocessDlq = async () => {
    setReprocessing(true);
    try {
      const res = await api("POST", "/api/notifications/dlq/reprocess");
      if (res.ok && res.data) {
        setDlq((prev) => ({ ...prev, count: res.data.remaining ?? 0 }));
      }
    } finally {
      setReprocessing(false);
    }
  };

  return (
    <div className="panel events">
      <div className="row" style={{ justifyContent: "space-between", margin: "0 0 6px" }}>
        <h3 style={{ margin: 0 }}>
          Events <b className={connected ? "up" : "down"}>{connected ? "live" : "disconnected"}</b>
        </h3>
        <div style={{ display: "flex", gap: "8px", alignItems: "center" }}>
          <span>
            DLQ: <b className={dlq?.count > 0 ? "down" : ""}>{dlq?.count ?? 0}</b> msg{dlq?.count === 1 ? "" : "s"}
          </span>
          <button
            onClick={reprocessDlq}
            disabled={reprocessing || !dlq?.count}
            style={{
              padding: "2px 8px",
              background: dlq?.count > 0 ? "var(--down)" : undefined,
              color: dlq?.count > 0 ? "#fff" : undefined,
              borderColor: dlq?.count > 0 ? "var(--down)" : undefined,
            }}
          >
            {reprocessing ? "Reprocessing..." : "Reprocess DLQ"}
          </button>
        </div>
      </div>
      <input placeholder="lọc theo correlationId / tripId / eventType" value={filter} onChange={(e) => setFilter(e.target.value)} />
      {replayed && <p className={replayed.ok ? "up" : "down"}>replay: {replayed.status} {JSON.stringify(replayed.data)}</p>}
      <table>
        <thead>
          <tr>
            <th>time</th>
            <th>routing key</th>
            <th>aggregate</th>
            <th>correlation</th>
            <th>eventId</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {shown.map((e, i) => (
            <tr key={i} className={e.deadLettered ? "dlq" : ""}>
              <td>{e.receivedAt?.slice(11, 23)}</td>
              <td>
                {e.deadLettered && <b>DLQ </b>}
                {e.routingKey}
              </td>
              <td>{short(e.body?.aggregateId)}</td>
              <td>
                <a onClick={() => setFilter(e.body?.correlationId ?? "")}>{short(e.body?.correlationId)}</a>
              </td>
              <td>{short(e.body?.eventId)}</td>
              <td>{!e.deadLettered && e.body?.eventId && <button onClick={() => replay(e.body.eventId)}>replay</button>}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default function Monitor() {
  return (
    <section className="monitor">
      <EventLog />
      {TABLES.map((t) => (
        <StateTable key={t.path} {...t} />
      ))}
    </section>
  );
}
