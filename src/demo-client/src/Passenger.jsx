import { useState } from "react";
import { api } from "./api.js";
import { Result } from "./App.jsx";
import Login from "./Login.jsx";

const point = (p) => ({ lat: Number(p.lat), lng: Number(p.lng) });

// Hành khách đặt chuyến; chuyến được theo dõi ở khối "Chuyến đang theo dõi" trên tờ chính.
export default function Passenger({ session, onSession, tripId, onTrip }) {
  const [pickup, setPickup] = useState({ lat: "10.7765", lng: "106.7010" });
  const [dropoff, setDropoff] = useState({ lat: "10.7626", lng: "106.6602" });
  const [result, setResult] = useState(null);

  async function requestTrip() {
    const r = await api("POST", "/api/trips", { pickup: point(pickup), dropoff: point(dropoff) }, session?.token);
    setResult(r);
    if (r.ok && r.data?.id) onTrip(r.data.id);
  }

  const coord = (label, value, set) => (
    <div className="field">
      <span>{label}</span>
      <input aria-label={`${label} – vĩ độ`} value={value.lat} onChange={(e) => set({ ...value, lat: e.target.value })} />
      <input aria-label={`${label} – kinh độ`} value={value.lng} onChange={(e) => set({ ...value, lng: e.target.value })} />
    </div>
  );

  return (
    <section className="panel" aria-labelledby="passenger-title">
      <h2 id="passenger-title">Hành khách</h2>
      <Login defaultEmail="passenger1@rideflow.demo" session={session} onSession={onSession} />
      {coord("Điểm đón", pickup, setPickup)}
      {coord("Điểm đến", dropoff, setDropoff)}
      <div className="row">
        <button className="primary" onClick={requestTrip} disabled={!session}>
          Đặt xe
        </button>
        <input aria-label="Theo dõi chuyến theo tripId" className="id" placeholder="hoặc dán tripId để theo dõi" value={tripId} onChange={(e) => onTrip(e.target.value.trim())} />
      </div>
      {!session && <p className="hint">Đăng nhập để đặt xe: gateway từ chối lệnh ghi không có token (401).</p>}
      <Result r={result} />
    </section>
  );
}
