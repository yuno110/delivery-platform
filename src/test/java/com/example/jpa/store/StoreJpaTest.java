package com.example.jpa.store;

import com.example.jpa.common.Address;
import com.example.jpa.common.Money;
import com.example.jpa.config.JpaAuditingConfig;
import com.example.jpa.member.Owner;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.PersistenceUnitUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.*;

@Import(JpaAuditingConfig.class)
@DataJpaTest(showSql = false)
public class StoreJpaTest {

    @Autowired
    EntityManager em;

    private static final LocalDate CHRISTMAS = LocalDate.of(2026, 12, 25);
    private static final LocalDate NEW_YEAR = LocalDate.of(2027, 1, 1);



    @Test
    void 가게를_저장하면_Money_두_개가_각자의_컬럼에_들어간다() {
        Owner owner = Owner.of("owner@test.com", "1234", "김사장", "010-4332-3333", "123-45-6789");
        em.persist(owner); // 점주 생성

        Store store = Store.of(
                owner,
                "김밥천국 서초점",
                Address.of("07865", "서울 서초구 반포본동 ", "2층"),
                Money.of(12_000), Money.of(3_000),
                30
        );
        System.out.println("===== persist 전 =====");
        em.persist(store);
        System.out.println("===== persist 후 =====");
        em.flush();
        em.clear();

        Store found = em.find(Store.class, store.getId());

        assertThat(found.getMinOrderAmount()).isEqualTo(Money.of(12_000)); // Money는 equals 를 구현했다.
        assertThat(found.getDeliveryFee()).isEqualTo(Money.of(3_000));
        assertThat(found.getOperationStatus()).isEqualTo(StoreOperationStatus.OPERATING);
    }

    @Test
    void 가게를_persist하면_영업시간_7개와_카테고리_2개가_함께_INSERT된다() {
        Owner owner = Owner.of("owner@test.com", "1234", "김사장", "010-3333-1212", "123-45-67893");
        em.persist(owner);

        // 카테고리도 owner처럼 가게와 별개로 이미 있어야 한다
        Category food = Category.root("음식", 1);
        Category korean = food.addChild("한식", 1);
        Category snack = food.addChild("분식", 2);

        em.persist(food);
        food.assignPath();
        em.flush(); // 준비 데이터의 SQL을 여기서 다 내보내서, 아래 구간에는 가게 쪽 SQL만 보이게

        Store store = Store.of(
                owner,
                "김밥천국 강남점",
                Address.of("07865", "서울 강남구 역삼동 ", "2층"),
                Money.of(12_000),
                Money.of(3_000),
                30
        );
        for (DayOfWeek day: DayOfWeek.values()) { // MONDAY ~ SUNDAY
            store.addBusinessHour(day, LocalTime.of(10, 0), LocalTime.of(22, 0));
        }
        store.addCategory(snack, true);
        store.addCategory(korean, false);

        System.out.println("===== persist 전 =====");
        em.persist(store); // 가게 1 + 영업시간 7 + 가게카테고리 2  = INSERT 10개
        System.out.println("===== persist 후 =====");

        em.flush();
        em.clear();

        Store found = em.find(Store.class, store.getId());
        assertThat(found.getBusinessHours()).hasSize(7);
        assertThat(found.getCategories()).hasSize(2);
    }

    @Test
    void 영업시간을_전부_교체하면_새것은_INSERT_옛것은_DELETE된다() {
        Owner owner = Owner.of("owner@test.com", "1234", "김사장", "010-3333-4444", "123-45-67890");
        em.persist(owner);

        Store store = Store.of(owner, "김밥천국 강남점",
                Address.of("06236", "서울 강남구 테헤란로 1", "1층"),
                Money.of(12_000), Money.of(3_000), 30);
        for (DayOfWeek day : DayOfWeek.values()) {
            store.addBusinessHour(day, LocalTime.of(10, 0), LocalTime.of(22, 0));   // 매일 10~22시
        }
        em.persist(store);
        em.flush();
        em.clear();

        Store found = em.find(Store.class, store.getId());

        System.out.println("===== clear 전 =====");
        found.clearBusinessHours();   // 무엇을 지울지 알아야 하니 여기서 영업시간을 SELECT 한다
        System.out.println("===== clear 후 =====");

        // 새 영업시간: 월요일 정기휴무, 나머지는 브레이크타임(15~17시)이 있는 두 구간
        for (DayOfWeek day : DayOfWeek.values()) {
            if (day == DayOfWeek.MONDAY) {
                continue;   // 행이 없으면 휴무
            }
            found.addBusinessHour(day, LocalTime.of(11, 0), LocalTime.of(15, 0));
            found.addBusinessHour(day, LocalTime.of(17, 0), LocalTime.of(23, 0));
        }

        // Hibernate 는 flush 때 SQL 을 "코드를 호출한 순서"가 아니라 정해진 순서로 실행한다.
        //   INSERT → UPDATE → 컬렉션 변경 → DELETE
        // 그래서 clear() 를 먼저 불렀어도 새 영업시간 INSERT 12개가 먼저, 옛 영업시간 DELETE 7개가 나중에 나간다.
        // 주의: unique 제약(예: unique(store_id, day_of_week))이 있으면 옛 행이 남은 채로 새 행이 INSERT 돼 위반이 난다.
        //   그럴 땐 clear() 직후 em.flush() 를 한 번 호출해 DELETE 를 먼저 내보낸다.
        System.out.println("===== flush 전 =====");
        em.flush();
        System.out.println("===== flush 후 =====");
        em.clear();

        Long count = em.createQuery(
                        "select count(h) from StoreBusinessHour h where h.store.id = :id", Long.class
                )
                .setParameter("id", store.getId())
                .getSingleResult();
        assertThat(count).isEqualTo(12);   // 6일 × 2구간. 옛 7개는 사라졌다
    }

    @Test
    void 같은_카테고리를_두_번_연결하면_도메인에서_막는다() {
        // 준비 : 점주와 카테고리는 가게와 별개로 이미 저장돼 있어야 한다(cascade 없음)
        Owner owner = Owner.of("owner@test.com", "1234", "박사장", "010-1111-2222", "988-23-24521");
        em.persist(owner);

        Category american = Category.root("양식", 1);
        em.persist(american); // id 생김 -> [검사1] 통과 조건
        american.assignPath();

        Store store = Store.of(
                owner, "파스타 매니아",
                Address.of("23442", "서울 강남구 테헤란로 1", "1층"),
                Money.of(12_000), Money.of(3_000),
                30
        );
        store.addCategory(american, true); // 첫 연결 : 정상

        // 두 번째 연결 : [검사2] 에서 예외. 가게를 persist 하지도 않았으니 SQL이 나갈 일이 없다
        assertThatThrownBy(() -> store.addCategory(american, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 연결된 카테고리입니다."); // 메세지까지 정확히 일치해야 통과
    }

    @Test
    void 도메인을_우회해도_DB_unique_제약이_막는다() {
        Owner owner = Owner.of("owner@test.com", "1234", "박사장", "010-1111-2222", "988-23-24521");
        em.persist(owner);

        Category korean = Category.root("한식", 1);
        em.persist(korean);
        korean.assignPath();

        Store store = Store.of(
                owner, "김밥천국 강남점", Address.of("09876", "서울 강남구 테헤란로 1", "2층"),
                Money.of(12_000), Money.of(3_000), 30
        );
        store.addCategory(korean, true);
        em.persist(store); // store + store_category(한식) INSERT, DB에 (store_id, category_id) 행이 생김

        // addCategory를 거치지 않고 같은 조합을 직접 만든다.
        // StoreCategory 생성자는 package-private 이라 같은 패키지인 테스트에서는 호출할 수 있다.
        // 실무에서는 다른 코드 경로(배치, 네이티브 쿼리, 동시 요청)가 이런 식으로 도메인 검사를 건너뛴다.
        StoreCategory duplicate = new StoreCategory(store, korean, false);

        // IDENTITY -> persist 순간 INSERT -> DB 가 unique (store_id, category_id) 위반으로 거절
        assertThatThrownBy(() -> em.persist(duplicate))
                .isInstanceOf(PersistenceException.class) // JPA 표준 예외
                .hasRootCauseInstanceOf(SQLIntegrityConstraintViolationException.class); // 맨 아래는 DB 의 거절
    }

    @Test
    void 주소만_바꿔도_UPDATE_문에는_모든_컬럼이_들어간다() {
        Owner owner = Owner.of("owner@test.com", "1234", "김사장", "010-5555-6666", "111-22-33333");
        em.persist(owner);

        Store store = Store.of(owner, "김밥천국 역삼점",
                Address.of("06236", "서울 강남구 테헤란로 1", "1층"),
                Money.of(12_000), Money.of(3_000), 30);
        em.persist(store);
        em.flush();
        em.clear();   // 이후 SELECT 로 다시 읽어 와서 "스냅샷"을 새로 만든다

        Store found = em.find(Store.class, store.getId());

        // Hibernate는 앱이 시작할 때 엔티티마다 "모든 컬럼 UPDATE" SQL을 한 번만 만들어 두고 계속 재사용한다.
        // SQL 문자열이 항상 같아서 DB와  JDBC가 준비된 문장(PreparedStatement)을 계속 재사용할 수 있고, 여러 행을 한 번에 보내는 배치에도 유리하다.
        // 대신 바뀌지 않은 값까지 다시 보낸다
        //
        // Store 클래스에 @DynamicUpdate 를 붙이면 (실험 후 제거함)
        //   update store set detail=?, street=?, zip_code=?, updated_at=? where id=?
        // - 바뀐 필드만 UPDATE 에 넣는다. 대신 flush 때마다 SQL 을 새로 만들어서 위의 재사용 이점이 줄어든다.
        // - detail 만 바꿨는데 street, zip_code 도 나가는 이유: Hibernate 에게 address 는 필드 하나(임베디드 통째)다.
        //   안의 값 하나만 달라도 주소 컬럼 3개를 전부 보낸다.
        // - updated_at 은 Auditing 이 값을 바꾸므로 함께 나간다.
        // 보통은 기본값(모든 컬럼)을 쓴다. 컬럼이 아주 많은 테이블이거나,
        // 여러 요청이 같은 행의 서로 다른 컬럼을 동시에 고쳐서 덮어쓰기를 피하고 싶을 때만 고려한다.
        System.out.println("===== flush 전 =====");
        found.changeAddress(Address.of("06236", "서울 강남구 테헤란로 1", "3층"));   // detail 만 다르다
        em.flush();   // 스냅샷과 비교 → 바뀐 게 있으니 UPDATE
        System.out.println("===== flush 후 =====");
        em.clear();

        assertThat(em.find(Store.class, store.getId()).getAddress().getDetail()).isEqualTo("3층");
    }

    @Test
    void getReference_프록시로_SELECT_없이_FK를_채운다() {
        Owner owner = Owner.of("owner@test.com", "12334", "김윤호", "010-8501-4685", "123-45-97632");
        em.persist(owner);
        em.flush();
        em.clear(); // 영속성 컨텍스트를 비운다. 안 비우면 getReference 가 프록시 대신 진짜 owner를 돌려준다
        Long ownerId = owner.getId();

        // 프록시가 초기화됐는지(DB에서 값을 읽어 왔는지) 알려 주는 도구
        PersistenceUnitUtil util = em.getEntityManagerFactory().getPersistenceUnitUtil();

        System.out.println("===== getReference 전 =====");
        Owner ownerRef = em.getReference(Owner.class, ownerId); // SELECT 없음
        System.out.println("===== getReference 후 =====");

        Store store = Store.of(
                ownerRef,
                "김밥천국 선릉점",
                Address.of("06236", "서울 강남구 테헤란로 1", "3층"),
                Money.of(12_000), Money.of(3_000),
                30
        );
        em.persist(store); // INSERT 에 owner_id가 들어간다. 여기까지 member, store_owner 조회 없음
        em.flush();
        System.out.println("===== persist 후 =====");

        assertThat(util.isLoaded(ownerRef)).isFalse(); // 아직 값을 안 읽었다

        owner.getId(); // id 는 프록시가 이미 알고 있어서 SELECT 없음
        assertThat(util.isLoaded(ownerRef)).isFalse();

        System.out.println("===== getName 전 =====");
        assertThat(ownerRef.getName()).isEqualTo("김윤호"); // id 이외의 값 -> 이때 SELECT
        System.out.println("===== getName 후 =====");
    }

    @Test
    void 가게를_삭제하면_자식이_먼저_DELETE되고_가게는_마지막이다() {
        Owner owner = Owner.of("owner@test.com", "12334", "김윤호", "010-8501-4685", "123-45-97632");
        em.persist(owner);

        Category korean = Category.root("한식", 1);
        em.persist(korean);
        korean.assignPath();

        Store store = Store.of(
                owner,
                "김밥천국 삼성점",
                Address.of("09231", "서울 강남구 테헤란로 1", "3층"),
                Money.of(12_000), Money.of(3_000),
                30
        );
        store.addBusinessHour(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(22, 0));
        store.addBusinessHour(DayOfWeek.TUESDAY, LocalTime.of(10, 0), LocalTime.of(22, 0));
        store.addCategory(korean, true);
        em.persist(store);
        em.flush();
        em.clear();

        Store found = em.find(Store.class, store.getId());

        System.out.println("===== remove 전 =====");
        em.remove(found); // cascade 대상을 알아야 하니 자식 컬렉션을 여기서 SELECT 한다
        System.out.println("===== remove 후 =====");
        em.flush(); // 실제 DELETE 는 여기서 나간다.
        System.out.println("===== flush 후 =====");
        em.clear();

        assertThat(em.find(Store.class, store.getId())).isNull();

        Long hotCount = em.createQuery("select count(h) from StoreBusinessHour h", Long.class).getSingleResult();
        Long linkCount = em.createQuery("select count(c) from StoreCategory c", Long.class).getSingleResult();
        assertThat(hotCount).isZero();
        assertThat(linkCount).isZero();

        // 연결된 점주와 카테고리는 가게 소유가 아니라서 그대로 남는다 (Store -> Owner, StoreCategory -> Category 에는 cascade가 없다)
        assertThat(em.find(Owner.class, owner.getId())).isNotNull();
        assertThat(em.find(Category.class, korean.getId())).isNotNull();
    }

    // 휴무일 2개가 있는 가게를 저장하고, 영속성 컨텍스트를 비운 뒤 다시 읽어서 돌려준다
    private Store 휴무일_2개인_가게() {
        Owner owner = Owner.of("owner@test.com", "1234", "김사장", "010-1234-5678", "444-55-66666");
        em.persist(owner);

        Store store = Store.of(owner, "김밥천국 잠실점",
                Address.of("05551", "서울 송파구 올림픽로 1", "1층"),
                Money.of(12_000), Money.of(3_000), 30);
        store.addHoliday(CHRISTMAS, "성탄절");
        store.addHoliday(NEW_YEAR, "   ");   // 공백 사유 → null
        em.persist(store);
        em.flush();
        em.clear();
        return em.find(Store.class, store.getId());
    }

    @Test
    void 휴무일을_저장하고_같은_날짜는_도메인에서_막는다() {
        Store found = 휴무일_2개인_가게();

        assertThat(found.getHolidays()).hasSize(2);
        assertThat(found.getHolidays())
                .filteredOn(h -> h.getHolidayDate().equals(NEW_YEAR))
                .singleElement()
                .satisfies(h -> assertThat(h.getReason()).isNull());

        assertThatThrownBy(() -> found.addHoliday(CHRISTMAS, "중복"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 등록된 휴무일입니다.");
    }

    @Test
    void 휴무일을_목록에서_빼면_DELETE된다() {
        Store found = 휴무일_2개인_가게();

        System.out.println("===== 삭제 flush 전 =====");
        found.removeHoliday(CHRISTMAS);
        em.flush();
        System.out.println("===== 삭제 flush 후 =====");
        em.clear();

        assertThat(em.find(Store.class, found.getId()).getHolidays()).hasSize(1);
    }

    @Test
    void 같은_날짜를_지우고_바로_다시_넣으면_flush_순서_때문에_unique_위반이_난다() {
        Store found = 휴무일_2개인_가게();

        found.removeHoliday(CHRISTMAS);
        found.addHoliday(CHRISTMAS, "사유 변경");   // 메모리에서는 문제없다

        // flush 순서는 INSERT → DELETE. 옛 행이 남은 채로 INSERT 가 먼저 나가 DB 가 거절한다
        assertThatThrownBy(() -> em.flush())
                .isInstanceOf(PersistenceException.class)
                .hasRootCauseInstanceOf(SQLIntegrityConstraintViolationException.class);
    }

    @Test
    void 지운_뒤_flush하면_같은_날짜를_다시_넣을_수_있다() {
        Store found = 휴무일_2개인_가게();

        System.out.println("===== 교체 전 =====");
        found.removeHoliday(CHRISTMAS);
        em.flush();   // DELETE 를 먼저 내보낸다
        found.addHoliday(CHRISTMAS, "사유 변경");
        em.flush();
        System.out.println("===== 교체 후 =====");
        em.clear();

        assertThat(em.find(Store.class, found.getId()).getHolidays()).hasSize(2);
    }





}
