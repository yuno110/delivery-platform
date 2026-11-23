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
- 주제마다 **API로 겪기 → 고치기 → SQL 로그로 확인 → 결과를 이 문서에 기록**. 컨트롤러 → 서비스(`@Transactional(readOnly = true)`) → 리포지토리로 실제 요청을 보낸다
  - (변경, 3번부터) 테스트 고정은 하지 않는다. 혼자 하는 학습이라 회귀를 지킬 대상이 적고, 이해는 로그를 보고 따져 보는 데서 나온다. 1·2번의 `seedTest`(`StoreNPlusOneTest`, `StoreMenuFetchTest`)는 그대로 둔다
- 요청은 `http/*.http` 파일(IntelliJ HTTP Client)로 저장소에 남긴다
- OSIV 는 1번에서 다룬다: 켜진 채(현재 기본값 true) 먼저 겪고, `spring.jpa.open-in-view: false` 로 끈 뒤 `LazyInitializationException` 확인. 2단계부터는 끈 상태로 간다

- [x] 1. N+1 재현 → 페치 조인 → `@EntityGraph` → `default_batch_fetch_size` 각각 비교
  - 결과(가게 목록 300건: 점주 이름 + 메뉴그룹 이름, `GET /api/stores`): 지연 로딩만 586번(1 + 서로 다른 점주 285 + 메뉴그룹 300 — 같은 점주는 1차 캐시), 페치 조인 1번, `@EntityGraph` 1번(owner 는 optional=false 라 inner join, 컬렉션은 left join), batch size 100 은 7번(1 + 3 + 3). `query/StoreNPlusOneTest` 로 고정
  - 엔티티를 그대로 반환하면 양방향 순환으로 JSON 깊이 500 초과 → 응답은 이미 커밋돼 200 + 잘린 JSON(서버에만 WARN). 점주 password 도 노출 → 응답은 DTO, 변환은 서비스 안에서
  - Hibernate 6 는 컬렉션 페치 조인 시 루트 중복을 자동 제거(distinct 불필요, 쓰면 SQL 까지 전달). DB 는 여전히 1,049행을 보낸다
  - 규칙: `default_batch_fetch_size` 는 전역 안전망으로 켠다. to-one 은 페치 조인, 컬렉션은 batch(페이징·다중 컬렉션 가능). 컬렉션 페치 조인은 페이징 없고 컬렉션 하나일 때만
  - OSIV 끔(`open-in-view: false`): 트랜잭션 밖(컨트롤러)에서 지연 로딩하면 `LazyInitializationException`. 켜 두면 요청 끝까지 커넥션을 붙잡고 N+1 이 서비스 밖에서 몰래 생긴다
- [x] 2. `MultipleBagFetchException` 재현 → Set / 쿼리 분리 / batch size 로 해결
  - 결과(메뉴판 `GET /api/stores/{id}/menus`, 가게 → 메뉴그룹 → 메뉴): List 둘 동시 페치 조인은 500 + SQL 0번(SQL 변환 단계에서 막힘, Spring 이 `InvalidDataAccessApiUsageException` 으로 감쌈). 쿼리 분리 2번, 지연 로딩 + batch size 3번(가게 / 메뉴그룹 / 메뉴). 두 응답 동일. `query/StoreMenuFetchTest` 로 고정
  - bag = `@OrderColumn` 없는 List. 중복 허용 + 식별 기준이 없어 조인 행에서 원래 목록을 복원할 수 없다 → Hibernate 가 실행 전에 거부
  - 쿼리 분리: 두 번째 쿼리(`MenuGroupRepository.findWithMenusByStoreId`)의 반환값은 안 쓴다. 같은 트랜잭션 = 같은 영속성 컨텍스트라 이미 있는 MenuGroup(같은 id = 같은 객체)의 미초기화 `menus` 를 채운다. 트랜잭션 밖이면 안 채워짐
  - batch size 의 IN 은 항상 `?` 100개: 컨텍스트에 있는 미초기화 컬렉션(그룹 3~4개)만 채우고 나머지는 null(SQL 문 재사용)
  - Set 해결책은 채택하지 않음: 예외만 사라지고 나란한 컬렉션이면 행이 곱으로 늘어난다(카테시안 곱). 순서(`LinkedHashSet`)·equals/hashCode 부담, 조회 하나 때문에 엔티티 타입을 바꾸게 됨
- [x] 3. 가게 상세 조립: 가게 → 메뉴그룹 → 메뉴 → 옵션그룹 → 옵션 계층을 쿼리 2~3번으로
  - 결과(`GET /api/stores/{id}/detail`, 가게 14: 메뉴그룹 4 · 메뉴 19 · 옵션그룹 3): A 지연 로딩 + batch 6번(가게 / 메뉴그룹 / 메뉴 / 메뉴옵션그룹 / 옵션그룹 / 옵션), B 단계별 쿼리 분리 4번(`/detail/split`, bag 하나씩 페치 조인: 가게+메뉴그룹, 메뉴그룹+메뉴, 메뉴+메뉴옵션그룹+옵션그룹, 옵션그룹+옵션). 두 응답 동일. batch 를 끄면 A 31번(1 + 1 + 4 + 19 + 3 + 3)
  - 지연 로딩 SQL 시점: 컬렉션은 들여다볼 때(`stream()`/`size()`), 프록시(to-one)는 id 외의 값을 읽을 때(`getName()`). 컬렉션으로 들어온 엔티티는 모든 컬럼이 채워져 있어 `getName()` 에 SQL 없음
  - batch 는 컬렉션과 프록시를 모두 묶는다. 같은 종류의 미초기화 대상이 컨텍스트에 1개면 `= ?`, 2개 이상이면 `in (?×100)`. 가게 단건이라 `menu_group` 은 `store_id = ?`(5-1 목록에선 IN). batch 끔: SQL 수 = 대상 개수, batch 켬: SQL 수 ≈ 계층 수
  - 같은 옵션그룹을 19개 메뉴가 공유해도 1차 캐시로 객체는 3개 → batch 를 꺼도 option_group·option_item 은 3번씩
  - B 에서 메뉴 쿼리를 빼도 에러 없이 4번(batch 끔: 22번). 빈 `menuOptionGroups` 만 지연 로딩으로 메워지고, 옵션그룹은 옵션 쿼리가 먼저 올려 둔 진짜 객체가 연결된다(프록시 안 생김)
  - 실수: 리포지토리 쿼리를 복사한 채 반환 타입만 다르면 기동은 되고 호출 시 결과 타입 불일치로 500
  - 결론: 단건 상세는 A(batch 에 맡기기)로 충분. B 는 쿼리 수를 꼭 줄여야 할 때. 차이 2번에 비해 리포지토리 메서드 3개 + "반환값을 안 쓰는 줄"의 유지보수 비용
- [x] 4. 다형 조회: `Member` 조회 결과 타입별 분기, `treat()` / `instanceOf`
  - 결과(`GET /api/members/{id}`, 시드 id: 고객 15~10014 · 점주 10015~10314 · 라이더 10315~10514): `findById` → member + 자식 3개 left join, `member_type` 으로 클래스 결정 → `MemberResponse.from` 의 switch 패턴 매칭(sealed 응답 record 3개, Member 는 sealed 가 아니라 default 필수)
  - 프록시 함정(`/{id}/reference`): `getReferenceById` 는 SQL 없이 `Member$HibernateProxy` 를 만든다(타입을 모르니 부모를 상속). Customer 의 형제라 `case Customer` 에 안 걸려 default → 500, SQL 0번. 초기화 후에도 클래스는 그대로. 같은 컨텍스트에 프록시가 먼저 있으면 이후 `findById` 도 그 프록시를 돌려준다(같은 id = 같은 객체). 해결: `Hibernate.unproxy(m, Member.class)`(`/reference/unproxy`) / 페치 조인 / 연관관계를 자식 타입으로 선언(기본) / 오버라이드 메서드는 프록시여도 정상 동작
  - `getReferenceById` 실무 용도: 검증 없이 FK 만 채울 때(주문 생성 시 customer, store). 없는 id 는 INSERT 때 FK 위반이라 존재·상태 확인이 필요하면 `findById`
  - 타입 조건(도보 라이더 52명 / 라이더 200명): `treat(m as Rider).vehicleType`(C-1) 은 골격(member + left join 3개) 그대로 `where rider.vehicle_type = ?` — 다른 타입은 그 칸이 null 이라 빠진다. `type(m) = Rider`(C-2) 는 `member_type = 'RIDER'`, 여러 타입은 `in (...)`. `RiderRepository.findByVehicleType`(C-3) 은 `rider join member` 2개뿐(모든 rider 행엔 member 짝이 있어 inner, member 출발이면 짝 없는 행이 많아 left)
  - `@DiscriminatorColumn` 이 없으면 Hibernate 가 `case when customer.member_id is not null then 1 ... end` 로 구분값을 만들어 `type()` 도 그걸 비교(`= 3`, `in (1, 2)`). 컬럼이 있으면 단순 비교라 읽기 쉽고 인덱스 가능
  - `@RequestParam` enum: `valueOf` 로 변환, 대소문자 구분(`walk` → 400 `MethodArgumentTypeMismatchException`), 앞뒤 공백은 허용
  - 결론: 타입을 알면 자식 엔티티로 직접 조회. `type()`/`treat()` 는 여러 타입을 섞어 볼 때나 부모 타입 연관관계에서
- [x] 5. 가게별 메뉴 수 / 그룹별 평균 가격 (group by, having, `select new` DTO 프로젝션)
  - 결과(가게 300개 메뉴 수): A Java 로 세기(`/menu-counts/java`, findAll + `getMenuGroups().getMenus().size()`)는 SQL 16번(1 + 3 × (메뉴그룹 1 + 메뉴 4)), 엔티티 6,998개(가게 300 + 메뉴그룹 1,049 + 메뉴 5,649), 약 460ms. B `group by`(`/menu-counts`)는 SQL 1번, 엔티티 0개, 약 75ms. 결과 동일
  - batch size 는 "한 번에 최대 100개": 대상이 많으면 100개씩 나뉜다(가게 100개분 메뉴그룹 ≈ 350개 → menu SQL 4번). N+1 을 N/100 + 1 로 줄일 뿐 없애지 못한다
  - `having`(`?minMenuCount=30` → 2개 가게, 최대 31개): 실행 순서 FROM → WHERE(행) → GROUP BY → HAVING(묶음) → SELECT → ORDER BY. 집계 값 조건은 having 에만. `@SoftDelete` 의 `deleted = false` 가 where 에 자동으로 붙어 삭제 메뉴 112개는 집계에서 빠진다
  - C 그룹별 가격 통계(`/{id}/menu-groups/price-stats`): `count` → Long(항상 숫자라 DTO 는 long), `avg` → Double(JPA 표준), `min/max` → 필드 타입의 래퍼 Long(대상 0행이면 NULL). `from MenuGroup left join mg.menus` 로 쓰면 빈 그룹도 나오고 `deleted = false` 는 on 절로 간다(시드엔 빈 그룹 0개)
  - `select new` 는 패키지 포함 전체 이름, 생성자 인자 순서·타입이 맞아야 하고 틀리면 기동 시 에러(Spring Data 가 @Query 검증)
  - 엔티티 vs DTO 프로젝션: DTO 는 영속성 컨텍스트에 안 들어가 변경 감지·지연 로딩·같은 id = 같은 객체가 없다. 필요한 컬럼과 계산 값만 가져온다. `readOnly = true` 는 스냅샷·dirty check 만 줄일 뿐 전체 컬럼 조회·객체 생성·연관 SQL 은 그대로
  - 결론: 쓰기는 엔티티, 집계·무거운 목록은 DTO 프로젝션. 단점: 리포지토리가 응답 DTO 에 묶이고 컬렉션을 못 담는다(계층 DTO 조립은 4단계)
- [x] 6. 카테고리 서브트리에 속한 가게 (path like + join) + `category.path` 인덱스 `EXPLAIN` 전후 비교
  - 결과(한식 31 = `/31/`, 하위 카테고리 11개 · 전체 40개): A 조인(`/by-category/31/join`, `from StoreCategory`)은 133행 / 가게 118개 → 같은 트리의 카테고리 여러 개에 걸린 가게가 연결 수만큼 반복. B `exists`(`/by-category/31`, `from Store`)는 118행. `.http` 응답 핸들러(`> {% client.log(...) %}`, `>` 는 줄 맨 앞)로 행 수 / 고유 id 수를 셌다
  - 중복 원인: `from` 이 연결 테이블이면 결과 한 행 = 조건을 통과한 연결 한 건. `select new` DTO 는 행마다 값을 담을 뿐 합치지 않는다. 해결은 `distinct`(다 만든 뒤 제거) 또는 `exists`(가게 기준으로 "하나라도 있나?") — "~에 속한 X" 는 exists 가 의도를 그대로 드러낸다
  - `where sc.store = s` → `sc.store_id = s.id`: 바깥 가게와 서브쿼리를 잇는 상관 서브쿼리. 빠지면 모든 가게가 나온다
  - 묵시적 조인: `sc.category.path` 처럼 FK 너머의 id 아닌 값을 경로로 읽으면 Hibernate 가 inner join 을 숨겨 넣는다(`sc.category.id` 는 FK 컬럼이라 조인 없음). 명시적 `join sc.category c` 로 써도 SQL 은 같고 읽기 쉽다
  - EXPLAIN(`table = c` 줄): 인덱스 없음 `ALL` · key NULL · rows 40 · Using where(풀 스캔). `idx_category_path` 후 `range` · rows 11 · Using where; Using index(정렬된 B-tree 에서 `/31/` 구간만, path + PK(id) 만 필요해 커버링). `select *` 면 Using index condition(테이블 재방문). 앞에도 `%`(`'%/31/%'`)면 `index` · rows 40 — 인덱스 전체 스캔이라 효과 없음(key 이름만 보고 안심 금지, type·rows 를 같이 본다)
  - 조인 순서 c → sc(`ref`, FK 인덱스) → s(`eq_ref`, PK): 뒤쪽은 원래 인덱스를 타고, 출발점 c 의 읽기 방식이 차이를 만든다
  - 인덱스는 DB 콘솔에서 직접 생성 + `Category` 에 `@Table(indexes = @Index(...))` 기록. `validate` 는 인덱스를 만들지도 검사하지도 않음(`jpa_test` 의 `create` 에서만 생성)
  - 결론: materialized path 는 하위 트리 조회가 앞부분 고정 like 라 인덱스 range 로 끝나는 설계. 데이터 40행에선 체감 차이가 없으니 실행 시간보다 EXPLAIN 의 type / rows 로 판단

4단계로 넘긴 것:
- 메뉴명 키워드로 가게 찾기 (exists 서브쿼리 vs join distinct 비교)
- 특정 시각 영업중인 가게 (business_hour 조건 + holiday not exists + 새벽 마감 처리) — 시드 휴무일은 모두 2026-12 이므로 기준 날짜를 12월 중 하루(예: 2026-12-25)로 잡아야 휴무일 조건이 결과에 영향을 준다
