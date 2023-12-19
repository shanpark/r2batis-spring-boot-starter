package io.github.shanpark.r2batis.mapper;

import io.github.shanpark.r2batis.exception.InvalidMapperElementException;
import lombok.Getter;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

@Getter
public class Query {

    private final String id;
    private final String databaseId;
    private final Class<?> resultClass;

    protected final List<SqlNode> sqlNodes = new ArrayList<>();

    public Query(Element element) {
        id = element.getAttribute("id").trim();
        databaseId = element.getAttribute("databaseId").trim();
        String resultType = element.getAttribute("resultType").trim();
        try {
            resultClass = !resultType.isBlank() ? Class.forName(resultType) : null;
        } catch (ClassNotFoundException e) {
            throw new InvalidMapperElementException(String.format("The specified resultType[%s] is invalid.", resultType), e);
        }
    }

    public String generateSql(MapperContext mapperContext) {
        StringBuilder sb = new StringBuilder();
        for (SqlNode sqlNode : sqlNodes)
            sb.append(sqlNode.generateSql(mapperContext)).append(" ");
        return sb.toString();
    }
}
