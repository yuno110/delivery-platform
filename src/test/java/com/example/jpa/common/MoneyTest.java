package com.example.jpa.common;


import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


class MoneyTest {

    // ===== 생성 =====

    @Test
    void 금액으로_생성한다() {
        // given & when
        Money money = Money.of(1000);

        // then
        assertThat(money.getAmount()).isEqualTo(1000L);
    }

    @Test
    void 영원도_만들_수_있다() {
        Money money = Money.of(0);
        assertThat(money.getAmount()).isZero();
    }

    @Test
    void 음수_금액은_만들_수_없다() {
        assertThatThrownBy(()-> Money.of(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("음수");
    }

    // ===== 연산 =====

    @Test
    void 금액을_더한다() {
        Money a = Money.of(1000);
        Money b = Money.of(500);
        Money result = a.plus(b);
        assertThat(result).isEqualTo(Money.of(1500));
    }

    @Test
    void 금액을_뺀다() {
        Money a = Money.of(1000);
        Money b = Money.of(300);

        Money result = a.minus(b);

        assertThat(result).isEqualTo(Money.of(700));
    }

    @Test
    void 뺀_결과가_음수면_예외가_난다() {
        Money total = Money.of(10000);
        Money discount = Money.of(15000);

        assertThatThrownBy(() -> total.minus(discount))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 금액을_곱한다() {
        Money price = Money.of(3000);

        assertThat(price.times(4)).isEqualTo(Money.of(12000));
        assertThat(price.times(0)).isEqualTo(Money.ZERO);
    }

    // ===== 불변셩 : 연산은 새 객체를 만들고 원본을 바꾸지 않는다 =====

    @Test
    void 더하기는_원본을_바꾸지_않는다() {
        Money original = Money.of(1000);
        Money result = original.plus(Money.of(500)); // 반환값을 일부러 받지 않는다
        assertThat(original).isEqualTo(Money.of(1000));
    }

    @Test
    void 연산_결과는_원본과_다른_객체다() {
        Money original = Money.of(1000);
        Money result = original.plus(Money.ZERO); // 0원을 더해 값은 같다
        assertThat(result).isEqualTo(original); // 값은 같지만
        assertThat(result).isNotSameAs(original); // 다른 객체다
    }

    // ===== 비교 =====

    @Test
    void 크다를_판단한다() {
        Money big = Money.of(2000);
        Money small = Money.of(1000);

        assertThat(big.isGreaterThan(small)).isTrue();
        assertThat(small.isGreaterThan(big)).isFalse();
        assertThat(big.isGreaterThan(Money.of(2000))).isFalse();   // 같으면 false
    }

    @Test
    void 작다를_판단한다() {
        Money big = Money.of(2000);
        Money small = Money.of(1000);

        assertThat(small.isLessThan(big)).isTrue();
        assertThat(big.isLessThan(small)).isFalse();
        assertThat(small.isLessThan(Money.of(1000))).isFalse();    // 같으면 false
    }

    @Test
    void 크거나_같다는_같을_때도_참이다() {
        Money money = Money.of(1000);

        assertThat(money.isGreaterThanOrEqual(Money.of(1000))).isTrue();
        assertThat(money.isGreaterThanOrEqual(Money.of(999))).isTrue();
        assertThat(money.isGreaterThanOrEqual(Money.of(1001))).isFalse();
    }

    @Test
    void 작거나_같다는_같을_때도_참이다() {
        Money money = Money.of(1000);

        assertThat(money.isLessThanOrEqual(Money.of(1000))).isTrue();
        assertThat(money.isLessThanOrEqual(Money.of(1001))).isTrue();
        assertThat(money.isLessThanOrEqual(Money.of(999))).isFalse();
    }

    @Test
    void compareTo_는_부호로_순서를_알려준다() {
        Money money = Money.of(1000);
        assertThat(money.compareTo(Money.of(500))).isPositive(); // 내가 크다
        assertThat(money.compareTo(Money.of(1000))).isZero(); // 같다
        assertThat(money.compareTo(Money.of(2000))).isNegative(); // 내가 작다
    }

    // ===== 큰 금액: compareTo 를 뺄셈으로 구현하면 여기서 실패한다 =====

    @Test
    void int_범위를_넘는_금액도_정확히_비교한다() {
        Money fiveBillion = Money.of(5_000_000_000L);   // 50억
        Money oneBillion = Money.of(1_000_000_000L);    // 10억

        assertThat(fiveBillion.isGreaterThan(oneBillion)).isTrue();
        assertThat(oneBillion.isLessThan(fiveBillion)).isTrue();
    }

    // ===== 값 타입: 값이 같으면 동등하다 =====

    @Test
    void 금액이_같으면_동등하다() {
        Money a = Money.of(1000);
        Money b = Money.of(1000);

        assertThat(a).isEqualTo(b);       // equals → 값이 같으니 동등
        assertThat(a).isNotSameAs(b);     // ==     → 그래도 다른 객체
    }

    @Test
    void 금액이_다르면_동등하지_않다() {
        assertThat(Money.of(1000)).isNotEqualTo(Money.of(2000));
    }

    @Test
    void 동등하면_해시코드도_같다() {
        Money a = Money.of(1000);
        Money b = Money.of(1000);

        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    // ===== ZERO 상수 =====

    @Test
    void ZERO_는_０원이다() {
        assertThat(Money.ZERO.isZero()).isTrue();
        assertThat(Money.of(1000).isZero()).isFalse();
    }

    @Test
    void ZERO_는_항상_같은_인스턴스다() {
        assertThat(Money.ZERO).isSameAs(Money.ZERO);
    }

    @Test
    void ZERO_와_of０은_값은_같지만_다른_객체다() {
        assertThat(Money.ZERO).isEqualTo(Money.of(0));
        assertThat(Money.ZERO).isNotSameAs(Money.of(0));
    }

    // ===== toString =====

    @Test
    void 천_단위_콤마와_원_단위를_붙여_출력한다() {
        assertThat(Money.of(9000)).hasToString("9,000원");
        assertThat(Money.of(1_234_567)).hasToString("1,234,567원");
        assertThat(Money.ZERO).hasToString("0원");
    }

    // ===== Comparable 구현 덕에 표준 API 가 Money 를 받아 준다 =====

    // 세 리스트 비교
    // List.of(a, b, c)                    // 완전 불변. add/remove/set 전부 ✗
    // Arrays.asList(a, b, c)              // 고정 크기 + 배열 뷰. set ✅, add/remove ✗
    // new ArrayList<>(List.of(a, b, c))   // 완전 가변 + 독립 복사본. 전부 ✅
    @Test
    void 금액_순으로_정렬된다() {
        // Arrays.asList가 반환하는것은 Arrays 안에 있는 전용 중첩 클래스이다. 즉 java.util.ArrayList가 아니다.
        // 반환값을 ArrayList로 캐스팅하면 ClassCastException이 발생한다. 항상 List<...>로 받아라.
        List<Money> moneys =  Arrays.asList(Money.of(3000), Money.of(1000), Money.of(2000));


        Collections.sort(moneys);

        assertThat(moneys).containsExactly(
                Money.of(1000), Money.of(2000), Money.of(3000));
    }




}
