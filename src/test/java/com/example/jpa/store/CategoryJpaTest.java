package com.example.jpa.store;

import com.example.jpa.config.QuerydslConfig;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static com.example.jpa.store.QCategory.category;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(QuerydslConfig.class)
@DataJpaTest(showSql = false)
public class CategoryJpaTest {

    @Autowired
    EntityManager em;

    @Autowired
    JPAQueryFactory queryFactory;

    @Autowired
    CategoryRepository categoryRepository;

    // 음식 > 한식 > 찌개, 세계 > 아시아 를 저장하고 {한식, 찌개, 아시아} 의 id 를 돌려준다
    private Long[] 이동용_트리_준비() {
        Category food = Category.root("음식", 1);
        Category korean = food.addChild("한식", 1);
        Category stew = korean.addChild("찌개", 1);

        Category world = Category.root("세계", 2);
        Category asia = world.addChild("아시아", 1);

        em.persist(food);
        em.persist(world);
        food.assignPath();
        world.assignPath();
        em.flush();
        em.clear();
        return new Long[]{korean.getId(), stew.getId(), asia.getId()};
    }

    @Test
    void 루트만_persist하면_트리_전체가_INSERT되고_path는_UPDATE로_채워진다() {

        Category food = Category.root("음식", 1);
        Category korean = food.addChild("한식", 1);
        Category stew = korean.addChild("찌개", 1);

        System.out.println("===== persist 전 =====");
        em.persist(food); // cascade PERSIST : 음식 -> 한식 -> 찌개 순서로 INSERT 3개 (path 는 null)
        System.out.println("===== persist 후 =====");

        food.assignPath(); // 이제 세 노드 모두 id가 있다. SQL없음, 필드만 바뀐다

        System.out.println("===== flush 전 =====");
        em.flush(); // 변경 감지 -> UPDATE 3개
        System.out.println("===== flush 후 =====");

        assertThat(food.getPath()).isEqualTo("/" + food.getId() + "/");
        assertThat(stew.getPath())
                .isEqualTo("/" + food.getId() + "/" + korean.getId() + "/" + stew.getId() + "/");
        assertThat(stew.getDepth()).isEqualTo(2);
    }

    @Test
    void path_like로_하위_트리를_한_번에_조회한다() {
        Category food = Category.root("음식", 1);
        Category korean = food.addChild("한식", 1);
        korean.addChild("찌개", 1);
        korean.addChild("국", 2);
        Category chinese = food.addChild("중식", 2);
        chinese.addChild("짜장", 1);

        em.persist(food);
        food.assignPath();
        em.flush();
        em.clear();

        System.out.println("===== 하위 트리 조회 전 =====");
        List<Category> result = queryFactory
                .selectFrom(category)
                .where(category.path.startsWith(korean.getPath()))
                .orderBy(category.depth.asc(), category.sortOrder.asc())
                .fetch();
        System.out.println("===== 하위 트리 조회 후 =====");

        // 한식 자신 + 그 아래 전부.  중식 쪽은 안 나온다
        assertThat(result).extracting(Category::getName).containsExactly("한식", "찌개", "국");
    }

    @Test
    void 벌크_UPDATE는_영속성_컨텍스트를_거치지_않아_옛_값이_남는다() {
        Long[] ids = 이동용_트리_준비();
        Category korean = em.find(Category.class, ids[0]);
        Category stew = em.find(Category.class, ids[1]);   // 찌개를 영속성 컨텍스트에 올려 둔다
        Category asia = em.find(Category.class, ids[2]);

        String oldPath = korean.getPath();
        String newPath = asia.getPath() + korean.getId() + "/";
        int depthDiff = asia.getDepth() + 1 - korean.getDepth();
        String stewOldPath = stew.getPath();

        korean.moveTo(asia);

        System.out.println("===== 벌크 전 =====");
        int updated = em.createQuery(
                      """                                                                                        
                      update Category c                                                                                                            
                      set c.path = replace(c.path, :oldPath, :newPath),                                                                             
                          c.depth = c.depth + :depthDiff                                                                                            
                      where c.path like concat(:oldPath, '%')                                                                                       
                      """
                )
                .setParameter("oldPath", oldPath)
                .setParameter("newPath", newPath)
                .setParameter("depthDiff", depthDiff)
                .executeUpdate();   // 쿼리 전에 자동 flush 로 parent 변경이 먼저 나가고, 이어서 벌크 UPDATE 가 나간다
        System.out.println("===== 벌크 후 =====");

        assertThat(updated).isEqualTo(2);   // 한식, 찌개

        // DB 는 바뀌었지만 메모리의 찌개 객체는 그대로다. 벌크는 DB 로 바로 가기 때문이다
        assertThat(stew.getPath()).isEqualTo(stewOldPath);
        assertThat(stew.getDepth()).isEqualTo(2);

        em.clear();   // 옛 값을 버린다

        Category reloaded = em.find(Category.class, ids[1]);   // 다시 SELECT
        assertThat(reloaded.getPath()).isEqualTo(newPath + ids[1] + "/");
        assertThat(reloaded.getDepth()).isEqualTo(3);
    }

    @Test
    void clearAutomatically면_벌크_후_영속성_컨텍스트가_자동으로_비워진다() {
        Long[] ids = 이동용_트리_준비();
        Category korean = em.find(Category.class, ids[0]);
        Category stew = em.find(Category.class, ids[1]);
        Category asia = em.find(Category.class, ids[2]);

        String oldPath = korean.getPath();
        String newPath = asia.getPath() + korean.getId() + "/";
        int depthDiff = asia.getDepth() + 1 - korean.getDepth();

        korean.moveTo(asia);
        System.out.println("===== update 전 =====");
        int updated = categoryRepository.moveSubtree(oldPath, newPath, depthDiff);
        System.out.println("===== update 후 =====");

        assertThat(updated).isEqualTo(2);
        assertThat(em.contains(stew)).isFalse();   // clear 됐으니 stew 는 이제 준영속

        Category reloaded = em.find(Category.class, ids[1]);   // em.clear() 없이 새 값을 읽는다
        assertThat(reloaded.getPath()).isEqualTo(newPath + ids[1] + "/");
        assertThat(reloaded.getDepth()).isEqualTo(3);

        Category movedKorean = em.find(Category.class, ids[0]);
        assertThat(movedKorean.getParent().getId()).isEqualTo(ids[2]);   // parent 변경도 저장됐다
    }

    @Test
    void 자기_하위로는_옮길_수_없다() {
        Long[] ids = 이동용_트리_준비();
        Category korean = em.find(Category.class, ids[0]);
        Category stew = em.find(Category.class, ids[1]);

        assertThatThrownBy(() -> korean.moveTo(stew))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> korean.moveTo(korean))
                .isInstanceOf(IllegalArgumentException.class);
    }



}
