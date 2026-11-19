package com.example.jpa.query;

import com.example.jpa.config.JpaAuditingConfig;
import com.example.jpa.config.QuerydslConfig;
import com.example.jpa.store.Store;
import com.example.jpa.store.StoreRepository;
import com.example.jpa.store.dto.StoreSummaryResponse;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Step 5-1: 가게 목록(점주 이름 + 메뉴그룹 이름) 조회의 SQL 개수
@Tag("seed")
@Import({JpaAuditingConfig.class, QuerydslConfig.class})
@DataJpaTest(showSql = false)   // SQL 수백 줄이 찍히지 않게. 개수는 Statistics 로 본다
public class StoreNPlusOneTest {

    @Autowired
    EntityManager em;

    @Autowired
    StoreRepository storeRepository;

    Statistics statistics;

    @BeforeEach
    void setUp() {
        statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
    }

    // StoreService 가 하는 일과 같다: 조회 → DTO 변환 (변환 중에 지연 로딩)
    private List<StoreSummaryResponse> toResponses(List<Store> stores) {
        return stores.stream().map(StoreSummaryResponse::from).toList();
    }

    @Test
    void 배치_사이즈가_N플러스1을_줄인다() {
        List<StoreSummaryResponse> responses = toResponses(storeRepository.findAll());

        assertThat(responses).hasSize(300);
        // batch size 100 : 가게 목록 1 + 점주 3(285 / 100) + 메뉴 그룹 3(300/100)
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1 + 3 + 3);
    }

    @Test
    void 페치_조인이면_쿼리_1번() {
        List<StoreSummaryResponse> responses = toResponses(storeRepository.findAllWithFetchJoin());

        // DB 는 1,049행(가게 × 메뉴그룹)을 주지만 Hibernate 6 가 가게 기준으로 중복을 없앤다
        assertThat(responses).hasSize(300);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void 엔티티_그래프도_쿼리_1번() {
        List<StoreSummaryResponse> responses = toResponses(storeRepository.findAllWithEntityGraph());

        assertThat(responses).hasSize(300);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void 세_방법의_결과는_같다() {
        List<StoreSummaryResponse> nPlusOne = toResponses(storeRepository.findAll());
        em.clear();   // 앞에서 로딩한 엔티티를 비워야 다음 방법이 자기 쿼리로 다시 가져온다
        List<StoreSummaryResponse> fetchJoin = toResponses(storeRepository.findAllWithFetchJoin());
        em.clear();
        List<StoreSummaryResponse> entityGraph = toResponses(storeRepository.findAllWithEntityGraph());

        // record 는 equals 가 필드 값 비교라 순서·내용이 모두 같아야 통과한다
        assertThat(fetchJoin).isEqualTo(nPlusOne);
        assertThat(entityGraph).isEqualTo(nPlusOne);
    }

}
