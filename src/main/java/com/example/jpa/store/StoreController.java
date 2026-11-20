package com.example.jpa.store;

import com.example.jpa.store.dto.StoreDetailResponse;
import com.example.jpa.store.dto.StoreMenuResponse;
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

    @GetMapping("/{storeId}/menus/two-bags")
    public StoreMenuResponse findMenusWithTwoBags(@PathVariable Long storeId) {
        return storeService.findMenusWithTwoBags(storeId);
    }

    @GetMapping("/{storeId}/menus/split")
    public StoreMenuResponse findMenusWithSplitQuery(@PathVariable Long storeId) {
        return storeService.findMenusWithSplitQuery(storeId);
    }

    @GetMapping("/{storeId}/menus")
    public StoreMenuResponse findMenus(@PathVariable Long storeId) {
        return storeService.findMenus(storeId);
    }

    @GetMapping("/{storeId}/detail")
    public StoreDetailResponse findStoreDetail(@PathVariable Long storeId) {
        return storeService.findStoreDetail(storeId);
    }

    @GetMapping("/{storeId}/detail/split")
    public StoreDetailResponse findStoreDetailWithSplitQuery(@PathVariable Long storeId) {
        return storeService.findStoreDetailWithSplitQuery(storeId);
    }


}
