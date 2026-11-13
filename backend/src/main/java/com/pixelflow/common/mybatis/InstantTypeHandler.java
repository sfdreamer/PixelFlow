package com.pixelflow.common.mybatis;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * Maps {@link Instant} to MySQL {@code DATETIME(3)} in UTC. Explicit instead of relying on the
 * driver default so that every mapper round-trip is unambiguous.
 */
public class InstantTypeHandler extends BaseTypeHandler<Instant> {

    private static final ZoneOffset UTC = ZoneOffset.UTC;

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Instant parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setObject(i, LocalDateTime.ofInstant(parameter, UTC));
    }

    @Override
    public Instant getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toInstant(rs.getObject(columnName, LocalDateTime.class));
    }

    @Override
    public Instant getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toInstant(rs.getObject(columnIndex, LocalDateTime.class));
    }

    @Override
    public Instant getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toInstant(cs.getObject(columnIndex, LocalDateTime.class));
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(UTC).truncatedTo(ChronoUnit.MILLIS);
    }
}
