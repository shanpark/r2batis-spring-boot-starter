package io.github.shanpark.r2batis.sql;

import lombok.Getter;
import org.w3c.dom.Element;

@Getter
public class SelectKey extends Select {
    private final String keyProperty; // Required.
    private final String keyColumn; // Optional. 하나만 지정 가능. (!!! MyBatis와 다름)
    private final String order; // Required. "BEFORE" or "AFTER"

    public SelectKey(Element element) {
        super(element);

        keyProperty = element.getAttribute("keyProperty").trim();
        if (keyProperty.isBlank())
            throw new RuntimeException("The <selectKey> element must include the 'keyProperty' attribute.");
        keyColumn = element.getAttribute("keyColumn").trim();
        order = element.getAttribute("order").trim();
        if (!order.equalsIgnoreCase("before") && !order.equalsIgnoreCase("after"))
            throw new RuntimeException("The 'order' attribute of the <selectKey> element can have two values: 'BEFORE' or 'AFTER'.");
    }
}
