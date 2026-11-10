import { useEffect, useState } from "react";
import { api, poll } from "./api.js";
import { Result } from "./App.jsx";

const PRESET_LOCATIONS = [
  { label: "Bến Thành (Q1)", lat: "10.7769", lng: "106.7009" },
  { label: "Landmark 81 (BT)", lat: "10.7951", lng: "106.7218" },
  { label: "Sân bay TSN (TB)", lat: "10.8185", lng: "106.6588" },
];

export default function Driver() {
  const [drivers, setDrivers] = useState([]);
  const [driverId, setDriverId] = useState("");
  const [loc, setLoc] = useState({ lat: "10.7769", lng: "106.7009" });
  const [tripId, setTripId] = useState("");
  const [trip, setTrip] = useState(null);
  const [result, setResult] = useState(null);

  useEffect(() => poll("/api/drivers", setDrivers), []);
  const list = Array.isArray(drivers) ? drivers : [];
  const me = list.find((d) => d.id === driverId);

  // Sync tripId from the server state of this driver
  useEffect(() => {
    if (me?.currentTripId) {
      setTripId(me.currentTripId);
    }
  }, [me?.currentTripId]);

  // Poll trip details whenever tripId is present
  useEffect(() => {
    if (tripId) {
      return poll(`/api/trips/${tripId}`, setTrip);
    }
    setTrip(null);
  }, [tripId]);

  const run = async (method, path, body) => setResult(await api(method, path, body));
  const availability = (available) => run("PUT", `/api/drivers/${driverId}/availability`, { available });

  return (
    <div className="panel">
      <h2>Driver Panel</h2>
      <select value={driverId} onChange={(e) => setDriverId(e.target.value)}>
        <option value="">-- chọn tài xế --</option>
        {list.map((d) => (
          <option key={d.id} value={d.id}>
            {d.fullName} ({d.vehiclePlate ?? "Xe máy"}) · {d.status}
          </option>
        ))}
      </select>

      {me && (
        <div style={{ marginTop: "8px" }}>
          <p style={{ margin: "4px 0" }}>
            Trạng thái:{" "}
            <b className={me.status === "AVAILABLE" ? "up" : me.status === "OFFLINE" ? "down" : ""}>
              {me.status}
            </b>
            {me.currentTripId && (
              <span> · Trip: <code>{me.currentTripId.slice(0, 8)}</code></span>
            )}
          </p>

          {/* Quick status switch buttons */}
          <div className="row">
            <button
              disabled={me.status === "AVAILABLE"}
              onClick={() => availability(true)}
            >
              🟢 Bật Online
            </button>
            <button
              disabled={me.status === "OFFLINE"}
              onClick={() => availability(false)}
            >
              🔴 Tắt Offline
            </button>
          </div>

          {/* GPS Location updater */}
          <div className="row">
            <span>Vị trí:</span>
            <input
              placeholder="lat"
              value={loc.lat}
              onChange={(e) => setLoc({ ...loc, lat: e.target.value })}
            />
            <input
              placeholder="lng"
              value={loc.lng}
              onChange={(e) => setLoc({ ...loc, lng: e.target.value })}
            />
            <button
              onClick={() =>
                run("PUT", `/api/drivers/${driverId}/location`, {
                  lat: Number(loc.lat),
                  lng: Number(loc.lng),
                })
              }
            >
              Cập nhật GPS
            </button>
          </div>

          {/* Presets */}
          <div className="row">
            <span className="hint">Mẫu:</span>
            {PRESET_LOCATIONS.map((p) => (
              <button
                key={p.label}
                onClick={() => {
                  setLoc({ lat: p.lat, lng: p.lng });
                  run("PUT", `/api/drivers/${driverId}/location`, {
                    lat: Number(p.lat),
                    lng: Number(p.lng),
                  });
                }}
              >
                {p.label}
              </button>
            ))}
          </div>

          {/* Active Trip Actions (Full lifecycle) */}
          <div style={{ marginTop: "10px", padding: "8px", border: "1px dashed var(--border)", borderRadius: "4px" }}>
            <h3 style={{ margin: "0 0 6px" }}>Vòng đời chuyến xe</h3>
            <div className="row">
              <input
                placeholder="Nhập hoặc gắn tripId"
                value={tripId}
                onChange={(e) => setTripId(e.target.value)}
                style={{ width: "160px" }}
              />
              <button
                disabled={!tripId}
                onClick={() => run("POST", `/api/trips/${tripId}/accept`)}
              >
                Accept
              </button>
              <button
                disabled={!tripId}
                onClick={() => run("POST", `/api/trips/${tripId}/reject`)}
              >
                Reject
              </button>
              <button
                disabled={!tripId}
                onClick={() => run("POST", `/api/trips/${tripId}/start`)}
              >
                Start
              </button>
              <button
                disabled={!tripId}
                onClick={() => run("POST", `/api/trips/${tripId}/complete`)}
              >
                Complete
              </button>
            </div>

            {trip && !trip.error && (
              <p style={{ margin: "4px 0", fontSize: "12px" }}>
                Chi tiết: Status <b>{trip.status}</b> · Cước: {trip.fare ? `${trip.fare.toLocaleString()} đ` : "Chưa chốt"}
              </p>
            )}
          </div>
        </div>
      )}

      <Result r={result} />
    </div>
  );
}
