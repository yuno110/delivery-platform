package com.example.jpa.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@EntityListeners(AuditingEntityListener.class)
@MappedSuperclass
public abstract class BaseTimeEntity {

    // @CreatedDate, @LastModifiedDate 는 표시일 뿐이다.
    // 이 어노테이션 자체는 아무것도 하지않는다. 실제로 LocalDateTime.now()를 넣어주는 코드가 따로 있어야한다.
    // 그걸 AuditingEntityListener가 해준다.

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

}
