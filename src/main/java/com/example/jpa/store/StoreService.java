package com.example.jpa.store;

import com.example.jpa.menu.MenuGroupRepository;
import com.example.jpa.menu.MenuRepository;
import com.example.jpa.menu.OptionGroupRepository;
import com.example.jpa.store.dto.*;
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
    private final MenuRepository menuRepository;
    private final OptionGroupRepository optionGroupRepository;
    private final CategoryRepository categoryRepository;


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

    // 5-3 A. 지연 로딩 + batch size. 리포지토리 코드 없음
    public StoreDetailResponse findStoreDetail(Long storeId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("가게가 없습니다: " + storeId));
        return StoreDetailResponse.from(store);
    }

    // 5-3 B. 단계별 쿼리 분리(bag 하나씩). 2~4번은 반환값을 안 쓴다 : 컨텍스트에 있는 엔티티의 컬렉션을 채우는 용도
    public StoreDetailResponse findStoreDetailWithSplitQuery(Long storeId) {
        Store store = storeRepository.findWithMenuGroups(storeId) // 1.가게 +메뉴그룹
                .orElseThrow(() -> new IllegalArgumentException("가게가 없습니다: " + storeId));
        menuGroupRepository.findWithMenusByStoreId(storeId); // 2.메뉴그룹 + 메뉴
        menuRepository.findWithOptionGroupsByStoreId(storeId); // 3.메뉴 + 메뉴옵션그룹 + 옵션그룹
        optionGroupRepository.findWithItemsByStoreId(storeId); // 4.옵션그룹 + 옵션
        return StoreDetailResponse.from(store); // 추가 SQL 없음
    }

    // 5-5 A. 엔티티를 전부 가져와서 Java로 센다(비교용 나쁜 예)
    public List<StoreMenuCountResponse> findMenuCountsInJava() {
        return storeRepository.findAll()
                .stream()
                .map(
                        store -> new StoreMenuCountResponse(
                                store.getId(), store.getName(),
                                store.getMenuGroups().stream().mapToLong(mg -> mg.getMenus().size()).sum()
                        )
                )
                .toList();
    }

    // 5-5 B. DB 가 세서 숫자만 보낸다
    public List<StoreMenuCountResponse> findMenuCounts(long minMenuCount) {
        return storeRepository.findMenuCounts(minMenuCount);
    }

    // 5-5 C. 메뉴그룹별 가격 통계
    public List<MenuGroupPriceResponse> findPriceStats(Long storeId) {
        return menuGroupRepository.findPriceStatsByStoreId(storeId);
    }

    // 5-6 A
    public List<StoreSimpleResponse> findStoresInCategoryWithJoin(Long categoryId) {
        return storeRepository.findByCategoryPathWithJoin(findCategoryPath(categoryId));
    }

    // 5-6 B
    public List<StoreSimpleResponse> findStoresInCategory(Long categoryId) {
        return storeRepository.findByCategoryPath(findCategoryPath(categoryId));
    }

    // 카테고리 id → path ("/31/"). 하위 트리는 이 문자열로 시작한다
    private String findCategoryPath(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("카테고리가 없습니다: " + categoryId))
                .getPath();
    }




}
