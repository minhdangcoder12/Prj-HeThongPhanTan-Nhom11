import { useState } from "react";
import { api } from "./api.js";
import { Result } from "./App.jsx";

// Login tối thiểu cho demo: token chỉ giữ trong state của App, mất khi tải lại trang.
export default function Login({ defaultEmail, session, onSession }) {
  const [email, setEmail] = useState(defaultEmail);
  const [password, setPassword] = useState("demo1234");
  const [result, setResult] = useState(null);

  async function login() {
    const r = await api("POST", "/api/auth/login", { email, password });
    setResult(r);
    if (r.ok) onSession(r.data);
  }

  return (
    <>
      <div className="row">
        <input style={{ width: "170px" }} value={email} onChange={(e) => setEmail(e.target.value)} />
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
        <button onClick={login}>Đăng nhập</button>
        {session && <span className="up">{session.role} · {session.userId.slice(0, 8)}</span>}
      </div>
      {result && !result.ok && <Result r={result} />}
    </>
  );
}
