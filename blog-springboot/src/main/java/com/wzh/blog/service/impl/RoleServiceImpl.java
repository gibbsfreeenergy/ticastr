package com.wzh.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.wzh.blog.constant.CommonConst;
import com.wzh.blog.dao.RoleDao;
import com.wzh.blog.dao.UserRoleDao;
import com.wzh.blog.vo.SearchQueryVO;
import com.wzh.blog.vo.PageResult;
import com.wzh.blog.dto.RoleDTO;
import com.wzh.blog.dto.UserRoleDTO;
import com.wzh.blog.entity.Role;
import com.wzh.blog.entity.RoleMenu;
import com.wzh.blog.entity.RoleResource;
import com.wzh.blog.entity.UserRole;
import com.wzh.blog.exception.BizException;
import com.wzh.blog.service.AuthorizationCacheService;
import com.wzh.blog.service.RoleMenuService;
import com.wzh.blog.service.RoleResourceService;
import com.wzh.blog.service.RoleService;
import com.wzh.blog.util.BeanCopyUtils;
import com.wzh.blog.vo.RoleVO;
import com.wzh.blog.web.PageQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 角色服务
 *
 * @author yezhiqiu
 * @date 2021/07/28
 */
@Service
public class RoleServiceImpl extends ServiceImpl<RoleDao, Role> implements RoleService {

    private final RoleDao roleDao;
    private final RoleResourceService roleResourceService;
    private final RoleMenuService roleMenuService;
    private final UserRoleDao userRoleDao;
    private final AuthorizationCacheService authorizationCacheService;

    public RoleServiceImpl(RoleDao roleDao,
                           RoleResourceService roleResourceService,
                           RoleMenuService roleMenuService,
                           UserRoleDao userRoleDao,
                           AuthorizationCacheService authorizationCacheService) {
        this.roleDao = roleDao;
        this.roleResourceService = roleResourceService;
        this.roleMenuService = roleMenuService;
        this.userRoleDao = userRoleDao;
        this.authorizationCacheService = authorizationCacheService;
    }




    @Override
    public List<UserRoleDTO> listUserRoles() {
        // 查询角色列表
        List<Role> roleList = roleDao.selectList(new LambdaQueryWrapper<Role>()
                .select(Role::getId, Role::getRoleName));
        return BeanCopyUtils.copyList(roleList, UserRoleDTO.class);
    }



    @Override
    public PageResult<RoleDTO> listRoles(SearchQueryVO conditionVO, PageQuery pageQuery) {
        // 查询角色列表
        List<RoleDTO> roleDTOList = roleDao.listRoles(pageQuery.offset(), pageQuery.size(), conditionVO);
        // 查询总量
        Long count = roleDao.selectCount(new LambdaQueryWrapper<Role>()
                .like(StringUtils.isNotBlank(conditionVO.getKeywords()), Role::getRoleName, conditionVO.getKeywords()));
        return new PageResult<>(roleDTOList, count);
    }

    @Transactional(rollbackFor = Exception.class)


    @Override
    public void saveOrUpdateRole(RoleVO roleVO) {
        // 判断角色名重复
        Role existRole = roleDao.selectOne(new LambdaQueryWrapper<Role>()
                .select(Role::getId)
                .eq(Role::getRoleName, roleVO.getRoleName()));
        if (Objects.nonNull(existRole) && !existRole.getId().equals(roleVO.getId())) {
            throw new BizException("角色名已存在");
        }
        // 保存或更新角色信息
        Role role = Role.builder()
                .id(roleVO.getId())
                .roleName(roleVO.getRoleName())
                .roleLabel(roleVO.getRoleLabel())
                .isDisable(CommonConst.FALSE)
                .build();
        this.saveOrUpdate(role);
        // 更新角色资源关系
        if (Objects.nonNull(roleVO.getResourceIdList())) {
            if (Objects.nonNull(roleVO.getId())) {
                roleResourceService.remove(new LambdaQueryWrapper<RoleResource>()
                        .eq(RoleResource::getRoleId, roleVO.getId()));
            }
            List<RoleResource> roleResourceList = roleVO.getResourceIdList().stream()
                    .map(resourceId -> RoleResource.builder()
                            .roleId(role.getId())
                            .resourceId(resourceId)
                            .build())
                    .collect(Collectors.toList());
            roleResourceService.saveBatch(roleResourceList);
        }
        // 更新角色菜单关系
        if (Objects.nonNull(roleVO.getMenuIdList())) {
            if (Objects.nonNull(roleVO.getId())) {
                roleMenuService.remove(new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getRoleId, roleVO.getId()));
            }
            List<RoleMenu> roleMenuList = roleVO.getMenuIdList().stream()
                    .map(menuId -> RoleMenu.builder()
                            .roleId(role.getId())
                            .menuId(menuId)
                            .build())
                    .collect(Collectors.toList());
            roleMenuService.saveBatch(roleMenuList);
        }
        authorizationCacheService.invalidate();
    }



    @Override
    public void deleteRoles(List<Integer> roleIdList) {
        // 判断角色下是否有用户
        Long count = userRoleDao.selectCount(new LambdaQueryWrapper<UserRole>()
                .in(UserRole::getRoleId, roleIdList));
        if (count > 0) {
            throw new BizException("该角色下存在用户");
        }
        roleDao.deleteByIds(roleIdList);
        authorizationCacheService.invalidate();
    }

}
