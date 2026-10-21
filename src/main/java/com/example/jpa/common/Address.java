package com.example.jpa.common;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED) // Hibernate가 DB에서 읽어 객체를 만들 때 기본 생성자를 리플렉션으로 호출해야한다(JPA 스펙 요구사항)
@Getter
@Embeddable // 참고 : 임베디드가 전부 null이면 객체가 아니라 null
public class Address {

    @Column(length = 10, nullable = false)
    private String zipCode;

    @Column(length = 200, nullable = false)
    private String street;

    @Column(length = 100)
    private String detail;

    private Address(String zipCode, String street, String detail) {
        this.zipCode = zipCode;
        this.street = street;
        this.detail = detail;
    }

    public static Address of(String zipCode, String street, String detail) {
        return new Address(
                requireText(zipCode, "우편번호는 필수입니다."),
                requireText(street, "도로명은 필수입니다."),
                StringUtils.trimToNull(detail)
        );
    }

    private static String requireText(String value, String message) {
        String trimmed = StringUtils.trimToNull(value);
        if(trimmed == null) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

}
