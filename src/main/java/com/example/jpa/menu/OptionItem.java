package com.example.jpa.menu;

import com.example.jpa.common.Money;
import com.example.jpa.common.Texts;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 하드 삭제. 지난 주문은 step2 에서 스냅샷(이름,가격 복사)으로 남기므로 이 행을 보존할 필요가 없다.
// 행이 지워져야 option_group 삭제 때 DB FK 가 "연결된 옵션 그룹"만 정확히 막아 준다
@Entity // 테이블명 option은 MYSQL 예약어라 option_item 으로 한다( 클래스명 그대로)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OptionItem { // 순한맛 / 치즈 / 사이즈

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_group_id", nullable = false, foreignKey = @ForeignKey(name = "fk_option_item_option_group"))
    private OptionGroup optionGroup;

    @Column(length = 50, nullable = false)
    private String name;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "extra_price", nullable = false))
    private Money extraPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OptionSaleStatus saleStatus;

    private int sortOrder;

    OptionItem(OptionGroup optionGroup, String name, Money extraPrice, int sortOrder) {
        if (extraPrice == null) {
            throw new IllegalArgumentException("추가 가격은 필수입니다. 무료면 Money.ZERO");
        }
        this.optionGroup = optionGroup;
        this.name = Texts.requireText(name, "옵션 이름은 필수입니다.");
        this.extraPrice = extraPrice;
        this.sortOrder = sortOrder;
        this.saleStatus = OptionSaleStatus.ON_SALE;
    }






}
