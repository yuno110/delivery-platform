package com.example.jpa.menu;


import com.example.jpa.store.dto.MenuGroupPriceResponse;
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

    // C. 한 가게의 메뉴그룹별 가격 통계. m.price.amount: 임베디드(Money) 안의 필드 → 컬럼 price
    @Query(
              """                                                                                                                                    
              select new com.example.jpa.store.dto.MenuGroupPriceResponse(                                                                          
                  mg.id, mg.name, count(m), avg(m.price.amount), min(m.price.amount), max(m.price.amount)
              )                                          
              from Menu m                                                                                                                           
              join m.menuGroup mg                                                                                                                   
              where mg.store.id = :storeId                                                                                                          
              group by mg.id, mg.name                                                                                                               
              order by mg.sortOrder                                                                                                                 
              """
    )
    List<MenuGroupPriceResponse> findPriceStatsByStoreId(@Param("storeId") Long storeId);






}
