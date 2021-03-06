package com.qbc.manager.core;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.JDBCType;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.google.common.base.CaseFormat;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.qbc.dto.core.ColumnInfoDTO;
import com.qbc.dto.core.DatabaseInfoDTO;
import com.qbc.dto.core.TableInfoDTO;
import com.qbc.utils.core.StringUtils;

import lombok.Cleanup;
import lombok.SneakyThrows;

/**
 * 数据库信息处理，用于获取数据库元信息
 *
 * @author Ma
 */
@Component
public class DatabaseInfoManager {

	private static final String DATABASE_POSTGRE_SQL = "PostgreSQL";

	@Autowired
	private DynamicRoutingDataSource dynamicRoutingDataSource;

	/** 表类型 */
	public enum TableType {

		/** 表 */
		TABLE,

		/** 视图 */
		VIEW

	}

	/**
	 * 获得数据库所有表和试图信息
	 * 
	 * @param dataSourceName   数据源名称
	 * @param catalog          类别名称；它必须与存储在数据库中的类别名称匹配；该参数为 "" 表示获取没有类别的那些描述；为null
	 *                         则表示该类别名称不应该用于缩小搜索范围
	 * @param schemaPattern    模式名称的模式； 它必须与存储在数据库中的模式名称匹配； 该参数为 "" 表示获取没有模式的那些描述；
	 *                         为null 则表示该模式名称不应该用于缩小搜索范围
	 * @param tableNamePattern 表名称模式； 它必须与存储在数据库中的表名称匹配
	 * @param tableTypes       要包括的表类型所组成的列表； 为null则表示返回所有类型
	 * @param jdbcTypeMap      java.sql.Types的SQL类型与Java类型的映射关系
	 * @return 数据库所有表和试图信息
	 */
	@SneakyThrows
	public DatabaseInfoDTO getDatabaseInfoDTO(String dataSourceName, String catalog, String schemaPattern,
			String tableNamePattern, TableType[] tableTypes, Map<JDBCType, String> jdbcTypeMap) {
		tableTypes = ObjectUtils.defaultIfNull(tableTypes, TableType.values());
		String[] types = Arrays.asList(tableTypes).stream().map(tableType -> tableType.name()).toArray(String[]::new);

		Map<JDBCType, String> defaultJdbcTypeMap = getDefaultJdbcTypeMap();
		jdbcTypeMap = ObjectUtils.defaultIfNull(jdbcTypeMap, new HashMap<>(0));
		defaultJdbcTypeMap.putAll(jdbcTypeMap);

		@Cleanup
		Connection connection = dynamicRoutingDataSource.getDataSource(dataSourceName).getConnection();
		DatabaseMetaData databaseMetaData = connection.getMetaData();

		// PostgreSQL时，默认不查询系统表
		String databaseProductName = databaseMetaData.getDatabaseProductName();
		if (DATABASE_POSTGRE_SQL.equalsIgnoreCase(databaseProductName)) {
			schemaPattern = "public";
		}

		// 获得所有字段信息
		Table<String, String, ColumnInfoDTO> columnInfoTable = getColumnInfoTable(catalog, schemaPattern,
				defaultJdbcTypeMap, databaseMetaData);

		// 设置主键
		ResultSet primaryKeyResultSet = databaseMetaData.getPrimaryKeys(catalog, schemaPattern, null);
		while (primaryKeyResultSet.next()) {
			String tableName = primaryKeyResultSet.getString("TABLE_NAME");
			String columnName = primaryKeyResultSet.getString("COLUMN_NAME");

			ColumnInfoDTO columnInfoDTO = columnInfoTable.get(tableName, columnName);
			if (columnInfoDTO != null) {
				columnInfoDTO.setKeySeq(primaryKeyResultSet.getShort("KEY_SEQ"));
			}
		}

		// 获得表信息
		List<TableInfoDTO> tableInfos = new ArrayList<>();
		ResultSet tableResultSet = databaseMetaData.getTables(catalog, schemaPattern, tableNamePattern, types);
		while (tableResultSet.next()) {
			String tableName = tableResultSet.getString("TABLE_NAME");

			TableInfoDTO tableInfoDTO = new TableInfoDTO();
			tableInfoDTO.setTableCat(tableResultSet.getString("TABLE_CAT"));
			tableInfoDTO.setTableSchem(tableResultSet.getString("TABLE_SCHEM"));
			tableInfoDTO.setTableName(tableResultSet.getString("TABLE_NAME"));
			tableInfoDTO.setTableType(tableResultSet.getString("TABLE_TYPE"));
			tableInfoDTO.setRemarks(tableResultSet.getString("REMARKS"));
			tableInfoDTO.setUpperCamelTableName(StringUtils.caseFormat(tableInfoDTO.getTableName().toLowerCase(),
					CaseFormat.LOWER_UNDERSCORE, CaseFormat.UPPER_CAMEL));

			// 设置表的字段信息
			List<ColumnInfoDTO> columnInfos = columnInfoTable.row(tableName).values().stream()
					.collect(Collectors.toList());
			tableInfoDTO.setColumnInfos(columnInfos);

			tableInfos.add(tableInfoDTO);
		}

		// 实例化数据库信息实体，并设置数据库信息到实体中。
		DatabaseInfoDTO databaseInfoDTO = new DatabaseInfoDTO();
		databaseInfoDTO.setDatabaseProductName(databaseMetaData.getDatabaseProductName());
		databaseInfoDTO.setDatabaseProductVersion(databaseMetaData.getDatabaseProductVersion());
		databaseInfoDTO.setTableInfos(tableInfos);

		return databaseInfoDTO;
	}

	@SneakyThrows
	private Table<String, String, ColumnInfoDTO> getColumnInfoTable(String catalog, String schemaPattern,
			Map<JDBCType, String> jdbcTypeMap, DatabaseMetaData databaseMetaData) {
		Table<String, String, ColumnInfoDTO> columnInfoTable = HashBasedTable.create();
		ResultSet columnResultSet = databaseMetaData.getColumns(catalog, schemaPattern, null, null);
		while (columnResultSet.next()) {
			String tableName = columnResultSet.getString("TABLE_NAME");
			String columnName = columnResultSet.getString("COLUMN_NAME");

			ColumnInfoDTO columnInfoDTO = new ColumnInfoDTO();
			columnInfoDTO.setColumnName(columnName);
			columnInfoDTO.setDataType(columnResultSet.getInt("DATA_TYPE"));
			columnInfoDTO.setTypeName(columnResultSet.getString("TYPE_NAME"));
			columnInfoDTO.setColumnSize(columnResultSet.getInt("COLUMN_SIZE"));
			columnInfoDTO.setDecimalDigits(columnResultSet.getInt("DECIMAL_DIGITS"));
			columnInfoDTO.setNullable(columnResultSet.getInt("NULLABLE") == 1);
			columnInfoDTO.setRemarks(columnResultSet.getString("REMARKS"));
			columnInfoDTO.setOrdinalPosition(columnResultSet.getInt("ORDINAL_POSITION"));
			columnInfoDTO.setAutoincrement("YES".equals(columnResultSet.getString("IS_AUTOINCREMENT")));
			columnInfoDTO.setLowerCamelColumnName(StringUtils.caseFormat(columnInfoDTO.getColumnName().toLowerCase(),
					CaseFormat.LOWER_UNDERSCORE, CaseFormat.LOWER_CAMEL));
			columnInfoDTO.setJavaType(jdbcTypeMap.get(JDBCType.valueOf(columnInfoDTO.getDataType())));

			columnInfoTable.put(tableName, columnName, columnInfoDTO);
		}
		return columnInfoTable;
	}

	private Map<JDBCType, String> getDefaultJdbcTypeMap() {
		Map<JDBCType, String> defaultJdbcTypeMap = new HashMap<>(50);
		defaultJdbcTypeMap.put(JDBCType.CHAR, String.class.getSimpleName());
		defaultJdbcTypeMap.put(JDBCType.VARCHAR, String.class.getSimpleName());
		defaultJdbcTypeMap.put(JDBCType.NVARCHAR, String.class.getSimpleName());
		defaultJdbcTypeMap.put(JDBCType.LONGVARCHAR, String.class.getSimpleName());
		defaultJdbcTypeMap.put(JDBCType.NUMERIC, BigDecimal.class.getName());
		defaultJdbcTypeMap.put(JDBCType.DECIMAL, BigDecimal.class.getName());
		defaultJdbcTypeMap.put(JDBCType.BIT, Boolean.class.getSimpleName());
		defaultJdbcTypeMap.put(JDBCType.TINYINT, Integer.class.getSimpleName());
		defaultJdbcTypeMap.put(JDBCType.SMALLINT, Integer.class.getSimpleName());
		defaultJdbcTypeMap.put(JDBCType.INTEGER, Integer.class.getSimpleName());
		defaultJdbcTypeMap.put(JDBCType.BIGINT, Long.class.getSimpleName());
		defaultJdbcTypeMap.put(JDBCType.REAL, Float.class.getSimpleName());
		defaultJdbcTypeMap.put(JDBCType.FLOAT, Double.class.getSimpleName());
		defaultJdbcTypeMap.put(JDBCType.DOUBLE, Double.class.getSimpleName());
		defaultJdbcTypeMap.put(JDBCType.BINARY, byte[].class.getSimpleName());
		defaultJdbcTypeMap.put(JDBCType.VARBINARY, byte[].class.getSimpleName());
		defaultJdbcTypeMap.put(JDBCType.LONGVARBINARY, byte[].class.getSimpleName());
		defaultJdbcTypeMap.put(JDBCType.DATE, LocalDate.class.getName());
		defaultJdbcTypeMap.put(JDBCType.TIME, LocalTime.class.getName());
		defaultJdbcTypeMap.put(JDBCType.TIMESTAMP, LocalDateTime.class.getName());
		defaultJdbcTypeMap.put(JDBCType.TIMESTAMP_WITH_TIMEZONE, OffsetDateTime.class.getName());
		return defaultJdbcTypeMap;
	}

	/**
	 * 获得数据库所有表和试图信息
	 * 
	 * @param dataSourceName   数据源名称
	 * @param tableNamePattern 表名称模式； 它必须与存储在数据库中的表名称匹配
	 * @return 数据库所有表和试图信息
	 */
	public DatabaseInfoDTO getDatabaseInfoDTO(String dataSourceName, String tableNamePattern) {
		return getDatabaseInfoDTO(dataSourceName, null, null, tableNamePattern, null, null);
	}

	/**
	 * 获得数据库所有表和试图信息
	 * 
	 * @param dataSourceName 数据源名称
	 * @return 数据库所有表和试图信息
	 */
	public DatabaseInfoDTO getDatabaseInfoDTO(String dataSourceName) {
		return getDatabaseInfoDTO(dataSourceName, null, null, null, null, null);
	}

	/**
	 * 获得数据库所有表和试图信息
	 * 
	 * @return 数据库所有表和试图信息
	 */
	public DatabaseInfoDTO getDatabaseInfoDTO() {
		return getDatabaseInfoDTO(null);
	}

}
