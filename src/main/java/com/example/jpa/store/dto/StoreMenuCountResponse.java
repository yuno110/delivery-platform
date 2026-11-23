package com.example.jpa.store.dto;

// 엔티티가 아니라 "계산 결과"를 담는 그릇. JPQL select new 로 직접 생성된다
public record StoreMenuCountResponse(Long storeId, String storeName, long menuCount) {
}
