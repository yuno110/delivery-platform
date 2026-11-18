package com.example.jpa.menu;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
// 한 메뉴에 같은 옵션 그룹을 두 번 연결하지 못하게(StoreCategory와 같은 구조)
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_menu_option_group", columnNames = {"menu_id", "option_group_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MenuOptionGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 주인. Menu.menuOptionGroups 의 mappedBy 대상
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_id", nullable = false, foreignKey = @ForeignKey(name = "fk_menu_option_group_menu"))
    private Menu menu;

    // 단방향: 옵션 그룹은 자기를 쓰는 메뉴 목록을 모른다. 여러 메뉴가 공유하므로 cascade 없음
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_group_id", nullable = false, foreignKey = @ForeignKey(name = "fk_menu_option_group_option_group"))
    private OptionGroup optionGroup;

    // 메뉴마다 옵션 그룹 노출 순서가 다르다 → 중간 엔티티에 둔다 (@ManyToMany 로는 못 넣는 컬럼)
    private int sortOrder;

    MenuOptionGroup(Menu menu, OptionGroup optionGroup, int sortOrder) {
        this.menu = menu;
        this.optionGroup = optionGroup;
        this.sortOrder = sortOrder;
    }

}
