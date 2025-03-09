package zw.co.manaService.userService.service;

import zw.co.manaService.userService.model.dto.RoleRequestDto;
import zw.co.manaService.userService.model.dto.RoleResponseDto;

import java.util.List;

public interface RoleService {
    RoleResponseDto createRole(RoleRequestDto roleRequestDto);

    RoleResponseDto updateRole(Long roleId, RoleRequestDto roleRequestDto);

    void deleteRole(Long roleId);

    RoleResponseDto getRoleById(Long roleId);

    List<RoleResponseDto> getAllRoles();
}