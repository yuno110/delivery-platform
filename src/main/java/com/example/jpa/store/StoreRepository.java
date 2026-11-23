package com.example.jpa.store;

import com.example.jpa.store.dto.StoreMenuCountResponse;
import com.example.jpa.store.dto.StoreSimpleResponse;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long> {

    @Query(
            """
            select s
            from Store s
            join fetch s.owner
            left join fetch s.menuGroups
            order by s.id
            """
    )
    List<Store> findAllWithFetchJoin();

    @EntityGraph(attributePaths = {"owner", "menuGroups"})
    @Query("select s from Store s order by s.id")
    List<Store> findAllWithEntityGraph();

    // A. 실패: List(bag) 컬렉션 두 개(menuGroups, menus)를 동시에 페치 조인
    @Query(
            """
            select s 
            from Store s 
            left join fetch s.menuGroups mg 
            left join fetch mg.menus 
            where s.id = :storeId    
            """
    )
    Optional<Store> findWithMenuGroupsAndMenus(@Param("storeId") Long storeId);

    // B-1. 가게 + 메뉴그룹 (bag 하나)
    @Query(
            """
            select s 
            from Store s 
            left join fetch s.menuGroups 
            where s.id = :storeId
            """
    )
    Optional<Store> findWithMenuGroups(@Param("storeId") Long storeId);

    // B. 가게별 메뉴 슈. 엔티티가 아니라 DTO를 바로 만든다 (select new + 패키지 포함 전체 이름)
    // from 이 Menu: 메뉴 행을 가게 단위로 묶어 센다. @SoftDelete 로 삭제된 메뉴는 자동 제외
    // having: 묶은 "뒤"의 조건(where는 묶기 "전" 행 조건)
    @Query(
            """
            select new com.example.jpa.store.dto.StoreMenuCountResponse(s.id, s.name, count(m))
            from Menu m
            join m.menuGroup mg
            join mg.store s
            group by s.id, s.name
            having count(m) >= :minMenuCount
            order by s.id
            """
    )
    List<StoreMenuCountResponse> findMenuCounts(@Param("minMenuCount") long minMenuCount);

    // 5-6 A. 조인: 가게 하나가 같은 트리의 카테고리 2개(예: 한식 > 찌개, 한식 > 국밥) 에 걸려 있으면 그 가게 행이 2번 나온다
    @Query(
            """
            select new com.example.jpa.store.dto.StoreSimpleResponse(s.id, s.name)
            from StoreCategory sc
            join sc.store s
            join sc.category c
            where c.path like concat(:path, '%')
            order by s.id
            """
    )
    List<StoreSimpleResponse> findByCategoryPathWithJoin(@Param("path") String path);

    // 5-6 B. exists: "이 가게에 조건에 맞는 연결이 하나라도 있나?" -> 가게 기준이라 가게당 한 번만
    @Query(
            """
            select new com.example.jpa.store.dto.StoreSimpleResponse(s.id, s.name)
            from Store s
            where exists (
                select 1
                from StoreCategory sc
                join sc.category c
                where sc.store = s
                and c.path like concat(:path, '%')
            )
            order by s.id
            """
    )
    List<StoreSimpleResponse> findByCategoryPath(@Param("path") String path);






}
