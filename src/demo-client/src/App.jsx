import { useEffect, useState } from "react";
import { poll } from "./api.js";
import Passenger from "./Passenger.jsx";
import Driver from "./Driver.jsx";
import Chaos from "./Chaos.jsx";
import Monitor from "./Monitor.jsx";

export default function App() {
  const [passenger, setPassenger] = useState(null);
  const [driver, setDriver] = useState(null);
  const [health, setHealth] = useState({});
  useEffect(() => poll("/api/monitor/health", setHealth, 3000), []);

  return (
    <>
      <header>
        <strong>RideFlow demo</strong>
        <span className="health">
          gateway <b className={health.error ? "down" : "up"}>{health.error ? "DOWN" : "UP"}</b>
          {!health.error &&
            Object.entries(health).map(([svc, status]) => (
              <span key={svc}>
                {svc} <b className={status === "UP" ? "up" : "down"}>{status}</b>
              </span>
            ))}
        </span>
      </header>
      <main>
        <section className="controls">
          <Passenger session={passenger} onSession={setPassenger} />
          <Driver session={driver} onSession={setDriver} />
          <Chaos token={passenger?.token} />
        </section>
        <Monitor />
      </main>
    </>
  );
}

// Shows the raw outcome of an action, including ProblemDetail errors (rule R7: never hide errors).
export function Result({ r }) {
  if (!r) return null;
  return (
    <pre className={r.ok ? "result ok" : "result err"}>
      {r.status} · corr {r.correlationId?.slice(0, 8)}
      {"\n"}
      {typeof r.data === "string" ? r.data : JSON.stringify(r.data, null, 2)}
    </pre>
  );
}
