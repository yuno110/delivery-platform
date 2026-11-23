package com.example.jpa.member.dto;

import com.example.jpa.member.*;

// 회원 응답. 타입마다 필드가 달라서 record 3개를 하나의 타입으로 묶는다
// sealed: 이 인터페이스의 구현은 아래 3개뿐이라고 컴파일러에게 못 박는다 (다른 곳에서 implements 불가)
public sealed interface MemberResponse permits MemberResponse.CustomerResponse, MemberResponse.OwnerResponse, MemberResponse.RiderResponse {

    // type: JSON 만 보고도 어떤 회원인지 알 수 있게 (record 이름은 JSON 에 안 나온다)
    record CustomerResponse(
            String type, Long id, String name, String email, CustomerGrade grade
    ) implements MemberResponse {}

    record OwnerResponse(
            String type, Long id, String name, String email, String businessNumber
    ) implements MemberResponse {}

    record RiderResponse(
            String type, Long id, String name, String email, VehicleType vehicleType, RiderWorkStatus workStatus
    ) implements MemberResponse {}

    // Java 21 switch 패턴 매칭: instanceof + 캐스팅을 한 번에. 들어온 객체의 "실제 클래스"로 분기한다
    static MemberResponse from(Member member) {
        return switch (member) {
            case Customer c -> new CustomerResponse("CUSTOMER", c.getId(), c.getName(), c.getEmail(), c.getGrade());
            case Owner o -> new OwnerResponse("OWNER", o.getId(), o.getName(), o.getEmail(), o.getBusinessNumber());
            case Rider r -> new RiderResponse("RIDER", r.getId(), r.getName(), r.getEmail(), r.getVehicleType(), r.getWorkStatus());
            // Member 는 sealed 가 아니라서 컴파일러가 "3개가 전부"인지 모른다 → default 필수
            default -> throw new IllegalStateException("알 수 없는 회원 타입: " + member.getClass().getSimpleName());
        };
    }


}
