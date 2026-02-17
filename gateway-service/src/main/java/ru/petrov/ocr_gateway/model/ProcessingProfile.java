package ru.petrov.ocr_gateway.model;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum ProcessingProfile {
    PARSE("parse"),
    CLEAN_PARSE("clean_parse"),
    NORM_PARSE("norm_parse"),
    FULL("clean_norm_parse"); // В воркере это "clean_norm_parse", но если в контракте "full" — оставляем "full"

    @JsonValue // Для Jackson (чтобы в RabbitMQ улетало "clean_norm_parse")
    private final String value;


    ProcessingProfile(String value) {
        this.value = value;
    }

    @Override
    public String toString() { // Для Hibernate (чтобы в базу писалось "clean_norm_parse")
        return value;
    }

    /**
     * Безопасный поиск профиля по строке
     * Если профиль не найден — возвращаем дефолтный PARSE
     */
    public static ProcessingProfile fromString(String text) {
        for (ProcessingProfile profile : ProcessingProfile.values()) {
            if (profile.value.equalsIgnoreCase(text)) {
                return profile;
            }
        }
        return PARSE;
    }
}
