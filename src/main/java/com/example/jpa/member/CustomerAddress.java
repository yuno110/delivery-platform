package com.example.jpa.member;

import com.example.jpa.common.Address;
import com.example.jpa.common.BaseTimeEntity;
import com.example.jpa.common.Texts;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerAddress extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // optional = false : 이 연관관계는 반드시 있다(null 불가)라고 JPA에게 알려주는 옵션
    // 조회할 때 left outer join 대신 inner join을 사용한다(null일 일이 없으니)
    // Hibernate는 이것만 보고도 DDL에 not null을 붙인다. nullable = false와 중복이지만 의도를 분명히 하기 위해 둘다 썼다
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(length = 30, nullable = false)
    private String alias;

    @Embedded
    private Address address;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;

    private CustomerAddress(String alias, Address address, BigDecimal latitude, BigDecimal longitude) {
        this.alias = Texts.requireText(alias, "주소 별칭은 필수입니다.");
        if (address == null) {
            throw new IllegalArgumentException("주소는 필수입니다.");
        }
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.defaultAddress = false;
    }

    public static CustomerAddress of(String alias, Address address) {
        return new CustomerAddress(alias, address, null, null);
    }

    public static CustomerAddress of(String alias, Address address, BigDecimal latitude, BigDecimal longitude) {
        return new CustomerAddress(alias, address, latitude, longitude);
    }

    // 접근 수식어 없음(package-private) = 같은 패키지의 Customer 만 호출 가능
    void assignTo(Customer customer) {
        this.customer = customer;
    }

    void markAsDefault() {
        this.defaultAddress = true;
    }

    void unmarkAsDefault() {
        this.defaultAddress = false;
    }

    public void changeAddress(Address address) {
        if (address == null) {
            throw new IllegalArgumentException("주소는 필수입니다.");
        }
        this.address = address;
    }



}
