package com.example.jpa.menu;

public enum MenuSaleStatus {
    ON_SALE, // 판매중
    SOLD_OUT, // 품절 (메뉴판에는 보이지만 주문 불가)
    HIDDEN // 숨김 (메뉴판에서 안 보임)
}
