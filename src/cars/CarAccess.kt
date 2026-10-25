package com.example.cars

import com.example.accounts.User
import com.example.organizations.OrganizationRepository
import com.example.organizations.Role

/** A private car is managed by its owner, an organization car by every member of the organization. */
fun canManage(user: User, car: Car, organizations: OrganizationRepository): Boolean =
    car.organizationId?.let { organizations.findMembership(it, user.id) != null } ?: (car.ownerId == user.id)

/** Deleting an organization car takes an admin; a private car is deleted by whoever manages it. */
fun canDelete(user: User, car: Car, organizations: OrganizationRepository): Boolean =
    car.organizationId?.let { organizations.findMembership(it, user.id)?.role == Role.ADMIN } ?: canManage(user, car, organizations)
