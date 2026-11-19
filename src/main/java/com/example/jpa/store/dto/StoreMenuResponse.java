package com.example.jpa.store.dto;

import com.example.jpa.menu.Menu;
import com.example.jpa.menu.MenuGroup;
import com.example.jpa.menu.MenuSaleStatus;
import com.example.jpa.store.Store;

import java.util.List;

public record StoreMenuResponse(Long id, String name, List<MenuGroupResponse> menuGroups) {

    public record MenuGroupResponse(Long id, String name, List<MenuResponse> menus) {
        static MenuGroupResponse from(MenuGroup menuGroup) {
            return new MenuGroupResponse(
                    menuGroup.getId(), menuGroup.getName(),
                    menuGroup.getMenus().stream().map(MenuResponse::from).toList()
            );
        }
    }

    public record MenuResponse(Long id, String name, long price, MenuSaleStatus saleStatus) {
        static MenuResponse from(Menu menu) {
            return new MenuResponse(
                    menu.getId(), menu.getName(), menu.getPrice().getAmount(), menu.getSaleStatus()
            );
        }
    }

    public static StoreMenuResponse from(Store store) {
        return new StoreMenuResponse(
                store.getId(), store.getName(),
                store.getMenuGroups().stream().map(MenuGroupResponse::from).toList()
        );
    }



}
