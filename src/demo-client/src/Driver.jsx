import { useEffect, useState } from "react";
import { api, poll } from "./api.js";
import { Result, Status } from "./App.jsx";
import Login from "./Login.jsx";

const PRESET_LOCATIONS = [
  { label: "Bến Thành (Q1)", lat: "10.7769", lng: "106.7009" },
  { label: "Landmark 81 (BT)", lat: "10.7951", lng: "106.7218" },
  { label: "Sân bay TSN (TB)", lat: "10.8185", lng: "106.6588" },
];

// Nút nên bấm tiếp và lời nhắc, theo status chuyến server trả về (chỉ hiển thị, nút nào cũng bấm được như cũ).
const NEXT_ACTION = {
  DRIVER_ASSIGNED: ["accept", "Có chuyến mới: bấm Nhận chuyến hoặc Từ chối."],
  ACCEPTED: ["arrive", "Lái tới điểm đón. Tới nơi, bấm Đã đến điểm đón."],
  ARRIVED: ["start", "Khách lên xe thì bấm Bắt đầu."],
  IN_PROGRESS: ["complete", "Tới điểm đến thì bấm Hoàn thành."],
};
const IDLE = {
  AVAILABLE: "Đang chờ chuyến… Saga sẽ gán chuyến khi có khách gần bạn.",
  OFFLINE: "Đang nghỉ. Bấm Bật nhận chuyến để nhận khách.",
  RESERVED: "Đang được giữ cho một chuyến.",
};

export default function Driver({ session, onSession }) {
  const [drivers, setDrivers] = useState([]);
  const [driverId, setDriverId] = useState("");
  const [loc, setLoc] = useState({ lat: "10.7769", lng: "106.7009" });
  const [tripId, setTripId] = useState("");
  const [trip, setTrip] = useState(null);
  const [result, setResult] = useState(null);

  useEffect(() => poll("/api/drivers", setDrivers), []);
  // Đăng nhập tài xế thì tự chọn đúng hồ sơ driver của tài khoản đó
  useEffect(() => { if (session?.driverId) setDriverId(session.driverId); }, [session?.driverId]);
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

  const run = async (method, path, body) => setResult(await api(method, path, body, session?.token));
  const availability = (available) => run("PUT", `/api/drivers/${driverId}/availability`, { available });

  const [nextAction, nextSay] = (trip && !trip.error && NEXT_ACTION[trip.status]) || [];
  const say = !me ? "Đăng nhập rồi chọn hồ sơ tài xế." : nextSay ?? IDLE[me.status] ?? "";
  const tripButton = (action, label, wide = true) => (
    <button
      className={`${action === nextAction ? "primary main" : ""}${wide ? " wide" : ""}`}
      disabled={!tripId}
      onClick={() => run("POST", `/api/trips/${tripId}/${action}`)}
    >
      {label}
    </button>
  );

  return (
    <section className="panel card driver" aria-labelledby="driver-title">
      <h2 id="driver-title">Tài xế</h2>
      <Login defaultEmail="driver1@rideflow.demo" session={session} onSession={onSession} />
      <select aria-label="Hồ sơ tài xế" value={driverId} onChange={(e) => setDriverId(e.target.value)}>
        <option value="">-- chọn tài xế --</option>
        {list.map((d) => (
          <option key={d.id} value={d.id}>
            {d.fullName} ({d.vehiclePlate ?? "Xe máy"}) · {d.status}
          </option>
        ))}
      </select>

      <div className="now" aria-live="polite">
        <span className="now-label">Trạng thái</span>
        {me ? <Status value={me.status} /> : <span className="dash">chưa chọn</span>}
        {trip && !trip.error && (
          <>
            <span className="now-label">Chuyến</span>
            <Status value={trip.status} />
          </>
        )}
        <p key={say} className="now-say">{say}</p>
        {trip && !trip.error && (
          <p className="now-meta">
            <code>{tripId.slice(0, 8)}</code> · Cước: {trip.fare ? `${trip.fare.toLocaleString()} đ` : "Chưa chốt"}
          </p>
        )}
      </div>

      {me && (
        <>
          {/* Vòng đời chuyến: nút cần bấm tiếp được tô đậm */}
          <div className="trip-actions">
            <input
              aria-label="tripId"
              placeholder="tripId"
              className="wide"
              value={tripId}
              onChange={(e) => setTripId(e.target.value)}
            />
            {tripButton("accept", "Nhận chuyến", false)}
            {tripButton("reject", "Từ chối", false)}
            {tripButton("arrive", "Đã đến điểm đón")}
            {tripButton("start", "Bắt đầu")}
            {tripButton("complete", "Hoàn thành")}
          </div>

          {/* Quick status switch buttons */}
          <div className="row">
            <button
              disabled={me.status === "AVAILABLE"}
              onClick={() => availability(true)}
            >
              Bật nhận chuyến
            </button>
            <button
              disabled={me.status === "OFFLINE"}
              onClick={() => availability(false)}
            >
              Nghỉ (offline)
            </button>
          </div>

          {/* GPS Location updater */}
          <div className="row">
            <span>Vị trí:</span>
            <input
              placeholder="vĩ độ"
              value={loc.lat}
              onChange={(e) => setLoc({ ...loc, lat: e.target.value })}
            />
            <input
              placeholder="kinh độ"
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
        </>
      )}

      <Result r={result} />
    </section>
  );
}
