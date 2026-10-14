import Link from "next/link";

export default function NotFound() {
  return (
    <div className="empty">
      Такой страницы или товара нет. <Link href="/">Вернуться в каталог</Link>
    </div>
  );
}
