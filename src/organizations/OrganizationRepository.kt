package com.example.organizations

import com.example.database.runScript
import java.util.UUID
import javax.sql.DataSource

class OrganizationRepository(private val dataSource: DataSource) {
    init {
        dataSource.runScript("users.sql") // memberships reference users
        dataSource.runScript("organizations.sql")
    }

    // One statement, so the organization never exists without its admin.
    fun create(name: String, adminId: Int): Organization = dataSource.connection.use { connection ->
        connection.prepareStatement(
            """WITH created AS (INSERT INTO organizations (name) VALUES (?) RETURNING *),
                    admin AS (INSERT INTO memberships (organization_id, user_id, role) SELECT id, ?, 'ADMIN' FROM created)
               SELECT * FROM created"""
        ).use { statement ->
            statement.setString(1, name)
            statement.setInt(2, adminId)
            statement.executeQuery().use { rows ->
                rows.next()
                Organization(rows.getInt("id"), rows.getString("name"))
            }
        }
    }

    fun exists(id: Int): Boolean = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT 1 FROM organizations WHERE id = ?").use { statement ->
            statement.setInt(1, id)
            statement.executeQuery().use { it.next() }
        }
    }

    // A random UUID comes from SecureRandom, so the code can't be guessed (same as login tokens).
    // 168 hours = 7 days: '7 days' is a calendar interval, which is an hour longer across a daylight saving change.
    fun invite(organizationId: Int): String = UUID.randomUUID().toString().also { code ->
        dataSource.connection.use { connection ->
            connection.prepareStatement("INSERT INTO invites (code, organization_id, expires_at) VALUES (?, ?, now() + interval '168 hours')").use { statement ->
                statement.setString(1, code)
                statement.setInt(2, organizationId)
                statement.executeUpdate()
            }
        }
    }

    // The row lock makes a code work once even when two users accept it at the same time.
    // An invite for a user who is already a member stays valid.
    fun accept(code: String, userId: Int): AcceptResult = dataSource.connection.use { connection ->
        connection.autoCommit = false
        val organizationId = connection.prepareStatement("SELECT organization_id FROM invites WHERE code = ? AND expires_at > now() FOR UPDATE").use { statement ->
            statement.setString(1, code)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getInt(1) else null }
        } ?: return AcceptResult.UNKNOWN_CODE
        val joined = connection.prepareStatement("INSERT INTO memberships (organization_id, user_id, role) VALUES (?, ?, 'MEMBER') ON CONFLICT DO NOTHING").use { statement ->
            statement.setInt(1, organizationId)
            statement.setInt(2, userId)
            statement.executeUpdate() > 0
        }
        if (joined) {
            connection.prepareStatement("DELETE FROM invites WHERE code = ?").use { it.setString(1, code); it.executeUpdate() }
        }
        connection.commit()
        if (joined) AcceptResult.JOINED else AcceptResult.ALREADY_MEMBER
    }

    fun findMembership(organizationId: Int, userId: Int): Membership? = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT * FROM memberships WHERE organization_id = ? AND user_id = ?").use { statement ->
            statement.setInt(1, organizationId)
            statement.setInt(2, userId)
            statement.executeQuery().use { rows ->
                if (rows.next()) Membership(userId, organizationId, Role.valueOf(rows.getString("role"))) else null
            }
        }
    }

    fun setRole(organizationId: Int, userId: Int, role: Role): ChangeResult = change(organizationId, userId, role)

    fun remove(organizationId: Int, userId: Int): ChangeResult = change(organizationId, userId, null)

    // shortcut: checked just before the account is deleted, so an admin demoted or an admin added in between isn't noticed, lock the rows when that matters.
    fun isLastAdmin(userId: Int): Boolean = dataSource.connection.use { connection ->
        connection.prepareStatement(
            """SELECT 1 FROM memberships m WHERE m.user_id = ? AND m.role = 'ADMIN' AND NOT EXISTS
               (SELECT 1 FROM memberships o WHERE o.organization_id = m.organization_id AND o.role = 'ADMIN' AND o.user_id <> m.user_id)"""
        ).use { statement ->
            statement.setInt(1, userId)
            statement.executeQuery().use { it.next() }
        }
    }

    // Sets the role of a member, or removes the member when role is null. The row locks make two changes at once
    // wait for each other, so an organization can't lose its last admin.
    private fun change(organizationId: Int, userId: Int, role: Role?): ChangeResult = dataSource.connection.use { connection ->
        connection.autoCommit = false
        val roles = connection.prepareStatement("SELECT user_id, role FROM memberships WHERE organization_id = ? FOR UPDATE").use { statement ->
            statement.setInt(1, organizationId)
            statement.executeQuery().use { rows ->
                buildMap { while (rows.next()) put(rows.getInt("user_id"), Role.valueOf(rows.getString("role"))) }
            }
        }
        val current = roles[userId] ?: return ChangeResult.NOT_A_MEMBER
        if (current == Role.ADMIN && role != Role.ADMIN && roles.values.count { it == Role.ADMIN } == 1) return ChangeResult.LAST_ADMIN
        val sql = if (role == null) "DELETE FROM memberships WHERE organization_id = ? AND user_id = ?"
                  else "UPDATE memberships SET role = ? WHERE organization_id = ? AND user_id = ?"
        connection.prepareStatement(sql).use { statement ->
            val offset = if (role == null) 0 else 1
            role?.let { statement.setString(1, it.name) }
            statement.setInt(1 + offset, organizationId)
            statement.setInt(2 + offset, userId)
            statement.executeUpdate()
        }
        connection.commit()
        ChangeResult.DONE
    }

    fun clear() {
        dataSource.connection.use { it.createStatement().use { statement -> statement.execute("TRUNCATE organizations RESTART IDENTITY CASCADE") } }
    }
}
