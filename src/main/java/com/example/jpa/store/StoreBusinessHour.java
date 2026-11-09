package com.example.jpa.store;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreBusinessHour {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    // java.time.DayOfWeek 도 enum 이라 우리 enum과 똑같이 STRING으로 저장한다
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DayOfWeek dayOfWeek;

    @Column(nullable = false)
    private LocalTime openTime;

    // close < open 이면 다음날 마감(새벽 영업). 영업중 판정 쿼리에서 처리한다
    @Column(nullable = false)
    private LocalTime closeTime;

    StoreBusinessHour(Store store, DayOfWeek dayOfWeek, LocalTime openTime, LocalTime closeTime) {
        this.store = store;
        this.dayOfWeek = dayOfWeek;
        this.openTime = openTime;
        this.closeTime = closeTime;
    }
}
