package zw.co.manaService.userService.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import zw.co.manaService.userService.model.Role;
import zw.co.manaService.userService.model.dto.RoleRequestDto;
import zw.co.manaService.userService.model.dto.RoleResponseDto;
import zw.co.manaService.userService.repository.RoleRepository;
import zw.co.manaService.userService.service.RoleService;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@SuppressWarnings("unused")
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;

    @Override
    public RoleResponseDto createRole(RoleRequestDto roleRequestDto) {
        Role role = new Role();
        role.setName(roleRequestDto.getName());
        role.setDescription(roleRequestDto.getDescription());
        Role savedRole = roleRepository.save(role);

        return mapToResponseDto(savedRole);
    }

    @Override
    public RoleResponseDto updateRole(Long roleId, RoleRequestDto roleRequestDto) {
        Role role = roleRepository.findById(roleId).orElseThrow(
                () -> new IllegalArgumentException("Role not found with id: " + roleId)
        );
        role.setName(roleRequestDto.getName());
        role.setDescription(roleRequestDto.getDescription());
        Role updatedRole = roleRepository.save(role);

        return mapToResponseDto(updatedRole);
    }

    @Override
    public void deleteRole(Long roleId) {
        if (!roleRepository.existsById(roleId)) {
            throw new IllegalArgumentException("Role not found with id: " + roleId);
        }
        roleRepository.deleteById(roleId);
    }

    @Override
    public RoleResponseDto getRoleById(Long roleId) {
        Role role = roleRepository.findById(roleId).orElseThrow(
                () -> new IllegalArgumentException("Role not found with id: " + roleId)
        );
        return mapToResponseDto(role);
    }

    @Override
    public List<RoleResponseDto> getAllRoles() {
        List<Role> roles = roleRepository.findAll();
        return roles.stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    private RoleResponseDto mapToResponseDto(Role role) {
        return new RoleResponseDto(
                role.getId(),
                role.getName(),
                role.getDescription()
        );
    }
}
