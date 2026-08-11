package dev.jonathan.ledgercore.controller;

import dev.jonathan.ledgercore.service.LedgerService;
import dev.jonathan.ledgercore.service.LegRequest;
import dev.jonathan.ledgercore.service.PostResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<CreateEntryResponse> create(@Valid @RequestBody CreateEntryRequest request,
                                                      @RequestHeader("Idempotency-Key") String idempotencyKey) {
        List<LegRequest> legs = new ArrayList<>();
        for (CreateEntryRequest.LegLine line : request.legs()) {
            legs.add(new LegRequest(line.accountId(), line.amount()));
        }
        PostResult result = ledgerService.postEntryIdempotent(idempotencyKey, request.description(), legs);
        CreateEntryResponse body = new CreateEntryResponse(result.entry().getId(), result.entry().getDescription());

        if (result.created()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } else {
            return ResponseEntity.ok(body);
        }
    }

}
