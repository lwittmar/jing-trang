package com.thaiopensource.validate.nvdl;

import com.thaiopensource.util.PropertyId;
import com.thaiopensource.util.PropertyMap;
import com.thaiopensource.util.PropertyMapBuilder;
import com.thaiopensource.validate.IncorrectSchemaException;
import com.thaiopensource.validate.Option;
import com.thaiopensource.validate.Schema;
import com.thaiopensource.validate.SchemaReader;
import com.thaiopensource.validate.ValidateProperty;
import com.thaiopensource.validate.auto.AutoSchemaReader;
import com.thaiopensource.validate.auto.SchemaFuture;
import com.thaiopensource.validate.auto.SchemaReceiver;
import com.thaiopensource.validate.auto.SchemaReceiverFactory;
import com.thaiopensource.validate.prop.wrap.WrapProperty;
import com.thaiopensource.validate.rng.CompactSchemaReader;
import com.thaiopensource.validate.rng.SAXSchemaReader;
import com.thaiopensource.xml.util.Name;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.net.URL;

/**
 * Schema receiver implementation for NVDL schemas. 
 *
 */
class SchemaReceiverImpl implements SchemaReceiver {
  /**
   * Relax NG schema for nvdl schemas.
   */
  private static final String NVDL_SCHEMA = "nvdl.rng";
  private static final String RNC_MEDIA_TYPE = "application/x-rnc";
  private final PropertyMap properties;
  
  /**
   * Property indicating if we need to check only attributes,
   * that means the root element is just a placeholder for the attributes.
   */
  private final Name attributeOwner;
  /**
   * The schema reader capable of parsing the input schema file.
   * It will be an auto schema reader as NVDL is XML.
   */
  private final SchemaReader autoSchemaReader;
  
  /**
   * Schema object created by this schema receiver.
   */
  private Schema nvdlSchema = null;
  
  /**
   * Required properties.
   */
  private static final PropertyId subSchemaProperties[] = {
    ValidateProperty.ERROR_HANDLER,
    ValidateProperty.XML_READER_CREATOR,
    ValidateProperty.ENTITY_RESOLVER,
    SchemaReceiverFactory.PROPERTY,
  };

  /**
   * Creates a schema receiver for NVDL schemas.
   * 
   * @param properties Properties.
   */
  public SchemaReceiverImpl(PropertyMap properties) {
    this.attributeOwner = WrapProperty.ATTRIBUTE_OWNER.get(properties);
    PropertyMapBuilder builder = new PropertyMapBuilder();
    for (int i = 0; i < subSchemaProperties.length; i++) {
      Object value = properties.get(subSchemaProperties[i]);
      if (value != null)
        builder.put(subSchemaProperties[i], value);
    }
    this.properties = builder.toPropertyMap();
    this.autoSchemaReader = new AutoSchemaReader(SchemaReceiverFactory.PROPERTY.get(properties));
  }

  public SchemaFuture installHandlers(XMLReader xr) {
    PropertyMapBuilder builder = new PropertyMapBuilder(properties);
    if (attributeOwner != null)
      WrapProperty.ATTRIBUTE_OWNER.put(builder, attributeOwner);
    return new SchemaImpl(builder.toPropertyMap()).installHandlers(xr, this);
  }

  Schema getNvdlSchema() throws IOException, IncorrectSchemaException, SAXException {
    if (nvdlSchema == null) {
      String className = SchemaReceiverImpl.class.getName();
      String resourceName = className.substring(0, className.lastIndexOf('.')).replace('.', '/') + "/resources/" + NVDL_SCHEMA;
      URL nvdlSchemaUrl = getResource(resourceName);
      nvdlSchema = SAXSchemaReader.getInstance().createSchema(new InputSource(nvdlSchemaUrl.toString()),
                                                              properties);
    }
    return nvdlSchema;
  }

  /**
   * Get a resource using this class class loader.
   * @param resourceName the resource.
   * @return An URL pointing to the resource.
   */
  private static URL getResource(String resourceName) {
    ClassLoader cl = SchemaReceiverImpl.class.getClassLoader();
    // XXX see if we should borrow 1.2 code from Service
    if (cl == null)
      return ClassLoader.getSystemResource(resourceName);
    else
      return cl.getResource(resourceName);
  }

  /**
   * Get the properties.
   * @return a PropertyMap.
   */
  PropertyMap getProperties() {
    return properties;
  }

  /**
   * Creates a child schema. This schema is referred in a validate action.
   * 
   * @param inputSource The input source for the schema.
   * @param schemaType The schema type.
   * @param options Options specified for this schema in the NVDL script.
   * @param isAttributesSchema Flag indicating if the schema should be modified
   * to check attributes only. 
   * @return
   * @throws IOException In case of IO problems.
   * @throws IncorrectSchemaException In case of invalid schema.
   * @throws SAXException In case if XML problems while creating the schema.
   */
  Schema createChildSchema(InputSource inputSource, String schemaType, PropertyMap options, boolean isAttributesSchema) throws IOException, IncorrectSchemaException, SAXException {
    SchemaReader reader = isRnc(schemaType) ? CompactSchemaReader.getInstance() : autoSchemaReader;
    PropertyMapBuilder builder = new PropertyMapBuilder(properties);
    if (isAttributesSchema)
      WrapProperty.ATTRIBUTE_OWNER.put(builder, ValidatorImpl.OWNER_NAME);
    for (int i = 0, len = options.size(); i < len; i++)
      builder.put(options.getKey(i), options.get(options.getKey(i)));
    return reader.createSchema(inputSource, builder.toPropertyMap());
  }

  /**
   * Get an option for the given URI.
   * @param uri The URI for an option.
   * @return Either the option from the auto schema reader or 
   * from the compact schema reader.
   */
  Option getOption(String uri) {
    Option option = autoSchemaReader.getOption(uri);
    if (option != null)
      return option;
    return CompactSchemaReader.getInstance().getOption(uri);
  }

  /**
   * Checks is a schema type is RNC.
   * @param schemaType The schema type specification.
   * @return true if the schema type refers to a RNC schema.
   */
  private static boolean isRnc(String schemaType) {
    if (schemaType == null)
      return false;
    schemaType = schemaType.trim();
    return schemaType.equals(RNC_MEDIA_TYPE);
  }
}
