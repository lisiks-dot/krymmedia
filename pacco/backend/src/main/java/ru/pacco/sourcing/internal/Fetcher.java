package ru.pacco.sourcing.internal;

/** Загрузка страниц поставщика. Отдельный интерфейс — чтобы тесты работали на сохранённых страницах. */
public interface Fetcher {

    /** HTML или текст по адресу; ошибка сети или ответ не 200 — {@link FetchException}. */
    String get(String url);

    class FetchException extends RuntimeException {
        private final int status;

        public FetchException(String message, int status) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }
}
