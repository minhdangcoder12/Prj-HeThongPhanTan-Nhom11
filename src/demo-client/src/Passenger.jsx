import { useEffect, useState } from "react";
import { api, poll } from "./api.js";
import { Result, Status } from "./App.jsx";
import Login from "./Login.jsx";

const point = (p) => ({ lat: Number(p.lat), lng: Number(p.lng) });

// Lời nhắn cho hành khách theo status server trả về (chỉ hiển thị, không suy ra trạng thái).
const SAY = {
  REQUESTED: "Đang tìm tài xế gần bạn…",
  MATCHING: "Tài xế vừa từ chối, đang tìm người khác…",
  DRIVER_ASSIGNED: "Đã có tài xế, chờ tài xế nhận chuyến.",
  ACCEPTED: "Tài xế đang đến điểm đón.",
  ARRIVED: "Tài xế đã tới điểm đón, mời bạn lên xe.",
  IN_PROGRESS: "Đang trên đường tới điểm đến.",
  COMPLETED: "Đã tới nơi, đang thanh toán…",
  PAID: "Đã thanh toán. Có thể đặt chuyến mới.",
  NO_DRIVER: "Không có tài xế phù hợp. Thử đặt lại.",
  PAYMENT_FAILED: "Thanh toán thất bại.",
  CANCELLED: "Chuyến đã huỷ.",
};

// Hành khách đặt chuyến; chuyến được theo dõi ở khối "Chuyến đang theo dõi" trên tờ chính.
export default function Passenger({ session, onSession, tripId, onTrip, tripStatus }) {
  const [pickup, setPickup] = useState({ lat: "10.7765", lng: "106.7010" });
  const [dropoff, setDropoff] = useState({ lat: "10.7626", lng: "106.6602" });
  const [result, setResult] = useState(null);
  const [notes, setNotes] = useState([]);

  useEffect(() => (session ? poll("/api/notifications", (d) => Array.isArray(d) && setNotes(d)) : setNotes([])), [session]);

  // Tin nhắn server gửi cho chính hành khách này, mới nhất trước.
  const inbox = notes
    .filter((n) => n.recipientId === session?.userId)
    .sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1))
    .slice(0, 3);

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

  const say = !session
    ? "Đăng nhập để đặt xe (không có token → 401)."
    : tripId
      ? SAY[tripStatus] ?? "Đang tải chuyến…"
      : "Nhập điểm đón, điểm đến rồi bấm Đặt xe.";

  return (
    <section className="panel card passenger" aria-labelledby="passenger-title">
      <h2 id="passenger-title">Hành khách</h2>
      <Login defaultEmail="passenger1@rideflow.demo" session={session} onSession={onSession} />
      <div className="now" aria-live="polite">
        <span className="now-label">Chuyến của bạn</span>
        {tripId ? <Status value={tripStatus} /> : <span className="dash">chưa có</span>}
        <p key={say} className="now-say">{say}</p>
      </div>
      {session && (
        <div className="inbox" aria-live="polite">
          <span className="now-label">Tin nhắn</span>
          {inbox.length === 0 ? (
            <span className="dash">chưa có</span>
          ) : (
            <ul>
              {/* key = id: chỉ tin mới mount nên chỉ tin mới chạy hiệu ứng. */}
              {inbox.map((n) => (
                <li key={n.id}>
                  <span>{n.message}</span>
                  <time dateTime={n.createdAt}>{new Date(n.createdAt).toLocaleTimeString()}</time>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
      {coord("Điểm đón", pickup, setPickup)}
      {coord("Điểm đến", dropoff, setDropoff)}
      <button className="primary main" onClick={requestTrip} disabled={!session}>
        Đặt xe
      </button>
      <input aria-label="Theo dõi chuyến theo tripId" className="id" placeholder="hoặc dán tripId để theo dõi" value={tripId} onChange={(e) => onTrip(e.target.value.trim())} />
      <Result r={result} />
    </section>
  );
}
