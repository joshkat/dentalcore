package com.dentalcore.users.internal.repository;

import com.dentalcore.users.internal.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    List<Permission> findByCodeIn(Collection<String> codes);

    @Query(value = """
            SELECT p.code FROM permissions p
            JOIN role_permissions rp ON rp.permission_id = p.id
            JOIN roles r ON r.id = rp.role_id
            WHERE r.name = :roleName
            ORDER BY p.code
            """, nativeQuery = true)
    List<String> findCodesByRoleName(@Param("roleName") String roleName);

    @Modifying
    @Query(value = "DELETE FROM role_permissions WHERE role_id = :roleId", nativeQuery = true)
    void deleteGrantsForRole(@Param("roleId") UUID roleId);

    @Modifying
    @Query(value = """
            INSERT INTO role_permissions (role_id, permission_id)
            VALUES (:roleId, :permissionId)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    void insertGrant(@Param("roleId") UUID roleId, @Param("permissionId") UUID permissionId);
}
