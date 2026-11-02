import { useEffect, useState } from "react";
import { api, poll } from "./api.js";
import { Result } from "./App.jsx";

const point = (p) => ({ lat: Number(p.lat), lng: Number(p.lng) });

export default function Passenger() {
  const [passengerId, setPassengerId] = useState(() => crypto.randomUUID());
  const [pickup, setPickup] = useState({ lat: "10.7765", lng: "106.7010" });
  const [dropoff, setDropoff] = useState({ lat: "10.7626", lng: "106.6602" });
  const [result, setResult] = useState(null);
  const [tripId, setTripId] = useState("");
  const [trip, setTrip] = useState(null);

  useEffect(() => (tripId ? poll(`/api/trips/${tripId}`, setTrip) : undefined), [tripId]);

  async function requestTrip() {
    const r = await api("POST", "/api/trips", { passengerId, pickup: point(pickup), dropoff: point(dropoff) });
    setResult(r);
    if (r.ok && r.data?.id) setTripId(r.data.id);
  }

  return (
    <div className="panel">
      <h2>Passenger</h2>
      <label>
        passengerId <input value={passengerId} onChange={(e) => setPassengerId(e.target.value)} />
      </label>
      <div className="row">
        pickup
        <input value={pickup.lat} onChange={(e) => setPickup({ ...pickup, lat: e.target.value })} />
        <input value={pickup.lng} onChange={(e) => setPickup({ ...pickup, lng: e.target.value })} />
      </div>
      <div className="row">
        dropoff
        <input value={dropoff.lat} onChange={(e) => setDropoff({ ...dropoff, lat: e.target.value })} />
        <input value={dropoff.lng} onChange={(e) => setDropoff({ ...dropoff, lng: e.target.value })} />
      </div>
      <div className="row">
        <button onClick={requestTrip}>Đặt xe</button>
        <input placeholder="theo dõi tripId" value={tripId} onChange={(e) => setTripId(e.target.value)} />
      </div>
      {trip && !trip.error && (
        <p>
          Trip <code>{trip.id?.slice(0, 8)}</code>: <b>{trip.status}</b> · driver {trip.driverId?.slice(0, 8) ?? "-"}
        </p>
      )}
      <Result r={result} />
    </div>
  );
}
