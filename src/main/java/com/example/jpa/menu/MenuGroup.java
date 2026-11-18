package com.example.jpa.menu;

import com.example.jpa.common.Money;
import com.example.jpa.common.Texts;
import com.example.jpa.store.Store;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MenuGroup { // 대표메뉴 / 사이드

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50, nullable = false)
    private String name;

    private int sortOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false, foreignKey = @ForeignKey(name = "fk_menu_group_store"))
    private Store store;

    // orphanRemoval 없음 : 메뉴는 다른 그룹으로 옮길 수 있어서 "목록에서 빠짐 = 삭제" 가 아니다.
    // 메뉴 삭제는 em.remove(menu) 로 명시한다 (->@SoftDelete 로 UPDATE)
    @OneToMany(mappedBy = "menuGroup", cascade = CascadeType.ALL)
    @OrderBy("sortOrder")
    private List<Menu> menus = new ArrayList<>();

    private MenuGroup (Store store, String name, int sortOrder) {
        this.store = store;
        this.name = Texts.requireText(name, "메뉴 그룹 이름은 필수입니다.");
        this.sortOrder = sortOrder;
    }

    // Store(store 패키지)가 호출해야 하는데 패키지가 달라서 package-private 생성자를 못 쓴다 -> public 팩토리
    // 직접 호출하지 말고 store.addMenuGroup()을 쓴다. 그래야 store.menuGroups 에도 들어가 cascade 대상이 된다
    public static MenuGroup of (Store store, String name, int sortOrder) {
        if (store == null) {
            throw new IllegalArgumentException("가게는 필수입니다.");
        }
        return new MenuGroup(store, name, sortOrder);
    }

    public Menu addMenu(String name, String description, Money price, boolean signature, int sortOrder) {
        Menu menu = new Menu(this, name, description, price, signature, sortOrder);
        menus.add(menu);
        return menu;
    }

    public List<Menu> getMenus() {
        return Collections.unmodifiableList(menus);
    }

    // Menu.moveTo전용. 리스트만 고친다(FK 는 Menu.menuGroup 이 바꾼다)
    // public이 아니라서 밖에서 리스트만 따로 고쳐 어긋나게 만들 수 없다
    void removeMenu(Menu menu) {
        menus.remove(menu);
    }

    void attachMenu(Menu menu) {
        menus.add(menu);
    }




}
