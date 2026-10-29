package com.example.jpa.member;

import com.example.jpa.common.Texts;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "store_owner")
@DiscriminatorValue("OWNER")
@PrimaryKeyJoinColumn(name = "member_id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Owner extends Member {

    @Column(length = 12, nullable = false, unique = true)
    private String businessNumber;

    // Store 컬렉션을 두지 않는다. 점주의 가게 목록은 쿼리로 가져온다.

    private Owner(String email, String password, String name, String phone, String businessNumber) {
        super(email, password, name, phone);
        this.businessNumber = Texts.requireText(businessNumber, "사업자번호는 필수입니다.");
    }

    public static Owner of(String email, String password, String name, String phone, String businessNumber) {
        return new Owner(email, password, name, phone, businessNumber);
    }

    public void changeBusinessNumber(String businessNumber) {
        this.businessNumber = Texts.requireText(businessNumber, "사업자번호는 필수입니다.");
    }
}
