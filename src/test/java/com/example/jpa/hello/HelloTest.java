package com.example.jpa.hello;

import static com.example.jpa.hello.QHello.hello;
import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 환경 확인용 테스트: MySQL 연결, JPA 저장, QueryDSL Q클래스 생성·조회가 모두 되는지 본다.
 * 테스트의 @Transactional 은 끝나면 롤백하므로 DB 에 데이터가 남지 않는다.
 */
@SpringBootTest
@Transactional
class HelloTest {

	@Autowired
	EntityManager em;

	@Autowired
	JPAQueryFactory queryFactory;

	@Test
	void jpaAndQuerydsl() {
		Hello saved = new Hello("hello");
		// 엔티티를 영속성 컨텍스트(PC)에 등록 + 쓰기지연큐에 INSERT 예약, DB에 쿼리 안 보냄(ID전략이 IDENTITY면 예외적으로 즉시 INSERT한다)
		em.persist(saved);

		// PC에 쌓인 변경(ISNERT, UPDATE, DELETE 쿼리)을 DB로 전송.
		// 커밋이 아니다(아직 같은 트랜잭션 안이라 롤백하면 사라진다)
		// PC를 비우는것도 아니다(flush후에도 saved는 여전히 영속 상태이다)
		em.flush();

		// PC를 비움(모든 엔티티 detached). DB로 아무것도 안보냄
		em.clear();

		// commit : 내부적으로 flush + 트랜잭션 커밋

		Hello found = queryFactory
				.selectFrom(hello)
				.where(hello.name.eq("hello"))
				.fetchOne();

		assertThat(found).isNotNull();
		assertThat(found.getId()).isEqualTo(saved.getId());

		// JPA의 동일성 보장 때문에 PC가 살아있으면 QueryDSL이 DB를 조회해 행을 읽어도 "이 ID는 이미 1차캐시에 있다"고 판단해서 캐시의 기존 인스턴스를 그대로 반환한다.
		// PC의 객체와 DB로 조회한 객체가 다른 인스턴스라서 원래는 같은 객체가 아니지만 위의 이유로 JPA가 같은 객체로 판단한다.
		// 그러나 여기서는 em.clear() 후 다시 조회했으므로 영속성 컨텍스트에 있던 그 객체가 아니므로 같은 객체가 아니다.
		assertThat(found).isNotSameAs(saved);
	}

}
