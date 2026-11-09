package com.example.jpa.store;

import com.example.jpa.common.Address;
import com.example.jpa.common.BaseTimeEntity;
import com.example.jpa.common.Money;
import com.example.jpa.common.Texts;
import com.example.jpa.member.Owner;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Getter
@Table(indexes = @Index(name = "idx_store_name", columnList = "name")) // 가게 이름 검색용 인덱스 columnList에는 컬럼 이름을 사용(필드 이름X)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Store extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 기본 이름은 owner_member_id 가 된다(Owner 의 PK컬럼이 member_id 라서) -> owner_id 로 지정
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private Owner owner;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(length = 20)
    private String phone;

    @Column(length = 500)
    private String description;

    @Embedded
    private Address address;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    // Money가 둘이라 둘 다 amount 컬럼이 되려 한다 -> 컬럼 이름을 각각 바꿔준다
    // @AttributeOverride 는 Money.amount 의 @Column(nullable = false) 를 통째로 대체한다.
    //   name 만 바뀌는 게 아니라 nullable 도 기본값(true)으로 돌아가므로 여기서 다시 적는다
    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "min_order_amount", nullable = false))
    private Money minOrderAmount;

    @Embedded // 위와 같은 이유로 nullable = false 를 다시 적는다
    @AttributeOverride(name = "amount", column = @Column(name = "delivery_fee", nullable = false))
    private Money deliveryFee;

    private int deliveryTimeMin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StoreOperationStatus operationStatus;

    // mappedBy = "store" -> "FK 관리는 StoreBusinessHour의 store 필드가 한다. 이 컬렉션은 그걸 비춰 보는 읽기 전용이다."
    // cascade 는 "내가 당한 일을 상대에게도 전파한다" 라는 뜻이다. 이 전파는 소유자에서 소유물로 흘러야 말이된다
    // orphanRemoval = true 는 "소유자에서 소유물이 없어지면 소유물도 삭제한다" 라는 뜻이다. ManyToOne 쪽에는 그런 목록이 없어서 이 옵션 자체가 없다.
    // 둘 다 가게에 완전히 딸린 것 → Customer.addresses 와 같은 설정
    @OneToMany(mappedBy = "store", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StoreCategory> categories = new ArrayList<>();

    @OneToMany(mappedBy = "store", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StoreBusinessHour> businessHours = new ArrayList<>();

    @OneToMany(mappedBy = "store", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StoreHoliday> holidays = new ArrayList<>();

    private Store(
            Owner owner, String name, Address address,
            Money minOrderAmount, Money deliveryFee, int deliveryTimeMin
    ) {
        if (owner == null) {
            throw new IllegalArgumentException("점주는 필수입니다.");
        }
        if (address == null) {
            throw new IllegalArgumentException("주소는 필수입니다.");
        }
        if (minOrderAmount == null || deliveryFee == null) {
            throw new IllegalArgumentException("최소주문금액과 배달비는 필수입니다");
        }

        this.owner = owner;
        this.name = Texts.requireText(name, "가게 이름은 필수입니다.");
        this.address = address;
        this.minOrderAmount = minOrderAmount;
        this.deliveryTimeMin = deliveryTimeMin;
        this.deliveryFee = deliveryFee;
        this.operationStatus = StoreOperationStatus.OPERATING;
    }

    public static Store of(Owner owner, String name, Address address, Money minOrderAmount, Money deliveryFee, int deliveryTimeMin) {
        return new Store(owner, name, address, minOrderAmount, deliveryFee, deliveryTimeMin);
    }

    /**
     * 카테고리
     */
    public void addCategory(Category category, boolean primary) {
        if(category == null) {
            throw new IllegalArgumentException("카테고리는 필수입니다.");
        }
        // [검사 1] 저장된 카테고리인가? (id == null 이면 persist 안 된 카테고리)
        // DB 의 store_category.category_id 에는 카테고리의 id 를 넣어야 하는데, 저장 안 된 카테고리는 id 가 없다.
        // 카테고리는 여러 가게가 같이 쓰는 데이터라 StoreCategory에서 cascade 로 대신 저장해 주지 않는다.
        // 검사가 없으면 나중에 em.persist(store) 때 TransientPropertyValueException 이 나서 원인을 찾기 어렵다 → 여기서 바로 막는다.
        // 아래 중복 검사에서 null.equals() 로 NPE 가 나는 것도 막는다.
        if (category.getId() == null) {
            throw new IllegalArgumentException("저장된 카테고리만 연결할 수 있습니다.");
        }
        // [검사 2] 이미 연결된 카테고리인가?
        // == 대신 id 비교: 같은 영속성 컨텍스트 안이면 == 도 동작한다(동일성 보장).
        // 하지만 다른 트랜잭션에서 가져온(준영속) 카테고리가 넘어오면 같은 행이어도 다른 객체라 == 가 false 다.
        // id 는 어디서 온 객체든 같다. getId() 는 LAZY 프록시에서도 SELECT 없이 바로 나온다.
        boolean alreadyAdded = categories.stream()
                .anyMatch(sc -> sc.getCategory().getId().equals(category.getId()));
        if (alreadyAdded) {
            throw new IllegalArgumentException("이미 연결된 카테고리입니다.");
        }
        // 대표 카테고리는 하나만
        if (primary) {
            categories.forEach(StoreCategory::unmarkPrimary);
        }
        categories.add(new StoreCategory(this, category, primary));
    }

    public List<StoreCategory> getCategories() {
        return Collections.unmodifiableList(categories);
    }

    /**
     * 영업 시간
     */
    public void addBusinessHour(DayOfWeek dayOfWeek, LocalTime openTime, LocalTime closeTime) {
        if(dayOfWeek == null || openTime == null || closeTime == null) {
            throw new IllegalArgumentException("영업시간은 필수입니다.");
        }
        businessHours.add(new StoreBusinessHour(this, dayOfWeek, openTime, closeTime));
    }

    // 영업시간 전체 교체용. 리스트에서 빠진 영업시간은 orphanRemoval 이 DELETE 한다.
    // flush 때 SQL 순서는 INSERT → UPDATE → DELETE 로 고정이라, clear() 후 add 해도 새것 INSERT 가 먼저 나간다
    //   (자세한 내용은 StoreJpaTest 의 영업시간 교체 테스트 주석)
    public void clearBusinessHours() {
        businessHours.clear();
    }

    public List<StoreBusinessHour> getBusinessHours() {
        return Collections.unmodifiableList(businessHours);
    }

    /**
     * 휴무일
     */
    public void addHoliday(LocalDate date, String reason) {
        if (date == null) {
            throw new IllegalArgumentException("휴무일은 필수입니다.");
        }
        boolean alreadyAdded = holidays.stream().anyMatch(h -> h.getHolidayDate().equals(date));
        if (alreadyAdded) {
            throw new IllegalArgumentException("이미 등록된 휴무일입니다.");
        }
        holidays.add(new StoreHoliday(this, date, reason));
    }

    public void removeHoliday(LocalDate date) {
        holidays.removeIf(h -> h.getHolidayDate().equals(date));
    }

    public List<StoreHoliday> getHolidays() {
        return Collections.unmodifiableList(holidays);
    }



    public void changeAddress(Address address) {
        if(address == null) {
            throw new IllegalArgumentException("주소는 필수입니다.");
        }
        this.address = address;
    }



}
