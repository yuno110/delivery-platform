package com.example.jpa.menu;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MenuGroupRepository extends JpaRepository<MenuGroup, Long> {

    // 한 가게의 메뉴그룹 + 메뉴. 컬렉션이 menus 하나뿐이라 패치 조인 가능
    @Query(
            """
            select mg
            from MenuGroup mg
            left join fetch mg.menus
            where mg.store.id = :storeId
            order by mg.sortOrder
            """
    )
    List<MenuGroup> findWithMenusByStoreId(@Param("storeId") Long storeId);

}
