import { useEffect, useState } from "react";
import { api, poll } from "./api.js";
import { Result } from "./App.jsx";

export default function Driver() {
  const [drivers, setDrivers] = useState([]);
  const [driverId, setDriverId] = useState("");
  const [loc, setLoc] = useState({ lat: "10.7769", lng: "106.7009" });
  const [tripId, setTripId] = useState("");
  const [result, setResult] = useState(null);

  useEffect(() => poll("/api/drivers", setDrivers), []);
  const list = Array.isArray(drivers) ? drivers : [];
  const me = list.find((d) => d.id === driverId);

  // Prefill the trip the server says this driver holds; the client never guesses state itself.
  useEffect(() => {
    if (me?.currentTripId) setTripId(me.currentTripId);
  }, [me?.currentTripId]);

  const run = async (method, path, body) => setResult(await api(method, path, body));
  const availability = (available) => run("PUT", `/api/drivers/${driverId}/availability`, { available });

  return (
    <div className="panel">
      <h2>Driver</h2>
      <select value={driverId} onChange={(e) => setDriverId(e.target.value)}>
        <option value="">-- chọn tài xế --</option>
        {list.map((d) => (
          <option key={d.id} value={d.id}>
            {d.fullName} ({d.status})
          </option>
        ))}
      </select>
      {driverId && (
        <>
          <div className="row">
            <button onClick={() => availability(true)}>Online</button>
            <button onClick={() => availability(false)}>Offline</button>
          </div>
          <div className="row">
            <input value={loc.lat} onChange={(e) => setLoc({ ...loc, lat: e.target.value })} />
            <input value={loc.lng} onChange={(e) => setLoc({ ...loc, lng: e.target.value })} />
            <button
              onClick={() => run("PUT", `/api/drivers/${driverId}/location`, { lat: Number(loc.lat), lng: Number(loc.lng) })}
            >
              Cập nhật vị trí
            </button>
          </div>
          <div className="row">
            <input placeholder="tripId" value={tripId} onChange={(e) => setTripId(e.target.value)} />
            {["accept", "reject", "start", "complete"].map((a) => (
              <button key={a} disabled={!tripId} onClick={() => run("POST", `/api/trips/${tripId}/${a}`)}>
                {a}
              </button>
            ))}
          </div>
        </>
      )}
      <Result r={result} />
    </div>
  );
}
