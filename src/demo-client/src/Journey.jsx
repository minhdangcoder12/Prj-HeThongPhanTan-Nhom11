import { useEffect, useState } from "react";
import { poll } from "./api.js";
import { Id, Status } from "./App.jsx";

// Thứ tự các bước lấy từ contract (public-api.yaml); bước hiện tại là đúng status server trả về.
const STEPS = [
  ["REQUESTED", "Đã đặt"],
  ["DRIVER_ASSIGNED", "Đã gán tài xế"],
  ["ACCEPTED", "Tài xế nhận"],
  ["IN_PROGRESS", "Đang chở"],
  ["COMPLETED", "Hoàn thành"],
  ["PAID", "Đã thanh toán"],
];
const OFF_PATH = {
  MATCHING: "Tài xế vừa từ chối, saga đang tìm tài xế khác.",
  NO_DRIVER: "Không còn tài xế nào phù hợp: saga kết thúc, chuyến dừng ở đây.",
  PAYMENT_FAILED: "Thanh toán thất bại: payment-service báo payment.failed.",
  CANCELLED: "Chuyến đã huỷ.",
};
const money = (v) => Number(v).toLocaleString("vi-VN");

export default function Journey({ tripId }) {
  const [trip, setTrip] = useState(null);
  useEffect(() => {
    setTrip(null);
    return tripId ? poll(`/api/trips/${tripId}`, setTrip) : undefined;
  }, [tripId]);

  if (!tripId) {
    return (
      <section className="journey" aria-labelledby="journey-title">
        <h2 id="journey-title">Chuyến đang theo dõi</h2>
        <p className="empty">Chưa có chuyến. Đăng nhập hành khách ở cột trái và bấm Đặt xe: từng bước của chuyến sẽ hiện ở đây.</p>
      </section>
    );
  }
  if (!trip || trip.error) {
    return (
      <section className="journey" aria-labelledby="journey-title">
        <h2 id="journey-title">Chuyến đang theo dõi</h2>
        {trip?.error ? <pre className="result err">{JSON.stringify(trip, null, 2)}</pre> : <p className="empty">Đang tải chuyến…</p>}
      </section>
    );
  }

  const at = STEPS.findIndex(([s]) => s === trip.status);
  const reached = at >= 0 ? at : trip.status === "MATCHING" ? 0 : -1;
  return (
    <section className="journey" aria-labelledby="journey-title">
      <div className="journey-head">
        <h2 id="journey-title">
          Chuyến <Id value={trip.id} />
        </h2>
        <Status value={trip.status} />
      </div>
      <ol className="steps">
        {STEPS.map(([code, label], i) => (
          <li key={code} className={`step ${i === at ? "current" : i <= reached ? "done" : ""}`} aria-current={i === at ? "step" : undefined}>
            <b>{label}</b>
            <small>{code}</small>
          </li>
        ))}
      </ol>
      {OFF_PATH[trip.status] && <p className="journey-note">{OFF_PATH[trip.status]}</p>}
      <dl className="facts">
        <div>
          <dt>Tài xế</dt>
          <dd className="id">
            <Id value={trip.driverId} />
          </dd>
        </div>
        <div>
          <dt>Cước</dt>
          <dd>
            {trip.fare != null ? money(trip.fare) : "–"}
            <span className="unit">đ</span>
          </dd>
        </div>
        <div>
          <dt>Hệ số giá</dt>
          <dd>{trip.surgeMultiplier != null ? `×${Number(trip.surgeMultiplier).toFixed(2)}` : "–"}</dd>
        </div>
        <div>
          <dt>Đặt lúc</dt>
          <dd>{trip.createdAt?.slice(11, 19) ?? "–"}</dd>
        </div>
        <div>
          <dt>Cập nhật</dt>
          <dd>{trip.updatedAt?.slice(11, 19) ?? "–"}</dd>
        </div>
      </dl>
    </section>
  );
}
