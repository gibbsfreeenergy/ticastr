package com.wzh.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wzh.blog.dao.RoleDao;
import com.wzh.blog.entity.Role;
import com.wzh.blog.enums.RoleEnum;
import org.springframework.stereotype.Service;

/**
 * Resolves the single role needed when the development bootstrap creates the
 * administrator account.
 */
@Service
public class RoleLookupService {

    private final RoleDao roleDao;

    public RoleLookupService(RoleDao roleDao) {
        this.roleDao = roleDao;
    }

    public Integer requireRoleId(RoleEnum role) {
        Role found = roleDao.selectOne(new LambdaQueryWrapper<Role>()
                .select(Role::getId)
                .eq(Role::getRoleLabel, role.getLabel()));
        if (found == null) {
            throw new IllegalStateException("Required role is missing: " + role.getLabel());
        }
        return found.getId();
    }
}
