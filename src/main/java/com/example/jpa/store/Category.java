package com.example.jpa.store;

import com.example.jpa.common.Texts;
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
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 자기 자신을 참조한다. 루트는 부모가 없으니 nullable (optional = false 를 안 붙인다)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    // root만 persist 하면 하위 트리가 전부 저장되도록 PERSIST만 건다
    // REMOVE 없음 : 카테고리 하나 지운다고 하위 트리가 통쨰로 사라지면 위험하다
    // orphanRemoval 없음 : 카테고리는 다른 부모 밑으로 옮길 수 있다(step2 마지막 체크포인트)
    @OneToMany(mappedBy = "parent", cascade = CascadeType.PERSIST)
    @OrderBy("sortOrder") // 필드 이름을 사용. 컬럼 이름 X
    private List<Category> children = new ArrayList<>();

    @Column(length = 50, nullable = false)
    private String name;

    // 엔티티에 직접 둔 기본형 + @Column 없음 → Hibernate 가 자동으로 not null 을 붙인다 (sortOrder 도 같다)
    // 주의: @Column 을 붙이는 순간 nullable 기본값(true)이 적용돼 자동 not null 이 사라진다 → 그땐 nullable = false 를 직접 쓴다
    private int depth;

    // INSERT 시점엔 내 id를 모르니 null로 들어갔다가, persist 후 assignPath()로 채운다 -> nullable
    private String path;

    private int sortOrder;

    private Category(Category parent, String name, int sortOrder) {
        this.parent = parent;
        this.name = Texts.requireText(name, "카테고리 이름은 필수입니다.");
        this.depth = (parent == null) ? 0 : parent.getDepth() + 1;
        this.sortOrder = sortOrder;
    }

    public static Category root(String name, int sortOrder) {
        return new Category(null, name, sortOrder);
    }

    public Category addChild(String name, int sortOrder) {
        Category child = new Category(this, name, sortOrder);
        children.add(child);
        return child;
    }

    public void assignPath() {
        if (id == null) {
            throw new IllegalStateException("persist 후에 호출해야 합니다.");
        }
        if (parent != null && parent.getPath() == null) {
            throw new IllegalStateException("부모의 path가 먼저 정해져야 합니다");
        }

        // parent.path 대신 getPath() : parent 가 LAZY 프록시면 필드는 비어있고 getter 를 거쳐야 값을 읽는다
        String parentPath = (parent == null) ? "/" : parent.getPath();
        this.path = parentPath + id + "/";
        children.forEach(Category::assignPath);
    }

    public void moveTo(Category newParent) {
        if (newParent == null) {
            throw new IllegalArgumentException("새 부모는 필수입니다.");
        }
        // 자기 자신이나 자기 하위 밑으로 옮기면 트리가 고리가 되어 깨진다.
        // 하위 카테고리의 path는 내 path 로 시작하므로 startsWith 로 알 수 있다.
        if(newParent.getPath().startsWith(path)) {
            throw new IllegalArgumentException("자기 자신이나 자기 하위로는 옮길 수 없습니다.");
        }
        this.parent = newParent;
    }

    public List<Category> getChildren() {
        return Collections.unmodifiableList(children);
    }

}
