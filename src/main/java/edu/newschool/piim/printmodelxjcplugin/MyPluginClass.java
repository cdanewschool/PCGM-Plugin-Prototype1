package edu.newschool.piim.printmodelxjcplugin;


/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

import com.sun.codemodel.*;
import com.sun.codemodel.writer.SingleStreamCodeWriter;

import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;
import com.sun.tools.xjc.model.Model;
import com.sun.tools.xjc.model.CClassInfo;
import com.sun.tools.xjc.model.CPropertyInfo;
import com.sun.tools.xjc.model.nav.NClass;
import com.sun.xml.xsom.XSSchema;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.text.*;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import java.io.Console;
import javax.xml.namespace.QName;
import java.lang.String;
import com.sun.tools.ws.wsdl.document.Operation;
import com.sun.tools.ws.wsdl.document.PortType;
import com.sun.tools.ws.wsdl.document.WSDLDocument;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;


import com.sun.tools.ws.wscompile.WsimportOptions;
import com.sun.tools.ws.wsdl.document.Service;
import com.sun.tools.xjc.BadCommandLineException;
import com.sun.tools.xjc.model.CElementInfo;
import com.sun.tools.xjc.outline.EnumOutline;
import com.sun.tools.xjc.outline.FieldOutline;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URL;
import org.w3c.dom.Document;
import org.w3c.dom.*;



/**
 *
 * @author marinekosh
 */
public class MyPluginClass extends Plugin {
Boolean isList = false;
Boolean isJAXB = false;
JDefinedClass responseClass = null;
String prevMethod = null;
Boolean isFunctionPrivate = false;
// Boolean isPrivate = false;
Boolean isFunctionPublic = false;
Boolean isListS= false;
String prevType = null;
Map publicFunctionsMap = new LinkedHashMap();
Map publicFunctionsMap2 = new LinkedHashMap();
Map privateFunctionsMap = new LinkedHashMap();
String parentPrv = null;
String parentPub = null;
String parentType;
Boolean isSubClass = false;
//Request
Map ParametersMap = new LinkedHashMap();
Map VariablesMap = new LinkedHashMap();


//TODO: g- test this with more than one request and response type in the xml schema
//TODO: g- look into why there are duplicate java files in the 'generated' folder, this does not occur at actual CAL wsimport time
//TODO: g- generate java docs - DONE for request parameter enum type
//TODO: g- do any design patterns apply well? explore, re-write code
//TODO: g- analyze other operations for config file, consider Green CDA
//TODO: g- write output to .java files -DONE
//TODO: g- where to write the .java files, what is the path? dynamic path setting
//TODO: g- figure out packaging, not use static file location for source
//TODO: g- code optimization, don't loop through the entire outline every time if possible
//TODO: g- code optimization, only check do checks on hl7 objects not others like string - DONE
//TODO: g- generate unique message ids/timestamps, use date util?
    private String request;
    private Document docResponse;
    private boolean isChildFunctionPublic;
   
   
    @Override
    public String getOptionName() {
        //throw new UnsupportedOperationException("Not supported yet.");
        return "MyPluginClass";
    }

    @Override
    public String getUsage() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    
 @Override
    public boolean run(Outline outline,
        @SuppressWarnings("unused") Options opt,
        @SuppressWarnings("unused") ErrorHandler errorHandler) throws SAXException
    {
        try {
            
            //createRequestClass(outline);
            generateResponseClass(outline);
        } catch (IOException ex) {
            Logger.getLogger(MyPluginClass.class.getName()).log(Level.SEVERE, null, ex);
        }

           return true;
    }
 private void createTimeStampMethod(JDefinedClass pcgmClass){
      JFieldVar dateFormatField = pcgmClass.field(JMod.PRIVATE, DateFormat.class, "dateFormat", JExpr._null());
      JFieldVar dateSimpleFormatField = pcgmClass.field(JMod.PRIVATE, SimpleDateFormat.class, "dateSimpleFormat", JExpr._null());
      JFieldVar dateField = pcgmClass.field(JMod.PRIVATE, Date.class, "date", JExpr._null());
      JMethod timeStampMethod = pcgmClass.method(JMod.PRIVATE, String.class, "getTimeStamp");
      timeStampMethod.body().assign(dateFormatField, JExpr.direct("new SimpleDateFormat(\"yyyyMMddHHmmssZ\")"));
      timeStampMethod.body()._return(JExpr.direct("dateFormat.format(new Date()).toString()"));
 }
 private void createRequestClass(Outline outline) throws SAXException, IOException {
      try {
            JCodeModel pcgmCodeModel = new JCodeModel();          
            //Create One Class that handles the generation of all Request Types
            JDefinedClass pcgmClass = pcgmCodeModel._class("edu.newschool.piim.generatedCode.pcgmHelperRequests");
            //Add ObjectFactory field
            JFieldVar ofField = pcgmClass.field(JMod.PROTECTED | JMod.STATIC, outline.getCodeModel()._getClass("org.hl7.v3.ObjectFactory"), "factory");
            ofField.init(JExpr._new(outline.getCodeModel()._getClass("org.hl7.v3.ObjectFactory")));
            //Add uid field
            JFieldVar uidField = pcgmClass.field(JMod.NONE, String.class, "uid", JExpr._null());
            //not used only for getting the 'import java.util.UUID' to generate.
            JFieldVar uuidField = pcgmClass.field(JMod.NONE, UUID.class, "uid2", JExpr._null());
            //Careate a method to get the current time stamp
            createTimeStampMethod(pcgmClass); 
            
            //Create a Function, within the Request Class, for each Request Type 
            for (ClassOutline classOutline : outline.getClasses()) {
                JDefinedClass implClass = classOutline.implClass;
                if (implClass.name().contains("Request") && !implClass.fullName().contains("generated")){
                    request = implClass.name();
                    //codemodel creates methods
                    JMethod pcgmMethod = pcgmClass.method(JMod.PUBLIC, implClass, "createRequest_pcgm");
                    //initialize the uid;
                    pcgmMethod.body().assign(uidField, JExpr.direct("UUID.randomUUID().toString()"));
                    //get the parameters and variables from the configuration file
                    getRequestMap("Variables");
                    getRequestMap("Parameters");
                    //for each parameter in the config file... 
                    for (Iterator it=ParametersMap.keySet().iterator(); it.hasNext(); ) {
                            Object key = it.next();
                            Object value = ParametersMap.get(key); 
                            //add it to the signiture of the request function
                            pcgmMethod.param(String.class, value.toString());
                            //get the parameter values for this parameter
                            Map paramValues = getValueArray(key.toString());
                            //if there is more than one parameter with this name then create an enum for the paramType 
                            if (paramValues.size()>1){
                                JDefinedClass jec = pcgmClass._enum(JMod.PUBLIC, value.toString()+"s");
                                //add javadocs to the method to display vaild parameter types to user
                                String javaDocString = value.toString() + " may have values: "; 
                                pcgmMethod.javadoc().append(javaDocString);
                                 for (Iterator pvit=paramValues.keySet().iterator(); pvit.hasNext(); ) {
                                    Object pvkey = pvit.next();
                                    String pvvalue = paramValues.get(pvkey).toString();
                                    jec.enumConstant(pvvalue);
                                    pcgmMethod.javadoc().append(pvvalue);
                                 }                               
                            }  
                    }                
                    JBlock pcgmBlock = pcgmMethod.body();
                    createRequestMethodBody(outline, request, pcgmBlock, "RqstMsg");
                    pcgmBlock._return(JExpr.ref("RqstMsg"));
                    pcgmMethod.name("createRequest" + request);
                }       
             }

            try {
               ByteArrayOutputStream out = new ByteArrayOutputStream();
               pcgmCodeModel.build(new SingleStreamCodeWriter(out));
               //System.out.println(out.toString());
               String fileName = "pcgmHelperRequests";
               OutputStream f2 = new FileOutputStream(fileName+".java");
               out.writeTo(f2);

            } catch (IOException ex) {
                Logger.getLogger(MyPluginClass.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            
        } catch (JClassAlreadyExistsException ex) {
            Logger.getLogger(MyPluginClass.class.getName()).log(Level.SEVERE, null, ex);
        }
 }
 
 
         
 private void createRequestMethodBody(Outline outline, String nextType, JBlock pcgmBlock, String varName) throws JClassAlreadyExistsException, SAXException, IOException{
     String nt = nextType;
    
     outterLoop:
     for (ClassOutline classOutline : outline.getClasses()) {
           JDefinedClass implClass = classOutline.implClass;
           String fieldName;
           String fieldNameUpper;
           String fieldTypeFullName; 
        
           //The POJOS for types CS,CE,CV,CR,CO do not have thier own fields they extend from CD abd they were not being picked up 
           //This is so we get the elements/attributes from CD 
           //The schema for CD has been altered so that we don't end up in an infinite loop
           //TODO: better solution to altering schema for CD
           if (nextType.contains("CS") || nextType.contains("CE") || nextType.contains("CV") || nextType.contains("CR") || nextType.matches("CO")){
               nextType = "CD";
           } 
          
          if (implClass.name().matches(nt) && !implClass.fullName().contains("generated")){
               
                if (implClass.name().matches("MCCIMT000100UV01Receiver")){
                    System.out.println("THIS IS THE NEXT TYPE STRING PASSED TO FUNCTION: " + nextType);
                }
                
                //Checks the confic file to see if there is an item that contains this variable name
                if ( !(isVariable(varName) || isParameter(varName))){
                    continue;
                }       
                JClass jClassavpImpl = implClass;
                JVar jvar = pcgmBlock.decl(jClassavpImpl, varName);
                jvar.init(JExpr._new(jClassavpImpl));
           }
           
           if (implClass.name().matches(nextType) && !implClass.fullName().contains("generated")){
                
                for (JFieldVar field : implClass.fields().values()){
                    
                   fieldTypeFullName = field.type().fullName();
                   fieldName = field.name();
                   fieldNameUpper = String.format( "%s%s", Character.toUpperCase(fieldName.charAt(0)), fieldName.substring(1) );
                   String concatVariableName = varName+ "_" + fieldName;
             
                   String ftn = field.type().name(); 
                   // returns the short type and sets values for isList, isListS, isJAXB
                   ftn = shortFieldType(ftn);  
                   String value = getDefaultValue(concatVariableName); 
                                    
                   JVar lhs = null;
                   if(!value.matches("null")){
                       if(value.matches("param")){
                           //pcgmBlock.directStatement(ftn+" "+concatVariableName+" = "+ null +";"); 
                           lhs = pcgmBlock.decl(field.type(), concatVariableName, JExpr._null());
                           setParamValueDS(concatVariableName, pcgmBlock);                 
                       }
                       else{
                          //pcgmBlock.directStatement(ftn+" "+concatVariableName+" = "+ value +";");
                          lhs = pcgmBlock.decl(field.type(), concatVariableName, JExpr.direct(value));
                       }   
                   }     
                   if (fieldTypeFullName.contains("org.hl7.v3")) {
                       createRequestMethodBody(outline, ftn, pcgmBlock, concatVariableName);                      
                   }
                  
                   if (isVariable(concatVariableName) || isParameter(concatVariableName)){
                       if (isListType(field.type().name())){
                           pcgmBlock.directStatement(varName+".get" +fieldNameUpper+ "().add(" +concatVariableName+");");
                       }
                       else if(isJAXBType(field.type().name())){
                           pcgmBlock.directStatement(varName+".set"+fieldNameUpper+"(factory.create"+nextType+fieldNameUpper+"("+concatVariableName+"));");
                       }
                       else{
                       pcgmBlock.directStatement(varName+".set" +fieldNameUpper+ "(" +concatVariableName+");");                       
                       }
                       
                   }  
                }
           }
        }
         
  }
 private boolean isVariable(String var) throws SAXException, IOException{
    if (VariablesMap.containsKey(var)) {
         return true;
    }
    for (Iterator it = VariablesMap.keySet().iterator(); it.hasNext(); ) {
        Object key = it.next();
        //Object value = VariablesMap.get(key); 
        //System.out.println("VariableMap KEY: " + key + ", VALUE: "+ value);
        if (key.toString().contains(var)){
            return true;
        }
    }
    return false;
 }
 
 private boolean isParameter(String var){
    if (ParametersMap.containsKey(var)){
      
        return true;
    }
    for (Iterator it = ParametersMap.keySet().iterator(); it.hasNext(); ) {
        Object key = it.next();
        //Object value = ParametersMap.get(key); 
        //System.out.println("VariableMap KEY: " + key + ", VALUE: "+ value);
        if (key.toString().contains(var)){
            return true;
        }
    }
        return false;
 }
 private boolean isListType(String ftn){
     if (ftn.contains("List<")){
          if (ftn.contains("List<Serializable>")) {
             isListS = true;            
          }               
          isList = true;
          return true;
      }
      return false;
 }
 private boolean isJAXBType(String ftn){
     if (ftn.contains("JAXBElement")){
         isJAXB = true;
        
         return true;        
     }
     return false;
 }
 private String shortFieldType(String ftn){
      isList = false;
      isListS = false;
      isJAXB = false;
     if (ftn.contains("List<")){
          if (ftn.contains("List<Serializable>")) {
             isListS = true;
          }
          int size = ftn.length()-1;
          ftn = ftn.substring(5, size);
          isList = true;
      }
      else if (ftn.contains("JAXBElement")){
          int size = ftn.length()-1;
          ftn = ftn.substring(12, size);
          isJAXB = true;
     }
     return ftn;
 }
 
 private String setParamValueDS(String concatVariableName, JBlock pcgmBlock) throws SAXException, IOException{
     Map paramValues;
     paramValues = getValueArray(concatVariableName);
     String paramName = null;
     if (paramValues.size()==1){
        for (Iterator it=paramValues.keySet().iterator(); it.hasNext(); ) {
              Object key = it.next();
              //Object value = paramValues.get(key).toString();
              pcgmBlock.directStatement(concatVariableName+" = "+key+ ";");      
        }
        return null;
     }
     if (paramValues.size()>1){
        
         if(ParametersMap.containsKey(concatVariableName)){
             Object pn = ParametersMap.get(concatVariableName).toString();
             paramName = (String) pn;
            }
     
        JExpression jx = JExpr.direct(paramName+"s" + ".valueOf("+paramName+")");      
        JSwitch jc = pcgmBlock._switch(jx); 
      
        for (Iterator it=paramValues.keySet().iterator(); it.hasNext(); ) {
              Object key = it.next();
              String value = paramValues.get(key).toString();
              JExpression je = JExpr.ref(value);   
              JCase case1 = jc._case(je);
              
              JBlock body = case1.body();
              body.directStatement(concatVariableName + " = \"" + key.toString()+ "\";");
              case1.body()._break();            
        }
        
        JCase defaultCase = jc._default();
        defaultCase.body().directStatement("System.out.println(\"No Valid Value!\");");
        //defaultCase.body().directStatement(concatVariableName + " = \"" + key.toString()+ "\";");
        
     }
     
    return null;
 }
 
 private Map getValueArray(String name) throws SAXException, IOException{
      Document doc;
      Map paramValues = new LinkedHashMap();
     try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder;
            builder = factory.newDocumentBuilder();
            doc = builder.parse("src/test/resources/pluginConfigFile.xml");
                
                NodeList nodeList = doc.getElementsByTagName(name);
                for(int i=0; i<nodeList.getLength(); i++){
                    //if (nodeList.item(i).getAttributes().getNamedItem("param") != null){
                        Object key = nodeList.item(i).getAttributes().getNamedItem("param").getNodeValue();
                        Object value =  nodeList.item(i).getTextContent();
                        paramValues.put(key, value);
                    //}
                }
                
       } catch (ParserConfigurationException ex) {
            Logger.getLogger(MyPluginClass.class.getName()).log(Level.SEVERE, null, ex);
            //paramValues.clear();
            //isFunctionPublic = false;
       }    
        return paramValues;
      
 }
 private void getRequestMap(String valueType) throws SAXException, IOException{
        Document doc = null;
        Object key;
        Object value = null;

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder;
            builder = factory.newDocumentBuilder();
            doc = builder.parse("src/test/resources/pluginConfigFile.xml");
            NodeList nodeList2 = doc.getElementsByTagName(request);
            
            for(int i=0; i<nodeList2.getLength(); i++){
                for(int j=0; j<(nodeList2.item(i).getChildNodes().getLength()); j++){
                    if(valueType.matches(nodeList2.item(i).getChildNodes().item(j).getNodeName())){
                        if (valueType.matches("Parameters")){
                            ParametersMap.clear();
                        }
                        else if (valueType.matches("Variables")){
                            VariablesMap.clear();
                        }
                        for(int k=0; k<nodeList2.item(i).getChildNodes().item(j).getChildNodes().getLength(); k++){
                            key = nodeList2.item(i).getChildNodes().item(j).getChildNodes().item(k).getNodeName();
                            if (key.toString().contains("text")){
                                continue;
                            }                        
                            if (nodeList2.item(i).getChildNodes().item(j).getChildNodes().item(k).getAttributes().getNamedItem("name") != null) {
                                value = nodeList2.item(i).getChildNodes().item(j).getChildNodes().item(k).getAttributes().getNamedItem("name").getNodeValue();
                            }
                            else{
                                value = nodeList2.item(i).getChildNodes().item(j).getChildNodes().item(k).getTextContent();
                            }
                       
                             if (valueType.matches("Parameters")){
                                ParametersMap.put(key, value);
                             }
                             else if (valueType.matches("Variables")){
                                VariablesMap.put(key, value);
                             } 
                        }
                    }
                }
            }
                 
        } catch (ParserConfigurationException ex) {
            Logger.getLogger(MyPluginClass.class.getName()).log(Level.SEVERE, null, ex);
            //isFunctionPublic = false;
        }
 }

 private String getDefaultValue(String name) throws SAXException, IOException{
     //TODO: Rewrite this function to use the RequestMaps already populated earlier: VariablesMap, ParametersMap
     Document doc = null;
   
     try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder;
            builder = factory.newDocumentBuilder();
            doc = builder.parse("src/test/resources/pluginConfigFile.xml");
            
               /*
                NodeList nodeList = doc.getElementsByTagName(name);
                for(int i=0; i<nodeList.getLength(); i++){
                  return nodeList.item(i).getTextContent();
                }
                */
                NodeList nodeList2 = doc.getElementsByTagName(request);
              
                for(int i=0; i<nodeList2.getLength(); i++){
                    for(int j=0; j<(nodeList2.item(i).getChildNodes().getLength()); j++){
                        if("Variables".matches(nodeList2.item(i).getChildNodes().item(j).getNodeName())){
                           for(int k=0; k<(nodeList2.item(i).getChildNodes().item(j).getChildNodes().getLength()); k++){
                              if(name.matches(nodeList2.item(i).getChildNodes().item(j).getChildNodes().item(k).getNodeName())){
                                return nodeList2.item(i).getChildNodes().item(j).getChildNodes().item(k).getTextContent();
                              }     
                           }
                        } 
                        
                    }
                    for(int j=0; j<(nodeList2.item(i).getChildNodes().getLength()); j++){
                        if("Parameters".matches(nodeList2.item(i).getChildNodes().item(j).getNodeName())){
                            for(int k=0; k<(nodeList2.item(i).getChildNodes().item(j).getChildNodes().getLength()); k++){
                              if(name.matches(nodeList2.item(i).getChildNodes().item(j).getChildNodes().item(k).getNodeName())){
                                return "param";
                                ///return nodeList2.item(i).getChildNodes().item(j).getChildNodes().item(k).getAttributes().getNamedItem("name").getNodeValue();  
                                  //return nodeList2.item(i).getChildNodes().item(j).getChildNodes().item(k).getTextContent();
                              }
                            }
                            //return "notparam";
                           /*for(int k=0; k<(nodeList2.item(i).getChildNodes().item(j).getChildNodes().getLength()); k++){
                              if(name.matches(nodeList2.item(i).getChildNodes().item(j).getChildNodes().item(k).getNodeName())){
                                return nodeList2.item(i).getChildNodes().item(j).getChildNodes().item(k).getTextContent();
                              }     
                           }*/
                        } 
                    }
                }
               
                 
                               
       } catch (ParserConfigurationException ex) {
            Logger.getLogger(MyPluginClass.class.getName()).log(Level.SEVERE, null, ex);
            //isFunctionPublic = false;
       }
        
       return "null";
}

 private void privateFunction(String findType) throws SAXException, IOException{
        Document doc;
        isFunctionPrivate = false;
      
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder;
            builder = factory.newDocumentBuilder();
            doc = builder.parse("src/test/resources/pluginConfig.xml");

            NodeList nodeOperation = doc.getElementsByTagName("Operation");
            String testFirstClass = responseClass.name();
            //for(int h=0; h<nodeOperation.getLength(); h++){
               if (nodeOperation.item(0).getAttributes().item(0).getNodeValue() == null ? testFirstClass == null : nodeOperation.item(0).getAttributes().item(0).getNodeValue().equals(testFirstClass)){
                NodeList nodeList =   doc.getElementsByTagName("functionPrivate");
                for(int i=0; i<nodeList.getLength(); i++){
                    //System.out.println("node item attribute node value " + nodeList.item(i).getAttributes().item(0).getNodeValue());
                    if (findType.equals(nodeList.item(i).getAttributes().item(0).getNodeValue())){
                        //TODO: find parent from original xml schema instead of config file
                        // we use the variable of the parent node to call the function to create the parent.
                        if (nodeList.item(i).getAttributes().getNamedItem("vparent") != null) {
                            parentPrv = nodeList.item(i).getAttributes().getNamedItem("vparent").getNodeValue();
                        }
                        isFunctionPrivate = true;
                        break;
                    }
                    else{
                     parentPrv = null;
                     isFunctionPrivate = false;
                    }
                }
// <editor-fold>
            /*for(int i=0; i<nodeListPublic.getLength(); i++){
                System.out.println("public node item0 attribute node value " + nodeListPublic.item(i).getAttributes().item(0).getNodeValue());
               System.out.println("public node item1 attribute node value " + nodeListPublic.item(i).getAttributes().item(1).getNodeValue());
                if (findType.equals(nodeListPublic.item(i).getAttributes().item(0).getNodeValue())){
               isPublic = true;
                break;
                }
            }*/
// </editor-fold>
               }
           // }
        } catch (ParserConfigurationException ex) {
            Logger.getLogger(MyPluginClass.class.getName()).log(Level.SEVERE, null, ex);
        }
      
 }
 private void getPublicFunctionsMap(String findType) throws SAXException, IOException{
        Document doc = null;
        isFunctionPublic = false;
        Object key;
        Object value;

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder;
            builder = factory.newDocumentBuilder();
            doc = builder.parse("src/test/resources/pluginConfig.xml");
            NodeList nodeOperation = doc.getElementsByTagName("Operation");
            String testFirstClass = responseClass.name();
            if (nodeOperation.item(0).getAttributes().item(0).getNodeValue() == null ? testFirstClass == null : nodeOperation.item(0).getAttributes().item(0).getNodeValue().equals(testFirstClass)){
                 NodeList nodeListPublic = doc.getElementsByTagName("functionPublic");
                publicFunctionsMap.clear();
              for(int i=0; i<nodeListPublic.getLength(); i++){
                  //we use the parent type to find get a list of the function to make public for that node
                 if (nodeListPublic.item(i).getAttributes().getNamedItem("tparent") == null)
                        continue;
                 if (findType.equals(nodeListPublic.item(i).getAttributes().getNamedItem("tparent").getNodeValue())){
                    //key = the name of the function (ie: patientFirstName)
                    key = nodeListPublic.item(i).getAttributes().getNamedItem("name").getNodeValue();
                    // value = the type of the object (ie: EnExplicitFamily)
                    value = nodeListPublic.item(i).getAttributes().getNamedItem("thl7").getNodeValue();
                    publicFunctionsMap.put(key, value);
                    parentType = nodeListPublic.item(i).getAttributes().getNamedItem("tparent").getNodeValue();

                    if (nodeListPublic.item(i).getAttributes().getNamedItem("vparent") != null)
                        parentPub = nodeListPublic.item(i).getAttributes().getNamedItem("vparent").getNodeValue();
                    else
                        parentPub = null;

                    isFunctionPublic = true;
                 }
                 else {
                    //isFunctionPublic = false;
                   
                }
              }
            }
         
        } catch (ParserConfigurationException ex) {
            Logger.getLogger(MyPluginClass.class.getName()).log(Level.SEVERE, null, ex);
            isFunctionPublic = false;
        }

 }
// <editor-fold>
/*
 private void getFunctionMod(String findType) throws SAXException, IOException{
        Document doc = null;
        isFunctionPublic = false;
        isFunctionPrivate = false;
        Object key;
        Object value;

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder;
            builder = factory.newDocumentBuilder();
            doc = builder.parse("src/test/resources/pluginConfig.xml");

            NodeList nodeListPublic = doc.getElementsByTagName("function");

            publicFunctionsMap.clear();
            for(int i=0; i<nodeListPublic.getLength(); i++){
               if (nodeListPublic.item(i).getAttributes().getNamedItem("fmod") == null)
                        continue;

               if (findType.equals(nodeListPublic.item(i).getAttributes().getNamedItem("tparent").getNodeValue())){

                   if(nodeListPublic.item(i).getAttributes().getNamedItem("fmod").getNodeValue().equals("Private"))
                       isFunctionPrivate = true;
                   else if(nodeListPublic.item(i).getAttributes().getNamedItem("fmod").getNodeValue().equals("Public"))
                       isFunctionPublic = true;

                    key = nodeListPublic.item(i).getAttributes().getNamedItem("name").getNodeValue();
                    value = nodeListPublic.item(i).getAttributes().getNamedItem("thl7").getNodeValue();
                    publicFunctionsMap.put(key, value);
                    parentType = nodeListPublic.item(i).getAttributes().getNamedItem("tparent").getNodeValue();
                    if (nodeListPublic.item(i).getAttributes().getNamedItem("vparent") != null)
                        parentPub = nodeListPublic.item(i).getAttributes().getNamedItem("vparent").getNodeValue();
                    else
                        parentPub = null;
                    }
                else {
                    //isFunctionPublic = false;

                }
            }

        } catch (ParserConfigurationException ex) {
            Logger.getLogger(MyPluginClass.class.getName()).log(Level.SEVERE, null, ex);
            isFunctionPublic = false;
        }

 }
 */
 // </editor-fold>
 private void getResponseMap(String valueType) throws SAXException, IOException {
     //Document doc = null;
        Object key;
        Object value = null;

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder;
            builder = factory.newDocumentBuilder();
            docResponse = builder.parse("src/test/resources/pluginConfigFile.xml");
            NodeList nodeList2 = docResponse.getElementsByTagName(valueType);
          
            for(int i=0; i<nodeList2.getLength(); i++){
               
                    key = nodeList2.item(i).getNodeName();
                    if (key.toString().contains("text")){
                        continue;
                    }
                    if (nodeList2.item(i).getAttributes().getNamedItem("name") != null){
                        value = nodeList2.item(i).getAttributes().getNamedItem("name").getNodeValue();
                    }
                    if (nodeList2.item(i).getAttributes().getNamedItem("type") != null){
                        String type = nodeList2.item(i).getAttributes().getNamedItem("type").getNodeValue();
                        if (type.matches("public")){
                             publicFunctionsMap2.put(key, value);
                        }
                        else if(type.matches("private")){
                             privateFunctionsMap.put(key, value);
                        }
                    }
                    else{
                         privateFunctionsMap.put(key, value);
                    }
                    if (nodeList2.item(i).getChildNodes().getLength()>0){   
                         for(int j=0; j<(nodeList2.item(i).getChildNodes().getLength()); j++){
                            if (nodeList2.item(i).getChildNodes().item(j).getNodeName().contains("text")){
                                continue;
                            }
                            getResponseMap(nodeList2.item(i).getChildNodes().item(j).getNodeName());
                         }
                    }
            }
                 
        } catch (ParserConfigurationException ex) {
            Logger.getLogger(MyPluginClass.class.getName()).log(Level.SEVERE, null, ex);
            //isFunctionPublic = false;
        }
}
 private boolean isPrivate(String value){
       if(privateFunctionsMap.containsKey(value))
       {
           return true;
       }
     return false;
 
 }
 
 private boolean isPublic(String value){
       if(publicFunctionsMap2.containsKey(value))
       {
           return true;
       }
     return false;
 
 }
 private String getParentNodeName(String value){
     NodeList node = docResponse.getElementsByTagName(value);
     if(node.item(0).getParentNode().getAttributes().getNamedItem("name") != null){
        return node.item(0).getParentNode().getAttributes().getNamedItem("name").getNodeValue();
     }
     return null;
 }
 private String getParentNodeType(String value){
    NodeList node = docResponse.getElementsByTagName(value);
     //for(int i=0; i<nodeList2.getLength(); i++){
       // if(value.matches(nodeList2.item(i).getNodeName())){
            if(node.item(0).getParentNode().getNodeName() != null){
                return node.item(0).getParentNode().getNodeName();
            }
       // }
     //}
     return null;
 }
 //FIX: isParent uses the getParentNodeType which uses the docResponse. it has the whole tree not just for this request type.
 private boolean isParent(String value){
    String parent = getParentNodeType(value);
    if(parent.matches(prevType)){
        return true;
    }
    return false;
 }
 private void getPublicChildFunctionsMap(String value){
    Object key;
    Object ovalue = null; 
    isChildFunctionPublic = false;
    publicFunctionsMap.clear();
    NodeList node = docResponse.getElementsByTagName(value);
     for(int i=0; i<node.getLength(); i++){  
          key = node.item(i).getNodeName();
          if (key.toString().contains("text")){
                        continue;
          }
          if (node.item(i).getChildNodes().getLength()>0){   
              for(int j=0; j<(node.item(i).getChildNodes().getLength()); j++){
                if (node.item(i).getChildNodes().item(j).getNodeName().contains("text")){
                    continue;
                }
                key =   node.item(i).getChildNodes().item(j).getAttributes().getNamedItem("name").getNodeValue();
                ovalue = node.item(i).getChildNodes().item(j).getNodeName();
                publicFunctionsMap.put(key, ovalue);
                isChildFunctionPublic = true;
              }
     }
     }
 }
 private String getReturnType(String value){
     NodeList node = docResponse.getElementsByTagName(value);
     if(node.item(0).getAttributes().getNamedItem("retrunType") != null){
        return node.item(0).getAttributes().getNamedItem("retrunType").getNodeValue();
     }
     return "none";
 }
public void generateResponseClass(Outline outline) throws SAXException, IOException{
      try {
         for (ClassOutline classOutline : outline.getClasses()) {
                //for each class in the outline that contains the word "Response" create a class and add to pcgmCodeModel
                //TODO: consider using the wsdl to get the list of responses
                JCodeModel pcgmCodeModel = new JCodeModel();
                JDefinedClass implClass = classOutline.implClass;
                if ((implClass.name().contains("Response") || implClass.name().contains("MessageType")) && !implClass.fullName().contains("generated")){
                    JDefinedClass pcgmClass = pcgmCodeModel._class("edu.newschool.piim.generatedCode.pcgmHelper"+implClass.name());
                    responseClass = implClass;
                    //call the function that generates the methods in the class created above
                   
                    privateFunctionsMap.clear();
                    publicFunctionsMap2.clear();
                    getResponseMap(implClass.name());
                    generateResponseMethod(outline, implClass.name(), pcgmClass, pcgmCodeModel, "result", null);
                
                  try {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        pcgmCodeModel.build(new SingleStreamCodeWriter(out));
                        //System.out.println("GENERATED CODE:  " + out.toString());
                        String fileName = "pcgmHelper"+responseClass.name();

                        OutputStream f2 = new FileOutputStream(fileName+".java");
                        out.writeTo(f2);

                    } catch (IOException ex) {
                        Logger.getLogger(MyPluginClass.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        } catch (JClassAlreadyExistsException ex) {
            Logger.getLogger(MyPluginClass.class.getName()).log(Level.SEVERE, null, ex);
        }

 }
 
public void generateResponseMethod(Outline outline, String nextType, JDefinedClass pcgmClass, JCodeModel pcgmCodeModel, String varName, JFieldVar theField) throws JClassAlreadyExistsException, SAXException, IOException{
        JMethod pcgmMethod = null;
        String jaxbStmnt = "";
        String classStmnt = "";
        
        
        for (ClassOutline classOutline : outline.getClasses()) {
           JDefinedClass implClass = classOutline.implClass;
         
           //looping through all the classes in the outline to find the one that matches the 'nextType' parameter for which a function may be generated
           if ((implClass.name().matches(nextType)) && !implClass.fullName().contains("generated")){
               //System.out.println("THIS IS THE NEXT TYPE STRING PASSED TO FUNCTION: " + nextType);
               //check if this class is in the configuration file to be created as a private function for internal use
               //privateFunction(implClass.name());
               //getFunctionMod(implClass.name());
               
               String varUp =  String.format( "%s%s", Character.toUpperCase(varName.charAt(0)), varName.substring(1) );
               varUp = "get"+varUp;
              
               //if the method is in the configuration file as privateFunction
               if (isPrivate(implClass.name()) && isParent(implClass.name())){
                   
                   String parentName = getParentNodeName(implClass.name());
                   //create the private method and add the parameter
                   //TODO: make methods static, there is an option for JMod.STATIC question is how to do both private and static??
                   JClass jClassavpImpl;
                   if(getReturnType(implClass.name()).matches("list")){
                           pcgmMethod = pcgmClass.method(JMod.PRIVATE, theField.type(), "pcgm_"+varUp);
                           jClassavpImpl = (JClass) theField.type();
                   }
                   else{
                            pcgmMethod = pcgmClass.method(JMod.PRIVATE, implClass, "pcgm_"+varUp);
                            jClassavpImpl = implClass;
                   }
                   //TODO: the paramenter type needs to have the full name, ie: org.hl7.v3 -- not if we use the initiate that will import the needed classes
                   pcgmMethod.varParam(responseClass, "param1");
                   //create the return variable and add it to the method body
                   JBlock pcgmBlock = pcgmMethod.body();
                   
                  
                   JVar jvar = pcgmBlock.decl(jClassavpImpl, varName);
                   //for the private function we initialize the variable
                   jvar.init(JExpr._new(jClassavpImpl));

                   //parentPrv should should only be null for the first itteration
                   String directStmnt = varName + " = " + "param1" +"." +varUp+ "()";
                  
                   //generate code that calls the parent function passing it param1
                   //TODO: generate code that checks for null values returned by parent function
                   String parentNode = getParentNodeName(implClass.name());
                   if (parentNode != null){
                      String parentUp =  String.format( "%s%s", Character.toUpperCase(parentNode.charAt(0)), parentNode.substring(1) );
                      parentUp = "pcgm_get"+parentUp;
                      directStmnt = varName + " = " + parentUp+"(param1)."+varUp+"()";
                    }
                   //if the nextType is/was a list then we need to use the .get(index) method.
                   if (isList) {
                       if(getReturnType(implClass.name()).matches("list")){
                           
                           
                       }
                       else {
                           directStmnt = directStmnt+".get(0);";
                       }
                     
                   }
                   else {
                       directStmnt = directStmnt+";";
                   }
                   pcgmBlock.directStatement(directStmnt);
                   
                   
                   if(getReturnType(getParentNodeType(implClass.name())).matches("list")){
                           JForLoop forLoop = pcgmBlock._for(); 
                           JVar i = forLoop.init(pcgmCodeModel.INT, "i", JExpr.lit(0));
                           JExpression je = JExpr.direct(parentName+".size()");
                           forLoop.test(JOp.lt(i, je));
                           forLoop.update(JExpr.assignPlus(i, JExpr.lit(1)));
                           forLoop.body().directStatement("This is where the good stuff goes!");
                   }
                   
                   pcgmBlock._return(jvar);
                   
                   
                }
               
                //if the method is in the configuration file as publicFunction
               
                if (isPublic(implClass.name())){
                    String parentName = getParentNodeName(implClass.name()); 
                   //create the public method and add the parameter
                   pcgmMethod = pcgmClass.method(JMod.PUBLIC, String.class, varUp);
                   pcgmMethod.varParam(responseClass, "param1");
                   //create the return variable and add it to the method body
                   JBlock pcgmBlock = pcgmMethod.body();
                   JClass jClassString = pcgmCodeModel.ref(String.class);
                   JVar jvar = pcgmBlock.decl(jClassString, varName);

                   //parentNode should never be null so this stmnt will allways get overridden, unless there is an error
                  String directStmnt = "//ERROR-Public function missing parent in config file.";
                  
                   if (parentName != null){
                      String parentUp =  String.format( "%s%s", Character.toUpperCase(parentName.charAt(0)), parentName.substring(1) );
                      parentUp = "pcgm_get"+parentUp;
                      directStmnt = parentName + " = " + parentUp+"(param1);";
                      String parentType = getParentNodeType(implClass.name());
                      if(parentType != null){
                        JClass jClassString2 = pcgmCodeModel.ref(parentType);
                        pcgmBlock.decl(jClassString2, parentName); 
                      }
                                   
                    }        
    
                   pcgmBlock.directStatement(directStmnt);

                   //TODO: NOW: try to use this for generating: EnExplicitFamily ob = (EnExplicitFamily) o.getValue();
                   if (isSubClass){
                       String ds = varName+"="+parentName+".getCode();";
                       pcgmBlock.directStatement(ds);
                   }

                   if (isListS){
                      //generate code to create a JAXBElement to handle the object we get from the serializable list
                      jaxbStmnt = "JAXBElement o = (JAXBElement)"+parentName+".getContent().get(i);";
                      classStmnt = "o.getValue().getClass().getName().equals(\"org.hl7.v3."+ nextType + "\")";
                      //the rest of the code in this block generates the if stmnts and for loops that set the value of the return variable
                      JConditional jb = pcgmBlock._if(JExpr.direct(parentName + "!= null"));
                      JForLoop forLoop = jb._then()._for();
                      JVar i = forLoop.init(pcgmCodeModel.INT, "i", JExpr.lit(0));
                      JExpression je = JExpr.direct(parentName+".getContent().size()");
                      forLoop.test(JOp.lt(i, je));
                      forLoop.update(JExpr.assignPlus(i, JExpr.lit(1)));
                      forLoop.body().directStatement(jaxbStmnt);
                      JConditional jb2 = forLoop.body()._if(JExpr.direct(classStmnt));
                      jb2._then().directStatement(varName+" = o.getValue().getContent();");
                      //isListS = false;
                    }              
                    pcgmBlock._return(jvar);
                    //isFunctionPublic = false;
 
               }
               
               if (implClass.fields().isEmpty() && implClass._extends() != null){
                    if (implClass._extends().fullName().contains("org.hl7.v3")){
                        isSubClass = true;
                        if(!isListS){
                            
                            //getPublicFunctionsMap(implClass.name());
                            getPublicChildFunctionsMap(implClass.name());
                            if(isChildFunctionPublic){
                                for (Iterator it=publicFunctionsMap.keySet().iterator(); it.hasNext(); ) {
                                 Object key = it.next();
                                 Object value = publicFunctionsMap.get(key);
                                 prevType = implClass.name(); 
                                 generateResponseMethod(outline, value.toString(), pcgmClass, pcgmCodeModel, key.toString(), theField);
                                }
                                isChildFunctionPublic = false;
                            }
                        }
                    }
                }

                //Loop through all the fields in this class
                for (JFieldVar field : implClass.fields().values()){
                    String ftn = field.type().name();
                    //if (ftn.contains("CE"))
                        //System.out.println("this is ce: " + ftn);
                    //System.out.println("field.name: "+field.name()+", field.type.name: "+ ftn+ ", field.class.name: "+ field.getClass().getName());
                    //for(JAnnotationUse annotat : field.annotations()){
                    //    System.out.println("field.annotation: "+ annotat.);
                    //}
                    
                    isList = false;  //identify if this field is a list. List<> => .get(index)
                    isJAXB = false;  //identify if this field is a JAXBElement. => .get(value)
                    isListS = false; //identify if this field is a Serializable list. List<Serializable> => .getContent()
                    isSubClass = false; //identify class that inherit from other classes
                    if (ftn.contains("List<")){
                        if (ftn.contains("List<Serializable>")) {
                            isListS = true;
                        }
                        int size = ftn.length()-1;
                        ftn = ftn.substring(5, size);
                        isList = true;
                    }
                    else if (ftn.contains("JAXBElement")){
                        int size = ftn.length()-1;
                        ftn = ftn.substring(12, size);
                        isJAXB = true;
                    }

                    

                    if (isListS) {
                      //getPublicFunctionsMap(implClass.name());
                        getPublicChildFunctionsMap(implClass.name());
                      if(isChildFunctionPublic){
                         for (Iterator it=publicFunctionsMap.keySet().iterator(); it.hasNext(); ) {
                            Object key = it.next();
                            Object value = publicFunctionsMap.get(key);
                            prevType = implClass.name(); 
                            generateResponseMethod(outline, value.toString(), pcgmClass, pcgmCodeModel, key.toString(), theField);
                         }
                         isChildFunctionPublic = false;
                      }
                    }
                             
                   if (field.type().fullName().contains("org.hl7.v3")) {
                     prevType = implClass.name();  
                     generateResponseMethod(outline, ftn, pcgmClass, pcgmCodeModel, field.name(), field);
                      
                    
                   }
            }
            
           
         } // end of if stmnt to match nexttype

    } //end of outmosts for loop
}  //end of function

} //end of class