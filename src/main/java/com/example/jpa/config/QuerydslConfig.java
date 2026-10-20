package com.example.jpa.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuerydslConfig {

	// 주입되는 EntityManager 는 트랜잭션마다 실제 EntityManager 로 위임하는 프록시라
	// 싱글톤 빈으로 공유해도 동시성 문제가 없다.
	@Bean
	public JPAQueryFactory jpaQueryFactory(EntityManager em) {
		return new JPAQueryFactory(em);
	}

}
