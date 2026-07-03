package com.plantops.iam.api;

import com.plantops.iam.dto.*;
import com.plantops.iam.service.IamService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/api/v1/iam")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class IamResource {

    @Inject
    IamService iamService;

    @GET
    @Path("/me")
    public IamService.CurrentUser me() {
        return iamService.currentUser();
    }

    @GET
    @Path("/workspaces")
    public List<WorkspaceMembershipDto> myWorkspaces() {
        return iamService.workspaceMemberships();
    }

    @GET
    @Path("/workspaces/{workspaceId}/modules")
    public List<ModuleToggleDto> workspaceModules(@PathParam("workspaceId") String workspaceId) {
        return iamService.listWorkspaceModules(workspaceId);
    }

    @PUT
    @Path("/workspaces/{workspaceId}/modules")
    public List<ModuleToggleDto> updateWorkspaceModules(
            @PathParam("workspaceId") String workspaceId,
            UpdateModulesRequest request) {
        return iamService.updateWorkspaceModules(workspaceId, request);
    }

    @GET
    @Path("/workspaces/{workspaceId}/adapters")
    public List<AdapterToggleDto> workspaceAdapters(@PathParam("workspaceId") String workspaceId) {
        return iamService.listWorkspaceAdapters(workspaceId);
    }

    @PUT
    @Path("/workspaces/{workspaceId}/adapters")
    public List<AdapterToggleDto> updateWorkspaceAdapters(
            @PathParam("workspaceId") String workspaceId,
            UpdateAdaptersRequest request) {
        return iamService.updateWorkspaceAdapters(workspaceId, request);
    }

    @GET
    @Path("/workspaces/{workspaceId}/members")
    public List<WorkspaceMemberDto> workspaceMembers(@PathParam("workspaceId") String workspaceId) {
        return iamService.listWorkspaceMembers(workspaceId);
    }

    @POST
    @Path("/workspaces/{workspaceId}/members")
    public WorkspaceMemberDto addWorkspaceMember(
            @PathParam("workspaceId") String workspaceId,
            AddWorkspaceMemberRequest request) {
        return iamService.addWorkspaceMember(workspaceId, request);
    }

    @DELETE
    @Path("/workspaces/{workspaceId}/members/{userId}")
    public void removeWorkspaceMember(
            @PathParam("workspaceId") String workspaceId,
            @PathParam("userId") String userId) {
        iamService.removeWorkspaceMember(workspaceId, userId);
    }

    @GET
    @Path("/workspaces/{workspaceId}/members/{userId}/permissions")
    public List<ModulePermissionDto> memberPermissions(
            @PathParam("workspaceId") String workspaceId,
            @PathParam("userId") String userId) {
        return iamService.listMemberPermissions(workspaceId, userId);
    }

    @PUT
    @Path("/workspaces/{workspaceId}/members/{userId}/permissions")
    public List<ModulePermissionDto> updateMemberPermissions(
            @PathParam("workspaceId") String workspaceId,
            @PathParam("userId") String userId,
            UpdateMemberPermissionsRequest request) {
        return iamService.updateMemberPermissions(workspaceId, userId, request);
    }
}
