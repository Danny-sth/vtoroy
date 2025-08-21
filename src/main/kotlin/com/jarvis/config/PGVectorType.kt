package com.jarvis.config

import com.pgvector.PGvector
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.type.SqlTypes
import org.hibernate.usertype.UserType
import java.io.Serializable
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types

class PGVectorType : UserType<PGvector> {

    override fun getSqlType(): Int = SqlTypes.OTHER

    override fun returnedClass(): Class<PGvector> = PGvector::class.java

    override fun equals(x: PGvector?, y: PGvector?): Boolean {
        return when {
            x == null && y == null -> true
            x == null || y == null -> false
            else -> x.toArray().contentEquals(y.toArray())
        }
    }

    override fun hashCode(x: PGvector?): Int {
        return x?.toArray()?.contentHashCode() ?: 0
    }

    override fun nullSafeGet(
        rs: ResultSet,
        position: Int,
        session: SharedSessionContractImplementor?,
        owner: Any?
    ): PGvector? {
        val value = rs.getObject(position)
        return if (rs.wasNull()) null else value as? PGvector
    }

    override fun nullSafeSet(
        st: PreparedStatement,
        value: PGvector?,
        index: Int,
        session: SharedSessionContractImplementor?
    ) {
        if (value == null) {
            st.setNull(index, Types.OTHER)
        } else {
            st.setObject(index, value)
        }
    }

    override fun deepCopy(value: PGvector?): PGvector? {
        return value?.let { PGvector(it.toArray().clone()) }
    }

    override fun isMutable(): Boolean = true

    override fun disassemble(value: PGvector?): Serializable? {
        return deepCopy(value)
    }

    override fun assemble(cached: Serializable?, owner: Any?): PGvector? {
        return deepCopy(cached as? PGvector)
    }
}