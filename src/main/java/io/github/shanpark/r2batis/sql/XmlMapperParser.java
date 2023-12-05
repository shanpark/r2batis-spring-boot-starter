package io.github.shanpark.r2batis.sql;

import io.github.shanpark.r2batis.exception.MapperParsingException;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
public class XmlMapperParser {

    public static Mapper parse(InputStream is) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();

            Document doc = builder.parse(is);
            Element root = doc.getDocumentElement();
            if (!root.getNodeName().equals("mapper")) {
                log.debug("Non-mapper file has been skipped. [Root name: {}]", root.getNodeName());
                return null; // mapper xml 파일이 아닌 xml 파일은 무시한다.
            }

            return new Mapper(root);
        } catch (ParserConfigurationException | IOException | SAXException e) {
            throw new MapperParsingException(e);
        }
    }
}
