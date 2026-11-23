# 2단계 — 주문 · 결제 · 쿠폰 · 배달

8 테이블. 1단계가 "어떻게 가져오느냐"(읽기)였다면 2단계는 **"어떻게 안전하게 바꾸느냐"**(쓰기 · 동시성)다.
장바구니는 넣지 않는다 — 구조가 주문과 같아(장바구니 → 항목 → 옵션) 새로 배우는 게 적다. 주문 요청 본문에 메뉴와 옵션을 직접 담는다.

## ERD (텍스트)

```
customer ──< orders >── store
               │
               ├──< order_item ──< order_item_option        (메뉴 · 옵션은 FK 없이 스냅샷 + id 값)
               ├──< order_status_history
               ├─── payment                                  (1:1, payment.order_id unique)
               ├─── delivery >── rider                       (1:1, delivery PK = order_id, @MapsId)
               └>── customer_coupon >── coupon               (coupon: SINGLE_TABLE 상속, 정액 / 정률)
customer ──< customer_coupon
```

`──<` : 1:N,  `>──` : N:1,  `───` : 1:1

## 패키지

`order`, `payment`, `coupon`, `delivery`. 요청 · 응답 DTO 는 각 패키지의 `dto`.

## 주문

### `orders` (엔티티 `Order`, `@Table(name = "orders")`) — `extends BaseTimeEntity`

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | bigint PK | |
| customer_id | bigint FK → customer.member_id, not null | `@ManyToOne(LAZY)` |
| store_id | bigint FK → store.id, not null | `@ManyToOne(LAZY)` |
| customer_coupon_id | bigint FK, nullable, unique | `@OneToOne(LAZY)` — 쿠폰 한 장은 주문 하나에만 (Step 4) |
| status | enum | PAYMENT_WAITING / PENDING / ACCEPTED / COOKED / DELIVERING / DELIVERED / REJECTED / CANCELED |
| zip_code, street, detail | | `Address` — 주문 시점 배달 주소 **복사** (고객이 주소를 바꿔도 주문은 그대로) |
| request_memo | varchar(200) | |
| menu_amount | bigint | `Money` ① 메뉴 합계 |
| delivery_fee | bigint | `Money` ② 주문 시점 가게 배달비 복사 |
| discount_amount | bigint | `Money` ③ 쿠폰 할인 |
| pay_amount | bigint | `Money` ④ = ① + ② − ③. 계산 값이지만 저장한다(결제 금액 대조 · 3단계 정산) |
| ordered_at | datetime(6) | 3단계 정산 · 4단계 기간 검색 기준. `created_at` 과 분리(시드에서 과거 날짜를 넣기 위해) |
| version | bigint | `@Version` (Step 3) |

연관 (Order 가 부모):
- `List<OrderItem> items` — cascade = ALL, orphanRemoval = true
- `List<OrderStatusHistory> histories` — cascade = PERSIST, `@OrderBy("changedAt")` (Step 3)

상태 흐름:
```
PAYMENT_WAITING ──결제 성공──> PENDING ──수락──> ACCEPTED ──조리완료──> COOKED ──픽업──> DELIVERING ──완료──> DELIVERED
      │                          ├──거절(점주)──> REJECTED   (환불)
      └──결제 실패 / 포기──> CANCELED  └──취소(고객, 수락 전만)──> CANCELED   (환불)
```
전이 규칙은 `Order` 의 의도 메서드(`accept()`, `reject(reason)`, `cancel(...)` …) 안에서만 검사한다. setter 없음.

### `order_item`

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | bigint PK | |
| order_id | bigint FK, not null | `@ManyToOne(LAZY)` |
| menu_id | bigint, not null | **연관관계 아님, FK 없음** — 그냥 `Long`. 이유는 Step 1 실험 |
| menu_name | varchar(100) | 스냅샷 |
| unit_price | bigint | `Money` ① 주문 시점 메뉴 가격 |
| option_price | bigint | `Money` ② 고른 옵션 추가금 합(1개 기준) |
| quantity | int | 1 이상 |
| line_amount | bigint | `Money` ③ = (① + ②) × quantity |

연관: `List<OrderItemOption> options` — cascade = ALL, orphanRemoval = true

### `order_item_option`

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | bigint PK | |
| order_item_id | bigint FK, not null | `@ManyToOne(LAZY)` |
| option_item_id | bigint, not null | FK 없음 — OptionItem 은 하드 삭제라 FK 가 있으면 옵션을 못 지운다(1단계 3-9 결정) |
| option_group_name | varchar(50) | 스냅샷 (맵기 선택) |
| option_item_name | varchar(50) | 스냅샷 (아주 매운맛) |
| extra_price | bigint | `Money` 스냅샷 |

### `order_status_history` — 쓰고 나면 바꾸지 않는다

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | bigint PK | |
| order_id | bigint FK, not null | `@ManyToOne(LAZY)` |
| from_status | enum, nullable | 첫 기록은 null |
| to_status | enum, not null | |
| changed_by | enum | CUSTOMER / OWNER / RIDER / SYSTEM |
| reason | varchar(200) | 거절 · 취소 사유 |
| changed_at | datetime(6) | `BaseTimeEntity` 를 상속하지 않는다(수정이 없으니 `updated_at` 불필요) |

`@Immutable` 로 UPDATE 를 막는 것도 실험해 본다.

## 결제

### `payment`

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | bigint PK | |
| order_id | bigint FK, not null, **unique** | `@OneToOne(LAZY)` — FK 를 가진 쪽이 주인 |
| method | enum | CARD / KAKAO_PAY / NAVER_PAY / TOSS_PAY |
| status | enum | READY / PAID / FAILED / CANCELED |
| amount | bigint | `Money` — 주문의 pay_amount 와 일치해야 한다 |
| pg_transaction_id | varchar(64) | 가짜 PG 가 돌려준 거래 번호, unique |
| fail_reason | varchar(200) | |
| paid_at, canceled_at | datetime(6) | |

PG 는 실제로 부르지 않는다. `FakePgClient`(요청 값으로 성공 / 실패를 정할 수 있게)를 둔다.
주문 하나에 결제 시도가 여러 번(실패 후 재시도)일 수 있다 → 1:1 이 맞는지도 Step 2 에서 따져 본다.

## 쿠폰

### `coupon` — SINGLE_TABLE 상속 (`abstract class Coupon extends BaseTimeEntity`)

`@Inheritance(strategy = SINGLE_TABLE)`, `@DiscriminatorColumn(name = "coupon_type")`

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | bigint PK | |
| coupon_type | varchar(31) | FIXED / RATE |
| store_id | bigint FK, nullable | null = 플랫폼 쿠폰(모든 가게) |
| name | varchar(100) | |
| min_order_amount | bigint | `Money` — 메뉴 합계 기준 |
| total_quantity | int | 선착순 수량 |
| issued_quantity | int | 발급된 수 — **동시성 실험 대상** |
| valid_from, valid_until | datetime(6) | |
| discount_amount | bigint, **nullable** | FIXED 전용 (`FixedAmountCoupon`) |
| discount_rate | int, **nullable** | RATE 전용 (`RateCoupon`), % |
| max_discount_amount | bigint, nullable | RATE 전용, 할인 상한 |

`Coupon.discount(Money menuAmount)` 를 추상 메서드로 두고 자식이 구현한다 — 1단계 5-4 의 프록시 `instanceof` 함정을 다형성으로 피한다.
자식 전용 컬럼은 DB 에서 not null 을 못 건다(다른 타입 행에선 null) → SINGLE_TABLE 의 대가. 1단계 JOINED 와 DDL 비교.

### `customer_coupon`

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | bigint PK | |
| customer_id | bigint FK, not null | `@ManyToOne(LAZY)` |
| coupon_id | bigint FK, not null | `@ManyToOne(LAZY)` |
| status | enum | ISSUED / USED / EXPIRED |
| issued_at, used_at | datetime(6) | |

unique(customer_id, coupon_id) — 같은 쿠폰 중복 발급 방지의 마지막 방어선.

## 배달

### `delivery` — 주문과 PK 공유

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| order_id | bigint **PK**, FK → orders.id | `@Id Long id` + `@MapsId @OneToOne(LAZY) Order order` — 자기 IDENTITY 없음 |
| rider_id | bigint FK, nullable | `@ManyToOne(LAZY)` — 배차 전엔 null |
| status | enum | WAITING / ASSIGNED / PICKED_UP / DELIVERED |
| rider_fee | bigint | `Money` — 라이더 배달료(3단계 정산) |
| assigned_at, picked_up_at, delivered_at | datetime(6) | |
| version | bigint | `@Version` (Step 5) |

`payment` 는 별도 PK + unique FK, `delivery` 는 PK 공유 — 1:1 의 두 방식을 둘 다 만들어 비교한다.

## 진행 방식

- 1단계 Step 5 와 같다: **API 로 겪기 → 고치기 → SQL 로그로 확인 → 같이 풀이 → 결과를 이 문서에 기록**. 테스트 고정은 하지 않는다. 요청은 `http/*.http`
- 스키마: 2단계 테이블을 만드는 동안 앱(`jpa`)은 `ddl-auto: update` — 1단계 테이블과 시드는 그대로 두고 새 테이블만 받는다. 매핑을 바꿨는데 `update` 가 못 따라가면(타입 · 제약 변경) 2단계 테이블만 drop 하고 재기동. Step 6 에서 `validate` 로 돌아간다
- 동시성 실험(Step 3 · 4 · 5)은 `.http` 로는 동시에 못 보낸다. 실습용 지연 파라미터로 두 요청을 손으로 겹치게 하거나, 요청 수십 개를 한 번에 보내는 스크립트를 그 스텝에서 준비한다
- 주문 · 결제 · 배달 API 에 인증은 없다. 누가 요청했는지는 요청 값(`customerId`, `ownerId`, `riderId`)으로 받는다

## 구현 순서와 체크포인트

### Step 1 — 주문 생성 (orders, order_item, order_item_option)

`POST /api/orders` — 본문: customerId, storeId, addressId, requestMemo, items[ menuId, quantity, optionItemIds[] ]

- [ ] DDL: `orders` 에 Money 4개(`@AttributeOverride` 4번), `order_item` · `order_item_option` 에 메뉴 · 옵션 FK 가 **없는** 것 확인
- [ ] 검증은 도메인에서: 가게 OPERATING, 메뉴가 그 가게 소속 · ON_SALE, 옵션이 그 메뉴에 연결된 옵션그룹 소속 · ON_SALE, 옵션그룹별 min / max 선택 수, 최소 주문 금액. 실패 → 400
- [ ] cascade INSERT 개수: orders 1 + order_item n + order_item_option m. IDENTITY 라 한 줄씩 나가는 것 확인
- [ ] 조회 수 줄이기: 메뉴 · 옵션을 한 번에 가져오는 쿼리(메뉴 n개를 `in`), customer 는 `getReferenceById`(FK 만 필요 — 1단계 5-4 에서 말한 실무 용도), 주소는 고객 소속 확인이 필요해 조회
- [ ] 스냅샷 확인: 주문 후 메뉴 가격 변경 · 메뉴 소프트 삭제 · 옵션 하드 삭제 → 주문 상세는 그대로
- [ ] 실험: `OrderItem.menu` 를 잠깐 `@ManyToOne Menu` 로 매핑 → `@SoftDelete` 엔티티로 가는 to-one 은 LAZY 가 안 되는 것(1단계 3-9), 삭제된 메뉴를 가리키면 어떻게 되는지 관찰 → `Long menuId` 로 되돌림
- [ ] 주소 스냅샷: 주문 후 고객 주소 변경 → 주문의 배달 주소는 그대로 (`Address` 값 타입 복사)

### Step 2 — 결제 (payment)

`POST /api/orders/{orderId}/payments` — 본문: method, (실습용) 실패 여부

- [ ] 결제 성공: payment PAID + 주문 PAYMENT_WAITING → PENDING. 금액 대조(요청 금액 ≠ pay_amount 면 거절)
- [ ] 1:1 매핑 DDL: `payment.order_id` 에 unique 가 생기는지
- [ ] 1:1 LAZY 함정: `Order` 쪽에 `@OneToOne(mappedBy = "order") Payment payment` 를 두면 주문을 조회할 때마다 payment SELECT 가 따라온다(주인이 아닌 쪽은 "없음 = null" 을 알려면 조회해야 해서 프록시를 못 만든다). 주문 목록에서 N+1 로 확인 → 단방향 + 쿼리로 해결
- [ ] 결제 실패 기록 남기기: 실패하면 예외 → 트랜잭션 롤백 → FAILED 기록도 사라짐. `REQUIRES_NEW` 로 분리 → 같은 클래스 안 호출이면 프록시를 안 거쳐 적용 안 되는 함정까지
- [ ] 재시도: 실패 후 다시 결제 → 1:1(unique) 이 막는다 → 결제 시도를 1:N 으로 볼지, 실패 행을 재사용할지 결정

### Step 3 — 주문 상태 · 이력 · 낙관적 락 (order_status_history)

`POST /api/orders/{id}/accept` · `/reject` · `/cook-complete` · `/cancel` 등

- [ ] 상태 전이 규칙: 허용되지 않은 전이(예: DELIVERED → CANCELED) → 409. 규칙은 `Order` 안에만
- [ ] 전이마다 이력 1행: `Order` 의 메서드가 이력까지 추가하고 cascade PERSIST 로 저장. 서비스가 이력을 따로 save 하지 않는다
- [ ] 취소 · 거절 → 결제 환불(payment CANCELED) 까지 한 트랜잭션
- [ ] 갱신 분실 재현: 점주 수락과 고객 취소가 동시에 → 둘 다 성공하고 나중 것이 덮어쓴다(이력은 둘 다 남는 모순)
- [ ] `@Version` 추가: `update ... where id = ? and version = ?` → 늦은 쪽 `ObjectOptimisticLockingFailureException` → 409 응답
- [ ] `@Immutable` 이력: 값을 바꿔도 UPDATE 가 안 나가는 것 확인
- [ ] 벌크 UPDATE(`update Order set status = ...`)는 version 을 안 올린다 → 낙관적 락을 우회하는 것 확인

### Step 4 — 쿠폰 (coupon, customer_coupon)

`POST /api/coupons/{id}/issue` (발급), 주문 생성에 `customerCouponId` 추가

- [ ] SINGLE_TABLE DDL: 테이블 1개, 자식 컬럼 nullable, `coupon_type`. 1단계 JOINED 와 조회 SQL 비교(조인 없음)
- [ ] 할인 계산은 다형성: `coupon.discount(menuAmount)`. `customerCoupon.getCoupon()` 이 프록시여도 오버라이드 메서드는 정상 동작(5-4 에서 말로만 본 것을 실제로)
- [ ] 주문에 적용: 유효기간 · 최소 주문 금액 · 가게 일치 · 상태 ISSUED 검사 → USED. 주문 취소 → ISSUED 로 복구
- [ ] 초과 발급 재현: 수량 100장에 동시 요청 200개 → `issued_quantity` 갱신 분실로 100장 넘게 발급
- [ ] 해결 세 가지 비교: 비관적 락(`@Lock(PESSIMISTIC_WRITE)` → `select ... for update`), 낙관적 락(`@Version` → 대부분 실패, 재시도 비용), 원자적 UPDATE(`set issued_quantity = issued_quantity + 1 where id = ? and issued_quantity < total_quantity` → 영향 행 수로 판단). 성공 수 · 시간 · SQL 비교
- [ ] 같은 고객 중복 발급: unique(customer_id, coupon_id) 위반 → 예외 변환

### Step 5 — 배달 (delivery)

결제 성공 시 delivery(WAITING) 생성, `POST /api/deliveries/{orderId}/assign` (라이더 수락) · `/pick-up` · `/complete`

- [ ] `@MapsId` DDL: `delivery` PK 가 `order_id` 이고 FK. INSERT 에 IDENTITY 가 없다(persist 시점이 아니라 flush 시점에 INSERT 되는 것 확인)
- [ ] 상태 연동: 배차 → 라이더 workStatus DELIVERING(`Rider.startDelivery()`), 픽업 → 주문 DELIVERING, 완료 → 주문 DELIVERED + 라이더 AVAILABLE. 엔티티 셋이 한 트랜잭션에서 바뀌는 UPDATE 순서
- [ ] 동시 수락: 라이더 둘이 같은 배달을 수락 → 둘 다 성공하는 것 재현 → `@Version` / 조건부 UPDATE(`where rider_id is null`) 비교
- [ ] 한 라이더가 두 배달을 동시에 수락 → 라이더 쪽 상태 검사도 경쟁. 어느 행을 잠가야 하는지

### Step 6 — 스키마 확정 · 시드

- [ ] `SHOW CREATE TABLE` 8개 점검. FK · UK 이름 규칙(1단계 Step 4 와 같게), 조회 패턴에 맞는 인덱스(`orders(customer_id, ordered_at)`, `orders(store_id, status)` …) 는 Step 7 EXPLAIN 으로 근거를 만든 뒤 추가
- [ ] `ddl-auto: validate` 복귀
- [ ] 시드: 최근 90일(2026-08-24 ~ 2026-11-22) 주문 약 3만 건. 상태 분포(대부분 DELIVERED, 일부 CANCELED · REJECTED), 쿠폰 사용 일부
- [ ] 적재 방식 비교: JPA + 도메인 메서드(1단계 방식, 60만 행 규모면 느리다) vs `JdbcTemplate.batchUpdate` + `rewriteBatchedStatements=true`. 시간 비교 후 선택

| 테이블 | 건수 | 비고 |
| --- | --- | --- |
| orders | ~30,000 | 고객 10,000명에 고르게, 90일 |
| order_item | ~75,000 | 주문당 1~4개 |
| order_item_option | ~100,000 | 항목당 0~3개 |
| order_status_history | ~150,000 | 주문당 상태 수만큼 |
| payment | ~30,000 | |
| delivery | ~27,000 | 거절 · 결제 전 취소는 없음 |
| coupon / customer_coupon | ~20 / ~15,000 | |

### Step 7 — 2단계 쿼리 연습

- [ ] 내 주문 목록(페이징): 주문 + 가게 이름 + 대표 메뉴. 컬렉션 페치 조인 + 페이징 → Hibernate 경고(메모리에서 페이징) 재현 → batch size / DTO 로 해결. `Page` 의 count 쿼리
- [ ] 주문 상세: 주문 + 항목 + 옵션 + 결제 + 배달 + 이력. 1:1 두 개와 List 두 단계가 섞인 조립(1단계 5-3 응용)
- [ ] 점주 접수 화면: 가게의 PENDING · ACCEPTED 주문을 오래된 순. `orders(store_id, status)` 인덱스 EXPLAIN 전후
- [ ] 라이더 배차 대기 목록: WAITING 배달 + 가게 주소. 동시에 여러 라이더가 보는 목록과 Step 5 경쟁의 연결

3단계로 넘길 것:
- 일별 · 가게별 매출 집계, 정산(수수료 · 라이더 배달료)
- 리뷰(배달 완료 주문만)
