package com.zapas.web;

import java.util.List;
import java.util.UUID;

/** Ответы API. Имена полей совпадают с объектом S, который использует фронт. */
public final class Dto {

    private Dto() {}

    /** total — «срок, дней», start — момент начала отсчёта (epoch ms), thr — порог напоминания, дней. */
    public record Item(UUID id, String name, int total, long start, int thr, boolean notified) {}

    /** from: manual | reminder | sudden */
    public record ListEntry(UUID id, String name, boolean done, UUID itemId, String from) {}

    public record Surprise(String name, long at) {}

    public record Log(List<Surprise> surprise, int reminders, int prevented) {}

    /** fresh — товары, по которым напоминание сработало именно в этом запросе (для Notification). */
    public record State(int offset, List<Item> items, List<ListEntry> list, Log log, List<String> fresh) {}
}
