package com.example.jpa.member;

import com.example.jpa.member.dto.MemberResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/{memberId}")
    public MemberResponse findMember(@PathVariable Long memberId) {
        return memberService.findMember(memberId);
    }

    @GetMapping("/{memberId}/reference")
    public MemberResponse findMemberByReference(@PathVariable Long memberId) {
        return memberService.findMemberByReference(memberId);
    }

    @GetMapping("/{memberId}/reference/unproxy")
    public MemberResponse findMemberByReferenceUnproxy(@PathVariable Long memberId) {
        return memberService.findMemberByReferenceUnproxy(memberId);
    }

    @GetMapping("/riders/treat")
    public List<MemberResponse> findRidersWithTreat(@RequestParam VehicleType vehicleType) {
        return memberService.findRidersWithTreat(vehicleType);
    }

    @GetMapping("/riders/type")
    public List<MemberResponse> findAllRidersWithType() {
        return memberService.findAllRidersWithType();
    }

    @GetMapping("/riders")
    public List<MemberResponse> findRiders(@RequestParam VehicleType vehicleType) {
        return memberService.findRiders(vehicleType);
    }


}
