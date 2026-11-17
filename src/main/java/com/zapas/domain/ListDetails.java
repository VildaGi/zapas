package com.zapas.domain;

import java.math.BigDecimal;
import java.util.Set;

/** Количество, единица и заметка позиции списка (всё необязательно, кроме единицы по умолчанию). */
public record ListDetails(BigDecimal quantity, String unit, String note) {

    public static final String DEFAULT_UNIT = "шт";
    public static final Set<String> UNITS = Set.of("шт", "уп.", "кг", "г", "л", "мл");
    public static final ListDetails EMPTY = new ListDetails(null, DEFAULT_UNIT, null);

    /** Приводит ввод пользователя к виду, который можно сохранить; бросает IllegalArgumentException при ошибке. */
    public static ListDetails normalize(BigDecimal quantity, String unit, String note) {
        if (quantity != null && quantity.signum() <= 0) {
            throw new IllegalArgumentException("Количество должно быть больше нуля");
        }
        String u = unit == null || unit.isBlank() ? DEFAULT_UNIT : unit.trim();
        if (!UNITS.contains(u)) {
            throw new IllegalArgumentException("Недопустимая единица измерения: " + u);
        }
        String n = note == null || note.isBlank() ? null : note.trim();
        if (n != null && n.length() > 200) {
            throw new IllegalArgumentException("Заметка слишком длинная (не более 200 символов)");
        }
        return new ListDetails(quantity, u, n);
    }
}
