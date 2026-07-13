package com.wzh.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wzh.blog.dao.RoleDao;
import com.wzh.blog.entity.Role;
import com.wzh.blog.enums.RoleEnum;
import com.wzh.blog.exception.NotFoundException;
import org.springframework.stereotype.Service;

@Service
public class RoleLookupService {

    private final RoleDao roleDao;

    public RoleLookupService(RoleDao roleDao) {
        this.roleDao = roleDao;
    }

    public Integer requireRoleId(RoleEnum roleType) {
        Role role = roleDao.selectOne(new LambdaQueryWrapper<Role>()
                .select(Role::getId)
                .eq(Role::getRoleLabel, roleType.getLabel()));
        if (role == null) {
            throw new NotFoundException("系统角色不存在: " + roleType.getLabel());
        }
        return role.getId();
    }
}
