package com.example.jpa.menu;

import com.example.jpa.common.Address;
import com.example.jpa.common.Money;
import com.example.jpa.config.JpaAuditingConfig;
import com.example.jpa.config.QuerydslConfig;
import com.example.jpa.member.Owner;
import com.example.jpa.store.Store;
import com.example.jpa.store.StoreOperationStatus;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;

import static com.example.jpa.menu.QMenu.menu;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import({JpaAuditingConfig.class, QuerydslConfig.class})
@DataJpaTest(showSql = false)
public class MenuJpaTest {

    @Autowired
    EntityManager em;

    @Autowired
    JPAQueryFactory queryFactory;

    private long count(String entityName) {
        return em.createQuery("select count(e) from " + entityName + " e", Long.class).getSingleResult();
    }

    @Test
    void 가게를_persist하면_메뉴그룹과_메뉴까지_INSERT된다() {
        Owner owner = Owner.of("owner@test.com", "1234", "김사장", "010-1234-5678", "555-66-77777");
        em.persist(owner);

        Store store = Store.of(
                owner, "김밥천국 역삼점",
                Address.of("06236", "서울 강남구 테헤란로 1", "1층"),
                Money.of(12_000), Money.of(3_000), 30
        );

        MenuGroup main = store.addMenuGroup("대표메뉴", 1);
        main.addMenu("김밥", "기본 김밥", Money.of(4_000), true, 1);
        main.addMenu("라면", null, Money.of(5_000), false, 2);

        MenuGroup side = store.addMenuGroup("사이드", 2);
        side.addMenu("만두", null, Money.of(3_000), false, 1);

        System.out.println("===== persist 전 =====");
        em.persist(store);   // 가게 1 + 메뉴그룹 2 + 메뉴 3 = INSERT 6개 (cascade 가 두 단계 전파)
        System.out.println("===== persist 후 =====");
        em.flush();
        em.clear();

        Store found = em.find(Store.class, store.getId());
        assertThat(found.getMenuGroups()).extracting(MenuGroup::getName)
                .containsExactly("대표메뉴", "사이드");   // @OrderBy 순서
        assertThat(found.getMenuGroups().getFirst().getMenus()).extracting(Menu::getName)
                .containsExactly("김밥", "라면");
        assertThat(found.getMenuGroups().getFirst().getMenus().getFirst().getSaleStatus())
                .isEqualTo(MenuSaleStatus.ON_SALE);
    }

    @Test
    void 메뉴_전체_구조를_저장하면_INSERT가_39개_나간다() {
        Owner owner = Owner.of("owner@test.com", "1234", "김사장", "010-1234-5678", "555-66-77777");
        em.persist(owner);

        Store store = Store.of(
                owner, "김밥천국 역삼점",
                Address.of("06236", "서울 강남구 테헤란로 1", "1층"),
                Money.of(12_000), Money.of(3_000), 30
        );

        // 메뉴 그룹 3 + 메뉴 10
        MenuGroup main = store.addMenuGroup("대표메뉴", 1);
        List<Menu> mains = List.of(
                main.addMenu("김밥", null, Money.of(4_000), true, 1),
                main.addMenu("라면", null, Money.of(5_000), false, 2),
                main.addMenu("떡볶이", null, Money.of(5_000), false, 3),
                main.addMenu("쫄면", null, Money.of(6_000), false, 4)
        );

        MenuGroup side = store.addMenuGroup("사이드", 2);
        List<Menu> sides = List.of(
                side.addMenu("만두", null, Money.of(3_000), false, 1),
                side.addMenu("튀김", null, Money.of(3_000), false, 2),
                side.addMenu("순대", null, Money.of(4_000), false, 3)
        );

        MenuGroup drink = store.addMenuGroup("음료", 3);
        List<Menu> drinks = List.of(
                drink.addMenu("콜라", null, Money.of(2_000), false, 1),
                drink.addMenu("사이다", null, Money.of(2_000), false, 2),
                drink.addMenu("식혜", null, Money.of(2_500), false, 3)
        );

        System.out.println("===== 저장 시작 =====");
        em.persist(store);   // ① store 1 + menu_group 3 + menu 10 = 14 (cascade 두 단계)

        // 옵션 그룹 3 + 항목 8. Store 에 컬렉션이 없으니 각각 persist (store 가 먼저 저장돼 있어야 store_id 를 채운다)
        OptionGroup spicy = OptionGroup.of(store, "맵기 선택", 1, 1);
        spicy.addItem("순한맛", Money.ZERO, 1);
        spicy.addItem("보통", Money.ZERO, 2);
        spicy.addItem("매운맛", Money.ZERO, 3);

        OptionGroup topping = OptionGroup.of(store, "토핑 추가", 0, 3);
        topping.addItem("치즈", Money.of(500), 1);
        topping.addItem("계란", Money.of(500), 2);
        topping.addItem("햄", Money.of(700), 3);

        OptionGroup size = OptionGroup.of(store, "사이즈", 1, 1);
        size.addItem("보통", Money.ZERO, 1);
        size.addItem("큰 사이즈", Money.of(1_000), 2);

        em.persist(spicy);     // ② option_group 1 + option_item 3
        em.persist(topping);   //    option_group 1 + option_item 3
        em.persist(size);      //    option_group 1 + option_item 2  → 합계 11

        // 메뉴 ↔ 옵션 그룹 연결 14개 (대표 4×2 + 사이드 3×1 + 음료 3×1)
        mains.forEach(m -> {
            m.addOptionGroup(spicy, 1);
            m.addOptionGroup(topping, 2);
        });
        sides.forEach(m -> m.addOptionGroup(topping, 1));
        drinks.forEach(m -> m.addOptionGroup(size, 1));

        em.flush();   // ③ 이미 영속 상태인 메뉴의 컬렉션에 추가된 연결 → flush 때 cascade 로 INSERT 14
        System.out.println("===== 저장 끝 =====");
        em.clear();

        // ① 14 + ② 11 + ③ 14 = INSERT 39개
        assertThat(count("MenuGroup")).isEqualTo(3);
        assertThat(count("Menu")).isEqualTo(10);
        assertThat(count("OptionGroup")).isEqualTo(3);
        assertThat(count("OptionItem")).isEqualTo(8);
        assertThat(count("MenuOptionGroup")).isEqualTo(14);
    }

    // 삭제 테스트용 최소 데이터: 그룹 1개에 메뉴 2개(김밥, 라면). 저장 후 clear 해서 DB에서 다시 읽게 한다
    private MenuGroup 메뉴_2개인_그룹() {
        Owner owner = Owner.of("owner@test.com", "1234", "김사장", "010-1234-5678", "555-66-77777");
        em.persist(owner);

        Store store = Store.of(
                owner, "김밥천국 역삼점",
                Address.of("06236", "서울 강남구 테헤란로 1", "1층"),
                Money.of(12_000), Money.of(3_000), 30
        );
        MenuGroup main = store.addMenuGroup("대표메뉴", 1);
        main.addMenu("김밥", null, Money.of(4_000), true, 1);
        main.addMenu("라면", null, Money.of(5_000), false, 2);

        em.persist(store);
        em.flush();
        em.clear();
        return main;   // detached 상태지만 id 를 꺼내는 용도로만 쓴다
    }

    @Test
    void 소프트_삭제하면_DELETE가_아니라_UPDATE가_나간다() {
        MenuGroup group = 메뉴_2개인_그룹();
        Long 김밥Id = group.getMenus().get(0).getId();

        Menu 김밥 = em.find(Menu.class, 김밥Id);
        System.out.println("===== remove 전 =====");
        em.remove(김밥);
        em.flush();   // 여기서 SQL 이 나간다: delete 가 아니라 update menu set deleted=true
        System.out.println("===== remove 후 =====");
        em.clear();

        // 1) em.find: 조회 SQL 에 deleted=false 가 붙어 못 찾는다
        assertThat(em.find(Menu.class, 김밥Id)).isNull();

        // 2) JPQL/QueryDSL: 똑같이 deleted=false 가 자동으로 붙는다
        List<Menu> menus = queryFactory
                .selectFrom(menu)
                .where(menu.menuGroup.id.eq(group.getId()))
                .fetch();
        assertThat(menus).extracting(Menu::getName).containsExactly("라면");

        // 3) 네이티브 SQL: Hibernate 가 조건을 붙이지 않는다 → 행은 그대로 남아 있다
        Number total = (Number) em.createNativeQuery("select count(*) from menu where menu_group_id = ?")
                .setParameter(1, group.getId())
                .getSingleResult();
        assertThat(total.longValue()).isEqualTo(2);

        Object deleted = em.createNativeQuery("select deleted from menu where id = ?")
                .setParameter(1, 김밥Id)
                .getSingleResult();
        assertThat(deleted).isEqualTo(true);   // bit(1) 컬럼은 Boolean 으로 읽힌다
    }

    @Test
    void 소프트_삭제된_메뉴는_컬렉션_로딩에서도_빠진다() {
        MenuGroup group = 메뉴_2개인_그룹();
        Long 김밥Id = group.getMenus().get(0).getId();

        em.remove(em.find(Menu.class, 김밥Id));
        em.flush();
        em.clear();   // 컬렉션을 DB 에서 새로 읽게 하려고 비운다

        MenuGroup found = em.find(MenuGroup.class, group.getId());
        System.out.println("===== getMenus 전 =====");
        // 지연 로딩: 여기서 컬렉션을 처음 건드릴 때 menu SELECT 가 나간다
        List<String> names = found.getMenus().stream().map(Menu::getName).toList();
        System.out.println("===== getMenus 후 =====");

        assertThat(names).containsExactly("라면");
    }

    // 김밥 1개 + "맵기 선택"(항목 3개) 1개를 연결한 최소 데이터. 메뉴 id 를 돌려준다
    private Long 옵션_연결된_메뉴() {
        Owner owner = Owner.of("owner@test.com", "1234", "김사장", "010-1234-5678", "555-66-77777");
        em.persist(owner);

        Store store = Store.of(
                owner, "김밥천국 역삼점",
                Address.of("06236", "서울 강남구 테헤란로 1", "1층"),
                Money.of(12_000), Money.of(3_000), 30
        );
        MenuGroup main = store.addMenuGroup("대표메뉴", 1);
        Menu 김밥 = main.addMenu("김밥", null, Money.of(4_000), true, 1);
        em.persist(store);

        OptionGroup spicy = OptionGroup.of(store, "맵기 선택", 1, 1);
        spicy.addItem("순한맛", Money.ZERO, 1);
        spicy.addItem("보통", Money.ZERO, 2);
        spicy.addItem("매운맛", Money.ZERO, 3);
        em.persist(spicy);   // addOptionGroup 은 id 가 있는(저장된) 옵션 그룹만 받는다

        김밥.addOptionGroup(spicy, 1);
        em.flush();
        em.clear();          // 전부 비워야 이후 접근마다 SQL 이 나가는 게 보인다
        return 김밥.getId();
    }

    @Test
    void 메뉴에서_옵션항목까지_지연_로딩_체인() {
        Long 김밥Id = 옵션_연결된_메뉴();

        System.out.println("===== 0. find =====");
        Menu 김밥 = em.find(Menu.class, 김밥Id);                       // SQL: menu

        System.out.println("===== 1. getMenuOptionGroups =====");
        MenuOptionGroup link = 김밥.getMenuOptionGroups().getFirst();          // SQL: menu_option_group

        System.out.println("===== 2. getOptionGroup =====");
        OptionGroup spicy = link.getOptionGroup();                     // SQL 없음: 프록시만 받는다
        System.out.println("타입: " + spicy.getClass().getSimpleName()); // getClass() 는 프록시를 깨우지 않는다

        System.out.println("===== 3. getItems =====");
        // SQL: option_group (프록시 초기화). 직전까지 spicy는 프록시 였기때문에 레이지 로딩이 걸려있는 items컬렉션을 get하더라도 SQL이 수행된다.
        List<OptionItem> items = spicy.getItems();

        System.out.println("===== 4. items 사용 =====");
        List<String> names = items.stream().map(OptionItem::getName).toList(); // SQL: option_item
        System.out.println("===== 끝 =====");

        assertThat(names).containsExactly("순한맛", "보통", "매운맛");
    }

    @Test
    void 가격_변경은_dirty_checking으로_UPDATE된다() {
        MenuGroup group = 메뉴_2개인_그룹();
        Long 김밥Id = group.getMenus().getFirst().getId();
        Menu 김밥 = em.find(Menu.class, 김밥Id);   // 이 순간의 값이 스냅샷으로 저장된다

        System.out.println("===== 가격 변경 =====");
        김밥.changePrice(Money.of(4_500));
        em.flush();                                // 스냅샷(4,000)과 달라서 UPDATE

        System.out.println("===== 같은 가격으로 다시 변경 =====");
        김밥.changePrice(Money.of(4_500));         // 새 Money 객체지만 값은 같다
        em.flush();                                // UPDATE 없음: 임베디드는 객체가 아니라 안의 값으로 비교한다
        System.out.println("===== 끝 =====");

        em.clear();
        assertThat(em.find(Menu.class, 김밥Id).getPrice()).isEqualTo(Money.of(4_500));
    }

    @Test
    void 품절과_판매재개는_상태_규칙을_지킨다() {
        MenuGroup group = 메뉴_2개인_그룹();
        Long 김밥Id = group.getMenus().getFirst().getId();
        Menu 김밥 = em.find(Menu.class, 김밥Id);

        김밥.soldOut();
        assertThatThrownBy(김밥::soldOut)          // 이미 품절 → 다시 품절 불가
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("판매중인 메뉴만 품절 처리할 수 있습니다");

        김밥.resumeSale();
        assertThatThrownBy(김밥::resumeSale)       // 이미 판매중 → 재개 불가
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("품절된 메뉴만 판매를 재개할 수 있습니다");

        김밥.soldOut();
        em.flush();   // 빼먹으면 아래 clear 가 변경을 버려서 DB 에 반영되지 않는다
        em.clear();
        assertThat(em.find(Menu.class, 김밥Id).getSaleStatus()).isEqualTo(MenuSaleStatus.SOLD_OUT);
    }

    // 이동 테스트용: 대표메뉴(김밥, 라면) + 사이드(만두). 저장 후 clear
    private Store 그룹_2개인_가게() {
        Owner owner = Owner.of("owner@test.com", "1234", "김사장", "010-1234-5678", "555-66-77777");
        em.persist(owner);

        Store store = Store.of(
                owner, "김밥천국 역삼점",
                Address.of("06236", "서울 강남구 테헤란로 1", "1층"),
                Money.of(12_000), Money.of(3_000), 30
        );
        MenuGroup main = store.addMenuGroup("대표메뉴", 1);
        main.addMenu("김밥", null, Money.of(4_000), true, 1);
        main.addMenu("라면", null, Money.of(5_000), false, 2);
        MenuGroup side = store.addMenuGroup("사이드", 2);
        side.addMenu("만두", null, Money.of(3_000), false, 1);

        em.persist(store);
        em.flush();
        em.clear();
        return store;   // detached. 그룹 id 를 꺼내는 용도로만 쓴다
    }

    @Test
    void moveTo는_주인과_양쪽_컬렉션을_함께_바꾼다() {
        Store store = 그룹_2개인_가게();
        Long mainId = store.getMenuGroups().get(0).getId();
        Long sideId = store.getMenuGroups().get(1).getId();

        MenuGroup main = em.find(MenuGroup.class, mainId);
        MenuGroup side = em.find(MenuGroup.class, sideId);
        Menu 라면 = main.getMenus().get(1);   // main.menus 초기화 (김밥, 라면)
        side.getMenus().size();               // side.menus 도 미리 초기화 (만두). 7-a 에서 어긋남이 드러났던 조건

        System.out.println("===== 이동 =====");
        라면.moveTo(side);
        em.flush();                           // UPDATE menu set menu_group_id = 사이드
        System.out.println("===== 이동 후 =====");

        // 메모리: 7-a 에서는 [김밥, 라면] / [만두] 였다. 편의 메서드가 양쪽을 맞췄다
        assertThat(라면.getMenuGroup().getName()).isEqualTo("사이드");
        assertThat(main.getMenus()).extracting(Menu::getName).containsExactly("김밥");
        assertThat(side.getMenus()).extracting(Menu::getName).containsExactly("만두", "라면");

        // DB: 메모리와 같은 모습이다
        em.clear();
        assertThat(em.find(MenuGroup.class, mainId).getMenus()).extracting(Menu::getName).containsExactly("김밥");
        assertThat(em.find(MenuGroup.class, sideId).getMenus()).extracting(Menu::getName).containsExactly("만두", "라면");
    }

    // 옵션 그룹만 만들고 어떤 메뉴에도 붙이지 않는다. 옵션 그룹 id 를 돌려준다
    private Long 연결_안_된_옵션그룹() {
        Owner owner = Owner.of("owner@test.com", "1234", "김사장", "010-1234-5678", "555-66-77777");
        em.persist(owner);
        Store store = Store.of(
                owner, "김밥천국 역삼점",
                Address.of("06236", "서울 강남구 테헤란로 1", "1층"),
                Money.of(12_000), Money.of(3_000), 30
        );
        em.persist(store);

        OptionGroup spicy = OptionGroup.of(store, "맵기 선택", 1, 1);
        spicy.addItem("순한맛", Money.ZERO, 1);
        spicy.addItem("매운맛", Money.ZERO, 2);
        em.persist(spicy);
        em.flush();
        em.clear();
        return spicy.getId();
    }

    @Test
    void 메뉴에_연결된_옵션그룹은_삭제할_수_없다() {
        Long 김밥Id = 옵션_연결된_메뉴();
        Long 맵기Id = em.find(Menu.class, 김밥Id).getMenuOptionGroups().get(0).getOptionGroup().getId();
        em.clear();

        OptionGroup 맵기 = em.find(OptionGroup.class, 맵기Id);
        System.out.println("===== 연결된 옵션 그룹 삭제 =====");
        em.remove(맵기);
        // menu_option_group.option_group_id 가 아직 이 옵션 그룹을 가리킨다(항목은 하드 삭제라 더는 막지 않는다)
        assertThatThrownBy(() -> em.flush())
                .isInstanceOf(PersistenceException.class)
                .rootCause()
                .isInstanceOf(SQLIntegrityConstraintViolationException.class)
                .hasMessageContaining("menu_option_group");
        System.out.println("===== 끝 =====");
    }

    @Test
    void 연결이_없어도_옵션그룹은_삭제할_수_없다() {
        Long 맵기Id = 연결_안_된_옵션그룹();

        OptionGroup 맵기 = em.find(OptionGroup.class, 맵기Id);
        System.out.println("===== 연결 없는 옵션 그룹 삭제 =====");
        em.remove(맵기);
        em.flush();   // delete option_item × 2 → delete option_group (자식 먼저)
        System.out.println("===== 끝 =====");
        em.clear();

        // 하드 삭제: 네이티브로 봐도 행이 없다
        Number groups = (Number) em.createNativeQuery("select count(*) from option_group where id = ?")
                .setParameter(1, 맵기Id).getSingleResult();
        Number items = (Number) em.createNativeQuery("select count(*) from option_item where option_group_id = ?")
                .setParameter(1, 맵기Id).getSingleResult();
        assertThat(groups.longValue()).isZero();
        assertThat(items.longValue()).isZero();
    }

    @Test
    void 연결을_끊고_flush한_뒤_옵션그룹을_지운다() {
        Long 김밥Id = 옵션_연결된_메뉴();
        Menu 김밥 = em.find(Menu.class, 김밥Id);
        OptionGroup 맵기 = 김밥.getMenuOptionGroups().get(0).getOptionGroup();
        Long 맵기Id = 맵기.getId();

        System.out.println("===== 1. 연결 끊기 =====");
        김밥.removeOptionGroup(맵기);

        // 연결 삭제(DELETE menu_option_group)를 먼저 DB 에 반영한다.
        // 빼면: 다음 flush 에서 "삭제될 맵기를 가리키는 연결 행"의 FK 를 null 로 바꾸는 UPDATE 가
        // DELETE 보다 먼저 실행되어 option_group_id NOT NULL 위반
        em.flush();
        System.out.println("===== 2. 옵션 그룹 삭제 =====");
        em.remove(맵기);
        em.flush();   // delete option_item × 3 → delete option_group
        System.out.println("===== 끝 =====");
        em.clear();

        assertThat(em.find(Menu.class, 김밥Id).getMenuOptionGroups()).isEmpty();
        Number groups = (Number) em.createNativeQuery("select count(*) from option_group where id = ?")
                .setParameter(1, 맵기Id).getSingleResult();
        assertThat(groups.longValue()).isZero();
    }

    @Test
    void 옵션_항목을_목록에서_빼면_DELETE된다() {
        Long 맵기Id = 연결_안_된_옵션그룹();

        OptionGroup 맵기 = em.find(OptionGroup.class, 맵기Id);
        OptionItem 매운맛 = 맵기.getItems().get(1);   // items 초기화 (순한맛, 매운맛)
        Long 매운맛Id = 매운맛.getId();

        System.out.println("===== 항목 제거 =====");
        맵기.removeItem(매운맛);   // 목록에서 빼기만 한다. em.remove 호출 없음
        em.flush();                // orphanRemoval → delete from option_item
        System.out.println("===== 끝 =====");

        // 이미 뺀 항목을 다시 빼면 예외
        assertThatThrownBy(() -> 맵기.removeItem(매운맛))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이 옵션 그룹의 항목이 아닙니다.");

        em.clear();
        assertThat(em.find(OptionGroup.class, 맵기Id).getItems())
                .extracting(OptionItem::getName)
                .containsExactly("순한맛");

        // 하드 삭제: 네이티브로 봐도 행이 없다
        Number rows = (Number) em.createNativeQuery("select count(*) from option_item where id = ?")
                .setParameter(1, 매운맛Id).getSingleResult();
        assertThat(rows.longValue()).isZero();
    }









}
