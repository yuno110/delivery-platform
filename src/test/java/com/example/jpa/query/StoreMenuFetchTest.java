package com.example.jpa.query;

import com.example.jpa.config.JpaAuditingConfig;
import com.example.jpa.config.QuerydslConfig;
import com.example.jpa.menu.MenuGroupRepository;
import com.example.jpa.store.Store;
import com.example.jpa.store.StoreRepository;
import com.example.jpa.store.dto.StoreMenuResponse;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.loader.MultipleBagFetchException;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Step 5-2: 메뉴판(가게 → 메뉴그룹 → 메뉴) 조회. List(bag) 컬렉션 두 단계
@Tag("seed")
@Import({JpaAuditingConfig.class, QuerydslConfig.class})
@DataJpaTest(showSql = false)
class StoreMenuFetchTest {

    @Autowired
    EntityManager em;

    @Autowired
    StoreRepository storeRepository;

    @Autowired
    MenuGroupRepository menuGroupRepository;

    Statistics statistics;

    Long storeId;

    @BeforeEach
    void setUp() {
        // 시드의 첫 가게 (id 는 하드코딩하지 않는다)
        storeId = em.createQuery("select min(s.id) from Store s", Long.class).getSingleResult();
        statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();   // 위 조회는 세지 않는다
    }

    @Test
    void List_컬렉션_두개를_동시에_페치_조인하면_예외() {
        // Spring 이 InvalidDataAccessApiUsageException 으로 감싸서 던진다 → 원인(root cause)으로 확인
        assertThatThrownBy(() -> storeRepository.findWithMenuGroupsAndMenus(storeId))
                .hasRootCauseInstanceOf(MultipleBagFetchException.class);
        // SQL 로 바꾸는 단계에서 막혀서 DB 에는 아무것도 안 나간다
        assertThat(statistics.getPrepareStatementCount()).isZero();
    }

    @Test
    void 쿼리를_나누면_2번() {
        Store store = storeRepository.findWithMenuGroups(storeId).orElseThrow();   // 가게 + 메뉴그룹
        menuGroupRepository.findWithMenusByStoreId(storeId);                       // 메뉴그룹 + 메뉴 (컨텍스트의 그룹을 채움)
        StoreMenuResponse response = StoreMenuResponse.from(store);

        assertThat(response.menuGroups()).isNotEmpty();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    void 지연_로딩과_batch_size면_3번() {
        // 가게 / 메뉴그룹 / 메뉴(IN 으로 그룹 전부)
        StoreMenuResponse response = StoreMenuResponse.from(storeRepository.findById(storeId).orElseThrow());

        assertThat(response.menuGroups()).isNotEmpty();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
    }

    @Test
    void 두_방법의_결과는_같다() {
        Store split = storeRepository.findWithMenuGroups(storeId).orElseThrow();
        menuGroupRepository.findWithMenusByStoreId(storeId);
        StoreMenuResponse splitResponse = StoreMenuResponse.from(split);
        em.clear();   // 비워야 아래가 지연 로딩으로 다시 가져온다
        StoreMenuResponse batchResponse = StoreMenuResponse.from(storeRepository.findById(storeId).orElseThrow());

        // 중첩 record 까지 equals 로 비교 (그룹 순서, 메뉴 순서, 값 모두)
        assertThat(splitResponse).isEqualTo(batchResponse);
    }
}
