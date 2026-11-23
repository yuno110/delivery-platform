package com.example.jpa.member;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MemberRepository extends JpaRepository<Member, Long> {

    // C-1. treat: "Member 로 보되 Rider 라고 치고" 자식 필드에 접근. 조건에 안 맞는 고객, 점주는 자연히 빠진다.
    @Query(
            """
            select m
            from Member m
            where treat(m as Rider).vehicleType = :vehicleType
            """
    )
    List<Member> findRidersByVehicleTypeWithTreat(@Param("vehicleType") VehicleType vehicleType);

    // C-2. type: 실제 타입으로 거른다. Rider 는 엔티티 이름(문자열 아님)
    @Query(
            """
            select m
            from Member m
            where type(m) = Rider
            """
    )
    List<Member> findAllRidersWithType();




}
