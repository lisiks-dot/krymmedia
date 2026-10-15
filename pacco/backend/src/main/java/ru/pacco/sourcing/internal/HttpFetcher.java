package ru.pacco.sourcing.internal;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import ru.pacco.sourcing.SourcingProperties;

/**
 * Вежливая загрузка: честный User-Agent, не чаще одного запроса в {@code delayMs} к одному хосту,
 * без обхода защиты от ботов. Если поставщик закрывает доступ — используйте фид партнёрской программы.
 */
@Component
class HttpFetcher implements Fetcher {

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final Map<String, Long> lastRequest = new ConcurrentHashMap<>();
    private final SourcingProperties props;

    HttpFetcher(SourcingProperties props) {
        this.props = props;
    }

    @Override
    public String get(String url) {
        URI uri = URI.create(url);
        pace(uri.getHost());
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", props.userAgent())
                .header("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "it-IT,it;q=0.9")
                .GET().build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new FetchException("Ответ " + resp.statusCode() + " от " + uri.getHost(), resp.statusCode());
            }
            return resp.body();
        } catch (IOException e) {
            throw new FetchException("Сеть: " + e.getMessage(), 0);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FetchException("Прервано", 0);
        }
    }

    private void pace(String host) {
        synchronized (lastRequest) {
            long now = System.currentTimeMillis();
            long wait = lastRequest.getOrDefault(host, 0L) + props.delayMs() - now;
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastRequest.put(host, System.currentTimeMillis());
        }
    }
}
