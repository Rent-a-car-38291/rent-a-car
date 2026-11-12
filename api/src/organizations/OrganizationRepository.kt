package com.example.organizations

import com.example.accounts.Users
import com.example.accounts.createUsers
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

internal object Organizations : Table("organizations") {
    val id = integer("id").autoIncrement()
    val name = text("name")
    override val primaryKey = PrimaryKey(id)
}

// A user is in an organization at most once; leaving the system or the organization removes the row.
private object Memberships : Table("memberships") {
    val organizationId = integer("organization_id").references(Organizations.id, onDelete = ReferenceOption.CASCADE)
    val userId = integer("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val role = text("role").check { it inList Role.entries.map { role -> role.name } }
    override val primaryKey = PrimaryKey(organizationId, userId)
}

// An invite code works once and expires; it is deleted when it is used.
private object Invites : Table("invites") {
    val code = text("code")
    val organizationId = integer("organization_id").references(Organizations.id, onDelete = ReferenceOption.CASCADE)
    val expiresAt = timestampWithTimeZone("expires_at")
    override val primaryKey = PrimaryKey(code)
}

// Also used by the cars, which can belong to an organization.
internal fun Database.createOrganizations() {
    createUsers() // memberships reference users
    transaction(this) { SchemaUtils.create(Organizations, Memberships, Invites) }
}

class OrganizationRepository(dataSource: DataSource) {
    private val db = Database.connect(dataSource)

    init {
        db.createOrganizations()
    }

    // One transaction, so the organization never exists without its admin.
    fun create(name: String, adminId: Int): Organization = transaction(db) {
        val id = Organizations.insert { it[Organizations.name] = name }[Organizations.id]
        Memberships.insert {
            it[organizationId] = id
            it[userId] = adminId
            it[role] = Role.ADMIN.name
        }
        Organization(id, name)
    }

    fun exists(id: Int): Boolean = transaction(db) { !Organizations.selectAll().where { Organizations.id eq id }.empty() }

    // A random UUID comes from SecureRandom, so the code can't be guessed (same as login tokens).
    // 168 hours = 7 days, a fixed length that doesn't change across a daylight saving change.
    fun invite(organizationId: Int): String = UUID.randomUUID().toString().also { code ->
        transaction(db) {
            Invites.insert {
                it[Invites.code] = code
                it[Invites.organizationId] = organizationId
                it[expiresAt] = OffsetDateTime.now().plusHours(168)
            }
        }
    }

    // The row lock makes a code work once even when two users accept it at the same time.
    // An invite for a user who is already a member stays valid.
    fun accept(code: String, userId: Int): AcceptResult = transaction(db) {
        val organizationId = Invites.select(Invites.organizationId)
            .where { (Invites.code eq code) and (Invites.expiresAt greater OffsetDateTime.now()) }
            .forUpdate().singleOrNull()?.get(Invites.organizationId)
        if (organizationId == null) {
            AcceptResult.UNKNOWN_CODE
        } else {
            val joined = Memberships.insertIgnore {
                it[Memberships.organizationId] = organizationId
                it[Memberships.userId] = userId
                it[role] = Role.MEMBER.name
            }.insertedCount > 0
            if (joined) Invites.deleteWhere { Invites.code eq code }
            if (joined) AcceptResult.JOINED else AcceptResult.ALREADY_MEMBER
        }
    }

    fun findMembership(organizationId: Int, userId: Int): Membership? = transaction(db) {
        Memberships.selectAll()
            .where { (Memberships.organizationId eq organizationId) and (Memberships.userId eq userId) }
            .singleOrNull()?.let { Membership(userId, organizationId, Role.valueOf(it[Memberships.role])) }
    }

    fun setRole(organizationId: Int, userId: Int, role: Role): ChangeResult = change(organizationId, userId, role)

    fun remove(organizationId: Int, userId: Int): ChangeResult = change(organizationId, userId, null)

    // shortcut: checked just before the account is deleted, so an admin demoted or an admin added in between isn't noticed, lock the rows when that matters.
    fun isLastAdmin(userId: Int): Boolean = transaction(db) {
        Memberships.selectAll().where { (Memberships.userId eq userId) and (Memberships.role eq Role.ADMIN.name) }.any { mine ->
            Memberships.selectAll().where {
                (Memberships.organizationId eq mine[Memberships.organizationId]) and (Memberships.role eq Role.ADMIN.name) and (Memberships.userId neq userId)
            }.empty()
        }
    }

    // Sets the role of a member, or removes the member when role is null. The row locks make two changes at once
    // wait for each other, so an organization can't lose its last admin.
    private fun change(organizationId: Int, userId: Int, role: Role?): ChangeResult = transaction(db) {
        val roles = Memberships.selectAll().where { Memberships.organizationId eq organizationId }.forUpdate()
            .associate { it[Memberships.userId] to Role.valueOf(it[Memberships.role]) }
        val current = roles[userId]
        when {
            current == null -> ChangeResult.NOT_A_MEMBER
            current == Role.ADMIN && role != Role.ADMIN && roles.values.count { it == Role.ADMIN } == 1 -> ChangeResult.LAST_ADMIN
            else -> {
                val member = (Memberships.organizationId eq organizationId) and (Memberships.userId eq userId)
                if (role == null) Memberships.deleteWhere { member } else Memberships.update({ member }) { it[Memberships.role] = role.name }
                ChangeResult.DONE
            }
        }
    }

    fun clear() {
        // Not TRUNCATE ... CASCADE: that would empty the cars table, which references organizations.
        transaction(db) { exec("DELETE FROM organizations; DO \$\$ BEGIN PERFORM setval(pg_get_serial_sequence('organizations', 'id'), 1, false); END \$\$") }
    }
}
