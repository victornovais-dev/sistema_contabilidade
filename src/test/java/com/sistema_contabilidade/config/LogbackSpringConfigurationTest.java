package com.sistema_contabilidade.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@DisplayName("Logback Spring configuration tests")
class LogbackSpringConfigurationTest {

  @Test
  @DisplayName("Deve configurar appenders ECS para arquivo e console")
  void deveConfigurarAppendersEcsParaArquivoEConsole() throws Exception {
    Document document = readLogbackConfiguration();

    Element fileAppender =
        findElementByAttribute(document.getDocumentElement(), "appender", "name", "ECS_JSON_FILE");
    Element consoleAppender =
        findElementByAttribute(
            document.getDocumentElement(), "appender", "name", "ECS_JSON_CONSOLE");

    assertNotNull(fileAppender);
    assertEquals(
        "ch.qos.logback.core.rolling.RollingFileAppender", fileAppender.getAttribute("class"));
    assertEquals(
        "co.elastic.logging.logback.EcsEncoder",
        findDirectChild(fileAppender, "encoder").getAttribute("class"));

    assertNotNull(consoleAppender);
    assertEquals("ch.qos.logback.core.ConsoleAppender", consoleAppender.getAttribute("class"));
    assertEquals(
        "co.elastic.logging.logback.EcsEncoder",
        findDirectChild(consoleAppender, "encoder").getAttribute("class"));
  }

  @Test
  @DisplayName("Deve enviar logs de producao para arquivo ECS e console")
  void deveEnviarLogsDeProducaoParaArquivoEConsole() throws Exception {
    Document document = readLogbackConfiguration();

    Element productionProfile =
        findElementByAttribute(document.getDocumentElement(), "springProfile", "name", "prod");
    Element root = findDirectChild(productionProfile, "root");

    assertNotNull(root);
    assertEquals("ERROR", root.getAttribute("level"));
    assertEquals(List.of("ECS_JSON_FILE", "ECS_JSON_CONSOLE"), collectAppenderRefs(root));
  }

  @Test
  @DisplayName("Deve manter profile nao produtivo escrevendo no arquivo ECS")
  void deveManterProfileNaoProdutivoEscrevendoNoArquivoEcs() throws Exception {
    Document document = readLogbackConfiguration();

    Element nonProductionProfile =
        findElementByAttribute(document.getDocumentElement(), "springProfile", "name", "!prod");
    Element root = findDirectChild(nonProductionProfile, "root");

    assertNotNull(root);
    assertEquals("ERROR", root.getAttribute("level"));
    assertEquals(List.of("ECS_JSON_FILE"), collectAppenderRefs(root));
  }

  private Document readLogbackConfiguration() throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);

    try (InputStream inputStream =
        Thread.currentThread().getContextClassLoader().getResourceAsStream("logback-spring.xml")) {
      assertNotNull(inputStream);
      return factory.newDocumentBuilder().parse(inputStream);
    }
  }

  private Element findElementByAttribute(
      Element parent, String tagName, String attributeName, String attributeValue) {
    NodeList elements = parent.getElementsByTagName(tagName);
    for (int index = 0; index < elements.getLength(); index++) {
      Node node = elements.item(index);
      if (!(node instanceof Element element)) {
        continue;
      }
      if (attributeValue.equals(element.getAttribute(attributeName))) {
        return element;
      }
    }
    return null;
  }

  private Element findDirectChild(Element parent, String tagName) {
    assertNotNull(parent);
    NodeList children = parent.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      Node node = children.item(index);
      if (node instanceof Element element && tagName.equals(element.getTagName())) {
        return element;
      }
    }
    return null;
  }

  private List<String> collectAppenderRefs(Element root) {
    List<String> appenderRefs = new ArrayList<>();
    NodeList children = root.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      Node node = children.item(index);
      if (!(node instanceof Element element)) {
        continue;
      }
      if ("appender-ref".equals(element.getTagName())) {
        appenderRefs.add(element.getAttribute("ref"));
      }
    }
    assertTrue(!appenderRefs.isEmpty());
    return appenderRefs;
  }
}
