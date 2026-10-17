package com.example.organizations

import kotlinx.serialization.Serializable

enum class Role { ADMIN, MEMBER }

@Serializable
data class Organization(val id: Int, val name: String)

@Serializable
data class Membership(val userId: Int, val organizationId: Int, val role: Role)

@Serializable
data class CreateOrganizationRequest(val name: String)

@Serializable
data class UpdateRoleRequest(val role: Role)

@Serializable
data class InviteResponse(val code: String)

enum class AcceptResult { JOINED, UNKNOWN_CODE, ALREADY_MEMBER }

enum class ChangeResult { DONE, NOT_A_MEMBER, LAST_ADMIN }
