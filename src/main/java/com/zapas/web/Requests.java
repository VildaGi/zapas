package com.zapas.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

public final class Requests {

    private Requests() {}

    public record ItemCreate(
            @NotBlank @Size(max = 200) String name,
            @Min(1) @Max(3650) Integer total,
            @Min(0) @Max(3650) Integer thr) {}

    public record ItemPatch(
            @Size(max = 200) String name,
            @Min(1) @Max(3650) Integer total,
            @Min(0) @Max(3650) Integer thr) {}

    public record ListCreate(@NotBlank @Size(max = 200) String name) {}

    public record ListPatch(@Size(max = 200) String name, Boolean done) {}

    public record LogEvent(@NotBlank String type, UUID itemId, Map<String, Object> properties) {}

    public record TimeShift(@NotNull @Min(-3650) @Max(3650) Integer offset) {}
}
