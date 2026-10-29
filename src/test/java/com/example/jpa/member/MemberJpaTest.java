package com.example.jpa.member;

import com.example.jpa.common.Address;
import com.example.jpa.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(JpaAuditingConfig.class)
@DataJpaTest(showSql = false)
class MemberJpaTest {

    @Autowired
    EntityManager em;

    @Autowired
    MemberRepository memberRepository;

    @Test
    void 고객을_persist하면_INSERT_2개가_즉시_나간다() {
        Customer customer = Customer.of("test@test.com", "1234", "김윤호", "010-3453-3324");

        System.out.println("===== persist 전 =====");
        em.persist(customer); // 원래 JPA는 INSERT를 모아 뒀다가 flush 때 보내는데 IDENTITY는 DB에 INSERT해야 id를 알 수 있어서 persist 순간 바로 보낸다
        System.out.println("===== persist 후 =====");

        assertThat(customer.getId()).isNotNull();
        assertThat(customer.getGrade()).isEqualTo(CustomerGrade.BRONZE);
        assertThat(customer.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(customer.getCreatedAt()).isNotNull();
    }

    @Test
    void 부모_타입으로_조회하면_자식_테이블을_전부_조인한다() {
        Customer customer = Customer.of("hong@test.com", "1234", "홍길동", "010-323-4433");

        em.persist(customer);
        em.flush(); // 아직 db에 안 보낸 SQL이 있으면 보낸다
        em.clear(); // 1차 캐시를 비운다 -> 다음 find는 DB에서 조회한다

        System.out.println("===== find(Member) 전 =====");
        Member found = em.find(Member.class, customer.getId()); // 부모 타입으로 조회
        System.out.println("===== find(Member) 후 =====");

        assertThat(found).isInstanceOf(Customer.class);
        assertThat(found).isNotSameAs(customer);
        assertThat(((Customer) found).getGrade()).isEqualTo(CustomerGrade.BRONZE);
    }

    @Test
    void 자식_타입으로_조회하면_부모_테이블만_조인한다() {
        Customer customer = Customer.of("test@test.com", "1234", "김윤호", "010-3453-3324");
        em.persist(customer);
        em.flush();
        em.clear();

        System.out.println("===== find(Customer)wjs =====");
        Customer found = em.find(Customer.class, customer.getId());// 자식 타입으로 조회
        System.out.println("===== find(Customer) 후 =====");

        assertThat(found.getEmail()).isEqualTo("test@test.com"); // 부모(member) 컬럼도 채워짐
        assertThat(found.getGrade()).isEqualTo(CustomerGrade.BRONZE);
    }

    @Test
    void 주소를_두_개_추가하면_첫_주소만_기본이다() {
        Customer customer = Customer.of("add@test.com", "123", "나영수", "010-9097-0989");
        CustomerAddress home = CustomerAddress.of("집", Address.of("02394", "서울 관악구 봉천동", "403호"));
        CustomerAddress office = CustomerAddress.of("회사", Address.of("02396", "서울 강서구 가양동", "901호"));

        customer.addAddress(home);
        customer.addAddress(office);

        System.out.println("===== persist 전 =====");
        em.persist(customer); // 주소는 persist 안 했다. cascade가 대신 해 준다
        System.out.println("===== persist 후 =====");

        assertThat(home.getId()).isNotNull(); // IDENTITY라 INSERT가 이미 나갔다
        assertThat(home.isDefaultAddress()).isTrue(); // 첫 주소는 자동으로 기본
        assertThat(office.isDefaultAddress()).isFalse();
    }

    @Test
    void 기본_주소를_바꾸면_UPDATE_두_개가_나간다() {
        Customer customer = Customer.of("change@test.com", "123", "박찬호", "010-9097-0989");
        customer.addAddress(
                CustomerAddress.of("집", Address.of("09342", "서울 서초구 반포동", "234호"))
        );
        customer.addAddress(
                CustomerAddress.of("회사", Address.of("23233","서울 중구 세종대로", "4층"))
        );

        em.persist(customer);
        em.flush();
        em.clear();

        Customer found = em.find(Customer.class, customer.getId());

        System.out.println("===== getAddresses 전 =====");
        // 여기서 LAZY 리스트에 처음 손을 대므로 주소 SELECT가 나간다
        CustomerAddress office = found.getAddresses()
                .stream()
                .filter(a -> "회사".equals(a.getAlias()))
                .findFirst()
                .orElseThrow();
        System.out.println("===== getAddresses 후 =====");

        found.changeDefaultAddress(office); // SQL없음. 객체 값만 바뀐다

        System.out.println("===== flush 전 =====");
        em.flush(); // 변경 감지 -> UPDATE
        System.out.println("===== flush 후 =====");
        em.clear();

        List<CustomerAddress> defaults = em.createQuery(
                        "select a from CustomerAddress a where a.customer.id = :id and a.defaultAddress = true",
                        CustomerAddress.class
                )
                .setParameter("id", customer.getId())
                .getResultList();

        assertThat(defaults.size() == 1);
        assertThat(defaults.getFirst().getAlias()).isEqualTo("회사");
    }

    @Test
    void 값을_바꾸면_updatedAt만_바뀐다() {
        Customer customer = Customer.of("audit@test.com", "1234", "나윤선", "010-2344-3434");
        em.persist(customer);
        em.flush();
        em.clear();

        // DB에 저장된 값 기준으로 비교하려고 다시 읽는다
        Customer before = em.find(Customer.class, customer.getId());
        LocalDateTime beforeCreatedAt = before.getCreatedAt();
        LocalDateTime beforeUpdatedAt = before.getUpdatedAt();
        System.out.println("===== 변경 전 createdAt = "+ beforeCreatedAt + ", updatedAt = " + beforeUpdatedAt);

        before.changePhone("010-9999-3333");

        System.out.println("===== 플러시 전 =====");
        // 변경 감지 -> UPDATE 직전에 AuditingEntityListener 가 updatedAt 을 now()로 바꾼다
        // created_at이 SET에 없다. BaseTimeEntity의 @Column(updatable = false)덕분이다.
        // UPDATE는 member테이블에만 나간다.JOINED상속에서는 바뀐 컬럼이 있는 테이블만 UPDATE한다
        em.flush();
        System.out.println("===== 플러시 후 =====");
        em.clear();

        Customer after = em.find(Customer.class, customer.getId());
        LocalDateTime afterCreatedAt = after.getCreatedAt();
        LocalDateTime afterUpdatedAt = after.getUpdatedAt();
        System.out.println("===== 변경 후 createdAt = "+ afterCreatedAt + ", updatedAt = " + afterUpdatedAt);

        assertThat(afterCreatedAt).isEqualTo(beforeCreatedAt);
        assertThat(afterUpdatedAt).isAfter(beforeUpdatedAt);
        assertThat(after.getPhone()).isEqualTo("010-9999-3333");
    }

    @Test
    void em으로_이메일_중복_저장하면_PersistenceException() {
        em.persist(Customer.of("dup@test.com", "1234", "김윤호", "010-4567-5443"));

        // IDENTITY 라 persist 하는 순간 INSERT -> DB 가 unique 위반으로 거절
        assertThatThrownBy(() -> em.persist(Customer.of("dup@test.com", "1234", "배철수", "010-1232-5678")))
                .isInstanceOf(PersistenceException.class) // JPA표준 예외
                .hasRootCauseInstanceOf(SQLIntegrityConstraintViolationException.class); // 맨 밑은 JDBC 예외
    }

    @Test
    void Repository로_이메일_중복_저장하면_DataIntegrityViolationException() {
        memberRepository.save(Customer.of("dup@test.com", "1234", "배철수", "010-1232-5678"));
        assertThatThrownBy(() -> memberRepository.save(Customer.of("dup@test.com", "1234", "김윤호", "010-4567-5443")))
                .isInstanceOf(DataIntegrityViolationException.class) // 스프링 예외로 바뀌었다
                .hasRootCauseInstanceOf(SQLIntegrityConstraintViolationException.class);
    }

    @Test
    void save에_새_객체를_넘기면_persist된다() {
        Customer customer = Customer.of("save@test.com", "1234", "김윤호", "010-3456-9284");

        System.out.println("===== save(새 객체) 전 =====");
        Customer saved = memberRepository.save(customer);
        System.out.println("===== save(새 객체) 후 =====");

        assertThat(saved).isSameAs(customer); // persist는 넘긴 객체를 그대로 관리한다
        assertThat(em.contains(customer)).isTrue(); // 영속성 컨텍스트가 관리 중
    }

    // persist = "이 객체를 관리해라"
    // merge   = "이 객체의 값을 관리 중인 객체에 복사해라"
    //
    // save()는 @Id 가 null 이면 persist, 아니면 merge 를 호출한다 (id 가 Long 래퍼 타입이라 null 로 판단 가능)
    //
    // 왜 넘긴 객체를 그대로 등록하지 않고 새로 가져오나?
    //   영속성 컨텍스트에는 id 하나당 객체 하나만 둘 수 있다 (동일성 보장).
    //   이미 id=1 객체가 있을 수도 있으니, 항상 "영속 객체 하나"를 기준으로 삼고 넘어온 값을 거기에 복사한다.
    //   - 영속성 컨텍스트에 이미 있으면 → SELECT 없이 그 객체에 복사
    //   - 없으면                       → SELECT 로 가져와서 복사
    //
    // 실무: 수정은 save 가 아니라 "조회 → 변경". 영속 객체라 flush 때 변경 감지가 UPDATE 한다.
    //   Customer customer = memberRepository.findById(id).orElseThrow();
    //   customer.changePhone(newPhone);   // save 호출 불필요
    @Test
    void save에_준영속_객체를_넘기면_merge_된다() {
        // ① id 가 null → persist. customer 객체 "자체"가 영속성 컨텍스트에 들어간다
        //    영속성 컨텍스트: [id=1 → customer (스냅샷 phone=5555)]
        //    스냅샷 = 처음 들어왔을 때의 값 사본. 변경 감지는 flush 때 "현재 값 vs 스냅샷"을 비교한다
        Customer customer = memberRepository.save(Customer.of("save@test.com", "1234", "이민우", "010-2345-5555"));

        // ② flush 로 INSERT 반영, clear 로 영속성 컨텍스트를 비운다
        //    영속성 컨텍스트: [비어 있음]
        //    customer 변수는 살아 있고 id 도 있지만 아무도 지켜보지 않는다 = 준영속
        em.flush();
        em.clear(); // 이제 customer는 준영속(id는 있는데 관리 안됨)

        // ③ 자바 필드만 바뀐다. 비교할 스냅샷이 없으니 변경 감지 대상이 아니다
        //    여기서 em.flush() 를 해도 SQL 은 하나도 안 나간다 (11번에서 확인)
        customer.changePhone("010-1111-2222");

        // ④ id 가 있음 → merge. 안에서 일어나는 일 (개념상):
        //      merged = 영속성 컨텍스트에서 id=1 찾기       → 없다
        //      merged = DB 에서 SELECT                     → 새 객체 생성 (phone=5555), 스냅샷도 이때 찍힘
        //      merged.phone = customer.phone ... 전부 복사  → merged.phone=1111
        //      return merged                               → customer 가 아니라 merged 를 돌려준다
        //    영속성 컨텍스트: [id=1 → merged (phone=1111, 스냅샷 phone=5555)]
        //    customer 는 값만 빌려주고 여전히 준영속
        //
        //    SELECT 에 customer_address 까지 left join 되는 이유:
        //      addresses 에 cascade = ALL(MERGE 포함)이라 주소 컬렉션도 merge 하려고 DB 의 주소 목록을 함께 가져온다
        System.out.println("===== save(준영속) 전 =====");
        Customer merged = memberRepository.save(customer);
        System.out.println("===== save(준영속) 후 =====");

        assertThat(merged).isNotSameAs(customer); // merge는 "다른 객체"를 돌려준다
        assertThat(em.contains(customer)).isFalse(); // 넘긴 객체는 여전히 준영속
        assertThat(em.contains(merged)).isTrue(); // 돌려받은 객체가 영속
        // 주의: 이후 수정은 반드시 merged 로 해야 한다. customer.changeXxx() 는 반영되지 않는다

        // ⑤ 변경 감지: merged 의 현재 값(1111) vs 스냅샷(5555) → 다르다 → UPDATE member set ... phone=? ...
        System.out.println("===== flush 전 =====");
        em.flush();
        System.out.println("===== flush 후 =====");
    }

    @Test
    void 준영속_상태에서_바꾼_값은_저장되지_않는다() {
        Customer customer = memberRepository.save(Customer.of("detach@test.com", "1234", "홍길동", "010-1111-2222"));
        em.flush();
        em.clear();                              // 트랜잭션이 끝난 것과 같다. customer 는 준영속

        customer.changePhone("010-9999-8888");   // 자바 필드만 바뀐다

        System.out.println("===== 준영속 flush 전 =====");
        em.flush();                              // 관리 중인 엔티티가 없으니 비교할 게 없다 → SQL 없음
        System.out.println("===== 준영속 flush 후 =====");

        Customer found = em.find(Customer.class, customer.getId());
        assertThat(found.getPhone()).isEqualTo("010-1111-2222");     // DB는 그대로
        assertThat(customer.getPhone()).isEqualTo("010-9999-8888");  // 자바 객체만 바뀌어 있다
    }

    @Test
    void 다시_조회해서_바꾸면_변경_감지로_저장된다() {
        Customer customer = memberRepository.save(Customer.of("detach@test.com", "1234", "홍길동", "010-1111-2222"));
        em.flush();
        em.clear();

        // 새 트랜잭션에서 id로 다시 조회 → 영속 객체 + 스냅샷
        Member found = memberRepository.findById(customer.getId()).orElseThrow();
        found.changePhone("010-9999-8888");      // save 호출 없음

        System.out.println("===== 영속 flush 전 =====");
        em.flush();                              // 현재 값 vs 스냅샷 → 다르다 → UPDATE
        System.out.println("===== 영속 flush 후 =====");
        em.clear();

        assertThat(em.find(Customer.class, customer.getId()).getPhone()).isEqualTo("010-9999-8888");
    }

    @Test
    void 고객을_삭제_하면_주소_고객_회원_순서로_삭제된다() {
        Customer customer = Customer.of("remove@test.com", "123", "테스트맨", "010-1111-2222");
        customer.addAddress(CustomerAddress.of("집", Address.of("09868", "서울 강남구 테헤란로", "102호")));
        customer.addAddress(CustomerAddress.of("회사", Address.of("12234", "서울 중구 세종대로", "302호")));
        em.persist(customer);
        em.flush();
        em.clear();

        Customer found = em.find(Customer.class, customer.getId());// remove 는 영속 객체에만 쓸 수 있다

        System.out.println("===== 삭제 전 =====");
        em.remove(found); // cascade REMOVE가 지울 주소를 알아야 하니 LAZY 리스트를 이때 읽는다
        System.out.println("===== 삭제 후 =====");

        assertThat(em.contains(found)).isFalse();   // 삭제 예정 상태. 더 이상 관리 대상이 아니다

        System.out.println("===== flush 전 =====");
        em.flush();         // DELETE 는 이때 나간다
        System.out.println("===== flush 후 =====");
        em.clear();

        assertThat(em.find(Customer.class, customer.getId())).isNull();   // 없으면 null

        Long addressCount = em.createQuery(
                        "select count(a) from CustomerAddress a where a.customer.id = :id", Long.class)
                .setParameter("id", customer.getId())
                .getSingleResult();
        assertThat(addressCount).isZero();          // 주소도 같이 지워졌다
    }












}
