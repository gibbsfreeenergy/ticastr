package com.wzh.blog.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.wzh.blog.dto.UserMenuDTO;
import com.wzh.blog.entity.Menu;

import java.util.List;

/**
 * Reads the administrator navigation assigned to the current account.
 */
public interface MenuService extends IService<Menu> {

    List<UserMenuDTO> listUserMenus();
}
