package com.example.jpa.menu;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OptionGroupRepository extends JpaRepository<OptionGroup, Long> {


    // 한 가게의 옵션그룹 + 옵션. 옵션그룹은 가게 소속(store_id)이라 메뉴를 거치지 않고 바로 찾는다
    @Query(
            """
            select og
            from OptionGroup og
            left join fetch og.items i
            where og.store.id = :storeId
            """
    )
    List<OptionGroup> findWithItemsByStoreId(@Param("storeId") Long storeId);

}
