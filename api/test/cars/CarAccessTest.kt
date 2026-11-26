package com.example.cars

import com.example.accounts.User
import com.example.accounts.UserRepository
import com.example.database.TestDatabase
import com.example.organizations.OrganizationRepository
import kotlin.test.*

// canManage and canDelete for every kind of user; the routes use the same two functions. Needs the test database for the memberships.
class CarAccessTest {
    private val users = UserRepository(TestDatabase.dataSource)
    private val organizations = OrganizationRepository(TestDatabase.dataSource)

    private fun car(ownerId: Int?, organizationId: Int?) = AddCarRequest(
        CarType.BEV, "Test", "EV-1", 1, 1.0, Location(0.0, 0.0), 4, BodyStyle.Sedan, 2020, 100, Transmission.Manual, "Red",
        capacity = 50.0, consumption = 5.0,
    ).toCar(ownerId, organizationId = organizationId)

    private fun user(name: String): User = users.register(name, "$name@example.com", "supersecret")!!

    @Test
    fun APrivateCarIsManagedAndDeletedByItsOwnerOnly() {
        users.clear()
        organizations.clear()
        val owner = user("owner")
        val other = user("other")
        val car = car(owner.id, null)

        assertTrue(canManage(owner, car, organizations))
        assertTrue(canDelete(owner, car, organizations))
        assertFalse(canManage(other, car, organizations))
        assertFalse(canDelete(other, car, organizations))
    }

    @Test
    fun AnOrganizationCarIsManagedByEveryMemberAndDeletedByAdminsOnly() {
        users.clear()
        organizations.clear()
        val admin = user("admin")
        val member = user("member")
        val outsider = user("outsider")
        val organization = organizations.create("Garage", admin.id)
        organizations.accept(organizations.invite(organization.id), member.id)
        val car = car(null, organization.id)

        assertTrue(canManage(admin, car, organizations))
        assertTrue(canManage(member, car, organizations))
        assertFalse(canManage(outsider, car, organizations))
        assertTrue(canDelete(admin, car, organizations))
        assertFalse(canDelete(member, car, organizations))
        assertFalse(canDelete(outsider, car, organizations))
    }

    @Test
    fun TheCreatorOfAnOrganizationCarLosesAccessWhenLeavingTheOrganization() {
        users.clear()
        organizations.clear()
        val admin = user("admin")
        val creator = user("creator")
        val organization = organizations.create("Garage", admin.id)
        organizations.accept(organizations.invite(organization.id), creator.id)
        val car = car(creator.id, organization.id)
        organizations.remove(organization.id, creator.id)

        assertFalse(canManage(creator, car, organizations))
        assertFalse(canDelete(creator, car, organizations))
        assertTrue(canManage(admin, car, organizations))
    }

    @Test
    fun AMemberOfAnotherOrganizationHasNoAccess() {
        users.clear()
        organizations.clear()
        val admin = user("admin")
        val rival = user("rival")
        val organization = organizations.create("Garage", admin.id)
        organizations.create("Rival garage", rival.id)

        assertFalse(canManage(rival, car(null, organization.id), organizations))
        assertFalse(canDelete(rival, car(null, organization.id), organizations))
    }

    @Test
    fun APrivateCarOfAMemberIsNotManagedByTheAdminsOfTheirOrganization() {
        users.clear()
        organizations.clear()
        val admin = user("admin")
        val member = user("member")
        val organization = organizations.create("Garage", admin.id)
        organizations.accept(organizations.invite(organization.id), member.id)

        assertFalse(canManage(admin, car(member.id, null), organizations))
    }
}
