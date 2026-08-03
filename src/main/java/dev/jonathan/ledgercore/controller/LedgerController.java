package dev.jonathan.ledgercore.controller;

import dev.jonathan.ledgercore.domain.JournalEntry;
import dev.jonathan.ledgercore.service.LedgerService;
import dev.jonathan.ledgercore.service.LegRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;


@RestController
@RequestMapping("/api/entries")
public class LedgerController {
    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateEntryResponse create(@Valid @RequestBody CreateEntryRequest request) {
        List<LegRequest> legs = new ArrayList<>();
        for (CreateEntryRequest.LegLine line : request.legs()) {
            legs.add(new LegRequest(line.accountId(), line.amount()));
        }
        JournalEntry entry = ledgerService.postEntry(request.description(), legs);
        return new CreateEntryResponse(entry.getId(), entry.getDescription());
    }

}
