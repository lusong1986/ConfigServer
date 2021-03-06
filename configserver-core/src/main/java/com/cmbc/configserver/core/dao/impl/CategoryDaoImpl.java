package com.cmbc.configserver.core.dao.impl;

import com.cmbc.configserver.core.dao.CategoryDao;
import com.cmbc.configserver.core.dao.util.JdbcTemplate;
import com.cmbc.configserver.domain.Category;
import com.cmbc.configserver.utils.ConfigServerLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.List;

/**
 * Created by tongchuan.lin<linckham@gmail.com><br/>
 *
 * @Date 2014/10/30
 * @Time 15:52
 */
@Service("categoryDao")
public class CategoryDaoImpl implements CategoryDao {
    private final static String SQL_CATEGORY_INSERT = "insert into config_category(cell,resource,type) values(?,?,?)";
    private final static String SQL_CATEGORY_UPDATE = "update config_category set cell=?,resource=?,type=? where id=?";
    private final static String SQL_CATEGORY_DELETE = "delete from config_category where id=?";
    private final static String SQL_CATEGORY_QUERY_ALL = "select * from config_category";
    private final static String SQL_CATEGORY_QUERY_RESOURCE = "select distinct resource from config_category where cell =?";
    private final static String SQL_CATEGORY_QUERY_TYPE = "select * from config_category where cell =? and resource=?";
    private final static String SQL_CATEGORY_QUERY_ID = "select * from config_category where id=?";
    private final static String SQL_CATEGORY_QUERY_CELL_RESOURCE_TYPE = "select * from config_category where cell =? and resource=? and type=?";

    @Autowired
    private JdbcTemplate jdbcTemplate;
    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Category save(final Category category) throws Exception {
        try {
            KeyHolder keyHolder =  new GeneratedKeyHolder();
            this.jdbcTemplate.update(new PreparedStatementCreator() {
                @Override
                public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
                    PreparedStatement preState = con.prepareStatement(SQL_CATEGORY_INSERT, Statement.RETURN_GENERATED_KEYS);
                    preState.setString(1,category.getCell());
                    preState.setString(2,category.getResource());
                    preState.setString(3,category.getType());
                    return preState;
                }
            },keyHolder);

            category.setId(keyHolder.getKey().intValue());

            return category;
        } catch (Exception ex) {
            ConfigServerLogger.error(new StringBuilder(128).append("insert config_category ").append(category).append("failed. Details is "), ex);
            throw ex;
        }
    }

    @Override
    public boolean update(Category category) throws Exception {
        try {
            this.jdbcTemplate.update(SQL_CATEGORY_UPDATE, new Object[]{
                    category.getCell(),
                    category.getResource(),
                    category.getType(),
                    category.getId()
            });
            return true;
        } catch (Exception ex) {
            ConfigServerLogger.error(new StringBuilder(128).append("update config_category ").append(category).append("failed. Details is "), ex);
            throw ex;
        }
    }

    @Override
    public boolean delete(Category category) throws Exception {
        try {
            this.jdbcTemplate.update(SQL_CATEGORY_DELETE, new Object[]{
                    category.getCell(),
                    category.getResource(),
                    category.getType(),
                    category.getId()
            });
            return true;
        } catch (Exception ex) {
            ConfigServerLogger.error(new StringBuilder(128).append("delete config_category ").append(category).append("failed. Details is "), ex);
            throw ex;
        }
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public List<Category> getAllCategory() throws Exception {
        try {
            return this.jdbcTemplate.query(SQL_CATEGORY_QUERY_ALL, new CategoryRowMapper());
        } catch (Exception ex) {
            ConfigServerLogger.error(new StringBuilder(128).append("getAllCategory failed. Details is "), ex);
            throw ex;
        }
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public List<String> getResources(String cell) throws Exception {
        try {
            return  this.jdbcTemplate.queryForList(SQL_CATEGORY_QUERY_RESOURCE, new Object[]{cell},String.class);
        } catch (Exception ex) {
            ConfigServerLogger.error(new StringBuilder(128).append("getResources of ").append(cell).append("failed. Details is "), ex);
            throw ex;
        }
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public List<String> getTypes(String cell, String resource) throws Exception {
        try {
            return this.jdbcTemplate.queryForList(SQL_CATEGORY_QUERY_TYPE, new Object[]{cell, resource});
        } catch (Exception ex) {
            ConfigServerLogger.error(new StringBuilder(128).append("getTypes of cell=").append(cell).append(",resource=").append(resource).append("failed. Details is "), ex);
            throw ex;
        }
    }

    /**
     * get the category according to the specified cell/resource/type
     *
     * @param cell     the category's cell
     * @param resource the category's resource
     * @param type     the category's type
     * @return the category which is applied for the query condition
     * @throws Exception
     */
    @Override
    @SuppressWarnings({"unchecked"})
    public Category getCategory(String cell, String resource, String type) throws Exception {
        try {
            List<Category> categories = this.jdbcTemplate.query(SQL_CATEGORY_QUERY_CELL_RESOURCE_TYPE, new Object[]{
                    cell,
                    resource,
                    type
            }, new CategoryRowMapper());
            if( null == categories || categories.isEmpty()){
                return Category.EMPTY_MESSAGE;
            }
            return categories.get(0);
        } catch (Exception ex) {
            ConfigServerLogger.error(new StringBuilder(128).append("get category by cell=").append(cell)
                    .append(",resource=").append(resource)
                    .append(",type=").append(type)
                    .append("failed. Details is "), ex);
            throw ex;
        }
    }

    /**
     * get the category by specified id
     *
     * @param categoryId the specified id
     * @return the category which is applied for the query condition
     * @throws Exception
     */
    @Override
    @SuppressWarnings({"unchecked"})
    public Category getCategoryById(int categoryId) throws Exception {
        try {
            List<Category> categories = this.jdbcTemplate.query(SQL_CATEGORY_QUERY_ID, new Object[]{
                    categoryId
            }, new CategoryRowMapper());
            if(null == categories || categories.isEmpty()){
                return Category.EMPTY_MESSAGE;
            }
            return categories.get(0);

        } catch (Exception ex) {
            ConfigServerLogger.error(new StringBuilder(128).append("get category by id=")
                    .append(categoryId).append("failed. Details is "), ex);
            throw ex;
        }
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public List<Category> getCategories(Object[] categoryIds) throws Exception {
        StringBuilder sqlBuilder = new StringBuilder(64).append("select * from config_category");
        if (null != categoryIds && categoryIds.length > 0) {
            int length = categoryIds.length;
            sqlBuilder.append(" where id in(");
            for (int i = 0; i < length; i++) {
                if (i != (length - 1)) {
                    sqlBuilder.append("?,");
                } else {
                    sqlBuilder.append("?");
                }
            }
            sqlBuilder.append(")");
        }
        String sql = sqlBuilder.toString();
        try {
            return (List<Category>) this.jdbcTemplate.query(sql, categoryIds, new CategoryRowMapper());
        } catch (Exception ex) {
            ConfigServerLogger.error(new StringBuilder(128).append("get category for sql {")
                    .append(sql).append("} failed. Details is "), ex);
            throw ex;
        }
    }

    private class CategoryRowMapper implements RowMapper {
        @Override
        public Category mapRow(ResultSet rs, int rowNum) throws SQLException {
            Category category;
            try {
                category = new Category();
                category.setId(rs.getInt("id"));
                category.setCell(rs.getString("cell"));
                category.setResource(rs.getString("resource"));
                category.setType(rs.getString("type"));
                return category;
            } catch (SQLException ex) {
                ConfigServerLogger.error("error when map row config_category: ", ex);
                throw ex;
            }
        }
    }
}