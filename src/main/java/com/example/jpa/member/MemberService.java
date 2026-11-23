package com.example.jpa.member;

import com.example.jpa.member.dto.MemberResponse;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final RiderRepository riderRepository;

    // A.부모 타입(Member)으로 조회. 결과 객체는 실제 자식 클래스(Customer, Owner, Rider) 로 만들어진다
    public MemberResponse findMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원이 없습니다: " + memberId));
        return MemberResponse.from(member);
    }

    // B.함정: getReferenceById 는 SELECT 없이 프록시를 준다.
    // 지연 로딩(@ManyToOne(Member) 연관관계를 따라갔을 때도 똑같은 프록시가 들어 있다(2단계 주문,리뷰에서 만날 상황)
    public MemberResponse findMemberByReference(Long memberId) {
        Member member = memberRepository.getReferenceById(memberId);
        return MemberResponse.from(member);
    }

    // B.해결: 프록시를 벗겨서(초기화 SQL 1번) 안에 든 진짜 Customer/Owner/Rider 를 꺼낸다
    public MemberResponse findMemberByReferenceUnproxy(Long memberId) {
        Member member = memberRepository.getReferenceById(memberId);
        Member real = Hibernate.unproxy(member, Member.class); // 프록시가 아니면 그대로 돌려준다
        return MemberResponse.from(real);
    }

    // C-1
    public List<MemberResponse> findRidersWithTreat(VehicleType vehicleType) {
        return memberRepository.findRidersByVehicleTypeWithTreat(vehicleType)
                .stream()
                .map(MemberResponse::from)
                .toList();
    }

    // C-2
    public List<MemberResponse> findAllRidersWithType() {
        return memberRepository.findAllRidersWithType()
                .stream()
                .map(MemberResponse::from)
                .toList();
    }

    // C-3
    public List<MemberResponse> findRiders(VehicleType vehicleType) {
        return riderRepository.findByVehicleType(vehicleType)
                .stream()
                .map(MemberResponse::from) // Rider 도 Member 이므로 그대로 넘길 수 있다
                .toList();
    }



}
