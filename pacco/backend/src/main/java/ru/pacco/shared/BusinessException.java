package ru.pacco.shared;

import java.util.List;

/** Ошибка бизнес-правила, которую API отдаёт клиенту как Problem Details. */
public class BusinessException extends RuntimeException {

    private final String code;
    private final int status;
    private final List<String> details;

    public BusinessException(int status, String code, String message, List<String> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public static BusinessException notFound(String what) {
        return new BusinessException(404, "not_found", what + " не найден", List.of());
    }

    public static BusinessException unprocessable(String code, String message) {
        return new BusinessException(422, code, message, List.of());
    }

    public static BusinessException unprocessable(String code, String message, List<String> details) {
        return new BusinessException(422, code, message, details);
    }

    public String code() {
        return code;
    }

    public int status() {
        return status;
    }

    public List<String> details() {
        return details;
    }
}
