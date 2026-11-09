package com.example.jpa.store;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
// 한 가게에 같은 카테고리를 두 번 연결하지 못하게 DB가 막는다(체크포인트5에서 확인)
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_store_category", columnNames = {"store_id", "category_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    // 단방향 : Category는 자기를 쓰는 가게 목록을 모른다(필요하면 쿼리로)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    // 기본 이름은 primary인데 MySQL 예약어라 DDL이 깨진다. -> is_primary
    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    StoreCategory(Store store, Category category, boolean primary) {
        this.store = store;
        this.category = category;
        this.primary = primary;
    }

    void unmarkPrimary() {
        this.primary = false;
    }
}
