package com.example.jpa.store;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDate;

@Entity
@Getter
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_store_holiday", columnNames = {"store_id", "holiday_date"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreHoliday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(nullable = false)
    private LocalDate holidayDate;

    // 사유는 선택 입력. 공백만 들어오면 null 로 저장한다
    // 생성자의 trimToNull 과 짝이다: null 을 넣으므로 컬럼도 null 허용이어야 한다(nullable = false 를 붙이면 INSERT 때 DB 가 거부)
    @Column(length = 100)
    private String reason;

    StoreHoliday(Store store, LocalDate holidayDate, String reason) {
        this.store = store;
        this.holidayDate = holidayDate;
        this.reason = StringUtils.trimToNull(reason);
    }
}
