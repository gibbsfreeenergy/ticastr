package com.wzh.blog.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.wzh.blog.administration.MenuRouteContract;
import com.wzh.blog.dao.MenuDao;
import com.wzh.blog.dto.UserMenuDTO;
import com.wzh.blog.entity.Menu;
import com.wzh.blog.security.CurrentUser;
import com.wzh.blog.service.MenuService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.wzh.blog.constant.CommonConst.TRUE;

@Service
public class MenuServiceImpl extends ServiceImpl<MenuDao, Menu> implements MenuService {

    private final MenuDao menuDao;
    private final CurrentUser currentUser;

    public MenuServiceImpl(MenuDao menuDao, CurrentUser currentUser) {
        this.menuDao = menuDao;
        this.currentUser = currentUser;
    }

    @Override
    public List<UserMenuDTO> listUserMenus() {
        List<Menu> menuList = menuDao.listMenusByUserInfoId(currentUser.id());
        menuList.forEach(MenuRouteContract::normalize);

        List<Menu> catalogs = menuList.stream()
                .filter(menu -> menu.getParentId() == null)
                .sorted(Comparator.comparing(Menu::getOrderNum,
                        Comparator.nullsLast(Integer::compareTo)))
                .toList();
        Map<Integer, List<Menu>> children = menuList.stream()
                .filter(menu -> menu.getParentId() != null)
                .collect(Collectors.groupingBy(Menu::getParentId));

        return catalogs.stream()
                .map(catalog -> toMenu(catalog, children.getOrDefault(catalog.getId(), List.of())))
                .toList();
    }

    private UserMenuDTO toMenu(Menu catalog, List<Menu> children) {
        List<UserMenuDTO> childMenus = new ArrayList<>();
        if (children.isEmpty()) {
            UserMenuDTO child = toMenu(catalog);
            child.setPath("");
            childMenus.add(child);
        } else {
            children.stream()
                    .sorted(Comparator.comparing(Menu::getOrderNum,
                            Comparator.nullsLast(Integer::compareTo)))
                    .map(this::toMenu)
                    .forEach(childMenus::add);
        }

        UserMenuDTO result = toMenu(catalog);
        result.setHidden(Objects.equals(catalog.getIsHidden(), TRUE));
        result.setChildren(childMenus);
        return result;
    }

    private UserMenuDTO toMenu(Menu menu) {
        return UserMenuDTO.builder()
                .name(menu.getName())
                .code(menu.getCode())
                .path(menu.getPath())
                .component(menu.getComponent())
                .routeKey(menu.getRouteKey())
                .icon(menu.getIcon())
                .iconKey(menu.getIconKey())
                .section(menu.getSection())
                .hidden(Objects.equals(menu.getIsHidden(), TRUE))
                .build();
    }
}
