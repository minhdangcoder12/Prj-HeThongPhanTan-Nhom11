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
// Gợi ý cho người demo theo status server trả về: chuyện gì đang xảy ra và bấm gì tiếp theo.
const NEXT = {
  REQUESTED: "Saga đang tìm tài xế rảnh gần điểm đón…",
  MATCHING: "Tài xế vừa từ chối, saga đang tìm tài xế khác.",
  DRIVER_ASSIGNED: "Tiếp theo: ở khối Tài xế, đăng nhập và bấm Accept (hoặc Reject để xem saga tìm người khác).",
  ACCEPTED: "Tiếp theo: tài xế bấm Start khi đón được khách.",
  IN_PROGRESS: "Tiếp theo: tài xế bấm Complete; payment-service sẽ tự thu tiền.",
  COMPLETED: "payment-service đang thu tiền: vài giây nữa chuyến chuyển sang Đã thanh toán.",
  PAID: "Chuyến đã xong. Có thể đặt chuyến mới hoặc chạy một kịch bản lỗi.",
  NO_DRIVER: "Không còn tài xế phù hợp, chuyến dừng ở đây. Bật D01 và đặt vị trí gần điểm đón ở khối Tài xế rồi đặt lại.",
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
      {NEXT[trip.status] && <p className="journey-note">{NEXT[trip.status]}</p>}
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
