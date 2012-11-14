/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.newschool.piim.printmodelxjcplugin;


import com.sun.codemodel.*;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;
import com.sun.tools.xjc.model.Model;
import com.sun.tools.xjc.model.CClassInfo;
import com.sun.tools.xjc.model.CPluginCustomization;
import com.sun.tools.xjc.model.CPropertyInfo;
import com.sun.tools.xjc.model.nav.NClass;
import com.sun.xml.xsom.XSElementDecl;
import com.sun.xml.xsom.XSParticle;
import com.sun.xml.xsom.XSSchema;
import java.util.*;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import java.io.Console;
/**
 *
 * @author marinekosh
 */
public class ParentPropertiesPlugin extends Plugin {
 public final static String
  NS = "http://megaplexx.de/xjc/plugins/parent-properties",
  TAG_USEPARENT = "useParent",
  ATTRIBUTE_PARENTFIELD = "parent";

 @Override
 public String getOptionName() {
  return "Xparent-properties";
 }

 @Override
 public String getUsage() {
  return "-Xparent-properties";
 }

 @Override
 public List<String> getCustomizationURIs() {
  return Collections.singletonList(NS);
 }

 @Override
 public boolean isCustomizationTagName(String nsUri, String localName) {
  return nsUri.equals(NS) && localName.equals(TAG_USEPARENT);
 }

 @Override
 public void postProcessModel(Model model, ErrorHandler errorHandler) {
  for (NClass nc : model.beans().keySet()) {
   CClassInfo cc = model.beans().get(nc);

   // Look for possible default parent property
   CPropertyInfo parentProperty = null;
   for (CPropertyInfo property : cc.getProperties()) {
    if (property.getSchemaComponent() instanceof XSParticle) {
     XSParticle particle = (XSParticle) property.getSchemaComponent();
     if (particle.getTerm().isElementDecl()) {
      XSElementDecl declaration = particle.getTerm().asElementDecl();
      if ("IDREF".equals(declaration.getType().getName())) {
       parentProperty = property;
       break;
      }
     }
    }
   }

   // Look for properties with our useParent annotation and set a custom field renderer.
   for (CPropertyInfo property : cc.getProperties()) {
    CPluginCustomization c = property.getCustomizations().find( NS, TAG_USEPARENT );
    if (c == null)
     continue;
    c.markAsAcknowledged();

    String parentField = c.element.getAttribute(ATTRIBUTE_PARENTFIELD);
    if (parentField == null || "".equals(parentField)) {
     if (parentProperty == null) {
      throw new IllegalStateException( "Founde useParent annotation, but no IDREF field found.");
     }
     parentField = parentProperty.getName(false);
    }

   // property.realization = new EscalateGetterToParentFieldRenderer( parentField );
   }
  }
 }

 @Override
 public boolean run(Outline model, Options opt, ErrorHandler errorHandler) throws SAXException {
  return true;
 }
}
