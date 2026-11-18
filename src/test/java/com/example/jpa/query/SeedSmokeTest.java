package com.example.jpa.query;

import com.example.jpa.config.JpaAuditingConfig;
import com.example.jpa.config.QuerydslConfig;
import com.example.jpa.store.Store;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

// seedTest 에서만 실행된다 (기본 test 는 이 태그를 제외)
@Tag("seed")
@Import({JpaAuditingConfig.class, QuerydslConfig.class})
@DataJpaTest   // 테스트마다 롤백 → 시드는 바뀌지 않는다
class SeedSmokeTest {

    @Autowired
    EntityManager em;

    Statistics statistics;

    @BeforeEach
    void setUp() {
        // Statistics 는 SessionFactory(앱 전체)에 하나 → 테스트마다 0 으로 되돌린다
        statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
    }

    @Test
    void 시드가_있는_jpa_스키마에_붙는다() {
        Long stores = em.createQuery("select count(s) from Store s", Long.class).getSingleResult();
        assertThat(stores).isEqualTo(300);
    }

    @Test
    void 실행된_SQL_개수를_셀_수_있다() {
        // id 는 1 부터라는 보장이 없다 → 실제 값을 조회해서 쓴다
        Long storeId = em.createQuery("select min(s.id) from Store s", Long.class).getSingleResult();
        statistics.clear();   // 위 조회는 세지 않는다

        Store store = em.find(Store.class, storeId);
        store.getOwner().getId();          // 프록시의 id → SQL 없음
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);

        store.getOwner().getName();        // 프록시 초기화 → SELECT 1번 더
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }
}
