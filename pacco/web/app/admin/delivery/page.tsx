"use client";

import { QueuePage } from "@/components/Queue";

export default function DeliveryPage() {
  return (
    <QueuePage
      title="Таможня и пункты выдачи"
      intro="Отметки, которые позже будут приходить от перевозчика и ПВЗ автоматически."
      queue="delivery"
      tabs={[
        { status: "HANDED_TO_CARRIER", label: "У перевозчика", hint: "Посылки в пути до границы." },
        { status: "IN_CUSTOMS", label: "На таможне", hint: "Декларацию подаёт таможенный представитель. Отметьте выпуск, когда придёт решение." },
        { status: "RELEASED", label: "Выпущены", hint: "Когда посылка приедет в ПВЗ, покупатель получит код получения." },
        { status: "AT_PICKUP_POINT", label: "В пунктах выдачи", hint: "Выдача — только по коду покупателя. После срока хранения отметьте «Не забрали»: вернём стоимость товаров." },
      ]}
    />
  );
}
