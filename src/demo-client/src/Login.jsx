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

  if (session) {
    return (
      <p className="signed-in">
        Đã đăng nhập {email} · {session.role}
      </p>
    );
  }
  return (
    <>
      <div className="row">
        <input aria-label="Email" value={email} onChange={(e) => setEmail(e.target.value)} />
        <input aria-label="Mật khẩu" type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
        <button className="primary" onClick={login}>Đăng nhập</button>
      </div>
      {result && !result.ok && <Result r={result} />}
    </>
  );
}
