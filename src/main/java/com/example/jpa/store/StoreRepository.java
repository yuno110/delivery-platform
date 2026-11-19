package com.example.jpa.store;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

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


}
