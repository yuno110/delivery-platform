package com.example.jpa.member;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@DiscriminatorValue("RIDER")
@PrimaryKeyJoinColumn(name = "member_id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Rider extends Member {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VehicleType vehicleType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiderWorkStatus workStatus;

    private Rider(String email, String password, String name, String phone, VehicleType vehicleType) {
        super(email, password, name, phone);
        if (vehicleType == null) {
            throw new IllegalArgumentException("차량 종류는 필수입니다.");
        }
        this.vehicleType = vehicleType;
        this.workStatus = RiderWorkStatus.OFF_DUTY;
    }

    public static Rider of(String email, String password, String name, String phone, VehicleType vehicleType) {
        return new Rider(email, password, name, phone, vehicleType);
    }

    // OFF_DUTY -> AVAILABLE -> DELIVERING -> AVAILABLE -> OFF_DUTY

    public void goOnDuty() {
        requireStatus(RiderWorkStatus.OFF_DUTY, "이미 근무 중입니다.");
        this.workStatus = RiderWorkStatus.AVAILABLE;
    }

    public void startDelivery() {
        requireStatus(RiderWorkStatus.AVAILABLE, "배차 가능 상태가 아닙니다.");
        this.workStatus = RiderWorkStatus.DELIVERING;
    }

    public void completeDelivery() {
        requireStatus(RiderWorkStatus.DELIVERING, "배달 중이 아닙니다.");
        this.workStatus = RiderWorkStatus.AVAILABLE;
    }

    public void goOffDuty() {
        if (this.workStatus == RiderWorkStatus.DELIVERING) {
            throw new IllegalStateException("배달을 완료한 뒤 퇴근할 수 있습니다.");
        }
        this.workStatus = RiderWorkStatus.OFF_DUTY;
    }

    public boolean isAssignable() {
        return isActive() && this.workStatus == RiderWorkStatus.AVAILABLE;
    }

    public void changeVehicleType(VehicleType vehicleType) {
        if (vehicleType == null) {
            throw new IllegalArgumentException("차량 종류는 필수입니다.");
        }
        this.vehicleType = vehicleType;
    }

    private void requireStatus(RiderWorkStatus expected, String message) {
        if (this.workStatus != expected) {
            throw new IllegalStateException(message + " (현재: " + this.workStatus + ")");
        }
    }



}

