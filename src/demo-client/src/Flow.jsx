import { useEffect, useMemo, useRef, useState } from "react";
import { Id } from "./App.jsx";

// Đồ thị topology của một chuyến, dựng từ event thật trên RabbitMQ mà gateway nghe được.
// Từng bước hiện lần lượt: đường đang gọi sáng lên, "phong bì" mang tên API/event chạy từ bên gửi sang bên nhận.

const NAME = { client: "Trình duyệt", gateway: "API gateway", trip: "trip-service", rabbit: "RabbitMQ", matching: "matching-service", driver: "driver-service", payment: "payment-service", notification: "notification", redis: "Redis" };
const SUB = { client: "khách · tài xế", gateway: "JWT · route · Consul", trip: "DB rideflow_trip", matching: "saga · DB rideflow_matching", rabbit: "exchange rideflow.events", driver: "DB rideflow_driver", payment: "DB rideflow_payment", notification: "DB rideflow_notification", redis: "GEO vị trí tài xế" };
const POS = { client: [80, 230], gateway: [340, 230], trip: [570, 80], matching: [570, 370], redis: [190, 470], rabbit: [810, 230], driver: [810, 460], payment: [1040, 90], notification: [1040, 370] };
const EDGES = [["client", "gateway"], ["gateway", "trip"], ["trip", "matching"], ["matching", "redis"], ["matching", "driver"], ["trip", "rabbit"], ["matching", "rabbit"], ["driver", "rabbit"], ["rabbit", "payment"], ["rabbit", "notification"]];
const KIND = { rest: "HTTP request (đồng bộ)", reply: "HTTP response", amqp: "Event AMQP (bất đồng bộ)", redis: "Lệnh Redis", db: "Ghi DB trong service", note: "Trạng thái saga", dlq: "Dead-letter queue" };
const TAG = { rest: "HTTP", reply: "HTTP", amqp: "AMQP", redis: "REDIS", db: "DB", note: "SAGA", dlq: "DLQ" };

// shortcut: chép tay từ các MessagingConfig (consumerQueue); thêm queue mới ở service nào thì sửa ở đây cùng PR.
const NOTI = ["notification", "notification.trip-events.q", "gửi tin cho khách và tài xế"];
const LISTEN = {
  "trip.created": [["matching", "matching.trip-created", "bắt đầu saga ghép tài xế"], NOTI],
  "trip.driver-rejected": [["matching", "matching.driver-rejected", "nhả tài xế, tìm người khác"], NOTI],
  "trip.completed": [["payment", "payment.trip-completed.q", "thu tiền chuyến"], NOTI],
  "trip.no-driver": [["trip", "trip.no-driver.q", "chuyển chuyến sang NO_DRIVER"], NOTI],
  "payment.succeeded": [["trip", "trip.payment-events.q", "chuyển chuyến sang PAID"], NOTI],
  "payment.failed": [["trip", "trip.payment-events.q", "chuyển chuyến sang PAYMENT_FAILED"], NOTI],
};
const listen = (type) =>
  LISTEN[type] ?? (type.startsWith("trip.") ? [NOTI] : type.startsWith("driver.") ? [["matching", "matching.driver-events.q", "cập nhật vị trí tài xế rảnh trong Redis"]] : []);

const ACTION = {
  "trip.accepted": ["accept", "Tài xế bấm Nhận chuyến"],
  "trip.driver-arrived": ["arrive", "Tài xế bấm Đã đến điểm đón"],
  "trip.started": ["start", "Tài xế bấm Bắt đầu"],
  "trip.completed": ["complete", "Tài xế bấm Hoàn thành"],
  "trip.driver-rejected": ["reject", "Tài xế bấm Từ chối"],
};

const short = (id) => (!id ? "…" : String(id).startsWith("00000000-0000") ? `…${String(id).slice(-4)}` : String(id).slice(0, 8));
const lane = (producer) => (producer ?? "").replace("-service", "");
// to: một hoặc nhiều nút nhận; store: việc nút nhận làm khi message tới (hiện thành nhãn trên nút).
const S = (t, kind, from, to, label, say, store) => ({ t, kind, from, to: [].concat(to), label, say, store });

// Một event -> các bước. REST không đi qua RabbitMQ nên được dựng lại từ event mà lệnh REST đó tạo ra (lúc = occurredAt);
// bước AMQP lấy lúc gateway thấy message trên RabbitMQ (receivedAt), vì outbox relay đẩy sau khi commit.
function stepsOf(e) {
  const b = e.body;
  const type = b.eventType;
  const made = b.occurredAt ?? e.receivedAt;
  const seen = e.receivedAt ?? made;
  const st = b.payload?.status;
  if (e.deadLettered) return [S(seen, "dlq", "rabbit", "rabbit", `DLQ · ${type}`, `Retry 2s → 5s → 10s vẫn lỗi: RabbitMQ chuyển ${type} sang dead-letter queue.`)];
  if (type === "matching.saga-changed") {
    const note = S(made, "note", "matching", "matching", `saga: ${st}`, `Saga lưu bước ${st} vào bảng matching_sagas trước khi đi tiếp.`);
    return st === "CANDIDATE_SELECTED"
      ? [S(made, "redis", "matching", "redis", "GEOSEARCH 3 km", "Saga hỏi Redis: tài xế rảnh nào gần điểm đón nhất.", "trả danh sách gần nhất"), note]
      : [note];
  }

  const out = [];
  const add = (...s) => out.push(S(made, ...s));
  if (type === "trip.created") {
    add("rest", "client", "gateway", "POST /api/trips", "Khách bấm Đặt xe: trình duyệt gửi request kèm JWT tới gateway.", "kiểm JWT");
    add("rest", "gateway", "trip", "POST /api/trips", "Gateway gắn X-User-Id, hỏi Consul địa chỉ trip-service rồi chuyển request.", "kiểm role PASSENGER");
    add("rest", "trip", "matching", "GET /internal/matching/supply", "trip-service hỏi matching có bao nhiêu tài xế rảnh quanh điểm đón để tính giá.", "đếm tài xế rảnh");
    add("reply", "matching", "trip", `surge ×${Number(b.payload?.surgeMultiplier ?? 1).toFixed(2)}`, "matching trả số tài xế rảnh; trip tính cước nhân hệ số giá.", "tính cước");
    add("db", "trip", "trip", "INSERT trip + outbox", "trip lưu chuyến và ghi event trip.created vào bảng outbox trong CÙNG một transaction.");
    add("reply", "trip", "gateway", "201 Created", "trip trả 201: chuyến đang REQUESTED, chưa có tài xế.");
    add("reply", "gateway", "client", "201 Created", "Khách nhận kết quả ngay. Việc tìm tài xế chạy nền qua event.");
  } else if (ACTION[type]) {
    const [action, who] = ACTION[type];
    add("rest", "client", "gateway", `POST …/${short(b.aggregateId)}/${action}`, `${who}: request kèm JWT của tài xế.`, "kiểm JWT");
    add("rest", "gateway", "trip", `POST …/${action}`, "Gateway gắn X-Driver-Id rồi chuyển request.", "khoá dòng, kiểm tài xế");
    add("db", "trip", "trip", `UPDATE → ${st} + outbox`, "trip kiểm bước chuyển trạng thái, đổi trạng thái và ghi outbox trong cùng transaction.");
    add("reply", "trip", "gateway", `200 ${st}`, "trip trả kết quả.");
    add("reply", "gateway", "client", `200 ${st}`, "Tài xế nhận kết quả ngay.");
  } else if (type === "trip.driver-assigned") {
    add("rest", "matching", "trip", "POST /internal/…/assign-driver", "Saga gán tài xế vào chuyến, kèm Idempotency-Key nên gọi lại không gán hai lần.", "UPDATE + outbox");
    add("reply", "trip", "matching", "200 DRIVER_ASSIGNED", "trip đã đổi chuyến sang DRIVER_ASSIGNED.");
  } else if (type === "driver.reserved") {
    add("rest", "matching", "driver", `POST /internal/drivers/${short(b.aggregateId)}/reserve`, "Saga giữ tài xế. Hai chuyến tranh nhau thì chỉ một chuyến thắng, nhờ câu UPDATE nguyên tử.", "UPDATE … WHERE AVAILABLE");
    add("reply", "driver", "matching", "200 RESERVED", "Giữ được tài xế.");
  }
  // Bước đầu của một thao tác người dùng (đặt xe, tài xế bấm nút): đồ thị nhảy tới đây khi thao tác mới xảy ra.
  if (out.length && (type === "trip.created" || ACTION[type])) out[0].start = true;
  out.push(S(seen, "amqp", lane(b.producer), "rabbit", type, `Outbox relay của ${b.producer} đẩy ${type} lên exchange rideflow.events.`, "route theo routing key"));
  // RabbitMQ chép vào mọi queue cùng lúc; tách mỗi queue một bước để đồ thị chỉ có một đường mỗi lúc.
  const subs = listen(type);
  subs.forEach(([to, queue, what], k) =>
    out.push(S(seen, "amqp", "rabbit", to, type, `RabbitMQ chép ${type} vào queue ${queue}${subs.length > 1 ? ` (${k + 1}/${subs.length}, cùng lúc)` : ""}: ${to} ${what}.`, "inbox ✓ rồi xử lý")));
  return out;
}

const ofTrip = (tripId) => (e) =>
  e.body && e.body.eventType !== "notification.sent" && (e.body.aggregateId === tripId || e.body.payload?.tripId === tripId);
const clock = (t) => {
  const d = new Date(t);
  return isNaN(d) ? "" : `${d.toLocaleTimeString("vi-VN", { hour12: false })}.${String(d.getMilliseconds()).padStart(3, "0")}`;
};

const NW = 156, NH = 58, TRAVEL = 1.0; // giây phong bì chạy tới giữa đường; nút nhận gợn sóng ngay sau đó
const pair = (a, b) => [a, b].sort().join("|");
// Đoạn thẳng giữa hai nút, cắt bớt hai đầu để mũi tên dừng ở mép hộp.
const seg = (a, b) => {
  const [x1, y1] = POS[a], [x2, y2] = POS[b];
  const dx = x2 - x1, dy = y2 - y1;
  const k = Math.min(Math.abs((NW / 2 + 8) / (dx || 1e-9)), Math.abs((NH / 2 + 8) / (dy || 1e-9)));
  return [x1 + dx * k, y1 + dy * k, x2 - dx * k, y2 - dy * k];
};

const ease = (p) => (p < 0.5 ? 4 * p * p * p : 1 - (-2 * p + 2) ** 3 / 2); // chậm đầu, nhanh giữa, chậm cuối

// Tiến độ 0 → 1 trong TRAVEL giây, đếm từ lúc bước này hiện (requestAnimationFrame: mượt, không phụ thuộc giờ của <svg>).
function useTravel(still) {
  const [p, setP] = useState(still ? 1 : 0);
  useEffect(() => {
    if (still) return undefined;
    let raf, start;
    const tick = (now) => {
      start ??= now;
      const v = Math.min((now - start) / (TRAVEL * 1000), 1);
      setP(v);
      if (v < 1) raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [still]);
  return p;
}

// Một lệnh gọi: dây mọc dần từ bên gửi, đầu dây là chấm sáng kèm đuôi sao chổi; phong bì tên API trượt mượt
// từ bên gửi tới giữa dây rồi đứng đó (một nhãn duy nhất). Tới nơi thì vạch sáng chảy đều về phía bên nhận.
function Call({ kind, from, to, label, still }) {
  const p = useTravel(still);
  const e = ease(p);
  const [x1, y1, x2, y2] = seg(from, to);
  const at = (t) => [x1 + (x2 - x1) * t, y1 + (y2 - y1) * t];
  const [hx, hy] = at(e);
  const [cx, cy] = at(0.5 * e);
  const w = label.length * 7.6 + 24;
  const arrived = p >= 1;
  return (
    <g className={`call ${kind}`}>
      <line className="glow" x1={x1} y1={y1} x2={hx} y2={hy} />
      <line className="wire" x1={x1} y1={y1} x2={hx} y2={hy} markerEnd={`url(#topo-${kind})`} />
      {arrived && <line className="flow" x1={x1} y1={y1} x2={x2} y2={y2} />}
      {!arrived &&
        [1, 2, 3, 4, 5].map((k) => {
          const [tx, ty] = at(Math.max(0, e - k * 0.035));
          return <circle key={k} className="tail" cx={tx} cy={ty} r={7 - k} opacity={0.55 - k * 0.09} />;
        })}
      {!arrived && <circle className="comet" cx={hx} cy={hy} r={8} />}
      <g className="envelope" transform={`translate(${cx} ${cy})`}>
        <rect x={-w / 2} y={-13} width={w} height={26} rx={13} />
        <text y={5} textAnchor="middle">{label}</text>
      </g>
    </g>
  );
}

export default function Flow({ tripId }) {
  const [events, setEvents] = useState([]);
  const [shown, setShown] = useState(0);
  const [playing, setPlaying] = useState(true);
  const [speed, setSpeed] = useState(2500);
  const still = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;

  useEffect(() => {
    const source = new EventSource("/api/monitor/events");
    // Mỗi lần nối lại, gateway gửi lại lịch sử: bỏ event đã có (bản vào DLQ giữ eventId gốc nên tính riêng).
    const key = (e) => `${e.body?.eventId}:${e.deadLettered}`;
    source.onmessage = (m) => {
      const e = JSON.parse(m.data);
      setEvents((prev) => (prev.some((p) => key(p) === key(e)) ? prev : [...prev, e].slice(-300)));
    };
    return () => source.close();
  }, []);
  const handled = useRef(-1); // vị trí thao tác người dùng gần nhất đã nhảy tới
  useEffect(() => {
    setShown(0);
    setPlaying(true);
    handled.current = -1;
  }, [tripId]);

  const steps = useMemo(() => {
    const all = events.filter(ofTrip(tripId)).flatMap((e) => stepsOf(e).map((s) => ({ ...s, ev: e.body }))).map((s, i) => ({ ...s, i }));
    return all.sort((a, b) => Date.parse(a.t) - Date.parse(b.t) || a.i - b.i);
  }, [events, tripId]);

  // Đồng bộ với thao tác: có thao tác mới (đặt xe, tài xế bấm nút) thì hiện ngay bước đầu của nó,
  // bỏ qua các bước cũ chưa phát (Phát lại để xem đủ). Không thì hàng đợi 2,5 s/bước làm đồ thị trễ cả phút.
  const lastStart = steps.findLastIndex((s) => s.start);
  useEffect(() => {
    if (lastStart > handled.current) {
      handled.current = lastStart;
      setShown(lastStart + 1);
      setPlaying(true);
    }
  }, [lastStart]);

  // Phụ thuộc atEnd chứ không phải steps.length: event mới đổ về không được đặt lại đồng hồ của bước đang hiện.
  const atEnd = shown >= steps.length;
  useEffect(() => {
    if (!playing || atEnd) return undefined;
    const t = setTimeout(() => setShown((n) => n + 1), speed);
    return () => clearTimeout(t);
  }, [playing, shown, atEnd, speed]);

  const n = Math.min(shown, steps.length);
  const cur = steps[n - 1];
  const moving = cur && cur.to[0] !== cur.from;
  const used = new Set(steps.slice(0, n).flatMap((s) => s.to.map((t) => pair(s.from, t))));
  const role = (id) => (!cur ? "" : moving && cur.to.includes(id) ? " dst" : cur.from === id ? " src" : "");

  return (
    <section className="block seq" aria-labelledby="seq-title">
      <div className="block-head">
        <h2 id="seq-title">Luồng hệ thống{tripId && <> · chuyến <Id value={tripId} /></>}</h2>
        <span className="row">
          <button onClick={() => setPlaying(!playing)} disabled={n >= steps.length}>{playing ? "Tạm dừng" : "Chạy tiếp"}</button>
          <button onClick={() => { setPlaying(false); setShown(n + 1); }} disabled={n >= steps.length}>Bước tiếp</button>
          <button onClick={() => { setShown(0); setPlaying(true); }}>Phát lại</button>
          <button className="ghost" onClick={() => setShown(steps.length)}>Hiện hết</button>
          <select aria-label="Tốc độ" value={speed} onChange={(e) => setSpeed(Number(e.target.value))}>
            <option value={4000}>Chậm</option>
            <option value={2500}>Vừa</option>
            <option value={1400}>Nhanh</option>
          </select>
        </span>
      </div>

      <div className={`seq-cap ${cur?.kind ?? ""}`} aria-live="polite">
        {cur ? (
          <>
            <span className="meta">
              Bước {n}/{steps.length}{n === steps.length && " (đã chạy hết, chờ event mới)"} · <span className={`kind ${cur.kind}`}>{KIND[cur.kind]}</span> · {NAME[cur.from]}
              {moving && <> → {NAME[cur.to[0]]}</>}{cur.store && <> ({cur.store})</>} · {clock(cur.t)}
            </span>
            <b key={n} className="say">{cur.say}</b>
          </>
        ) : (
          <b className="say">
            {!tripId
              ? "Đăng nhập Hành khách ở cột trái rồi bấm Đặt xe: từng request và event sẽ chạy lần lượt qua các service ở đây."
              : steps.length ? "Bấm Chạy tiếp hoặc Bước tiếp để xem từng bước." : "Đang chờ event đầu tiên của chuyến…"}
          </b>
        )}
      </div>
      {/* JSON thật của event tạo ra bước này; mở ra thì giữ nguyên khi sang bước sau. */}
      {cur && (
        <details className="result">
          <summary>
            JSON event của bước này: <code>{cur.ev.eventType}</code> · eventId <code>{short(cur.ev.eventId)}</code> · correlationId <code>{short(cur.ev.correlationId)}</code>
          </summary>
          <pre>{JSON.stringify(cur.ev, null, 2)}</pre>
        </details>
      )}

      <div className="topo-wrap">
        <svg className="topo" width="100%" viewBox="-10 20 1140 495" role="img" aria-label="Đồ thị các service, đường đang gọi sáng lên">
          <defs>
            {["rest", "reply", "amqp", "redis"].map((k) => (
              <marker key={k} id={`topo-${k}`} viewBox="0 0 10 10" refX="9" refY="5" markerWidth="5" markerHeight="5" orient="auto">
                <path d="M0 0 L10 5 L0 10 z" className={`head ${k}`} />
              </marker>
            ))}
          </defs>
          {EDGES.map(([a, b]) => {
            const [x1, y1, x2, y2] = seg(a, b);
            return <line key={pair(a, b)} className={used.has(pair(a, b)) ? "edge used" : "edge"} x1={x1} y1={y1} x2={x2} y2={y2} />;
          })}
          {Object.entries(POS).map(([id, [x, y]]) => {
            const r = role(id);
            // Nhãn trên nút chỉ cho bước tự làm (ghi DB, saga, DLQ): bước có dây đã có phong bì, tránh hai nhãn cùng lúc.
            const badge = cur && !moving && cur.from === id && cur.label;
            return (
              <g key={id} className={`node ${id}${r}`}>
                {r === " dst" && !still && <rect key={n} className="ripple" x={x - NW / 2} y={y - NH / 2} width={NW} height={NH} rx={12} style={{ animationDelay: `${TRAVEL}s` }} />}
                <rect className="box" x={x - NW / 2} y={y - NH / 2} width={NW} height={NH} rx={12} />
                <text className="name" x={x} y={y - 2} textAnchor="middle">{NAME[id]}</text>
                <text className="sub" x={x} y={y + 18} textAnchor="middle">{SUB[id]}</text>
                {badge && (
                  <g key={n} className={`badge ${cur.kind}`} style={{ animationDelay: moving ? `${TRAVEL}s` : "0s" }}>
                    <rect x={x - badge.length * 3.9 - 14} y={y - NH / 2 - 32} width={badge.length * 7.8 + 28} height={26} rx={13} />
                    <text x={x} y={y - NH / 2 - 14} textAnchor="middle">{badge}</text>
                  </g>
                )}
              </g>
            );
          })}
          {moving && <Call key={n} kind={cur.kind} from={cur.from} to={cur.to[0]} label={cur.label} still={still} />}
        </svg>
        <div className="legend">
          <span><i className="sw rest" />HTTP request</span>
          <span><i className="sw reply" />HTTP response</span>
          <span><i className="sw amqp" />Event qua RabbitMQ</span>
          <span><i className="sw redis" />Redis</span>
          <span><i className="sw db" />Việc trong service</span>
        </div>
      </div>

      <ol className="topo-log" start={Math.max(1, n - 5)}>
        {steps.slice(Math.max(0, n - 6), n).map((s, i, arr) => (
          <li key={`${n}-${i}`} className={i === arr.length - 1 ? "cur" : ""}>
            <span className={`kind ${s.kind}`}>{TAG[s.kind]}</span>
            <b>{NAME[s.from]}</b>
            {s.to[0] !== s.from && <> → <b>{s.to.map((t) => NAME[t]).join(" + ")}</b></>}
            <code>{s.label}</code>
          </li>
        ))}
      </ol>
      <p className="panel-note">
        Mũi tên HTTP và việc trong service được dựng lại từ event mà lệnh đó tạo ra (REST không đi qua RabbitMQ); mũi tên AMQP là message thật gateway thấy trên RabbitMQ.
      </p>
    </section>
  );
}
