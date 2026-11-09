# delivery-platform

| 항목 | 버전 |
| --- | --- |
| Java | 21 |
| Spring Boot | 4.1.1 (Hibernate 7) |
| QueryDSL | 7.3.0 (`io.github.openfeign.querydsl` — 원조 `com.querydsl`의 유지보수 포크, 패키지명은 `com.querydsl.*` 그대로) |
| DB | 로컬 MySQL 8.0, 스키마 `jpa` |
| 빌드 | Gradle 9.7.1 (Wrapper) |


## 테스트할 때 알아 둘 것

- 테스트도 같은 MySQL `jpa` 스키마를 쓴다. 테스트의 `@Transactional`은 끝나면 **롤백**하므로 DB에 남지 않는다. 데이터를 직접 보고 싶으면 `@Commit`(또는 `@Rollback(false)`)을 붙인다.
- 롤백되면 `flush`가 안 일어나 **INSERT/UPDATE 로그가 안 보일 수 있다.** 쿼리를 보려면 `em.flush()`를 직접 호출한다.
- `@DataJpaTest`는 `spring.test.database.replace: none` 설정으로 내장 DB 대신 MySQL을 쓴다. Boot 4에서 패키지가 `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`로 바뀌었다.
- `@DataJpaTest`는 JPA 관련 빈만 띄우므로 `JPAQueryFactory`가 없다. `@Import(QuerydslConfig.class)`를 붙인다.

## 순서

도메인 예: `Member` – `Team`(N:1)로 시작해서 `Order` – `OrderItem` – `Item`, `Order` – `Delivery`(1:1)로 넓힌다.

1. **영속성 컨텍스트** — 1차 캐시와 동일성(`==`), 쓰기 지연, 변경 감지(dirty checking), `flush`/`clear`, `detach`와 `merge`
2. **엔티티 매핑** — `@Column`, `@Enumerated(EnumType.STRING)`, 기본키 전략(MySQL은 `IDENTITY` → persist 시점에 INSERT가 나가는 것 확인), `@Embedded` 값 타입
3. **연관관계** — 단방향/양방향, 연관관계의 주인(`mappedBy`), 연관관계 편의 메서드, `@ManyToMany` 대신 중간 엔티티
4. **상속·공통 매핑** — `@Inheritance`(JOINED / SINGLE_TABLE), `@MappedSuperclass` + Auditing(`@EnableJpaAuditing`)
5. **프록시와 로딩** — `getReference`, LAZY vs EAGER, `cascade`, `orphanRemoval`
6. **JPQL** — 페치 조인, 컬렉션 페치 조인 + 페이징의 한계, DTO 직접 조회(`new`), 벌크 연산 후 영속성 컨텍스트 불일치
7. **N+1** — 재현 → 페치 조인 / `@EntityGraph` / `default_batch_fetch_size` 각각으로 해결하고 SQL 비교
8. **Spring Data JPA** — 쿼리 메서드, `@Query`, `Pageable`/`Page`/`Slice`, `@Modifying(clearAutomatically = true)`
9. **QueryDSL** — 기본 조회·조인·서브쿼리, 동적 쿼리(`BooleanBuilder` vs `where` 다중 파라미터), `Projections` / `@QueryProjection`, 페이징 + count 쿼리 분리, 사용자 정의 리포지토리(`XxxRepositoryCustom` + `Impl`)
10. **OSIV와 트랜잭션 경계** — `open-in-view: false`에서 API 계층 설계(DTO 변환 위치)
