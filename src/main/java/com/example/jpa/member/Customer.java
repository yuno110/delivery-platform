package com.example.jpa.member;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Entity
@Getter
@DiscriminatorValue("CUSTOMER")
// 이 테이블의 PK컬럼명을 "member_id"로 지정한다
// 생략하면 이 테이블의 컬럼명도 id가 된다. customer.member_id가 보기에 더 좋아서 사용
// 자바에서는 customer.getId()로 사용
@PrimaryKeyJoinColumn(name = "member_id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer extends Member {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CustomerGrade grade;

    // mappedBy : 나는 주인이 아니다. 읽기만 한다.
    //   mappedBy가 붙은 @OneToMany는 양방향의 반대쪽일 뿐이라 문제없다.
    //   이 리스트는 "select ... from customer_address where customer_id = ?" 의 조회 결과를 담아 둔 것이다.
    //   그래서 list.add / list.remove 는 그 자체로 SQL을 만들지 않는다.
    //   DB에 반영되는 건 FK의 주인인 CustomerAddress의 customer 필드뿐이다 (값이 바뀌면 변경 감지 → UPDATE).
    //   양방향이면 트랜잭션 안에서 리스트도 맞아야 하므로 편의 메서드(addAddress)로 양쪽을 함께 세팅한다.
    //
    // cascade : EntityManager 메서드를 Customer에게 호출하면 리스트 안의 주소에게도 똑같이 호출한다.
    //   PERSIST : em.persist(customer) 때 + 플러시 때(JPA 스펙). 리스트에 새로 add한 주소는
    //             플러시 때 "처음 보는 객체"로 발견되어 persist → INSERT. (add 자체가 INSERT를 만드는 게 아니다)
    //   REMOVE  : em.remove(customer) 때만. List.remove()와는 이름만 같고 무관하다.
    //
    // orphanRemoval : 리스트에서 빠진 주소에 em.remove()를 대신 호출한다 → DELETE.
    //   cascade REMOVE = 고객을 지울 때 (회원 탈퇴)  /  orphanRemoval = 주소 하나만 지울 때 (고객은 유지)
    //   다른 곳으로 옮겨 갈 수 있는 객체(예: 다른 그룹으로 이동하는 메뉴)에는 붙이지 않는다.
    //
    // new ArrayList<>() 초기화 필수 : new로 만든 객체에는 Hibernate가 컬렉션을 넣어 주지 않는다 (안 하면 addAddress에서 NPE).
    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CustomerAddress> addresses = new ArrayList<>();

    private Customer(String email, String password, String name, String phone) {
        super(email, password, name, phone);
        this.grade = CustomerGrade.BRONZE;
    }

    public static Customer of(String email, String password, String name, String phone) {
        return new Customer(email, password, name, phone);
    }

    public void addAddress(CustomerAddress address) {
        if (address == null) {
            throw new IllegalArgumentException("주소는 필수입니다.");
        }
        addresses.add(address); // 컬렉션(메모리)
        address.assignTo(this); //FK주인(DB에 반영되는 쪽)

        if (addresses.size() == 1) {
            address.markAsDefault();
        }
    }

    public void removeAddress(CustomerAddress address) {
        boolean wasDefault = address.isDefaultAddress();
        boolean removed = addresses.remove(address);

        if (removed && wasDefault && !addresses.isEmpty()) {
            addresses.getFirst().markAsDefault();
        }
    }

    public void changeDefaultAddress(CustomerAddress target) {
        if (!addresses.contains(target)) {
            throw new IllegalArgumentException("이 고객의 주소가 아닙니다.");
        }
        addresses.forEach(CustomerAddress::unmarkAsDefault);
        target.markAsDefault();
    }

    public Optional<CustomerAddress> findDefaultAddress() {
        return addresses.stream()
                .filter(CustomerAddress::isDefaultAddress)
                .findFirst();
    }

    public List<CustomerAddress> getAddresses() {
        // Lombok @Getter가 만드는 getter는 원본 리스트를 그대로 반환한다.
        // 그러면 customer.getAddresses().add(address)를 통해 원본 리스트를 변경할 수 있다.
        return Collections.unmodifiableList(addresses);
    }

    public void changeGrade(CustomerGrade grade) {
        if (grade == null) {
            throw new IllegalArgumentException("등급은 필수입니다.");
        }
        this.grade = grade;
    }

}
