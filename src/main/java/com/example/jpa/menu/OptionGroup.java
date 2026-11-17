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
public class OptionGroup { // 맵기 선택 / 토핑 추가 / 사이즈

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(length = 50, nullable = false)
    private String name;

    private int minSelect; // 0 = 선택 안 해도 됨

    private int maxSelect; // 1 =

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    // 옵션 아이템은 이 옵션 그룹 전용 -> ALL + orphanRemoval. 목록에서 뺴거나 그룹을 지우면 항목 행이 DELETE 된다.
    @OneToMany(mappedBy = "optionGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder")
    private List<OptionItem> items = new ArrayList<>();

    private OptionGroup(Store store, String name, int minSelect, int maxSelect) {
        this.store = store;
        this.name = Texts.requireText(name, "옵션 그룹 이름은 필수입니다.");
        this.minSelect = minSelect;
        this.maxSelect = maxSelect;
    }

    public static OptionGroup of(Store store, String name, int minSelect, int maxSelect) {
        if (store == null) {
            throw new IllegalArgumentException("가게는 필수입니다.");
        }
        if (minSelect < 0 || maxSelect < 1 || minSelect > maxSelect) {
            throw new IllegalArgumentException("선택 개수가 올바르지 않습니다: min=" + minSelect + ", max=" + maxSelect);
        }
        return new OptionGroup(store, name, minSelect, maxSelect);
    }

    public OptionItem addItem(String name, Money extraPrice, int sortOrder) {
        OptionItem item = new OptionItem(this, name, extraPrice, sortOrder);
        items.add(item);
        return item;
    }

    public List<OptionItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public void removeItem(OptionItem item) {
        if (item == null) {
            throw new IllegalArgumentException("옵션 항목은 필수입니다.");
        }
        boolean removed = items.removeIf(i -> i.getId().equals(item.getId()));
        if (!removed) {
            throw new IllegalArgumentException("이 옵션 그룹의 항목이 아닙니다.");
        }
    }
}
