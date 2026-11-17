package com.zapas.web;

import com.zapas.service.ShoppingListService;
import com.zapas.service.StateService;
import com.zapas.service.StockService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Контракт совпадает с api.js. Каждый изменяющий запрос возвращает актуальное состояние целиком,
 * так что фронту достаточно сделать S = ответ и перерисоваться.
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final StateService state;
    private final StockService stock;
    private final ShoppingListService list;

    public ApiController(StateService state, StockService stock, ShoppingListService list) {
        this.state = state;
        this.stock = stock;
        this.list = list;
    }

    @GetMapping("/state")
    public Dto.State getState() {
        return state.snapshot();
    }

    // ---- запасы ----

    @PostMapping("/items")
    public Dto.State addItem(@Valid @RequestBody Requests.ItemCreate body) {
        stock.add(body.name(), body.total(), body.thr());
        return state.snapshot();
    }

    @PatchMapping("/items/{id}")
    public Dto.State patchItem(@PathVariable UUID id, @Valid @RequestBody Requests.ItemPatch body) {
        stock.patch(id, body.name(), body.total(), body.thr());
        return state.snapshot();
    }

    @DeleteMapping("/items/{id}")
    public Dto.State deleteItem(@PathVariable UUID id) {
        stock.delete(id);
        return state.snapshot();
    }

    @PostMapping("/items/{id}/bought")
    public Dto.State bought(@PathVariable UUID id) {
        stock.bought(id);
        return state.snapshot();
    }

    @PostMapping("/items/{id}/sudden")
    public Dto.State sudden(@PathVariable UUID id) {
        stock.sudden(id);
        return state.snapshot();
    }

    // ---- список покупок ----

    @PostMapping("/list")
    public Dto.State addListItem(@Valid @RequestBody Requests.ListCreate body) {
        list.add(body.name(), body.details());
        return state.snapshot();
    }

    @PatchMapping("/list/{id}")
    public Dto.State patchListItem(@PathVariable UUID id, @Valid @RequestBody Requests.ListPatch body) {
        list.patch(id, body.name(), body.done(), body.details());
        return state.snapshot();
    }

    @DeleteMapping("/list/{id}")
    public Dto.State deleteListItem(@PathVariable UUID id) {
        list.delete(id);
        return state.snapshot();
    }

    @PostMapping("/list/clear")
    public Dto.State clearList() {
        list.clearPurchased();
        return state.snapshot();
    }

    // ---- служебное ----

    @PostMapping("/log")
    public ResponseEntity<Void> log(@Valid @RequestBody Requests.LogEvent body) {
        state.logClientEvent(body.type(), body.itemId(), body.properties());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/time")
    public Dto.State setOffset(@Valid @RequestBody Requests.TimeShift body) {
        state.setOffset(body.offset());
        return state.snapshot();
    }

    @PostMapping("/reset")
    public Dto.State reset() {
        state.reset();
        return state.snapshot();
    }
}
