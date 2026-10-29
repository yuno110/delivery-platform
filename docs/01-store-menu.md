# 1단계 — 회원 · 가게 · 메뉴

15 테이블. 순서대로 만들고, 각 스텝의 체크포인트를 확인한 뒤 다음으로 간다.

## ERD (텍스트)

```
member ──┬── customer ──< customer_address
         ├── owner ──< store ──< store_business_hour
         └── rider          ──< store_holiday
                            ──< store_category >── category (self: parent_id)
                            ──< menu_group ──< menu ──< menu_option_group >── option_group ──< option_item
                            ──< option_group            (option_group 은 store 소유, 여러 메뉴가 공유)
```

`──<` : 1:N,  `>──` : N:1

## 공통

### `BaseTimeEntity` (`@MappedSuperclass`, `@EntityListeners(AuditingEntityListener.class)`)

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| created_at | datetime(6) | `@CreatedDate`, `updatable = false` |
| updated_at | datetime(6) | `@LastModifiedDate` |

`@EnableJpaAuditing`은 별도 `@Configuration`에 둔다. `@DataJpaTest`는 `@Configuration`을 안 띄우므로 `QuerydslConfig`처럼 `@Import` 해야 한다.

### 값 타입 (`@Embeddable`)

| 클래스 | 필드 → 컬럼 | 비고 |
| --- | --- | --- |
| `Address` | zipCode → zip_code varchar(10) not null, street varchar(200) not null, detail varchar(100) | Customer 주소와 Store 주소에 재사용 |
| `Money` | amount bigint | 한 엔티티에 Money가 둘이면 `@AttributeOverride(name="amount", column=@Column(name="..."))`로 컬럼명 분리 |

`Money`는 `plus`, `minus`, `multiply`, `isGreaterThan` 정도의 메서드와 `equals/hashCode`를 가진 불변 객체로.

## 회원 — JOINED 상속

### `member` (루트, `abstract class Member extends BaseTimeEntity`)

`@Inheritance(strategy = JOINED)`, `@DiscriminatorColumn(name = "member_type")`

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | bigint PK | IDENTITY |
| member_type | varchar(31) | 구분자: CUSTOMER / OWNER / RIDER (`@DiscriminatorValue`) |
| email | varchar(100) | unique, not null |
| password | varchar(100) | not null (더미) |
| name | varchar(50) | not null |
| phone | varchar(20) | |
| status | enum | ACTIVE / SUSPENDED / WITHDRAWN |

### `customer`

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| member_id | bigint PK, FK → member.id | `@PrimaryKeyJoinColumn(name = "member_id")` — 안 쓰면 `id`가 된다 |
| grade | enum | BRONZE / SILVER / GOLD / VIP |

연관: `@OneToMany(mappedBy = "customer", cascade = ALL, orphanRemoval = true) List<CustomerAddress> addresses`

### `customer_address`

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | bigint PK | |
| customer_id | bigint FK, not null | `@ManyToOne(LAZY)` |
| alias | varchar(30) | 집 / 회사 |
| zip_code, street, detail | | `Address` 임베디드 |
| latitude | decimal(10,7) | 4단계 거리 정렬용 |
| longitude | decimal(10,7) | |
| is_default | bit | 고객당 정확히 하나 — DB로 강제 못 함, 도메인 메서드로 보장 |

### `owner`

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| member_id | bigint PK, FK | |
| business_number | varchar(12) | unique, not null |

Owner → Store 는 단방향(Store 쪽 `@ManyToOne`만). 점주의 가게 목록은 쿼리로 가져온다.

### `rider`

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| member_id | bigint PK, FK | |
| vehicle_type | enum | MOTORCYCLE / BICYCLE / CAR / WALK |
| work_status | enum | OFF_DUTY / AVAILABLE / DELIVERING — 부모의 `status`와 필드명 충돌 피함 |

## 가게

### `category` — 자기참조 트리

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | bigint PK | |
| parent_id | bigint FK → category.id, nullable | `@ManyToOne(LAZY) parent` (주인) |
| name | varchar(50) | not null |
| depth | int | 루트 0 |
| path | varchar(255) | materialized path `/1/4/13/` — 하위 트리 조회를 `path like '/1/4/%'`로. 재귀 없이 해결 |
| sort_order | int | |

연관: `@OneToMany(mappedBy = "parent") List<Category> children` + `@OrderBy("sortOrder")`.
IDENTITY라 `em.persist` 직후 id가 있으므로 그때 `path`를 세팅한다 → flush 시 UPDATE 한 번 더 나가는 것을 확인.
`@PrePersist`에서는 id가 없어 path를 못 만든다 — IDENTITY의 한계.

### `store`

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | bigint PK | |
| owner_id | bigint FK → owner.member_id, not null | `@ManyToOne(LAZY)` |
| name | varchar(100) | not null, index |
| phone | varchar(20) | |
| description | varchar(500) | |
| zip_code, street, detail | | `Address` |
| latitude, longitude | decimal(10,7) | |
| min_order_amount | bigint | `Money` ① |
| delivery_fee | bigint | `Money` ② — 둘이라서 `@AttributeOverride` 필요 |
| delivery_time_min | int | 예상 배달 시간(분) |
| operation_status | enum | OPERATING / PAUSED(점주 임시중지) / CLOSED(폐업) |

"지금 영업중" = `operation_status = OPERATING` AND 오늘 요일의 영업시간 안 AND 오늘이 휴무일 아님. 4단계에서 쿼리로.

연관 (모두 Store가 부모, cascade = ALL, orphanRemoval = true):
- `List<StoreCategory> categories`
- `List<StoreBusinessHour> businessHours`
- `List<StoreHoliday> holidays`
- `List<MenuGroup> menuGroups` + `@OrderBy("sortOrder")`

OptionGroup은 Store 소유지만 Store에서 컬렉션으로 들고 있지 않는다(쿼리로).

### `store_category` — N:M 중간 엔티티

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | bigint PK | |
| store_id | bigint FK, not null | |
| category_id | bigint FK, not null | `@ManyToOne(LAZY)` 단방향 (Category는 가게 목록을 모름) |
| is_primary | bit | 대표 카테고리 — 이 추가 컬럼이 `@ManyToMany` 대신 엔티티를 쓰는 이유 |

`@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"store_id", "category_id"}))`

### `store_business_hour`

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | bigint PK | |
| store_id | bigint FK, not null | |
| day_of_week | tinyint 또는 enum | `java.time.DayOfWeek`를 그대로 쓰면 Hibernate가 tinyint(1=월)로 매핑한다. `@Enumerated(STRING)`이 먹는지 DDL로 확인하고, 아니면 자체 enum |
| open_time | time(6) | `LocalTime` |
| close_time | time(6) | `close < open`이면 다음날 마감(새벽 영업). 영업중 판정에서 처리 |

같은 요일에 여러 행 허용 (브레이크타임 = 두 구간). 요일에 행이 없으면 정기휴무.

### `store_holiday`

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | bigint PK | |
| store_id | bigint FK, not null | |
| holiday_date | date | `LocalDate` |
| reason | varchar(100) | |

unique(store_id, holiday_date)

## 메뉴

### `menu_group`

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | bigint PK | |
| store_id | bigint FK, not null | |
| name | varchar(50) | 대표메뉴 / 사이드 / 음료 |
| sort_order | int | |

연관: `@OneToMany(mappedBy = "menuGroup", cascade = ALL) List<Menu> menus` + `@OrderBy("sortOrder")` — **orphanRemoval 없음**: 메뉴는 다른 그룹으로 옮겨 갈 수 있어서 "리스트에서 빠짐 = 삭제"가 아니다. 삭제는 `em.remove(menu)`(→ `@SoftDelete`)로 명시한다

### `menu` — `@SoftDelete`

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | bigint PK | |
| menu_group_id | bigint FK, not null | `@ManyToOne(LAZY)` |
| name | varchar(100) | not null |
| description | varchar(500) | |
| price | bigint | `Money` |
| sale_status | enum | ON_SALE / SOLD_OUT / HIDDEN |
| is_signature | bit | 대표메뉴 |
| sort_order | int | |
| image_url | varchar(500) | |
| deleted | bit | `@SoftDelete`가 자동 추가. 기본 컬럼명 `deleted` |

`org.hibernate.annotations.SoftDelete`. `em.remove(menu)`가 UPDATE로 바뀌고, 모든 조회에서 자동 제외된다.
Hibernate 7은 `strategy = SoftDeleteType.TIMESTAMP`(삭제 시각 저장)도 있다 — 비교해 볼 것.

연관: `@OneToMany(mappedBy = "menu", cascade = ALL, orphanRemoval = true) List<MenuOptionGroup> optionGroups` + `@OrderBy("sortOrder")`

### `option_group` — 가게 단위, 여러 메뉴가 공유

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | bigint PK | |
| store_id | bigint FK, not null | `@ManyToOne(LAZY)` 단방향 |
| name | varchar(50) | 맵기 선택 / 사이드 추가 |
| min_select | int | 0 = 선택 안 해도 됨 |
| max_select | int | 1 = 단일 선택 |

연관: `@OneToMany(mappedBy = "optionGroup", cascade = ALL, orphanRemoval = true) List<OptionItem> items` + `@OrderBy("sortOrder")`

### `option_item` — `@SoftDelete`

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | bigint PK | |
| option_group_id | bigint FK, not null | |
| name | varchar(50) | |
| extra_price | bigint | `Money` |
| sale_status | enum | ON_SALE / SOLD_OUT |
| sort_order | int | |
| deleted | bit | |

테이블명을 `option`으로 하면 MySQL 예약어에 걸린다.

### `menu_option_group` — Menu ↔ OptionGroup 연결

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | bigint PK | |
| menu_id | bigint FK, not null | `@ManyToOne(LAZY)` (주인, Menu 컬렉션의 mappedBy 대상) |
| option_group_id | bigint FK, not null | `@ManyToOne(LAZY)` 단방향 |
| sort_order | int | 메뉴마다 옵션그룹 노출 순서가 다름 — 추가 컬럼 |

unique(menu_id, option_group_id)

메뉴 하나의 옵션 전체 = `menu → optionGroups(List) → optionGroup → items(List)`. 3단 경로.
두 List를 한 쿼리로 페치 조인하면 `MultipleBagFetchException`이 난다 — 4단계에서 재현하고 해결.

## 구현 순서와 체크포인트

각 스텝: 엔티티 작성 → `./gradlew compileJava`(Q클래스) → 테스트 실행 → DDL과 SQL 로그 확인.

Step 1~3은 **매핑 + 등록/수정/삭제** 연습이다. 데이터는 테스트 안에서 직접 만들고(롤백), 양은 적다.
대량 데이터는 Step 4에서 넣고, 조회 연습은 Step 5부터 그 위에서 한다.
CRUD 체크포인트는 `EntityManager` 직접 호출과 Spring Data `Repository` 경유를 **둘 다** 해 보고 SQL 차이를 본다.

### Step 1 — common + member (BaseTimeEntity, Address, Money, Member, Customer, CustomerAddress, Owner, Rider)

- [x] DDL: 테이블 5개. `member.member_type` 존재, 자식 테이블 PK가 `member_id`이고 member.id를 참조
- [x] enum 컬럼이 MySQL native `enum('ACTIVE',...)`으로 생겼는지 (`SHOW CREATE TABLE member`)
- [x] `em.persist(customer)` → INSERT member, INSERT customer **2개가 persist 시점에** 나간다 (IDENTITY). flush 전에 로그 확인
- [x] `em.clear()` 후 `em.find(Member.class, id)` → 자식 테이블 3개를 LEFT OUTER JOIN 하는 SQL. 결과가 `instanceof Customer`
- [x] QueryDSL: `selectFrom(member).where(member.instanceOf(Customer.class))` vs `selectFrom(customer)` SQL 비교 (outer join vs inner join)
- [x] `customer.addAddress(...)` 두 번 + 기본 주소 교체 → 도메인 메서드로 is_default 하나만 유지되는지
- [x] Auditing: persist 후 `createdAt` 세팅, 이름 변경 + flush 후 `updatedAt`만 변경(dirty checking UPDATE SQL 확인)
- [x] email 중복 persist → 예외 타입 확인. `em` 직접 호출(`PersistenceException`)과 Repository 경유(`DataIntegrityViolationException`)가 다르다
- [x] (패스 — Claude가 대신 실행해 결과만 공유) 실험: `@Inheritance`를 `SINGLE_TABLE`로 바꿔 띄워 보고 DDL(nullable 컬럼 범람)과 조회 SQL 비교 → JOINED로 되돌림
- [x] Repository: `memberRepository.save(Customer.of(...))` → persist. `em.clear()` 후 준영속 객체를 다시 `save(detached)` → **merge** → SELECT 후 UPDATE 나가는 것 확인
- [x] 수정: 트랜잭션 밖에서(또는 clear 후) 바꾼 값은 저장되지 않는다. 다시 조회해서 바꾸거나 merge 해야 하는 것 확인
- [x] 삭제: `em.remove(customer)` → DELETE 순서 확인 — customer_address(cascade) → customer → member (JOINED는 자식 테이블 먼저)
- [x] (변경) `hello` 패키지는 삭제하지 않고 복습용으로 보존 — HelloTest 의 persist/flush/clear/동일성 주석

### Step 2 — category, store, store_category, store_business_hour, store_holiday

- [ ] 3단 카테고리 트리(음식 > 한식 > 찌개)를 루트에서 cascade로 persist. 각 노드 `path` 세팅. INSERT 뒤 UPDATE(path)가 따라오는 것 확인
- [ ] `path like '/1/4/%'`로 하위 트리 조회 — JOIN도 재귀도 없음
- [ ] Store + 영업시간 7개 + 카테고리 2개를 cascade로 persist → INSERT 개수 세기
- [ ] 영업시간 전체 교체: `businessHours.clear()` + add → DELETE 후 INSERT (orphanRemoval). orphanRemoval을 빼면 `store_id = null` UPDATE를 시도하다 not null에 걸리는 것 확인
- [ ] 같은 카테고리 두 번 연결 → unique 위반
- [ ] DDL에 `min_order_amount`, `delivery_fee` 두 컬럼 (`@AttributeOverride`)
- [ ] `StoreBusinessHour.dayOfWeek` 컬럼 타입 확인
- [ ] Bean Validation과 DDL의 경계 (`Address`에 `@NotBlank`를 남겨 둔 채로 확인한 뒤 제거)
  - `SHOW CREATE TABLE store` → `@NotBlank`가 `zip_code`에 `not null`을 만들었나? (예상: **아니오**. Hibernate는 `@NotNull`만 DDL에 반영한다)
  - `@NotBlank`를 `@NotNull`로 바꾸면 DDL이 달라지나? (예상: **예**)
  - `@Embedded`에 `@Valid` 없이 잘못된 `Address`를 저장하면 `@NotBlank`가 발동하나? (예상: **아니오** — 중첩 객체 검증은 `@Valid`가 필요)
  - 확인 후: 값 타입에서 `@NotBlank` 제거(생성자 검증과 메시지가 중복된다). Bean Validation은 4단계 요청 DTO에서 쓴다
- [ ] 수정: 가게 주소(`Address`)만 교체 → UPDATE 문에 **모든 컬럼**이 들어가는 것 확인 (Hibernate 기본). `@DynamicUpdate` 붙여서 바뀐 컬럼만 나가는 것과 비교 → 기본값으로 되돌림
- [ ] `em.getReference(Owner.class, id)`로 SELECT 없이 `Store.owner` 세팅해서 persist — 프록시로 FK만 채우는 패턴
- [ ] 삭제: `em.remove(store)` → 자식(hours, categories, holidays, menu_groups…)이 먼저 DELETE 되고 store가 마지막인지. cascade를 빼면 FK 위반
- [ ] 카테고리 이동(parent 변경): 하위 트리 전체의 `path`를 벌크 UPDATE(`set path = replace(path, :old, :new) where path like :old%`) → 벌크 연산 후 영속성 컨텍스트의 값이 옛것인 것 확인 → `clearAutomatically` / `em.clear()`

### Step 3 — menu_group, menu, option_group, option_item, menu_option_group

- [ ] Store → MenuGroup 3개 → Menu 10개, OptionGroup 3개(items 포함), 메뉴마다 옵션그룹 1~2개 연결. 전체 cascade persist INSERT 개수
- [ ] `@SoftDelete`: `em.remove(menu)` → **UPDATE** `deleted = true`. clear 후 `em.find` → null. `selectFrom(menu)` 결과에서 제외. 네이티브 `select count(*) from menu`로 행은 남아 있음
- [ ] 소프트 삭제된 메뉴가 `menuGroup.getMenus()` 컬렉션 로딩에서도 빠지는지
- [ ] `menu.getOptionGroups().get(0).getOptionGroup().getItems()` 지연 로딩 체인 — SQL 3번 나가는 것 확인 (4단계 N+1 소재)
- [ ] `@OrderBy`가 컬렉션 로딩 SQL의 `order by`로 나타나는지
- [ ] 수정: 메뉴 가격 변경(`Money` 교체) → dirty checking UPDATE. 품절 처리(`soldOut()`) 같은 의도 메서드로만 바꾼다
- [ ] 메뉴를 다른 그룹으로 이동: 주인(`menu.menuGroup`)만 바꾸면 양쪽 컬렉션이 어긋난다 → 연관관계 편의 메서드로 양쪽 정리. 같은 트랜잭션 안에서 `oldGroup.getMenus()`에 여전히 들어 있는 것 확인
- [ ] 실험: `menus`에 `orphanRemoval = true`를 잠깐 붙이고 같은 이동을 해 본다 → 옮긴 메뉴가 삭제(`deleted = true`)되는지, 예외가 나는지 관찰. JPA 스펙은 "고아가 된 엔티티를 다른 연관관계에 다시 붙이지 말라"고만 하고 결과를 보장하지 않는다. 확인 후 원복
- [ ] 메뉴에 연결된 OptionGroup 삭제 시도 → FK 위반(menu_option_group이 참조). 링크 먼저 제거(`menu.optionGroups.remove` + orphanRemoval → DELETE) 후 삭제
- [ ] `optionGroup.items.remove(item)` → orphanRemoval + `@SoftDelete` 조합 — DELETE가 아니라 UPDATE `deleted = true`로 나가는지 확인

### Step 4 — 스키마 확정 · 시드 (여기서부터 대량 데이터)

- [ ] `SHOW CREATE TABLE` 15개를 훑어 의도와 다른 것 정리 (varchar 길이, nullable, 인덱스, FK 이름)
- [ ] `ddl-auto: validate`로 변경, 기동 확인
- [ ] 시드 적재 (아래 규모)
- [ ] 적재 후 `@SpringBootTest`에서 count 쿼리로 규모 확인

| 테이블 | 건수 | 비고 |
| --- | --- | --- |
| category | ~40 | 3단, 루트 6개(한식/중식/일식/양식/분식/카페) |
| member (customer / owner / rider) | 10,000 / 300 / 200 | |
| customer_address | ~15,000 | 고객당 1~3개, 하나는 is_default |
| store | 300 | owner당 1개(일부 2개) |
| store_category | ~500 | 가게당 1~2개 |
| store_business_hour | ~1,800 | 가게당 6일, 일부 브레이크타임 2구간, 일부 새벽 마감 |
| store_holiday | ~300 | |
| menu_group | ~1,000 | 가게당 3~4개 |
| menu | ~5,000 | 그룹당 3~8개, 일부 SOLD_OUT/HIDDEN, 일부 deleted |
| option_group | ~1,200 | 가게당 3~5개 |
| option_item | ~5,000 | 그룹당 2~6개 |
| menu_option_group | ~8,000 | 메뉴당 1~3개 |

### Step 5 — 1단계 쿼리 연습 (시드 후, 상세는 그때)

- 가게별 메뉴 수 / 그룹별 평균 가격 (group by, having)
- 카테고리 서브트리에 속한 가게 (path like + join)
- 특정 시각 영업중인 가게 (business_hour 조건 + holiday not exists + 새벽 마감 처리)
- 메뉴명 키워드로 가게 찾기 (exists 서브쿼리 vs join distinct 비교)
- 가게 상세 DTO: 가게 → 메뉴그룹 → 메뉴 → 옵션그룹 → 옵션 계층을 쿼리 2~3번으로 조립
- N+1 재현 → 페치 조인 / `@EntityGraph` / `default_batch_fetch_size` 각각 비교
- `MultipleBagFetchException` 재현 → Set / 쿼리 분리 / batch size로 해결
- 다형 조회: `Member` 조회 결과 타입별 분기, `treat()` / `instanceOf`
