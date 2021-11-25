/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2019-2021 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2021 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 ******************************************************************************/

package org.opennms.features.config.dao.impl.util;

import com.google.common.base.Strings;
import com.google.common.io.Resources;
import org.apache.ws.commons.schema.XmlSchemaCollection;
import org.eclipse.persistence.dynamic.DynamicEntity;
import org.eclipse.persistence.internal.oxm.ByteArraySource;
import org.eclipse.persistence.jaxb.MarshallerProperties;
import org.eclipse.persistence.jaxb.UnmarshallerProperties;
import org.eclipse.persistence.jaxb.dynamic.DynamicJAXBContext;
import org.eclipse.persistence.jaxb.dynamic.DynamicJAXBContextFactory;
import org.eclipse.persistence.oxm.MediaType;
import org.json.JSONArray;
import org.json.JSONObject;
import org.opennms.core.xml.JaxbUtils;
import org.opennms.features.config.dao.api.ConfigConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLFilter;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.sax.SAXSource;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * It handles all kinds of xml <> json conventions.
 */
public class XmlConverter implements ConfigConverter {
    private static final Logger LOG = LoggerFactory.getLogger(XmlConverter.class);
    public static final String VALUE_TAG = "__VALUE__";

    private final DynamicJAXBContext jaxbContext;
    private final XmlSchema xmlSchema;
    private String xsdName;
    private String rootElement;
    private Map<String, String> elementNameToValueNameMap;

    public XmlConverter(final String xsdName,
                        final String rootElement,
                        Map<String, String> elementNameToValueNameMap)
            throws IOException {
        this.xsdName = Objects.requireNonNull(xsdName);
        this.rootElement = Objects.requireNonNull(rootElement);
        this.elementNameToValueNameMap = elementNameToValueNameMap;
        this.xmlSchema = this.readXmlSchema();
        this.jaxbContext = getDynamicJAXBContextForService(xmlSchema);
    }

    /**
     * It searches the xsd defined in configuration class and load into schema.
     *
     * @return XmlSchema
     * @throws IOException
     */
    private XmlSchema readXmlSchema() throws IOException {
        String xsdStr = Resources.toString(SchemaUtil.getSchemaPath(xsdName), StandardCharsets.UTF_8);
        final XsdModelConverter xsdModelConverter = new XsdModelConverter(xsdStr);
        final XmlSchemaCollection schemaCollection = xsdModelConverter.getCollection();
        // Grab the first namespace that includes 'opennms', sort for predictability
        List<String> namespaces = Arrays.stream(schemaCollection.getXmlSchemas())
                .map(org.apache.ws.commons.schema.XmlSchema::getTargetNamespace)
                .filter(targetNamespace -> targetNamespace.contains("opennms")).collect(Collectors.toList());

        if (namespaces.size() != 1) {
            LOG.error("XSD must contain one 'opennms' namespaces!");
            throw new IllegalArgumentException("XSD must contain one 'opennms' namespaces!");
        }

        return new XmlSchema(xsdStr, namespaces.get(0), rootElement);
    }

    public String getRootElement() {
        return rootElement;
    }


    /**
     * Convert xml to json. If elementNameToValueNameMap is not null, it will setup XmlValue attribute name properly
     *
     * @param sourceXml
     * @return json string
     */
    @Override
    public String xmlToJson(String sourceXml) {
        try {
            final XMLFilter filter = JaxbUtils.getXMLFilterForNamespace(this.xmlSchema.getNamespace());
            final InputSource inputSource = new InputSource(new StringReader(sourceXml));
            final SAXSource source = new SAXSource(filter, inputSource);

            final Unmarshaller u = jaxbContext.createUnmarshaller();
            DynamicEntity entity = (DynamicEntity) u.unmarshal(source);

            final Marshaller m = jaxbContext.createMarshaller();
            m.setProperty(MarshallerProperties.MEDIA_TYPE, MediaType.APPLICATION_JSON);
            m.setProperty(MarshallerProperties.JSON_INCLUDE_ROOT, false);

            //dirty tricks to detect xml value (use for JSONObject remove)
            m.setProperty(MarshallerProperties.JSON_VALUE_WRAPPER, VALUE_TAG);

            final StringWriter writer = new StringWriter();
            m.marshal(entity, writer);
            String jsonStr = writer.toString();
            if (jsonStr.indexOf(VALUE_TAG) != -1) {
                JSONObject json = new JSONObject(jsonStr);
                if (!this.elementNameToValueNameMap.isEmpty()) {
                    json = this.replaceXmlValueAttributeName(json);
                }
                json = this.removeEmptyValueTag(json);
                return json.toString();
            } else {
                return jsonStr;
            }
        } catch (JAXBException | SAXException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private JSONObject removeEmptyValueTag(JSONObject json) {
        json.toMap().forEach((key, value) -> {
            if (VALUE_TAG.equals(key) && value instanceof String && ((String) value).trim().length() == 0) {
                LOG.warn("!!!!!! REMOVING KEY {} VALUE {}", key, value);
                json.remove(key);
            } else if (value instanceof JSONObject) {
                removeEmptyValueTag((JSONObject) value);
            } else if (value instanceof JSONArray) {
                ((JSONArray) value).forEach(arrayItem -> {
                    if (arrayItem instanceof JSONObject)
                        this.removeEmptyValueTag((JSONObject) arrayItem);
                });
            }
        });
        return json;
    }

    //TODO: need more data for testing
    private JSONObject replaceXmlValueAttributeName(JSONObject json) {

        this.elementNameToValueNameMap.forEach((elementName, valueName) -> {
            if (json.has(elementName)) {
                Object value = json.get(elementName);
                if (value instanceof JSONArray) {
                    JSONArray tmpList = (JSONArray) value;
                    tmpList.forEach(item -> {
                        if (item instanceof JSONObject) {
                            replaceKey((JSONObject) item, VALUE_TAG, valueName);
                        }
                    });

                } else if (value instanceof JSONObject) {
                    replaceKey((JSONObject) value, VALUE_TAG, valueName);
                }
            }
        });
        return json;
    }

    private void replaceKey(JSONObject json, String oldKey, String newKey) {
        Object value = json.remove(oldKey);
        json.put(newKey, value);
    }

    @Override
    public String jsonToXml(final String jsonStr) {
        try {
            final Unmarshaller u = jaxbContext.createUnmarshaller();
            u.setProperty(UnmarshallerProperties.MEDIA_TYPE, MediaType.APPLICATION_JSON);
            u.setProperty(UnmarshallerProperties.JSON_INCLUDE_ROOT, false);

            Class<? extends DynamicEntity> entityClass = getTopLevelEntity(jaxbContext);
            ByteArraySource byteArraySource = new ByteArraySource(jsonStr.getBytes(StandardCharsets.UTF_8));
            DynamicEntity entity = u.unmarshal(byteArraySource, entityClass).getValue();

            final Marshaller m = jaxbContext.createMarshaller();
            final StringWriter writer = new StringWriter();
            m.marshal(entity, writer);
            return writer.toString();
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    private DynamicJAXBContext getDynamicJAXBContextForService(XmlSchema xmlSchema) {
        final String xsd = xmlSchema.getXsdContent();

        try (InputStream is = new ByteArrayInputStream(xsd.getBytes(StandardCharsets.UTF_8))) {
            return DynamicJAXBContextFactory.createContextFromXSD(is, null, null, null);
        } catch (JAXBException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Class<? extends DynamicEntity> getTopLevelEntity(DynamicJAXBContext jc) {
        String className = namespace2package(xmlSchema.getNamespace()) +
                "." +
                TopLevelElementToClass.topLevelElementToClass(xmlSchema.getTopLevelObject());
        return jc.newDynamicEntity(className).getClass();
    }

    public static String namespace2package(String s) {
        // "http://xmlns.opennms.org/xsd/config/vacuumd" -> "org.opennms.xmlns.xsd.config.vacuumd"
        final URL url;
        try {
            url = new URL(s);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        StringBuilder pkgName = new StringBuilder();

        // Split and reverse the host part
        String[] parts = url.getHost().split("\\.");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (i != parts.length - 1) {
                pkgName.append(".");
            }
            pkgName.append(parts[i]);
        }

        // Split and append the parts of the path
        parts = url.getPath().split("/");
        for (String part : parts) {
            if (Strings.isNullOrEmpty(part)) {
                continue;
            }
            pkgName.append(".");
            pkgName.append(part);
        }

        String packageName = pkgName.toString();
        packageName = packageName.replace('-', '_');
        return packageName;
    }

    public XmlSchema getXmlSchema() {
        return xmlSchema;
    }
}