package com.example.jpa.store;

import com.example.jpa.store.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/menu-counts/java")
    public List<StoreMenuCountResponse> findMenuCountsInJava() {
        return storeService.findMenuCountsInJava();
    }

    // minMenuCount 를 안 주면 0 → 모든 가게
    @GetMapping("/menu-counts")
    public List<StoreMenuCountResponse> findMenuCounts(@RequestParam(defaultValue = "0") long minMenuCount) {
        return storeService.findMenuCounts(minMenuCount);
    }

    @GetMapping("/{storeId}/menu-groups/price-stats")
    public List<MenuGroupPriceResponse> findPriceStats(@PathVariable Long storeId) {
        return storeService.findPriceStats(storeId);
    }

    @GetMapping("/by-category/{categoryId}/join")
    public List<StoreSimpleResponse> findStoresInCategoryWithJoin(@PathVariable Long categoryId) {
        return storeService.findStoresInCategoryWithJoin(categoryId);
    }

    @GetMapping("/by-category/{categoryId}")
    public List<StoreSimpleResponse> findStoresInCategory(@PathVariable Long categoryId) {
        return storeService.findStoresInCategory(categoryId);
    }


}
