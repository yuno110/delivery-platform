package com.example.jpa.member;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RiderRepository extends JpaRepository<Rider, Long> {

    // C-3. 자식 엔티티로 직접. 메서드 이름으로 쿼리 생성(where vehicle_type = ?)
    List<Member> findByVehicleType(VehicleType vehicleType);

}
