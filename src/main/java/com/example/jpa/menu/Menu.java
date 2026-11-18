package com.example.jpa.menu;

import com.example.jpa.common.Money;
import com.example.jpa.common.Texts;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.SoftDelete;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Getter
@SoftDelete // em.remove 가 DELETE 대신 UPDATE deleted = ture. 모든 조회에서 deleted = true 행이 자동 제외된다
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Menu { // 김밥 / 라면

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_group_id", nullable = false, foreignKey = @ForeignKey(name = "fk_menu_menu_group"))
    private MenuGroup menuGroup;

    // 연결 (MenuOptionGroup)은 이 메뉴 전용 -> ALL + orphanRemoval. 목록에서 빼면 연결 행이 DELETE 된다
    @OneToMany(mappedBy = "menu", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder")
    private List<MenuOptionGroup> menuOptionGroups = new ArrayList<>();

    @Column(length = 100, nullable = false)
    private String name;

    @Column(length = 500)
    private String description;

    // Money 의 컬럼 이름 amount -> price. @AttributeOverride는 @Column을 통째로 대체하므로 nullable = false 를 다시 쓴다
    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "price", nullable = false))
    private Money price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MenuSaleStatus saleStatus;

    // StoreCategory.primary와 같은 이유로 is_를 붙인다(boolean 필드 이름과 컬럼 이름을 구분)
    @Column(name = "is_signature", nullable = false)
    private boolean signature;

    private int sortOrder;

    @Column(length = 500)
    private String imageUrl;

    Menu(MenuGroup menuGroup, String name, String description, Money price, boolean signature, int sortOrder) {
        if (price == null) {
            throw new IllegalArgumentException("가격은 필수입니다.");
        }
        this.menuGroup = menuGroup;
        this.name = Texts.requireText(name, "메뉴 이름은 필수입니다.");
        this.description = StringUtils.trimToNull(description);
        this.price = price;
        this.signature = signature;
        this.sortOrder = sortOrder;
        this.saleStatus = MenuSaleStatus.ON_SALE;   // 새 메뉴는 판매중으로 시작
    }

    public void addOptionGroup(OptionGroup optionGroup, int sortOrder) {
        if (optionGroup == null) {
            throw new IllegalArgumentException("옵션 그룹은 필수입니다.");
        }
        if (optionGroup.getId() == null) {
            throw new IllegalArgumentException("저장된 옵션 그룹만 연결할 수 있습니다.");
        }
        boolean alreadyAdded = menuOptionGroups.stream()
                .anyMatch(mog -> mog.getOptionGroup().getId().equals(optionGroup.getId()));
        if (alreadyAdded) {
            throw new IllegalArgumentException("이미 연결된 옵션 그룹입니다");
        }
        menuOptionGroups.add(new MenuOptionGroup(this, optionGroup, sortOrder));

    }

    public List<MenuOptionGroup> getMenuOptionGroups() {
        return Collections.unmodifiableList(menuOptionGroups);
    }

    // 연결 끊기. 목록에서 빼면 orphanRemoval 로 menu_option_group 행이 DELETE 된다(옵션 그룹 자체는 그대로)
    public void removeOptionGroup(OptionGroup optionGroup) {
        if (optionGroup == null) {
            throw new IllegalArgumentException("옵션 그룹은 필수입니다.");
        }
        boolean removed = menuOptionGroups.removeIf(mog -> mog.getOptionGroup().getId().equals(optionGroup.getId()));
        if(!removed) {
            throw new IllegalArgumentException("연결되지 않은 옵션 그룹입니다.");
        }
    }

    // setter 대신 의도를 이름에 담는다. 불변 Money는 값을 고치지 않고 통째로 교체한다.
    public void changePrice(Money newPrice) {
        if (newPrice == null) {
            throw new IllegalArgumentException("가격은 필수입니다.");
        }
        this.price = newPrice;
    }

    // 판매중일 때만 품절 처리할 수 있다. 숨김(HIDDEN) 메뉴를 품절로 바꾸는 실수를 막는다
    public void soldOut() {
        if (saleStatus != MenuSaleStatus.ON_SALE) {
            throw new IllegalStateException("판매중인 메뉴만 품절 처리할 수 있습니다 : " + saleStatus);
        }
        this.saleStatus = MenuSaleStatus.SOLD_OUT;
    }

    // 품절된 메뉴만 다시 판매중으로 되돌린다
    public void resumeSale() {
        if(saleStatus != MenuSaleStatus.SOLD_OUT) {
            throw new IllegalStateException("품절된 메뉴만 판매를 재개할 수 있습니다 : " + saleStatus);
        }
        this.saleStatus = MenuSaleStatus.ON_SALE;
    }

    // 메뉴판에서 숨긴다(점주 화면에서만 보임). 판매중·품절 어느 쪽에서든 가능
    public void hide() {
        if (saleStatus == MenuSaleStatus.HIDDEN) {
            throw new IllegalStateException("이미 숨긴 메뉴입니다.");
        }
        this.saleStatus = MenuSaleStatus.HIDDEN;
    }

    // 메뉴를 같은 가게의 다른 그룹으로 옮긴다. 주인(FK)과 양쪽 컬렉션을 한 번에 맞춘다
    public void moveTo(MenuGroup newGroup) {
        if (newGroup == null) {
            throw new IllegalArgumentException("옮길 메뉴 그룹은 필수입니다.");
        }
        // id 로 비교 : 한쪽이 프록시여도 getId() 는 SQL 없이 값을 준다
        if (newGroup.getId().equals(menuGroup.getId())) {
            throw new IllegalArgumentException("이미 이 그룹에 있는 메뉴입니다.");
        }
        // 피자가게 그룹으로 김밥을 옮기는 실수를 막는다
        if(!newGroup.getStore().getId().equals(menuGroup.getStore().getId())) {
            throw new IllegalArgumentException("다른 가게의 메뉴 그룹으로는 옮길 수 없습니다.");
        }
        menuGroup.removeMenu(this); // 옛 그룹 리스트에서 뺀다
        this.menuGroup = newGroup; // 주인 변경 -> flush 때 UPDATE menu_group_id
        newGroup.attachMenu(this); // 새 그룹 리스트에 넣는다
    }



}
