package dev.jonathan.ledgercore.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateEntryRequest(@NotBlank String description, @NotNull @Size(min = 2) @Valid List<LegLine> legs) {
    public record LegLine(@NotNull Long accountId, @NotNull Long amount) {
    }
}
