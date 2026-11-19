package com.example.jpa.store;

import com.example.jpa.menu.MenuGroupRepository;
import com.example.jpa.store.dto.StoreMenuResponse;
import com.example.jpa.store.dto.StoreSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreService {

    private final StoreRepository storeRepository;

    private final MenuGroupRepository menuGroupRepository;

    public List<StoreSummaryResponse> findStores() {
        return storeRepository.findAll().stream()
                .map(StoreSummaryResponse::from)
                .toList();
    }


    // A. 페치 조인 (SQL 1번)
    public List<StoreSummaryResponse> findStoresWithFetchJoin() {
        return storeRepository.findAllWithFetchJoin().stream()
                .map(StoreSummaryResponse::from)
                .toList();
    }

    // B. @EntityGraph (SQL 1번)
    public List<StoreSummaryResponse> findStoresWithEntityGraph() {
        return storeRepository.findAllWithEntityGraph().stream()
                .map(StoreSummaryResponse::from)
                .toList();
    }

    // A. MultipleBagFetchException
    public StoreMenuResponse findMenusWithTwoBags(Long storeId) {
        Store store = storeRepository.findWithMenuGroupsAndMenus(storeId)
                .orElseThrow(() -> new IllegalArgumentException("가게가 없습니다: " + storeId));
        return StoreMenuResponse.from(store);
    }

    // B. 쿼리 나누기: 컬렉션 하나씩 페치 조인 (SQL 2번)
    public StoreMenuResponse findMenusWithSplitQuery(Long storeId) {
        // 1번: 가게 + 메뉴그룹. 이 시점에 각 MenuGroup.menus 는 아직 미초기화
        Store store = storeRepository.findWithMenuGroups(storeId)
                .orElseThrow(() -> new IllegalArgumentException("가게가 없습니다: " + storeId));

        // 2번: 메뉴그룹 + 메뉴. 반환값을 일부러 안 쓴다 (지우면 안 되는 줄)
        //   - 리포지토리가 달라도 이 메서드의 트랜잭션 안에서는 영속성 컨텍스트가 하나다
        //   - 결과 행의 메뉴그룹 id 가 이미 컨텍스트에 있으면(1번에서 가져옴) 새 객체를 만들지 않고 그 객체를 쓴다 (같은 id = 같은 객체)
        //   - 페치 조인이라 그 객체의 미초기화 menus 를 행의 메뉴들로 채운다 → store.getMenuGroups() 안의 객체가 채워진 것
        //   - 이미 있는 엔티티의 필드는 덮어쓰지 않고, 이미 초기화된 컬렉션이면 다시 채우지 않는다
        //   - 트랜잭션이 없으면 호출마다 컨텍스트가 달라 채워지지 않는다 → OSIV 꺼짐이라 LazyInitializationException
        menuGroupRepository.findWithMenusByStoreId(storeId);

        return StoreMenuResponse.from(store);                              // 추가 SQL 없음
    }

    // C. 그냥 지연 로딩. batch size 가 IN 으로 묶어 준다 (SQL 3번: 가게 / 메뉴그룹 / 메뉴)
    //   메뉴 SQL: where menu_group_id in (1, 2, 3, 4, null, ... null) and deleted = false
    //   - 묶는 대상은 "지금 컨텍스트에 있는 미초기화 menus 컬렉션" → 가게 1개라 메뉴그룹 수(3~4개)만 들어간다
    //   - 나머지 칸은 null: 값 개수와 상관없이 ? 100개짜리 같은 SQL 을 재사용하려고 (null 은 어떤 행과도 일치하지 않음)
    //   - deleted = false 는 Menu 의 @SoftDelete 가 붙인다
    public StoreMenuResponse findMenus(Long storeId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("가게가 없습니다: " + storeId));
        return StoreMenuResponse.from(store);
    }


}
