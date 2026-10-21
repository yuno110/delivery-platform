package com.example.jpa.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// AuditingEntityListener는 hibernate가 직접 new해서 만든다. 스프링이 만드는 빈이아니다.
// 리스너가 실제 일을 하려면 Spring쪽 부품들이 등록되어 있어야한다.
// AuditingHandler(@CreatedDate 붙은 필드를 찾아 값을 채운다)
// DateTimeProvider(현재시간을 가져오는 역할)
// JpaMetamodelMappingContext(엔티티 매핑정보를 가져오는 역할)
// @EnableJpaAuditing이 하는 일이 이 부품들을 빈으로 등록하고 Hibernate가 만든 AuditingEntityListener에 꽂아준다.
@EnableJpaAuditing
@Configuration
public class JpaAuditingConfig {
}
