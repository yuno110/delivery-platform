package com.example.jpa.menu;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MenuRepository extends JpaRepository<Menu, Long> {

    // 한 가게의 메뉴 + 메뉴옵션그룹(컬렉션1개) + 옵션그룹(to-one). bag 이 하나라 패치 조인 가능
    @Query(
            """
            select m
            from Menu m
            left join fetch m.menuOptionGroups mog
            left join fetch mog.optionGroup 
            where m.menuGroup.store.id = :storeId
            """
    )
    List<Menu> findWithOptionGroupsByStoreId(@Param("storeId") Long storeId);

}
