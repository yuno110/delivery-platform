package com.example.jpa.member;

import com.example.jpa.config.JpaAuditingConfig;
import com.example.jpa.config.QuerydslConfig;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static com.example.jpa.member.QCustomer.customer;
import static com.example.jpa.member.QMember.member;
import static org.assertj.core.api.Assertions.assertThat;

@Import({QuerydslConfig.class, JpaAuditingConfig.class})
@DataJpaTest(showSql = false)
public class MemberQuerydslTest {

    @Autowired
    EntityManager em;

    @Autowired
    JPAQueryFactory queryFactory;

    @BeforeEach
    void setUp() {
        em.persist(Customer.of("hong@test.com", "1234", "홍길동", "010-2332-3434"));
        // 고객도 아닌 회원도 있어야 instanceOf가 걸러내는지 볼 수 있다
        em.persist(Owner.of("kim@test.com", "1234", "김사장", "010-232-3434", "123-43-5667"));
        em.flush();
        em.clear();
    }

    @Test
    void 부모_Q타입에서_instanceOf로_거른다() {
        System.out.println("===== member.isntanceOf 전 =====");
        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.instanceOf(Customer.class))
                .fetch();
        System.out.println("===== member.isntanceOf 후 =====");
        assertThat(result).hasSize(1); // 점주는 걸러졌다
        assertThat(result.getFirst()).isInstanceOf(Customer.class);
    }

    @Test
    void 자식_Q타입으로_바로_조회한다() {
        System.out.println("===== selectFrom(customer) 전 =====");
        List<Customer> result = queryFactory
                .selectFrom(customer)
                .fetch();
        System.out.println("===== selectFrom(customer) 후 =====");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getEmail()).isEqualTo("hong@test.com");
    }







}
