package edu.newschool.piim.printmodelxjcplugin;

/**© Copyright 2012 PIIM
 *
 * @author marinekosh
 *
* This file is part of PIIM Canonic GUI Model (PCGM) java plugin prototype project.
* 
* PCGM is supported by the Telemedicine and Advanced Technology Research Center (TATRC) at the U.S. Army Medical Research and Materiel Command (USAMRMC) 
* and developed by Parsons Institute for Information Mapping (PIIM). The prototype source code is in the Public Domain under GNU Lesser General Public License. 
* You will find the text of the GNU Lesser General Public License in the LICENSE File or at http://www.gnu.org/licenses/>.
* 
* PCGM is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation,
* either version 3 of the License, or (at your option) any later version.
* 
* PCGM is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
* See the GNU Lesser General Public License for more details.
* 
*/

import com.sun.codemodel.*;
import com.sun.codemodel.writer.SingleStreamCodeWriter;

import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;
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
import java.lang.String;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;


import java.io.FileOutputStream;
import java.io.OutputStream;
import org.w3c.dom.Document;
import org.w3c.dom.*;




public class MyPluginClass extends Plugin {
Boolean isList = false;
Boolean isJAXB = false;
JClass responseClass = null;
String prevMethod = null;
Boolean isFunctionPrivate = false;
Boolean isFunctionPublic = false;
Boolean isListS= false;
String prevType = null;
JClass prevClass = null;
Map publicFunctionsMapSubList = new LinkedHashMap();
Map publicFunctionsMapWholeList = new LinkedHashMap();
Map privateFunctionsMapWholeList = new LinkedHashMap();
String parentPrv = null;
String parentPub = null;
String parentType;
Boolean isSubClass = false;
Map ParametersMap = new LinkedHashMap();
Map VariablesMap = new LinkedHashMap();

private String request;
private Document docResponse;
private boolean isChildFunctionPublic;
   
//TODO: g- look into why there are duplicate java files in the 'generated' folder, this does not occur at actual CAL wsimport time
//TODO: g- generate java docs - DONE for request parameter enum type
//TODO: g- analyze other operations for config file, consider Green CDA
//TODO: g- write output to .java files -DONE
//TODO: g- where to write the .java files, what is the path? dynamic path setting
//TODO: g- figure out packaging, not use static file location for source
//TODO: g- code optimization, don't loop through the entire outline every time if possible
//TODO: g- code optimization, only do checks on hl7 objects not others like string - DONE
//TODO: g- generate unique message ids/timestamps, use date util -DONE


   
    @Override
    public String getOptionName() {
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
            //Entry point
            generateRequestClass(outline);
            generateResponseClass(outline);
        } catch (IOException ex) {
            Logger.getLogger(MyPluginClass.class.getName()).log(Level.SEVERE, null, ex);
        }

           return true;
    }
 
 private void generateRequestClass(Outline outline) throws SAXException, IOException {
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
                    generateRequestMethodBody(outline, request, pcgmBlock, "RqstMsg");
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
 private void generateRequestMethodBody(Outline outline, String nextType, JBlock pcgmBlock, String varName) throws JClassAlreadyExistsException, SAXException, IOException{
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
               
              /* //used for testing
                if (implClass.name().matches("MCCIMT000100UV01Receiver")){
                    System.out.println("THIS IS THE NEXT TYPE STRING PASSED TO FUNCTION: " + nextType);
                }
              */
              
                //Checks the config file to see if there is an item that contains this variable name
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
                       generateRequestMethodBody(outline, ftn, pcgmBlock, concatVariableName);                      
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
 private void generateResponseClass(Outline outline) throws SAXException, IOException{
      try {
         for (ClassOutline classOutline : outline.getClasses()) {
                //for each class in the outline that contains the word "Response" create a class and add to pcgmCodeModel
                JCodeModel pcgmCodeModel = new JCodeModel();
                JDefinedClass implClass = classOutline.implClass;
                if ((implClass.name().contains("Response") || implClass.name().contains("MessageType")) && !implClass.fullName().contains("generated")){
                    JDefinedClass pcgmClass = pcgmCodeModel._class("edu.newschool.piim.generatedCode.pcgmHelper"+implClass.name());
                    responseClass = implClass;
                    privateFunctionsMapWholeList.clear();
                    publicFunctionsMapWholeList.clear();
                    getResponseMap(implClass.name());
                    JFieldVar justAnyField = implClass.fields().values().iterator().next();
                    //call the function that generates the methods in the class created above
                    generateResponseMethod(outline, implClass.name(), pcgmClass, pcgmCodeModel, "RspnsMsg", "RspnsMsg", justAnyField);
               
                    try {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        pcgmCodeModel.build(new SingleStreamCodeWriter(out));
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
 private void generateResponseMethod(Outline outline, String nextType, JDefinedClass pcgmClass, JCodeModel pcgmCodeModel, String concatVarName, String varName, JFieldVar theField) throws JClassAlreadyExistsException, SAXException, IOException{
        JMethod pcgmMethod = null;
               
        for (ClassOutline classOutline : outline.getClasses()) {
           JDefinedClass implClass = classOutline.implClass;
           
           if(implClass.name().matches("ts")) {
                   System.out.println("before the condition: " + nextType);
               }
           //looping through all the classes in the outline to find the one that matches the 'nextType' parameter for which a function may be generated
           if ((implClass.name().matches(nextType)) && !implClass.fullName().contains("generated")){
               /*//used for testing
               if(implClass.name().matches("TSExplicit")) {
                   System.out.println("THIS IS THE NEXT TYPE STRING PASSED TO FUNCTION: " + nextType);
               }
               */
               String varUp =  String.format( "%s%s", Character.toUpperCase(varName.charAt(0)), varName.substring(1) );
               varUp = "get"+varUp;
              
               String parentName = getParentNodeName(concatVarName);
               String parentUp;
               if (parentName != "none"){
                      parentUp =  String.format( "%s%s", Character.toUpperCase(parentName.charAt(0)), parentName.substring(1) );
                      parentUp = "pcgm_get"+parentUp;                    
              }
               else{
                   parentUp = "";
               } 
              
               //if the method is in the configuration file as privateFunction //&& isParent(concatVarName)
               if (isPrivate(concatVarName, implClass.name())){  
                   
                   //create the private method and add the parameter
                   JClass jClassavpImpl;
                   JBlock pcgmBlock;                    
                   JVar jvar;
                   //if the retrun type specified in the configfile is of type list then use the field name as the retrun type of the method being created
                   if(getReturnType(concatVarName).matches("list")){
                           pcgmMethod = pcgmClass.method(JMod.PRIVATE, pcgmCodeModel.ref(List.class).narrow(implClass), "pcgm_"+varUp);
                           jClassavpImpl = pcgmCodeModel.ref(List.class).narrow(implClass);
                           pcgmBlock = pcgmMethod.body();
                           jvar = pcgmBlock.decl(jClassavpImpl, varName+"List");
                           jvar.init(JExpr._null());
                   }
                   else if(getReturnType(concatVarName).matches("void") && isList){
                           pcgmMethod = pcgmClass.method(JMod.PRIVATE, void.class, "pcgm_"+varUp);
                           jClassavpImpl = pcgmCodeModel.ref(List.class).narrow(implClass);
                           pcgmBlock = pcgmMethod.body();
                           jvar = pcgmBlock.decl(jClassavpImpl, varName+"List");
                           jvar.init(JExpr._null());       
                   }
                   else{
                            pcgmMethod = pcgmClass.method(JMod.PRIVATE, implClass, "pcgm_"+varUp);
                            jClassavpImpl = implClass;
                            pcgmBlock = pcgmMethod.body();
                            jvar = pcgmBlock.decl(jClassavpImpl, varName);
                            jvar.init(JExpr._new(jClassavpImpl));
                   }
                   pcgmMethod.param(responseClass, "param1");
                  
                   //parentPrv should only be null for the first itteration
                   String directStmnt = varName + " = " + "param1" +"." +varUp+ "()";
                  
                   //if the nextType is/was a list then we need to use the .get(index) method.
                   if (isList) {
                       if(getReturnType(concatVarName).matches("list")){
                            directStmnt = varName+"List" + " = " + parentUp+"(param1)."+varUp+"()";
                            directStmnt = directStmnt+";";
                       }
                       else {
                           directStmnt = varName + " = " + parentUp+"(param1)."+varUp+"()";
                           directStmnt = directStmnt+".get(0);";
                       }
                     
                   }
                   else {
                       directStmnt = varName + " = " + parentUp+"(param1)."+varUp+"()";
                       directStmnt = directStmnt+";";
                   }
                                   
                   int lastIndex = concatVarName.lastIndexOf("_");
                   String subString = concatVarName.substring(0, lastIndex);
                   
                   if((getReturnType(subString)).matches("list")){
                           //pcgmMethod.type(pcgmCodeModel.ref(List.class).narrow(implClass)); 
                          
                           JVar jvar2 = pcgmBlock.decl(pcgmCodeModel.ref(List.class).narrow(prevClass), parentName+"List");
                           JVar jvar3 = pcgmBlock.decl(prevClass, parentName);
                           jvar2.init(JExpr.direct(parentUp+"(param1)"));
                           jvar3.init(JExpr._null());
                           JForLoop forLoop = pcgmBlock._for(); 
                           JVar i = forLoop.init(pcgmCodeModel.INT, "i", JExpr.lit(0));
                           JExpression je = JExpr.direct(parentName+"List.size()");
                           forLoop.test(JOp.lt(i, je));
                           forLoop.update(JExpr.assignPlus(i, JExpr.lit(1)));
                           directStmnt = parentName + " = " + parentName +"List.get(i);";
                           
                           JBlock loopBlock = forLoop.body();
                           loopBlock.directStatement(directStmnt);
                           if(isJAXB){
                               loopBlock.decl(implClass, varName, JExpr.direct(parentName+"."+varUp+"().getValue()"));
                           }
                           else{
                               loopBlock.decl(implClass, varName, JExpr.direct(parentName+"."+varUp+"()"));
                           }
                           loopBlock.directStatement(varName+"List"+".add("+varName+");");
                   }
                   else{
                        pcgmBlock.directStatement(directStmnt);
                   }
                   pcgmBlock._return(jvar);
                   
                   
                }
               
                //if the method is in the configuration file as publicFunction
                if (isPublic(concatVarName, implClass.name()) ){
                    createPublicClass(pcgmCodeModel, pcgmClass, pcgmMethod, concatVarName, varName, theField);          
               }                                    
               
                if (implClass.fields().isEmpty() && implClass._extends() != null){
                    if (implClass._extends().fullName().contains("org.hl7.v3")){
                        isSubClass = true;
                        if(!isListS){
                            getPublicChildFunctionsMap(concatVarName);
                            if(isChildFunctionPublic){
                                for (Iterator it=publicFunctionsMapSubList.keySet().iterator(); it.hasNext(); ) {
                                 //key = variableName, value = hl7 type
                                 Object key = it.next();
                                 Object value = publicFunctionsMapSubList.get(key);
                                 String concatVName = concatVarName+ "_" + key.toString();
                                 generateResponseMethod(outline, value.toString(), pcgmClass, pcgmCodeModel, concatVName, key.toString(), theField);
                                }
                                isChildFunctionPublic = false;
                            }
                        }
                    }
                }
               
                //Loop through all the fields in this class
                for (JFieldVar field : implClass.fields().values()){
                    String ftn = field.type().name();
                    
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

                    
                    getPublicChildFunctionsMap(concatVarName);
                    if (isListS) {
                       if(isChildFunctionPublic){
                         for (Iterator it=publicFunctionsMapSubList.keySet().iterator(); it.hasNext(); ) {
                            Object key = it.next();
                            Object value = publicFunctionsMapSubList.get(key);
                            String concatVName = concatVarName+ "_" + key.toString();
                            prevType = implClass.name(); 
                            prevClass = implClass;
                            generateResponseMethod(outline, value.toString(), pcgmClass, pcgmCodeModel, concatVName, key.toString(), theField);
                         }
                         isChildFunctionPublic = false;
                      }
                    }                
                    
                   if (field.type().fullName().contains("org.hl7.v3")) {
                       String concatVName = concatVarName+ "_" + field.name();
                       prevType = implClass.name();
                       prevClass = implClass;
                       generateResponseMethod(outline, ftn, pcgmClass, pcgmCodeModel, concatVName, field.name(), field);         
                   }
                   else if(isChildFunctionPublic){
                        if(publicFunctionsMapSubList.containsKey(field.name()) && publicFunctionsMapSubList.containsValue(ftn)){
                            String concatVName = concatVarName+ "_" + field.name();
                            prevType = implClass.name();
                            prevClass = implClass;
                           createPublicClass(pcgmCodeModel, pcgmClass, pcgmMethod,  concatVName, field.name(), field);
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
 private void createTimeStampMethod(JDefinedClass pcgmClass){
      JFieldVar dateFormatField = pcgmClass.field(JMod.PRIVATE, DateFormat.class, "dateFormat", JExpr._null());
      JFieldVar dateSimpleFormatField = pcgmClass.field(JMod.PRIVATE, SimpleDateFormat.class, "dateSimpleFormat", JExpr._null());
      JFieldVar dateField = pcgmClass.field(JMod.PRIVATE, Date.class, "date", JExpr._null());
      JMethod timeStampMethod = pcgmClass.method(JMod.PRIVATE, String.class, "getTimeStamp");
      timeStampMethod.body().assign(dateFormatField, JExpr.direct("new SimpleDateFormat(\"yyyyMMddHHmmssZ\")"));
      timeStampMethod.body()._return(JExpr.direct("dateFormat.format(new Date()).toString()"));
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
                        Object key = nodeList.item(i).getAttributes().getNamedItem("param").getNodeValue();
                        Object value =  nodeList.item(i).getTextContent();
                        paramValues.put(key, value);
                }
                
       } catch (ParserConfigurationException ex) {
            Logger.getLogger(MyPluginClass.class.getName()).log(Level.SEVERE, null, ex);
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
                              }
                            }     
                        } 
                    }
                }
           
                               
       } catch (ParserConfigurationException ex) {
            Logger.getLogger(MyPluginClass.class.getName()).log(Level.SEVERE, null, ex);
           
       }
        
       return "null";
}
 private void getResponseMap(String valueType) throws SAXException, IOException {
     // this build a flat map (key,value pair) of all the elements in the config file for the response message type(valueType paramenter)
        Object key = null;
        Object value = null;

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder;
            builder = factory.newDocumentBuilder();
            //parses entire config file
            docResponse = builder.parse("src/test/resources/pluginConfigFile.xml");
            //gets only the elements that are for this response message type (ie PatientDemographicsPRPAMT201303UVResponseType)
            NodeList nodeList2 = docResponse.getElementsByTagName(valueType);
          
            for(int i=0; i<nodeList2.getLength(); i++){
                    // the key is the concat variable name (ie RspnsMsg_subject_patientPerson_name)
                    key = nodeList2.item(i).getNodeName();
                    if (key.toString().contains("text")){
                        continue;
                    }
                    //the value is the hl7 type
                    if (nodeList2.item(i).getAttributes().getNamedItem("type") != null){
                        value = nodeList2.item(i).getAttributes().getNamedItem("type").getNodeValue();
                    }
                    //add the elements to the local maps according to their function mod
                    if (nodeList2.item(i).getAttributes().getNamedItem("functionMod") != null){
                        String type = nodeList2.item(i).getAttributes().getNamedItem("functionMod").getNodeValue();
                        if (type.matches("public")){
                             publicFunctionsMapWholeList.put(key, value);
                        }
                        else if(type.matches("private")){
                             privateFunctionsMapWholeList.put(key, value);
                        }
                    }
                    else{
                        //if it does not explicitly say what the functionMod is then its private
                         privateFunctionsMapWholeList.put(key, value);
                    }
                    //dig into the next level of children 
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
 private boolean isPrivate(String keyValue, String valueValue){
     //in the local private map list checking if key/value pair exists
       if(privateFunctionsMapWholeList.containsKey(keyValue) && privateFunctionsMapWholeList.containsValue(valueValue))
       {
           return true;
       }
     return false;
 
 }
 private boolean isPublic(String keyValue, String valueValue){
       if(publicFunctionsMapWholeList.containsKey(keyValue) || publicFunctionsMapWholeList.containsValue(valueValue))
       {
           return true;
       }
     return false;
 
 }
 private String getParentNodeName(String value){
     NodeList nodeList = docResponse.getElementsByTagName(value);
     for(int i=0; i<nodeList.getLength(); i++){
        if(nodeList.item(i).getParentNode().getAttributes().getNamedItem("name") != null){
          String pNode = nodeList.item(i).getParentNode().getAttributes().getNamedItem("name").getNodeValue();
          //if (privateFunctionsMapWholeList.containsValue(pNode) || publicFunctionsMapWholeList.containsValue(pNode)){
                    return pNode;
                }
        //}
     }
     return "none";
 }
 private String getParentElementName(String value){
     NodeList nodeList = docResponse.getElementsByTagName(value);
      for(int i=0; i<nodeList.getLength(); i++){
          String pNode = nodeList.item(i).getParentNode().getNodeName();
          if (privateFunctionsMapWholeList.containsValue(pNode) || publicFunctionsMapWholeList.containsValue(pNode)){
                    return pNode;
                }
      }
     return "none";
 }
 private String getParentNodeType(String value){     
    NodeList nodeList = docResponse.getElementsByTagName(value);
    String parentNodeType;
    for(int i=0; i<nodeList.getLength(); i++){
         if(nodeList.item(0).getParentNode().getAttributes().getNamedItem("type") != null){
            parentNodeType = nodeList.item(i).getParentNode().getAttributes().getNamedItem("type").getNodeValue();
            return parentNodeType;
         }
         //String iNode = node.item(i).getNodeName();
        // if(pNode.matches(prevType)){
            // if (privateFunctionsMapWholeList.containsKey(pNode) || publicFunctionsMapWholeList.containsKey(pNode) || privateFunctionsMapWholeList.containsKey(iNode) || publicFunctionsMapWholeList.containsKey(iNode) ){
                
            //}
        // }
         
        
    }
    
    /*String pNode = node.item(0).getParentNode().getNodeName();
    String iNode = node.item(0).getNodeName();
    if(pNode != null){
        if (privateFunctionsMap.containsKey(pNode) || publicFunctionsMap2.containsKey(pNode) || privateFunctionsMap.containsKey(iNode) || publicFunctionsMap2.containsKey(iNode) ){
          return pNode;
        }
    }  */ 
     return "none";
 }
 private boolean isParent(String value){
    String parent = getParentNodeType(value);
    if (parent == null){
        return false;
    }
    if(parent.matches(prevType)){
        return true;
    }
    return false;
 }
 private void getPublicChildFunctionsMap(String value){
    Object key;
    Object ovalue = null; 
    isChildFunctionPublic = false;
    publicFunctionsMapSubList.clear();
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
                key = node.item(i).getChildNodes().item(j).getAttributes().getNamedItem("name").getNodeValue();
                ovalue = node.item(i).getChildNodes().item(j).getAttributes().getNamedItem("type").getNodeValue();
              
                publicFunctionsMapSubList.put(key, ovalue);
                isChildFunctionPublic = true;
              }
     }
     }
 }
 private String getReturnType(String value){
     NodeList nodeList = docResponse.getElementsByTagName(value);
     for(int i=0; i<nodeList.getLength(); i++){
        if(nodeList.item(i).getAttributes().getNamedItem("returnType") != null){
            return nodeList.item(i).getAttributes().getNamedItem("returnType").getNodeValue();
        }
     }
     return "none";
 }
 private String getPublicName(String value){
      NodeList nodeList = docResponse.getElementsByTagName(value);
     for(int i=0; i<nodeList.getLength(); i++){
        if(nodeList.item(i).getAttributes().getNamedItem("namePublic") != null){
            return nodeList.item(i).getAttributes().getNamedItem("namePublic").getNodeValue();
        }
     }
     return "none";
 }
 private void createPublicClass(JCodeModel pcgmCodeModel, JDefinedClass pcgmClass, JMethod pcgmMethod, String concatVarName, String varName, JFieldVar field){
   
               String parentName = getParentNodeName(concatVarName);
               String parentUp;
               String varUp =  String.format( "%s%s", Character.toUpperCase(varName.charAt(0)), varName.substring(1) );
                if (parentName != "none"){
                      parentUp =  String.format( "%s%s", Character.toUpperCase(parentName.charAt(0)), parentName.substring(1) );
                      parentUp = "pcgm_get"+parentUp;
                     
              }
               else{
                   parentUp = "";
               } 
               pcgmMethod = pcgmClass.method(JMod.PUBLIC, String.class, getPublicName(concatVarName));
               pcgmMethod.param(responseClass, "param1");
                              
               //create the return variable and add it to the method body
                   JBlock pcgmBlock = pcgmMethod.body();
                   JClass jClassString = pcgmCodeModel.ref(String.class);
                   JVar jvar = pcgmBlock.decl(jClassString, varName);

                   //parentNode should never be null so this stmnt will allways get overridden, unless there is an error
                  String directStmnt = "//ERROR-Public function missing parent in config file.";
                  
                   if (parentName != null){
                      directStmnt = parentName + " = " + parentUp+"(param1);";
                      String parentType = getParentNodeType(concatVarName);
                      if(parentType != null){
                        JClass jClassString2 = pcgmCodeModel.ref(parentType);
                        pcgmBlock.decl(jClassString2, parentName); 
                      }
                                   
                    }        
    
                   pcgmBlock.directStatement(directStmnt);

                   if (isListS){
                      //generate code to create a JAXBElement to handle the object we get from the serializable list
                      String jaxbStmnt = "JAXBElement o = (JAXBElement)"+parentName+".getContent().get(i);";
                      String classStmnt = "o.getValue().getClass().getName().equals(\"org.hl7.v3."+ field.type().name() + "\")";
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
                    }
                   else{
                       String ds = varName+"="+parentName+".get"+varUp+"();";
                       pcgmBlock.directStatement(ds);
                   }
                    pcgmBlock._return(jvar);                             
}
} //end of class