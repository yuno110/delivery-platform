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

### `store_owner` (엔티티 `Owner`, `@Table(name = "store_owner")`)

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
- `List<StoreCategory> storeCategories`
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
| open_time | time(0) | `LocalTime` (분 단위면 충분해서 기본 정밀도 유지) |
| close_time | time(0) | `close < open`이면 다음날 마감(새벽 영업). 영업중 판정에서 처리 |

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

연관: `@OneToMany(mappedBy = "menu", cascade = ALL, orphanRemoval = true) List<MenuOptionGroup> menuOptionGroups` + `@OrderBy("sortOrder")`

### `option_group` — 가게 단위, 여러 메뉴가 공유

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | bigint PK | |
| store_id | bigint FK, not null | `@ManyToOne(LAZY)` 단방향 |
| name | varchar(50) | 맵기 선택 / 사이드 추가 |
| min_select | int | 0 = 선택 안 해도 됨 |
| max_select | int | 1 = 단일 선택 |

연관: `@OneToMany(mappedBy = "optionGroup", cascade = ALL, orphanRemoval = true) List<OptionItem> items` + `@OrderBy("sortOrder")`

### `option_item` — 하드 삭제 (Step 3-9 에서 `@SoftDelete` 제거: 주문 이력은 Step 2 스냅샷으로 보존, FK 가 연결된 옵션 그룹 삭제를 막게 한다)

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | bigint PK | |
| option_group_id | bigint FK, not null | |
| name | varchar(50) | |
| extra_price | bigint | `Money` |
| sale_status | enum | ON_SALE / SOLD_OUT |
| sort_order | int | |

테이블명을 `option`으로 하면 MySQL 예약어에 걸린다.

### `menu_option_group` — Menu ↔ OptionGroup 연결

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | bigint PK | |
| menu_id | bigint FK, not null | `@ManyToOne(LAZY)` (주인, Menu 컬렉션의 mappedBy 대상) |
| option_group_id | bigint FK, not null | `@ManyToOne(LAZY)` 단방향 |
| sort_order | int | 메뉴마다 옵션그룹 노출 순서가 다름 — 추가 컬럼 |

unique(menu_id, option_group_id)

메뉴 하나의 옵션 전체 = `menu → menuOptionGroups(List) → optionGroup → items(List)`. 3단 경로.
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

- [x] 3단 카테고리 트리(음식 > 한식 > 찌개)를 루트에서 cascade로 persist. 각 노드 `path` 세팅. INSERT 뒤 UPDATE(path)가 따라오는 것 확인
- [x] `path like '/1/4/%'`로 하위 트리 조회 — JOIN도 재귀도 없음
- [x] Store + 영업시간 7개 + 카테고리 2개를 cascade로 persist → INSERT 개수 세기
- [x] 영업시간 전체 교체: `businessHours.clear()` + add → **INSERT 먼저, DELETE 나중** (flush 순서 고정: INSERT → UPDATE → DELETE). orphanRemoval을 빼면 에러 없이 옛 행이 남아 쌓인다(7 + 12 = 19). `store_id = null` UPDATE는 mappedBy 없는 단방향 @OneToMany의 동작
- [x] 같은 카테고리 두 번 연결 → 도메인(`addCategory` id 비교)에서 먼저 막고, 우회하면 DB `uk_store_category`가 막는다 (`Duplicate entry` → `PersistenceException`, root cause `SQLIntegrityConstraintViolationException`)
- [x] DDL에 `min_order_amount`, `delivery_fee` 두 컬럼 (`@AttributeOverride`)
- [x] `StoreBusinessHour.dayOfWeek` 컬럼 타입 확인 — `@Enumerated` 없으면 tinyint ORDINAL(0=월), STRING이면 MySQL enum
- [x] (패스) Bean Validation과 DDL의 경계 — not null 은 `@Column(nullable = false)` 로 통일, 값 검증은 생성자/정적 팩토리, `@NotBlank` 등은 4단계 요청 DTO 에서 사용. (참고: `@NotNull` 은 엔티티 직속 필드만 DDL 에 반영되고 임베디드 안은 안 됨. 임베디드 검증엔 `@Valid` 필요)
- [x] 수정: 가게 주소(`Address`)만 교체 → UPDATE 문에 **모든 컬럼**이 들어가는 것 확인 (Hibernate 기본). `@DynamicUpdate` 붙여서 바뀐 컬럼만 나가는 것과 비교(임베디드는 통째로 하나의 필드라 주소 컬럼 3개 모두 나감) → 기본값으로 되돌림
- [x] `em.getReference(Owner.class, id)`로 SELECT 없이 `Store.owner` 세팅해서 persist — 프록시로 FK만 채우는 패턴 (id 외의 값을 읽는 순간 SELECT. 없는 id면 persist 때 FK 위반, 값을 읽을 때 `EntityNotFoundException`)
- [x] 삭제: `em.remove(store)` → 자식(hours, storeCategories)이 먼저 DELETE 되고 store가 마지막인지. cascade를 빼면 FK 위반 → 결정(Step 3 후): **가게는 물리 삭제하지 않는다.** menu_groups 가 있으면 menu(@SoftDelete) 행이 남아 menu_group DELETE 가 FK 위반, option_group 은 Store 에서 cascade 가 없어 FK 위반. 폐업은 `store.close()` → operation_status = CLOSED (pause/resume 포함). 이 항목의 em.remove 실험은 자식이 하드 삭제 대상뿐인 경우로 한정
- [x] 카테고리 이동(parent 변경): 하위 트리 전체의 `path`를 벌크 UPDATE(`set path = replace(path, :old, :new) where path like :old%`) → 벌크 연산 후 영속성 컨텍스트의 값이 옛것인 것 확인 → `clearAutomatically` / `em.clear()`

### Step 3 — menu_group, menu, option_group, option_item, menu_option_group

- [x] Store → MenuGroup 3개 → Menu 10개, OptionGroup 3개(items 포함), 메뉴마다 옵션그룹 1~2개 연결. 전체 cascade persist INSERT 개수 → **39개** (store 1·menu_group 3·menu 10 / option_group 3·option_item 8 / menu_option_group 14). 부모 → 자식 순: `menu_group → menu` 그룹 단위로 번갈아, `option_group → option_item` 도 그룹 단위, 링크는 맨 마지막
- [x] `@SoftDelete`: `em.remove(menu)` → **UPDATE** `deleted = true`. clear 후 `em.find` → null. `selectFrom(menu)` 결과에서 제외. 네이티브 `select count(*) from menu`로 행은 남아 있음 → 확인: remove 시 `update menu set deleted=true where id=? and deleted=false`. 앞에 `menu_option_group` SELECT 1번(cascade REMOVE 전파 대상 확인용). 네이티브 count 2, deleted=true
- [x] 소프트 삭제된 메뉴가 `menuGroup.getMenus()` 컬렉션 로딩에서도 빠지는지 → 확인: 컬렉션 로딩 SQL에도 `and m1_0.deleted=false` + `order by m1_0.sort_order`
- [x] `menu.getMenuOptionGroups().get(0).getOptionGroup().getItems()` 지연 로딩 체인 — SQL 3번 나가는 것 확인 (4단계 N+1 소재) → 확인: find 후 `menu_option_group` → (getOptionGroup 은 프록시라 SQL 없음) → `option_group`(getItems 호출 시 프록시 초기화) → `option_item`(순회 시) 3번
- [x] `@OrderBy`가 컬렉션 로딩 SQL의 `order by`로 나타나는지 (`from menu_group ... order by mg1_0.sort_order`, `from menu ... and m1_0.deleted=false order by m1_0.sort_order`)
- [x] 수정: 메뉴 가격 변경(`Money` 교체) → dirty checking UPDATE. 품절 처리(`soldOut()`) 같은 의도 메서드로만 바꾼다 → 확인: changePrice 시 전체 컬럼 UPDATE(`where id=?`, deleted 조건 없음). 같은 값 Money 로 교체하면 UPDATE 없음(임베디드는 값 비교). soldOut/resumeSale 상태 규칙 예외
- [x] 메뉴를 다른 그룹으로 이동: 주인(`menu.menuGroup`)만 바꾸면 양쪽 컬렉션이 어긋난다 → 연관관계 편의 메서드로 양쪽 정리. 같은 트랜잭션 안에서 `oldGroup.getMenus()`에 여전히 들어 있는 것 확인 → 확인: 주인만 바꾸면 flush 후에도 main.menus=[김밥,라면], side.menus=[만두] (DB 는 정상). `menu.moveTo()` 가 removeMenu → menuGroup 변경 → attachMenu 로 양쪽 정리. 컬렉션 미로딩 상태면 moveTo 안에서 menu_group·menu SELECT 2번(add 는 SELECT 없음)
- [x] 실험: `menus`에 `orphanRemoval = true`를 잠깐 붙이고 같은 이동을 해 본다 → 옮긴 메뉴가 삭제(`deleted = true`)되는지, 예외가 나는지 관찰. JPA 스펙은 "고아가 된 엔티티를 다른 연관관계에 다시 붙이지 말라"고만 하고 결과를 보장하지 않는다. 확인 후 원복 → 확인: (A) moveTo 정상이면 이동은 되지만 고아 삭제 예약 → side 쪽 cascade PERSIST 가 취소, 흔적으로 `menu_option_group` SELECT 1번. (B) attachMenu 누락 시 `update menu set deleted=true` 로 삭제됨. (C) em.find 순서를 side → main 으로 바꾸면 정상 moveTo 여도 삭제됨(flush 의 cascade 가 엔티티 로딩 순서대로 돈다). 결론: 옮겨 다니는 자식에는 orphanRemoval 금지. 원복 완료
- [x] 메뉴에 연결된 OptionGroup 삭제 시도 → FK 위반(menu_option_group이 참조). 링크 먼저 제거(`menu.removeOptionGroup` + orphanRemoval → DELETE) → **flush** → 삭제. (설계 변경: OptionItem 이 `@SoftDelete` 면 남은 항목 행의 FK 때문에 연결이 없어도 삭제 불가, OptionGroup 까지 `@SoftDelete` 로 하면 MenuOptionGroup→OptionGroup LAZY 가 사라져 연결 로딩 때마다 option_group SELECT. → OptionItem 을 하드 삭제로 변경) → 확인: 연결된 채 삭제는 menu_option_group FK 위반. 연결 없으면 delete option_item × n → delete option_group. 끊기 → flush → remove 순서로 성공. flush 를 빼면 연결 행에 option_group_id=null UPDATE 가 DELETE 보다 먼저 나가 NOT NULL 위반
- [x] `optionGroup.items.remove(item)` → orphanRemoval 로 `delete from option_item` 이 나가는지 확인 (OptionItem 하드 삭제로 변경되어 원래 계획의 "@SoftDelete → UPDATE" 는 2번 메뉴 삭제에서 이미 확인) → 확인: `optionGroup.removeItem(item)` 후 flush 에 `delete from option_item where id=?` 한 줄. em.remove 없이 orphanRemoval 이 삭제

### Step 4 — 스키마 확정 · 시드 (여기서부터 대량 데이터)

- [x] `SHOW CREATE TABLE` 15개를 훑어 의도와 다른 것 정리 (varchar 길이, nullable, 인덱스, FK 이름) → 확인: 명세와 일치. 정리: FK 16개·UK 5개 이름 지정(`fk_{테이블}_{참조 테이블}`, `uk_{테이블}_{컬럼}`, unique 는 `@Table(uniqueConstraints)` 로 이동). category.path 인덱스는 Step 5 에서 EXPLAIN 으로 전후 비교. business_hour 는 time(0) 유지, 문서 정정(store_owner, option_item deleted 제거)
- [x] `ddl-auto: validate`로 변경, 기동 확인 → 확인: 앱(jpa)은 validate 로 기동(DDL 0건). 테스트는 jpa_test 로 분리 — src/test/resources/application-test.yml(createDatabaseIfNotExist, ddl-auto: create) + build.gradle test 태스크에서 프로파일 local,test. validate 는 테이블·컬럼 존재와 타입만 본다(길이·nullable·제약은 안 봄)
- [x] 시드 적재 (아래 규모) → `seed/SeedRunner`(seed 프로파일, JPA + 도메인 메서드, 청크마다 flush·clear, Random(42) 고정). 실행: `./gradlew bootRun --args='--spring.profiles.active=local,seed'` 약 17초. 결과: category 40, member 10,500(고객 10,000·점주 300·라이더 200), customer_address 19,987, store 300, store_category 483, business_hour 2,358, holiday 229, menu_group 1,049, menu 5,649(+삭제 112), option_group 1,197, option_item 4,587, menu_option_group 8,410
- [x] 적재 후 `@SpringBootTest`에서 count 쿼리로 규모 확인 → (변경) 테스트는 jpa_test 를 쓰므로 jpa 시드를 볼 수 없다. SeedRunner.printCounts() 가 적재 직후 엔티티별 count 를 로그로 남기는 것으로 대신함

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

### Step 5 — 1단계 쿼리 연습: "어떻게 가져오느냐"

범위: 지연 로딩·N+1·페치 조인·컬렉션 페치의 한계. "무엇을 조건으로 찾느냐"(동적 조건, 페이징, 리포트)는 4단계.

진행 방식:
- 시드가 있는 `jpa` 스키마에서 돌린다. 쿼리 연습 테스트는 `@Tag("seed")` + 별도 Gradle 태스크 `seedTest`(프로파일 `local`, validate, 롤백). 기본 `test` 는 이 태그를 제외
- SQL 개수는 Hibernate `Statistics`(`getPrepareStatementCount()`)로 숫자를 검증한다 (`seedTest` 에서만 `generate_statistics`)
- JPQL·Spring Data(`@Query`, `@EntityGraph`)로 개념을 잡고, 같은 쿼리를 QueryDSL 로 한 번씩 옮겨 본다
- 주제마다 **API로 겪기 → 고치기 → 테스트로 고정**. 1·2·3번은 컨트롤러 → 서비스(`@Transactional(readOnly = true)`) → 리포지토리로 실제 요청을 보내 SQL 로그를 보고, 고친 뒤 `seedTest` 에서 SQL 개수를 검증한다. 4·5·6번은 테스트만
- 요청은 `http/*.http` 파일(IntelliJ HTTP Client)로 저장소에 남긴다
- OSIV 는 1번에서 다룬다: 켜진 채(현재 기본값 true) 먼저 겪고, `spring.jpa.open-in-view: false` 로 끈 뒤 `LazyInitializationException` 확인. 2단계부터는 끈 상태로 간다

- [ ] 1. N+1 재현 → 페치 조인 → `@EntityGraph` → `default_batch_fetch_size` 각각 비교
- [ ] 2. `MultipleBagFetchException` 재현 → Set / 쿼리 분리 / batch size 로 해결
- [ ] 3. 가게 상세 조립: 가게 → 메뉴그룹 → 메뉴 → 옵션그룹 → 옵션 계층을 쿼리 2~3번으로
- [ ] 4. 다형 조회: `Member` 조회 결과 타입별 분기, `treat()` / `instanceOf`
- [ ] 5. 가게별 메뉴 수 / 그룹별 평균 가격 (group by, having, `select new` DTO 프로젝션)
- [ ] 6. 카테고리 서브트리에 속한 가게 (path like + join) + `category.path` 인덱스 `EXPLAIN` 전후 비교

4단계로 넘긴 것:
- 메뉴명 키워드로 가게 찾기 (exists 서브쿼리 vs join distinct 비교)
- 특정 시각 영업중인 가게 (business_hour 조건 + holiday not exists + 새벽 마감 처리) — 시드 휴무일은 모두 2026-12 이므로 기준 날짜를 12월 중 하루(예: 2026-12-25)로 잡아야 휴무일 조건이 결과에 영향을 준다
