# 실습 로드맵 — 배달 플랫폼

목표: 복잡한 비즈니스 위에서 JPA/QueryDSL을 직접 써 보며 함정을 겪고 해결한다.
코드는 직접 작성하고, 각 단계의 체크포인트를 테스트와 SQL 로그로 확인한 뒤 넘어간다.

## 도메인 한 줄 요약

고객(Customer)이 가게(Store)의 메뉴(Menu)를 옵션(OptionItem)과 함께 주문(Order)하고,
쿠폰(Coupon)·결제(Payment)를 거쳐 라이더(Rider)가 배달(Delivery)한다.
플랫폼은 가게별 수수료를 떼고 일 단위로 정산(Settlement)한다.

## 단계

| 단계 | 범위 | 테이블 | 핵심 연습 |
| --- | --- | --- | --- |
| 1 | 회원 · 가게 · 메뉴 | 15 | 상속(JOINED), 자기참조 트리, N:M 중간 엔티티, 중첩 컬렉션, 소프트 삭제, 값 타입, Auditing |
| 2 | 주문 · 결제 · 쿠폰 · 배달 | ~12 | 스냅샷, 상태기계 + 이력, 1:1, 낙관적/비관적 락, 쿠폰 상속(SINGLE_TABLE), 예약어(`order`) |
| 3 | 리뷰 · 통계 · 정산 | ~6 | 집계, 복합키(`@EmbeddedId`), 유효기간 데이터, 벌크 연산, 비정규화 vs 계산 |
| 4 | 검색 · 리포트 | 0 | QueryDSL 동적 쿼리, 페이징 + count 최적화, 서브쿼리, 날짜 함수 템플릿, 계층 DTO 조립 |

## 공통 규칙

- 패키지: 도메인별 — `common`, `member`, `store`, `menu`, (2단계~) `order`, `coupon`, `payment`, `delivery`, `review`, `settlement`
- PK: `Long id`, `GenerationType.IDENTITY`. 복합키는 3단계 정산에서만 쓴다.
- 시각: `LocalDateTime`. `createdAt`/`updatedAt`은 `BaseTimeEntity` + Spring Data Auditing.
- 금액: KRW 정수. `Money` 값 타입(`long amount`).
- enum: 항상 `@Enumerated(EnumType.STRING)`. Hibernate 6.2+는 MySQL에서 native `ENUM(...)` 컬럼으로 만든다 — DDL에서 확인.
- 삭제: 마스터 데이터(메뉴·옵션)는 소프트 삭제(`@SoftDelete`), 나머지는 물리 삭제.
- 컬렉션: 기본 `List` + `@OrderBy`. List 둘을 동시에 페치 조인하면 `MultipleBagFetchException` — 겪은 뒤 해결한다.
- `@ManyToOne`, `@OneToOne`은 항상 `fetch = LAZY` 명시 (기본값이 EAGER인 것이 함정).
- 생성자: `@NoArgsConstructor(access = PROTECTED)` + 정적 팩토리(또는 생성자 `@Builder`). setter 금지, 상태 변경은 의도가 드러나는 메서드로.
- Lombok 금지 목록: 엔티티에 `@Setter`, `@ToString`(연관관계 순환·지연 로딩 유발), `@EqualsAndHashCode`, `@Data`.
- 양방향은 실제로 탐색이 필요할 때만. 연관관계의 주인은 항상 FK를 가진 N 쪽.
- 예약어 주의: `order` → 테이블명 `orders`, `option` → `option_item`. (`hibernate.auto_quote_keyword=true`로 자동 인용도 가능하지만 이름을 피하는 쪽이 낫다.)

## 스키마 · 데이터 운용

1. 엔티티 먼저 작성 → `ddl-auto: create`로 띄워 Hibernate가 만든 DDL을 본다 (`SHOW CREATE TABLE`). 의도와 다르면 매핑을 고친다.
2. 단계의 DDL이 확정되면 `ddl-auto: validate`로 바꾸고 시드 데이터를 넣는다. 테스트는 `@Transactional` 롤백이므로 데이터가 유지된다.
3. 다음 단계에서 테이블을 추가할 때는 `update`로 한 번 띄워 추가만 받고 다시 `validate`.
   `update`는 컬럼 삭제·타입 변경·제약 변경을 못 한다 — 그때는 ALTER를 직접 친다.
4. IDENTITY는 `hibernate.jdbc.batch_size`가 있어도 INSERT 배치가 안 된다. 대량 시드는 SQL(MySQL 8 재귀 CTE) 또는 `JdbcTemplate.batchUpdate`(+ `rewriteBatchedStatements=true`)로.
