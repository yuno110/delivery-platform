package com.example.jpa.common;

import com.querydsl.core.annotations.QueryTransient;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Embeddable
public class Money implements Comparable<Money> {

    public static final Money ZERO = Money.of(0); // 불변객체라 공유해도 안전하다

    // 기본형 자동 not null 은 "엔티티에 직접 둔 필드"에만 붙는다. 임베디드 안에서는 안 붙는다.
    //   (엔티티의 Money 필드 자체가 null 일 수 있으니, Hibernate 가 안쪽 컬럼을 nullable 로 만든다)
    // 그래서 Address.zipCode 처럼 nullable = false 를 직접 쓴다 → amount bigint not null
    // 주의: 엔티티에서 @AttributeOverride 로 컬럼을 바꾸면 이 @Column 은 통째로 대체된다 (Store 참고)
    @Column(nullable = false)
    private long amount;

    private Money(long amount) {
        this.amount = amount;
    }

    public static Money of(long amount) {
        if(amount < 0) {
            throw new IllegalArgumentException("금액은 음수일 수 없습니다: " + amount);
        }
        return new Money(amount);
    }

    public Money plus(Money other) {
        return Money.of(this.amount + other.amount);
    }

    public Money minus(Money other) {
        return Money.of(this.amount - other.amount);
    }

    public Money times(int multiplier) {
        return Money.of(this.amount * multiplier);
    }

    // 비교는 전부 compareTo 에 위임한다. 비교 로직이 한 곳에만 있어야
    // 나중에 규칙이 바뀌어도(통화 비교 등) 고칠 자리가 하나다.
    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    public boolean isLessThan(Money other) {
        return compareTo(other) < 0;
    }

    public boolean isGreaterThanOrEqual(Money other) {
        return compareTo(other) >= 0;
    }

    public boolean isLessThanOrEqual(Money other) {
        return compareTo(other) <= 0;
    }

    @QueryTransient
    public boolean isZero() {
        return this.amount == 0;
    }

    // compareTo 에서는 뺄셈을 쓰지 말고 항상 XXX.compare 를 쓴다 (자바 기본 규칙).
    //   (int)(this.amount - other.amount) 로 구현하면 차이가 int 범위(약 21억)를 넘을 때
    //   비트가 잘려 부호가 뒤집힌다. 50억 - 10억 = 40억 → (int) 캐스팅 시 음수
    //   → "50억이 10억보다 작다"고 조용히 틀린다. 정산 합계는 쉽게 int 를 넘는다.
    // Long.compare 는 뺄셈 없이 -1/0/1 만 반환하므로 오버플로가 불가능하다.
    @Override
    public int compareTo(Money other) {
        return Long.compare(this.amount, other.amount);
    }

    // %,d = 천 단위 콤마. 테스트 실패 메시지가 Money@1b6d3586 대신 "1,500원" 으로 찍힌다
    @Override
    public String toString() {
        return String.format("%,d원", this.amount);
    }


}
