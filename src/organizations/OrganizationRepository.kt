package com.example.organizations

import com.example.database.runScript
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

    fun findMembership(organizationId: Int, userId: Int): Membership? = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT * FROM memberships WHERE organization_id = ? AND user_id = ?").use { statement ->
            statement.setInt(1, organizationId)
            statement.setInt(2, userId)
            statement.executeQuery().use { rows ->
                if (rows.next()) Membership(userId, organizationId, Role.valueOf(rows.getString("role"))) else null
            }
        }
    }

    fun clear() {
        dataSource.connection.use { it.createStatement().use { statement -> statement.execute("TRUNCATE organizations RESTART IDENTITY CASCADE") } }
    }
}
