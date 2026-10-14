import Link from "next/link";

export default function NotFound() {
  return (
    <main className="wrap lost">
      <Link href="/" className="logo">ilpacco<span>.ru</span></Link>
      <h1>Такой страницы нет</h1>
      <p className="muted">Возможно, товар сняли с продажи или в ссылке опечатка.</p>
      <div className="controls">
        <Link href="/catalog" className="btn">Перейти в каталог</Link>
        <Link href="/orders" className="btn ghost">Мои заказы</Link>
      </div>
    </main>
  );
}
