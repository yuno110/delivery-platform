package com.example.jpa.store;

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






}
