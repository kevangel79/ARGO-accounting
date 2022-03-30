package org.accounting.system.services.authorization;

import com.mongodb.MongoWriteException;
import org.accounting.system.beans.RequestInformation;
import org.accounting.system.dtos.authorization.RoleRequestDto;
import org.accounting.system.dtos.authorization.RoleResponseDto;
import org.accounting.system.dtos.authorization.update.UpdateRoleRequestDto;
import org.accounting.system.entities.authorization.Role;
import org.accounting.system.enums.AccessType;
import org.accounting.system.enums.Collection;
import org.accounting.system.enums.Operation;
import org.accounting.system.exceptions.ConflictException;
import org.accounting.system.mappers.RoleMapper;
import org.accounting.system.repositories.authorization.RoleRepository;
import org.bson.types.ObjectId;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.NotFoundException;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class RoleService {

    @Inject
    RoleRepository roleRepository;

    @Inject
    RequestInformation requestInformation;

    /**
     * Maps the {@link RoleRequestDto} to {@link Role}.
     * Then the {@link Role} is stored in the mongo database.
     *
     * @param request The POST request body
     * @return The stored role has been turned into a response body
     */
    public RoleResponseDto save(RoleRequestDto request) {

        var role = RoleMapper.INSTANCE.requestToRole(request);

        try{
            roleRepository.persist(role);
        } catch (MongoWriteException e) {
            throw new ConflictException("There is already a role with this name : " + request.name);
        }

        return RoleMapper.INSTANCE.roleToResponse(role);
    }

    /**
     * It examines whether the role has access to execute an operation to a collection.
     * {@link AccessType#NEVER} always takes precedence over any other access type.
     * @param providedRoles The roles that have been provided by OIDC server
     * @return true or false
     */
    public boolean hasAccess(List<String> providedRoles, Collection collection, Operation operation){

       List<AccessType> accessTypeList = providedRoles
               .stream()
               .map(role-> roleRepository.getRolePermissionsUponACollection(role, collection))
               .flatMap(java.util.Collection::stream)
               .filter(permission -> permission.operation.equals(operation))
               .map(permission -> permission.accessType)
               .collect(Collectors.toList());

       AccessType precedence = AccessType.higherPrecedence(accessTypeList);
       requestInformation.setAccessType(precedence);

       return precedence.access;
    }

    /**
     * Returns the available Accounting System roles
     * @return role response body
     */
    public List<RoleResponseDto> fetchRoles(){

        var roles = roleRepository.getAllEntities();

        var rolesToResponse = RoleMapper.INSTANCE.rolesToResponse(roles);

        return rolesToResponse;
    }

    /**
     * Fetches a Role by given id.
     *
     * @param id The Role id
     * @return The corresponding Role
     */
    public RoleResponseDto fetchRole(String id){

        var role = roleRepository.fetchEntityById(new ObjectId(id));

        return RoleMapper.INSTANCE.roleToResponse(role);
    }

    /**
     * Delete a Role by given id.
     * @param roleId The Role to be deleted
     * @return If the operation is successful or not
     * @throws NotFoundException If the Role doesn't exist
     */
    public boolean delete(String roleId){

        return roleRepository.deleteEntityById(new ObjectId(roleId));
    }

    /**
     * This method is responsible for updating a part or all attributes of existing Role.
     *
     * @param id The Role to be updated.
     * @param request The Role attributes to be updated
     * @return The updated Role
     * @throws NotFoundException If the Role doesn't exist
     */
    public RoleResponseDto update(String id, UpdateRoleRequestDto request){

        Role role = null;

        try{
            role = roleRepository.updateEntityById(new ObjectId(id), request);
        } catch (MongoWriteException e){
            throw new ConflictException("The role name should be unique. A Role with that name has already been created.");
        }

        return RoleMapper.INSTANCE.roleToResponse(role);
    }

}