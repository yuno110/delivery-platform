package com.example.jpa.member;

import com.example.jpa.common.BaseTimeEntity;
import com.example.jpa.common.Texts;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@Entity
@Getter
// 자바 필드가 없으므로 코드에서 읽을 수 없다. Hibernate가 INSERT할 때 알아서 넣고, 조회할 때 이 값을 보고 어떤 자식 객체를 만들지 결정할때 쓴다
@DiscriminatorColumn(name = "member_type")
@Inheritance(strategy = InheritanceType.JOINED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100, nullable = false, unique = true)
    private String email;

    @Column(length = 100, nullable = false)
    private String password;

    @Column(length = 50, nullable = false)
    private String name;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING) // 안 붙여도 JPA가 잘 저장한다. 붙이는 이유는 기본값이 ORDINAL이라서 STRING을 사용하기 위함이다.
    @Column(nullable = false)
    private MemberStatus status;

    protected Member(String email, String password, String name, String phone) {
        this.email = Texts.requireText(email, "이메일은 필수입니다.");
        this.password = Texts.requireText(password, "비밀번호는 필수입니다.");
        this.name = Texts.requireText(name, "이름은 필수입니다.");
        this.phone = StringUtils.trimToNull(phone);
        this.status = MemberStatus.ACTIVE;
    }

    public void suspend() {
        requireNotWithdrawn();
        this.status = MemberStatus.SUSPENDED;
    }

    public void activate() {
        requireNotWithdrawn();
        this.status = MemberStatus.ACTIVE;
    }

    public void withdraw() {
        this.status = MemberStatus.WITHDRAWN;
    }

    public void changePhone(String phone) {
        this.phone = StringUtils.trimToNull(phone);
    }

    public boolean isActive() {
        return this.status == MemberStatus.ACTIVE;
    }

    private void requireNotWithdrawn() {
        if (this.status == MemberStatus.WITHDRAWN) {
            throw new IllegalStateException("탈퇴한 회원은 변경할 수 없습니다.");
        }
    }

}
