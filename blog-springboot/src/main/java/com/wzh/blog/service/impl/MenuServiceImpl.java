package com.wzh.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.wzh.blog.administration.MenuRouteContract;
import com.wzh.blog.dao.MenuDao;
import com.wzh.blog.dao.RoleMenuDao;
import com.wzh.blog.dto.MenuDTO;
import com.wzh.blog.dto.LabelOptionDTO;
import com.wzh.blog.dto.UserMenuDTO;
import com.wzh.blog.entity.Menu;
import com.wzh.blog.entity.RoleMenu;
import com.wzh.blog.exception.BizException;
import com.wzh.blog.security.CurrentUser;
import com.wzh.blog.service.MenuService;
import com.wzh.blog.util.BeanCopyUtils;
import com.wzh.blog.vo.SearchQueryVO;
import com.wzh.blog.vo.MenuVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static com.wzh.blog.constant.CommonConst.*;
import static com.wzh.blog.constant.CommonConst.COMPONENT;

/**
 * 菜单服务
 *
 * @author yezhiqiu
 * @date 2021/07/28
 */
@Service
public class MenuServiceImpl extends ServiceImpl<MenuDao, Menu> implements MenuService {
    private final MenuDao menuDao;
    private final RoleMenuDao roleMenuDao;
    private final CurrentUser currentUser;

    public MenuServiceImpl(MenuDao menuDao, RoleMenuDao roleMenuDao, CurrentUser currentUser) {
        this.menuDao = menuDao;
        this.roleMenuDao = roleMenuDao;
        this.currentUser = currentUser;
    }




    @Override
    public List<MenuDTO> listMenus(SearchQueryVO conditionVO) {
        // 查询菜单数据
        List<Menu> menuList = menuDao.selectList(new LambdaQueryWrapper<Menu>()
                .like(StringUtils.isNotBlank(conditionVO.getKeywords()), Menu::getName, conditionVO.getKeywords()));
        menuList.forEach(MenuRouteContract::normalize);
        // 获取目录列表
        List<Menu> catalogList = listCatalog(menuList);
        // 获取目录下的子菜单
        Map<Integer, List<Menu>> childrenMap = getMenuMap(menuList);
        // 组装目录菜单数据
        List<MenuDTO> menuDTOList = catalogList.stream().map(item -> {
            MenuDTO menuDTO = toMenuDTO(item);
            // 获取目录下的菜单排序
            List<MenuDTO> list = childrenMap.getOrDefault(item.getId(), List.of()).stream()
                    .map(this::toMenuDTO)
                    .sorted(Comparator.comparing(MenuDTO::getOrderNum))
                    .collect(Collectors.toList());
            menuDTO.setChildren(list);
            childrenMap.remove(item.getId());
            return menuDTO;
        }).sorted(Comparator.comparing(MenuDTO::getOrderNum)).collect(Collectors.toList());
        // 若还有菜单未取出则拼接
        if (CollectionUtils.isNotEmpty(childrenMap)) {
            List<Menu> childrenList = new ArrayList<>();
            childrenMap.values().forEach(childrenList::addAll);
            List<MenuDTO> childrenDTOList = childrenList.stream()
                    .map(this::toMenuDTO)
                    .sorted(Comparator.comparing(MenuDTO::getOrderNum))
                    .collect(Collectors.toList());
            menuDTOList.addAll(childrenDTOList);
        }
        return menuDTOList;
    }

    @Transactional(rollbackFor = Exception.class)


    @Override
    public void saveOrUpdateMenu(MenuVO menuVO) {
        Menu menu = BeanCopyUtils.copyObject(menuVO, Menu.class);
        MenuRouteContract.normalize(menu);
        this.saveOrUpdate(menu);
    }



    @Override
    public void deleteMenu(Integer menuId) {
        // 查询是否有角色关联
        Long count = roleMenuDao.selectCount(new LambdaQueryWrapper<RoleMenu>()
                .eq(RoleMenu::getMenuId, menuId));
        if (count > 0) {
            throw new BizException("菜单下有角色关联");
        }
        // 查询子菜单
        List<Integer> menuIdList = menuDao.selectList(new LambdaQueryWrapper<Menu>()
                        .select(Menu::getId)
                        .eq(Menu::getParentId, menuId))
                .stream()
                .map(Menu::getId)
                .collect(Collectors.toList());
        menuIdList.add(menuId);
        menuDao.deleteByIds(menuIdList);
    }



    @Override
    public List<LabelOptionDTO> listMenuOptions() {
        // 查询菜单数据
        List<Menu> menuList = menuDao.selectList(new LambdaQueryWrapper<Menu>()
                .select(Menu::getId, Menu::getName, Menu::getParentId, Menu::getOrderNum));
        // 获取目录列表
        List<Menu> catalogList = listCatalog(menuList);
        // 获取目录下的子菜单
        Map<Integer, List<Menu>> childrenMap = getMenuMap(menuList);
        // 组装目录菜单数据
        return catalogList.stream().map(item -> {
            // 获取目录下的菜单排序
            List<LabelOptionDTO> list = new ArrayList<>();
            List<Menu> children = childrenMap.get(item.getId());
            if (CollectionUtils.isNotEmpty(children)) {
                list = children.stream()
                        .sorted(Comparator.comparing(Menu::getOrderNum))
                        .map(menu -> LabelOptionDTO.builder()
                                .id(menu.getId())
                                .label(menu.getName())
                                .build())
                        .collect(Collectors.toList());
            }
            return LabelOptionDTO.builder()
                    .id(item.getId())
                    .label(item.getName())
                    .children(list)
                    .build();
        }).collect(Collectors.toList());
    }



    @Override
    public List<UserMenuDTO> listUserMenus() {
        // 查询用户菜单信息
        List<Menu> menuList = menuDao.listMenusByUserInfoId(currentUser.id());
        menuList.forEach(MenuRouteContract::normalize);
        // 获取目录列表
        List<Menu> catalogList = listCatalog(menuList);
        // 获取目录下的子菜单
        Map<Integer, List<Menu>> childrenMap = getMenuMap(menuList);
        // 转换前端菜单格式
        return convertUserMenuList(catalogList, childrenMap);
    }

    /**
     * 获取目录列表
     *
     * @param menuList 菜单列表
     * @return 目录列表
     */
    private List<Menu> listCatalog(List<Menu> menuList) {
        return menuList.stream()
                .filter(item -> Objects.isNull(item.getParentId()))
                .sorted(Comparator.comparing(Menu::getOrderNum))
                .collect(Collectors.toList());
    }

    /**
     * 获取目录下菜单列表
     *
     * @param menuList 菜单列表
     * @return 目录下的菜单列表
     */
    private Map<Integer, List<Menu>> getMenuMap(List<Menu> menuList) {
        return menuList.stream()
                .filter(item -> Objects.nonNull(item.getParentId()))
                .collect(Collectors.groupingBy(Menu::getParentId));
    }

    /**
     * 转换用户菜单格式
     *
     * @param catalogList 目录
     * @param childrenMap 子菜单
     */
    private List<UserMenuDTO> convertUserMenuList(List<Menu> catalogList, Map<Integer, List<Menu>> childrenMap) {
        return catalogList.stream().map(item -> {
            // 获取目录
            UserMenuDTO userMenuDTO = toUserMenuDTO(item);
            List<UserMenuDTO> list = new ArrayList<>();
            // 获取目录下的子菜单
            List<Menu> children = childrenMap.get(item.getId());
            if (CollectionUtils.isNotEmpty(children)) {
                // 多级菜单处理
                list = children.stream()
                        .sorted(Comparator.comparing(Menu::getOrderNum))
                        .map(menu -> {
                            UserMenuDTO dto = toUserMenuDTO(menu);
                            dto.setHidden(Objects.equals(menu.getIsHidden(), TRUE));
                            return dto;
                        })
                        .collect(Collectors.toList());
            } else {
                // 一级菜单处理
                userMenuDTO.setPath(item.getPath());
                userMenuDTO.setComponent(COMPONENT);
                UserMenuDTO child = toUserMenuDTO(item);
                child.setPath("");
                list.add(child);
            }
            userMenuDTO.setHidden(Objects.equals(item.getIsHidden(), TRUE));
            userMenuDTO.setChildren(list);
            return userMenuDTO;
        }).collect(Collectors.toList());
    }

    private MenuDTO toMenuDTO(Menu menu) {
        return MenuDTO.builder()
                .id(menu.getId())
                .name(menu.getName())
                .code(menu.getCode())
                .path(menu.getPath())
                .component(menu.getComponent())
                .routeKey(menu.getRouteKey())
                .icon(menu.getIcon())
                .iconKey(menu.getIconKey())
                .section(menu.getSection())
                .createTime(menu.getCreateTime())
                .orderNum(menu.getOrderNum())
                .isHidden(menu.getIsHidden())
                .children(List.of())
                .build();
    }

    private UserMenuDTO toUserMenuDTO(Menu menu) {
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
