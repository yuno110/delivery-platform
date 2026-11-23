package com.example.jpa.store;

import com.example.jpa.store.dto.StoreMenuCountResponse;
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

}
