package com.example.organizations

import kotlinx.serialization.Serializable

enum class Role { ADMIN, MEMBER }

@Serializable
data class Organization(val id: Int, val name: String)

class Membership(val userId: Int, val organizationId: Int, val role: Role)

@Serializable
data class CreateOrganizationRequest(val name: String)

@Serializable
data class InviteResponse(val code: String)

enum class AcceptResult { JOINED, UNKNOWN_CODE, ALREADY_MEMBER }
