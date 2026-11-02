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
    <div className="panel events">
      <h3>
        Events <b className={connected ? "up" : "down"}>{connected ? "live" : "disconnected"}</b>
      </h3>
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
