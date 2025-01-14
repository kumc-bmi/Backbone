package org.ohnlp.backbone.io.jdbc;

import com.fasterxml.jackson.databind.JsonNode;
import com.mchange.v2.c3p0.ComboPooledDataSource;
import org.apache.beam.sdk.io.jdbc.JdbcIO;
import org.apache.beam.sdk.schemas.Schema;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PDone;
import org.apache.beam.sdk.values.Row;
import org.ohnlp.backbone.api.Load;
import org.ohnlp.backbone.api.exceptions.ComponentInitializationException;

import java.io.Serializable;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.LinkedList;
import java.util.List;

/**
 * Performs data load using a JDBC connector
 * This class is essentially a configuration wrapper around the beam provided {@link JdbcIO} transform with additional
 * support added for user-customizeable PreparedStatement usage
 *
 * <p>
 * Expected configuration structure:
 * <pre>
 *     {
 *         "url": "jdbc_url_to_database",
 *         "driver": "jdbc.driver.class",
 *         "user": "dbUsername",
 *         "password": "dbPassword",
 *         "query": "query_to_execute_for_load in preparedstatemnt format. Substitute ? for variables (1-indexed)",
 *         "paramMappings": [
 *              "this_column_name_will_be_mapped_to_the_first_?",
 *              "this_column_name_will_be_mapped_to_the_second_?",
 *              ...
 *              etc.
 *         ]
 *     }
 * </pre>
 */
public class JDBCLoad extends Load {
    private JdbcIO.Write<Row> runnableInstance;

    public void initFromConfig(JsonNode config) throws ComponentInitializationException {
        try {
            String url = config.get("url").asText();
            String driver = config.get("driver").asText();
            String user = config.get("user").asText();
            String password = config.get("password").asText();
            String query = config.get("query").asText();
            List<RowToPSMappingFunction> mappingOps = new LinkedList<>();
            int i = 1;
            for (JsonNode child : config.get("paramMappings")) {
                mappingOps.add(new RowToPSMappingFunction(i, child.asText()));
                i++;
            }
            ComboPooledDataSource ds = new ComboPooledDataSource();
            ds.setDriverClass(driver);
            ds.setJdbcUrl(url);
            ds.setUser(user);
            ds.setPassword(password);
            ds.setMaxIdleTime(config.has("idleTimeout") ? config.get("idleTimeout").asInt() : 0);
            JdbcIO.DataSourceConfiguration datasourceConfig = JdbcIO.DataSourceConfiguration.create(ds);
            this.runnableInstance = JdbcIO.<Row>write()
                    .withDataSourceConfiguration(datasourceConfig)
                    .withStatement(query)
                    .withPreparedStatementSetter((JdbcIO.PreparedStatementSetter<Row>) (element, preparedStatement) -> {
                        for (RowToPSMappingFunction func : mappingOps) {
                            func.map(element, preparedStatement);
                        }
                    });
        } catch (Throwable t) {
            throw new ComponentInitializationException(t);
        }
    }

    public PDone expand(PCollection<Row> input) {
        return runnableInstance.expand(input);
    }

    // Handles execution of row to preparedstatement type mappings
    private static class RowToPSMappingFunction implements Serializable {
        private final String fieldName;
        private final int idx;
        private transient RowMappingFunction execute;

        public RowToPSMappingFunction(int index, String fieldName) {
            this.idx = index;
            this.fieldName = fieldName;
        }

        public void map(Row row, PreparedStatement ps) throws SQLException {
            // Do type resolution only once
            if (this.execute == null) {
                Schema.TypeName type = row.getSchema().getField(fieldName).getType().getTypeName();
                if (type.isLogicalType()) {
                    type = row.getSchema().getField(fieldName).getType().getLogicalType().getBaseType().getTypeName();
                }
                switch (type) {
                    case BYTE:
                        this.execute = (data, statement) -> {
                            statement.setByte(this.idx, data.getByte(this.fieldName));
                        };
                        break;
                    case INT16:
                        this.execute = (data, statement) -> {
                            statement.setShort(this.idx, data.getInt16(this.fieldName));
                        };
                        break;
                    case INT32:
                        this.execute = (data, statement) -> {
                            statement.setInt(this.idx, data.getInt32(this.fieldName));
                        };
                        break;
                    case INT64:
                        this.execute = (data, statement) -> {
                            statement.setLong(this.idx, data.getInt64(this.fieldName));
                        };
                        break;
                    case DECIMAL:
                        this.execute = (data, statement) -> {
                            statement.setBigDecimal(this.idx, data.getDecimal(this.fieldName));
                        };
                        break;
                    case FLOAT:
                        this.execute = (data, statement) -> {
                            statement.setFloat(this.idx, data.getFloat(this.fieldName));
                        };
                        break;
                    case DOUBLE:
                        this.execute = (data, statement) -> {
                            statement.setDouble(this.idx, data.getDouble(this.fieldName));
                        };
                        break;
                    case STRING:
                        this.execute = (data, statement) -> {
                            statement.setString(this.idx, data.getString(this.fieldName));
                        };
                        break;
                    case DATETIME:
                        this.execute = (data, statement) -> {
                            statement.setTimestamp(this.idx, new Timestamp(data.getDateTime(this.fieldName).getMillis()));
                        };
                        break;
                    case BOOLEAN:
                        this.execute = (data, statement) -> {
                            statement.setBoolean(this.idx, data.getBoolean(this.fieldName));
                        };
                        break;
                    case BYTES:
                        this.execute = (data, statement) -> {
                            statement.setBytes(this.idx, data.getBytes(this.fieldName));
                        };
                        break;
                    default:
                        // TODO revisit the other options available
                        throw new UnsupportedOperationException(type.name() + " is not currently a supported type for JDBC encoding");
                }
            }
            this.execute.run(row, ps);
        }
    }

    /**
     * Interface definition for a row mapping function
     */
    private interface RowMappingFunction {
        void run(Row row, PreparedStatement ps) throws SQLException;
    }
}
