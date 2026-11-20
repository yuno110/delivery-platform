package com.example.jpa.store.dto;

import com.example.jpa.menu.*;
import com.example.jpa.store.Store;

import java.util.List;

// 가게 상세 (가게 -> 메뉴그룹 -> 메뉴 -> 옵션그룹 -> 옵션). 5단계를 다 탐색하므로 어디서 SQL이 나가는지 세기 좋다
public record StoreDetailResponse(Long id, String name, List<MenuGroupResponse> menuGroups) {

    public record MenuGroupResponse(Long id, String name, List<MenuResponse> menus) {
        static MenuGroupResponse from(MenuGroup menuGroup) {
            return new MenuGroupResponse(
                    menuGroup.getId(), menuGroup.getName(),
                    menuGroup.getMenus().stream().map(MenuResponse::from).toList() // menus 컬렉션 초기화
            );
        }
    }

    public record MenuResponse(Long id, String name, long price, MenuSaleStatus saleStatus,
                               List<OptionGroupResponse> optionGroups
    ) {
        static MenuResponse from(Menu menu) {
            return new MenuResponse(
                    menu.getId(), menu.getName(), menu.getPrice().getAmount(), menu.getSaleStatus(),
                    menu.getMenuOptionGroups().stream().map(OptionGroupResponse::from).toList() // menuOptionGroups 컬렉션 초기화
            );
        }
    }

    // 중간 엔티티(MenuOptionGroup)는 응답에 드러내지 않는다. 클라이언트에겐 "이 메뉴의 옵션그룹"일 뿐
    public record OptionGroupResponse(Long id, String name, int minSelect, int maxSelect,
                                      List<OptionItemResponse> items) {
        static OptionGroupResponse from(MenuOptionGroup menuOptionGroup) {
            OptionGroup optionGroup = menuOptionGroup.getOptionGroup();   // 프록시일 수 있다 (getId 까지는 SQL 없음)
            return new OptionGroupResponse(
                    optionGroup.getId(), optionGroup.getName(),            // getName 에서 프록시 초기화
                    optionGroup.getMinSelect(), optionGroup.getMaxSelect(),
                    optionGroup.getItems().stream().map(OptionItemResponse::from).toList()  // items 초기화
            );
        }
    }

    public record OptionItemResponse(Long id, String name, long extraPrice, OptionSaleStatus saleStatus) {
        static OptionItemResponse from(OptionItem item) {
            return new OptionItemResponse(item.getId(), item.getName(), item.getExtraPrice().getAmount(), item.getSaleStatus());
        }
    }

    public static StoreDetailResponse from(Store store) {
        return new StoreDetailResponse(
                store.getId(), store.getName(),
                store.getMenuGroups().stream().map(MenuGroupResponse::from).toList()
        );
    }



}
