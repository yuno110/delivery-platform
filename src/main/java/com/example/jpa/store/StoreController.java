package com.example.jpa.store;

import com.example.jpa.store.dto.StoreSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stores")
public class StoreController {

    private final StoreService storeService;

    @GetMapping
    public List<StoreSummaryResponse> findStores() {
        return storeService.findStores();
    }

    @GetMapping("/fetch-join")
    public List<StoreSummaryResponse> findStoresWithFetchJoin() {
        return storeService.findStoresWithFetchJoin();
    }

    @GetMapping("/entity-graph")
    public List<StoreSummaryResponse> findStoresWithEntityGraph() {
        return storeService.findStoresWithEntityGraph();
    }



}
