package com.example.jpa.store.dto;

import com.example.jpa.menu.MenuGroup;
import com.example.jpa.store.Store;
import com.example.jpa.store.StoreOperationStatus;

import java.util.List;

public record StoreSummaryResponse (
        Long id,
        String name,
        StoreOperationStatus operationStatus,
        String ownerName,
        List<String> menuGroupNames
) {
    // Entity -> DTO 변환. 여기서 getter 를 부르는 순간 지연 로딩이 일어난다
    public static StoreSummaryResponse from(Store store) {
        return new StoreSummaryResponse(
                store.getId(),
                store.getName(),
                store.getOperationStatus(),
                store.getOwner().getName(), // @ManyToOne LAZY -> OWNER SELECT
                store.getMenuGroups().stream().map(MenuGroup::getName).toList() // @OneToMany -> 메뉴 그룹 SELECT
        );
    }
}
