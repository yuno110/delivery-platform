package com.example.jpa.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    // @Modifying : SELECT 가 아니라 UPDATE/DELETE 라는 표시. 없으면 실행할 때 에러가 난다
    // clearAutomatically : 실행 후 em.clear() 를 대신 호출한다
    @Modifying(clearAutomatically = true)
    @Query(
            """
            UPDATE Category c
            SET c.path = REPLACE(c.path, :oldPath, :newPath),
                c.depth = c.depth + :depthDiff
            WHERE c.path LIKE CONCAT(:oldPath, '%')
            """
    )
    int moveSubtree(
            @Param("oldPath") String oldPath,
            @Param("newPath") String newPath,
            @Param("depthDiff") int depthDiff
    );

}
