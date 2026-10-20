package ru.pacco.catalog.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Файлы медиа на диске (в Docker — том media). Ключи генерирует сервер, путь из запроса не берём.
 * Для роста — заменить на S3-совместимое хранилище с тем же интерфейсом.
 */
@Component
public class MediaStorage {

    private final Path root;

    public MediaStorage(@Value("${pacco.media.dir:${java.io.tmpdir}/ilpacco-media}") String dir) throws IOException {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public long save(String key, InputStream in) {
        Path target = path(key);
        try {
            Files.createDirectories(target.getParent());
            return Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path path(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Неверный ключ файла");
        }
        return p;
    }

    public void delete(String key) {
        try {
            Files.deleteIfExists(path(key));
        } catch (IOException ignored) {
            // файл уже удалён или недоступен — запись в базе всё равно удаляем
        }
    }
}
