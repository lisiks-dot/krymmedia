package ru.pacco.sourcing.internal;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Соблюдаем robots.txt поставщика: правила для «User-agent: *», самое длинное совпадение побеждает,
 * при равной длине — Allow. Поддерживаются «*» и «$». Если robots.txt недоступен — разрешено (как у поисковиков).
 */
@Component
public class RobotsPolicy {

    record Rule(boolean allow, String path, Pattern pattern) {
    }

    private final Fetcher fetcher;
    private final Map<String, List<Rule>> cache = new ConcurrentHashMap<>();

    RobotsPolicy(Fetcher fetcher) {
        this.fetcher = fetcher;
    }

    public boolean allowed(String url) {
        URI uri = URI.create(url);
        String origin = uri.getScheme() + "://" + uri.getHost();
        List<Rule> rules = cache.computeIfAbsent(origin, o -> {
            try {
                return parse(fetcher.get(o + "/robots.txt"));
            } catch (RuntimeException e) {
                return List.of();
            }
        });
        String path = (uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath())
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        return allowed(rules, path);
    }

    static boolean allowed(List<Rule> rules, String path) {
        Rule best = null;
        for (Rule r : rules) {
            if (r.pattern().matcher(path).find()) {
                if (best == null || r.path().length() > best.path().length()
                        || (r.path().length() == best.path().length() && r.allow())) {
                    best = r;
                }
            }
        }
        return best == null || best.allow();
    }

    static List<Rule> parse(String robots) {
        List<Rule> rules = new ArrayList<>();
        boolean inStar = false;
        boolean lastWasAgent = false;
        for (String raw : robots.split("\\R")) {
            String line = raw.replaceAll("#.*", "").trim();
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String key = line.substring(0, colon).trim().toLowerCase();
            String value = line.substring(colon + 1).trim();
            if (key.equals("user-agent")) {
                boolean star = value.equals("*");
                inStar = lastWasAgent ? (inStar || star) : star;
                lastWasAgent = true;
                continue;
            }
            lastWasAgent = false;
            if (!inStar || value.isEmpty()) continue;
            if (key.equals("allow") || key.equals("disallow")) {
                rules.add(new Rule(key.equals("allow"), value, toPattern(value)));
            }
        }
        return rules;
    }

    private static Pattern toPattern(String path) {
        StringBuilder re = new StringBuilder("^");
        for (char c : path.toCharArray()) {
            if (c == '*') re.append(".*");
            else if (c == '$') re.append("$");
            else re.append(Pattern.quote(String.valueOf(c)));
        }
        return Pattern.compile(re.toString());
    }
}
