package de.elnarion.util.plantuml.generator;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import de.elnarion.util.plantuml.generator.classdiagram.ClassType;
import de.elnarion.util.plantuml.generator.classdiagram.ClassifierType;
import de.elnarion.util.plantuml.generator.classdiagram.RelationshipType;
import de.elnarion.util.plantuml.generator.classdiagram.UMLClass;
import de.elnarion.util.plantuml.generator.classdiagram.UMLField;
import de.elnarion.util.plantuml.generator.classdiagram.UMLMethod;
import de.elnarion.util.plantuml.generator.classdiagram.UMLRelationship;
import de.elnarion.util.plantuml.generator.classdiagram.VisibilityType;
import de.elnarion.util.plantuml.generator.config.PlantUMLConfig;
import de.elnarion.util.plantuml.generator.config.PlantUMLConfigBuilder;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;

/**
 * This class provides the ability to generate a PlantUML class diagram out of a
 * list of package names. Therefore this class scans directories and jars for
 * all classes contained directly in these packages and generates a class
 * diagram out of them via reflection
 * <p>
 * To be able to get the right classes you have to provide the necessary
 * ClassLoader, which is able to load these classes.
 * <p>
 * Currently this generator supports the following class types:
 * <ul>
 * <li>class</li>
 * <li>abstract class</li>
 * <li>annotation</li>
 * <li>enum</li>
 * <li>interface</li>
 * </ul>
 * <p>
 * with some restrictions:
 * <p>
 * The type annotation does not contain any further information than its name
 * and no Annotation relationships are included in the diagram.
 * <p>
 * The type enum contains only the constants.
 * <p>
 * <p>
 * All other types contain field and method informations.
 * <p>
 * Relationships are included for
 * <ul>
 * <li>inheritance</li>
 * <li>realization</li>
 * <li>aggregation (only for List and Set types)</li>
 * <li>usage (only direct usage via field)
 * </ul>
 * <p>
 * You can hide all fields via hideFields parameter or you can hide all methods
 * via hideMethods parameter.
 * <p>
 * If you want to hide a list of classes you have to provide a String List with
 * full qualified class names.
 */
public class PlantUMLClassDiagramGenerator {

    private static final String CLASS_ENDING = ".class";
    private PlantUMLConfig plantUMLConfig;
    private Map<String, UMLClass> classes;
    private List<Class<?>> resolvedClasses = new ArrayList<>();
    private Map<UMLClass, List<UMLRelationship>> classesAndRelationships;

    /**
     * Instantiates a new Plant UML diagram generator.
     *
     * @param paramClassloader     ClassLoader - the ClassLoader used for loading
     *                             all diagram classes
     * @param paramScanPackages    List&lt;String&gt; - all the packages which
     *                             directly contained classes are the base for the
     *                             class diagram
     * @param paramBlacklistRegexp String - regular expression used to reduce the
     *                             amount of classes defined by the packages to scan
     * @param paramHideClasses     List&lt;String&gt; - the full qualified class
     *                             names which should be hidden in the resulting
     *                             diagram (they are not excluded from the diagram,
     *                             they are just hidden)
     * @param paramHideFields      boolean - true, if fields should be hidden,
     *                             false, if not
     * @param paramHideMethods     boolean - true, if methods should be hidden,
     *                             false, if not
     */
    public PlantUMLClassDiagramGenerator(final ClassLoader paramClassloader, final List<String> paramScanPackages,
                                         final String paramBlacklistRegexp, final List<String> paramHideClasses, final boolean paramHideFields,
                                         final boolean paramHideMethods) {
        this(new PlantUMLConfigBuilder(paramBlacklistRegexp, paramScanPackages).withHideFieldsParameter(paramHideFields)
                .withClassLoader(paramClassloader).withHideMethods(paramHideMethods).withHideClasses(paramHideClasses)
                .build());
    }

    /**
     * Instantiates a new plant UML class diagram generator.
     *
     * @param paramClassloader     ClassLoader - the ClassLoader used for loading
     *                             all diagram classes
     * @param paramWhitelistRegexp Regular expression used to define all classes
     *                             which should be part of the diagram (alternative
     *                             to scan packages definition)
     * @param paramHideClasses     List&lt;String&gt; - the full qualified class
     *                             names which should be hidden in the resulting
     *                             diagram (they are not excluded from the diagram,
     *                             they are just hidden)
     * @param paramHideFields      boolean - true, if fields should be hidden,
     *                             false, if not
     * @param paramHideMethods     boolean - true, if methods should be hidden,
     *                             false, if not
     * @param paramScanPackages    List&lt;String&gt; - all the packages which
     *                             should be used as basis for the whitelist scan
     */
    public PlantUMLClassDiagramGenerator(final ClassLoader paramClassloader, final String paramWhitelistRegexp,
                                         final List<String> paramHideClasses, final boolean paramHideFields, final boolean paramHideMethods,
                                         final List<String> paramScanPackages) {
        this(new PlantUMLConfigBuilder(paramScanPackages, paramWhitelistRegexp).withHideFieldsParameter(paramHideFields)
                .withClassLoader(paramClassloader).withHideMethods(paramHideMethods).withHideClasses(paramHideClasses)
                .build());
    }

    public PlantUMLClassDiagramGenerator(final PlantUMLConfig paramPlantUMLConfig) {
        plantUMLConfig = paramPlantUMLConfig;
        classesAndRelationships = new HashMap<>();
        classes = new HashMap<>();
    }

    /**
     * Generate the class diagram string for all classes in the configured packages.
     *
     * @return String - the text containing all Plant UML class diagram definitions
     * @throws IOException            - thrown if a class or jar File could not be
     *                                read
     * @throws ClassNotFoundException - thrown if a class in a package could not be
     *                                found or if a package does not contain any
     *                                class information
     */
    public String generateDiagramText() throws ClassNotFoundException, IOException {
        resolvedClasses.clear();
        // read all classes from directories or jars
        resolvedClasses.addAll(getAllDiagramClasses());
        // sort all classes for a reliable sorted result
        Collections.sort(resolvedClasses, (o1, o2) -> o1.getName().compareTo(o2.getName()));
        // map java classes to UMLClass, UMLField, UMLMethod and UMLRelationship objects
        for (final Class<?> clazz : resolvedClasses) {
            mapToDomainClasses(clazz);
        }
        // build the Plant UML String by calling the corresponding method on all
        // PlantUMLDiagramElements
        final StringBuilder builder = new StringBuilder();
        builder.append("@startuml");
        builder.append(plantUMLConfig.getDiagramDirection());
        builder.append(System.lineSeparator());
        builder.append(System.lineSeparator());

        final List<UMLClass> listToCompare = new ArrayList<>();
        listToCompare.addAll(classes.values());
        // because the ordered list could be changed in between, sort the list
        Collections.sort(listToCompare, (o1, o2) -> o1.getName().compareTo(o2.getName()));
        final Collection<UMLClass> classesList = listToCompare;
        final List<UMLRelationship> relationships = new ArrayList<>();
        // add all class information to the diagram (includes field and method
        // information)
        // because of the full qualified class names packages are added automatically
        // and need not be added manually
        for (final UMLClass clazz : classesList) {
            relationships.addAll(classesAndRelationships.get(clazz));
            builder.append(clazz.getDiagramText(plantUMLConfig.isSimplifyDiagrams()));
            builder.append(System.lineSeparator());
            builder.append(System.lineSeparator());
        }
        builder.append(System.lineSeparator());
        builder.append(System.lineSeparator());
        // because the ordered list could be changed in between, sort the list
        Collections.sort(relationships, (o1, o2) -> o1.getDiagramText(plantUMLConfig.isSimplifyDiagrams()).compareTo(o2.getDiagramText(plantUMLConfig.isSimplifyDiagrams())));
        // add all class relationships to the diagram
        for (final UMLRelationship relationship : relationships) {
            builder.append(relationship.getDiagramText(plantUMLConfig.isSimplifyDiagrams()));
            builder.append(System.lineSeparator());
        }
        addHideToggles(builder, classesList, relationships);
        builder.append(System.lineSeparator());
        builder.append(System.lineSeparator());
        // add diagram end to text
        builder.append("@enduml");
        return builder.toString();
    }

    /**
     * Adds the hide toggles to the class diagram string. Toggles are set for hide
     * methods, hide field and for each full qualified class name in the list
     * hideClasses. Only if there are classes and relationships in the diagram the
     * toggles are set.
     *
     * @param paramBuilder     - StringBuilder - the builder used to generate the
     *                         class diagram text
     * @param paramClassesList - Collection&lt;{@link UMLClass}&gt; - the list of
     *                         all classes in the class diagram
     * @param relationships    - List&lt;{@link UMLRelationship}&gt; - the list of
     *                         all relationships in the class diagram
     */
    private void addHideToggles(final StringBuilder paramBuilder, final Collection<UMLClass> paramClassesList,
                                final List<UMLRelationship> relationships) {
        if ((paramClassesList != null && !paramClassesList.isEmpty()) || (!relationships.isEmpty())) {
            if (plantUMLConfig.isHideFields()) {
                paramBuilder.append(System.lineSeparator());
                paramBuilder.append("hide fields");
            }
            if (plantUMLConfig.isHideMethods()) {
                paramBuilder.append(System.lineSeparator());
                paramBuilder.append("hide methods");
            }
            if (plantUMLConfig.getHideClasses() != null && !plantUMLConfig.getHideClasses().isEmpty()) {
                for (final String hideClass : plantUMLConfig.getHideClasses()) {
                    paramBuilder.append(System.lineSeparator());
                    paramBuilder.append("hide ");
                    paramBuilder.append(hideClass);
                }
            }
        }
    }

    /**
     * Maps the java class to a {@link UMLClass}.
     *
     * @param paramClassObject - Class&lt;?&gt; - the java class object to be
     *                         processed
     */
    private void mapToDomainClasses(final Class<?> paramClassObject) {
        if (!includeClass(paramClassObject)) {
            return;
        }

        final int modifiers = paramClassObject.getModifiers();
        VisibilityType visibilityType = VisibilityType.PUBLIC;
        if (Modifier.isPrivate(modifiers)) {
            visibilityType = VisibilityType.PRIVATE;
        } else if (Modifier.isProtected(modifiers)) {
            visibilityType = VisibilityType.PROTECTED;
        }
        ClassType classType = ClassType.CLASS;
        if (paramClassObject.isAnnotation()) {
            classType = ClassType.ANNOTATION;
        } else if (paramClassObject.isEnum()) {
            classType = ClassType.ENUM;
        } else if (paramClassObject.isInterface()) {
            classType = ClassType.INTERFACE;
        } else if (Modifier.isAbstract(modifiers)) {
            classType = ClassType.ABSTRACT_CLASS;
        }

        final UMLClass umlClass = new UMLClass(visibilityType, classType, new ArrayList<UMLField>(),
                new ArrayList<de.elnarion.util.plantuml.generator.classdiagram.UMLMethod>(),
                plantUMLConfig.isSimplifyDiagrams() ? paramClassObject.getSimpleName() : paramClassObject.getName(),
                new ArrayList<String>());
        final List<UMLRelationship> relationships = new ArrayList<>();
        classesAndRelationships.put(umlClass, relationships);
        classes.put(paramClassObject.getName(), umlClass);

        if (classType == ClassType.ENUM) {
            addEnumConstants(paramClassObject, umlClass);
        } else {
            addFields(paramClassObject.getDeclaredFields(), paramClassObject.getDeclaredMethods(), umlClass);
            addMethods(paramClassObject.getDeclaredMethods(), paramClassObject.getDeclaredFields(), umlClass);
        }
        addSuperClassRelationship(paramClassObject, umlClass);
        addInterfaceRelationship(paramClassObject, umlClass);
        addAnnotationRelationship(paramClassObject, umlClass);
    }

    /**
     * Reads all enum constants from the java enum class object and adds them as
     * {@link UMLField} objects to the given {@link UMLClass} object. Only constants
     * are included, values are ignored.
     *
     * @param paramClassObject - Class&lt;?&gt; - the class object of the enum to be
     *                         processed
     * @param paramUmlClass    - {@link UMLClass} - the UML class object where the
     *                         field information should be added
     */
    private void addEnumConstants(final Class<?> paramClassObject, final UMLClass paramUmlClass) {
        final Object[] enumConstants = paramClassObject.getEnumConstants();
        for (final Object enumConstant : enumConstants) {
            final UMLField field = new UMLField(ClassifierType.NONE, VisibilityType.PUBLIC, enumConstant.toString(),
                    null);
            paramUmlClass.addField(field);
        }
    }

    /**
     * Adds an association relationship for each Annotation of a class which is also
     * part of the diagram (needs to be an element of one of the configured
     * packages).
     *
     * @param paramClassObject - Class&lt;?&gt; - the class object which should be
     *                         checked for annotation relationships
     * @param umlClass         - {@link UMLClass} - the UML class from which the
     *                         relationship starts from
     */
    private void addAnnotationRelationship(final Class<?> paramClassObject, final UMLClass umlClass) {
        final Annotation[] annotations = paramClassObject.getAnnotations();
        if (annotations != null) {
            for (final Annotation annotation : annotations) {
                if (includeClass(annotation.getClass())) {
                    final UMLRelationship relationship = new UMLRelationship(null, null, null,
                            paramClassObject.getName(), annotation.getClass().getName(), RelationshipType.ASSOCIATION);
                    addRelationship(umlClass, relationship);
                }
            }
        }
    }

    /**
     * Little helper method which links an {@link UMLRelationship} object to an
     * {@link UMLClass} in the internal map.
     *
     * @param paramUmlClass        - {@link UMLClass} - the class which should be
     *                             linked to the {@link UMLRelationship}
     * @param paramUmlRelationship - {@link UMLRelationship} - the relationship
     *                             which should be linked to the {@link UMLClass}
     */
    private void addRelationship(final UMLClass paramUmlClass, final UMLRelationship paramUmlRelationship) {
        List<UMLRelationship> relationshipList = classesAndRelationships.computeIfAbsent(paramUmlClass,
                k -> new ArrayList<>());
        if (relationshipList == null) {
            relationshipList = new ArrayList<>();
            classesAndRelationships.put(paramUmlClass, relationshipList);
        }
        relationshipList.add(paramUmlRelationship);
    }

    /**
     * Adds a realization relationship for each interface the given java class
     * object implements and which is also part of the class diagram.
     *
     * @param paramClassObject - Class&lt;?&gt; - the class object which should be
     *                         checked for interface relationships
     * @param paramUmlClass    - {@link UMLClass} - the UML class from which the
     *                         relationship starts from
     */
    private void addInterfaceRelationship(final Class<?> paramClassObject, final UMLClass paramUmlClass) {
        final Class<?>[] interfaces = paramClassObject.getInterfaces();
        if (interfaces != null) {
            for (final Class<?> interfaceElement : interfaces) {
                if (includeClass(interfaceElement)) {
                    final UMLRelationship relationship = new UMLRelationship(null, null, null,
                            paramClassObject.getName(), interfaceElement.getName(), RelationshipType.REALIZATION);
                    addRelationship(paramUmlClass, relationship);
                }
            }
        }
    }

    /**
     * Adds a inheritance relationship for the super class of the given java class
     * object and which needs also to be part of the class diagram.
     *
     * @param paramClassObject - Class&lt;?&gt; - the class object which should be
     *                         checked for super class relationship
     * @param paramUmlClass    - {@link UMLClass} - the UML class from which the
     *                         relationship starts from
     */
    private void addSuperClassRelationship(final Class<?> paramClassObject, final UMLClass paramUmlClass) {
        final Class<?> superClass = paramClassObject.getSuperclass();
        if (superClass != null && includeClass(superClass)) {
            final UMLRelationship relationship = new UMLRelationship(null, null, null, paramClassObject.getName(),
                    superClass.getName(), RelationshipType.INHERITANCE);
            addRelationship(paramUmlClass, relationship);
        }
    }

    /**
     * Iterates over an array of declared methods of a java class, creates
     * {@link UMLMethod} objects and adds them to the given {@link UMLClass} object.
     * <p>
     * If a declared method is a getter or setter method of a declared field it is
     * ignored.
     *
     * @param paramDeclaredMethods - Method[] - the array of declared methods in the
     *                             java class corresponding to the given
     *                             {@link UMLClass} object
     * @param paramDeclaredFields  - Field[] - the array of declared fields in the
     *                             java class corresponding to the given
     *                             {@link UMLClass} object
     * @param paramUmlClass        - {@link UMLClass} - the uml class where the
     *                             {@link UMLMethod} objects should be added
     */
    private void addMethods(final Method[] paramDeclaredMethods, final Field[] paramDeclaredFields,
                            final UMLClass paramUmlClass) {
        if (paramDeclaredMethods != null) {
            for (final Method method : paramDeclaredMethods) {
                final String methodName = method.getName();
                // ignore normal getters and setters
                if ((methodName.startsWith("get") || methodName.startsWith("set") || methodName.startsWith("is"))
                        && paramDeclaredFields != null && isGetterOrSetterMethod(method, paramDeclaredFields)) {
                    continue;
                }
                // Do not add method if they should be ignored/removed
                if (plantUMLConfig.isRemoveMethods())
                    continue;
                // if there is a blacklist for method and the method name matches it, then
                // ignore/remove the field
                if (plantUMLConfig.getMethodBlacklistRegexp() != null
                        && methodName.matches(plantUMLConfig.getMethodBlacklistRegexp()))
                    continue;
                String returnType;
                if (plantUMLConfig.isSimplifyDiagrams()) {
                    returnType = method.getReturnType().getSimpleName();
                } else {
                    returnType = method.getReturnType().getName();
                }
                returnType = removeJavaLangPackage(returnType);
                final Class<?>[] parameterTypes = method.getParameterTypes();
                final Map<String, String> parameters = convertToParameterStringMap(parameterTypes);
                final int modifier = method.getModifiers();
                final VisibilityType visibilityType = getVisibility(modifier);
                // check if method should be visible by maximum visibility
                if (!visibilityOk(plantUMLConfig.getMaxVisibilityMethods(), visibilityType))
                    continue;
                final ClassifierType classifierType = getClassifier(modifier);
                if (plantUMLConfig.getMethodClassifierToIgnore().contains(classifierType))
                    continue;
                final List<String> stereotypes = new ArrayList<>();
                if (method.isAnnotationPresent(Deprecated.class)) {
                    stereotypes.add("deprecated");
                }
                if (Modifier.isSynchronized(modifier)) {
                    stereotypes.add("synchronized");
                }
                final de.elnarion.util.plantuml.generator.classdiagram.UMLMethod umlMethod = new de.elnarion.util.plantuml.generator.classdiagram.UMLMethod(
                        classifierType, visibilityType, returnType, methodName, parameters, stereotypes);
                paramUmlClass.addMethod(umlMethod);
            }
        }
    }

    /**
     * Removes the java lang package string from a full qualified class name. This
     * makes it easier to read the class diagram.
     *
     * @param paramTypeName - String - the full qualified type name
     * @return String - the type name without the java.lang package name
     */
    private String removeJavaLangPackage(String paramTypeName) {
        if (paramTypeName.startsWith("java.lang.")) {
            paramTypeName = paramTypeName.substring("java.lang.".length(), paramTypeName.length());
        }
        return paramTypeName;
    }

    /**
     * Gets the classifier type (none, abstract_static, static, abstract) from the
     * modifier int.
     *
     * @param paramModifier - int - the modifier used for determining the classifier
     *                      type
     * @return {@link ClassifierType} - the classifier type
     */
    private ClassifierType getClassifier(final int paramModifier) {
        ClassifierType classifierType = ClassifierType.NONE;
        if (Modifier.isStatic(paramModifier)) {
            if (Modifier.isAbstract(paramModifier)) {
                classifierType = ClassifierType.ABSTRACT_STATIC;
            } else {
                classifierType = ClassifierType.STATIC;
            }
        } else if (Modifier.isAbstract(paramModifier)) {
            classifierType = ClassifierType.ABSTRACT;
        }
        return classifierType;
    }

    /**
     * Gets the visibility type (package_private, public, private, protected) from
     * the modifier int.
     *
     * @param paramModifier - int - the modifier used for determining the visibility
     *                      type
     * @return {@link VisibilityType} - the visibility type
     */
    private VisibilityType getVisibility(final int paramModifier) {
        VisibilityType visibilityType = VisibilityType.PACKAGE_PRIVATE;
        if (Modifier.isPublic(paramModifier)) {
            visibilityType = VisibilityType.PUBLIC;
        } else if (Modifier.isPrivate(paramModifier)) {
            visibilityType = VisibilityType.PRIVATE;
        } else if (Modifier.isProtected(paramModifier)) {
            visibilityType = VisibilityType.PROTECTED;
        }
        return visibilityType;
    }

    /**
     * Converts an array of parameter types to a string map with synthetic parameter
     * names and their mapped full qualified type name.
     * <p>
     * Parameter names are synthetic because in Java 7 the parameter names cannot be
     * determined in all cases.
     *
     * @param paramParameterTypes - Class&lt;?&gt;[] - the array of all parameter
     *                            types which should be processed
     * @return Map&lt;String,String&gt; - the result
     */
    private Map<String, String> convertToParameterStringMap(final Class<?>[] paramParameterTypes) {
        final Map<String, String> parameters = new LinkedHashMap<>();
        if (paramParameterTypes != null) {
            int counter = 1;
            for (final Class<?> parameter : paramParameterTypes) {
                String parameterName = parameter.getName();
                if (parameterName.lastIndexOf('.') > 0) {
                    parameterName = parameterName.substring(parameterName.lastIndexOf('.') + 1, parameterName.length());
                }
                parameterName = "param" + parameterName + counter;
                if (plantUMLConfig.isSimplifyDiagrams()) {
                    parameters.put(parameterName, removeJavaLangPackage(parameter.getSimpleName()));
                } else {
                    parameters.put(parameterName, removeJavaLangPackage(parameter.getName()));
                }
                counter++;
            }
        }
        return parameters;
    }

    /**
     * Checks if a method is a getter or a setter method of a declared field.
     *
     * @param paramMethod         - Method - the method to be checked
     * @param paramDeclaredFields - Field[] - the declared fields used for the check
     * @return true, if is getter or setter method
     */
    private boolean isGetterOrSetterMethod(final Method paramMethod, final Field[] paramDeclaredFields) {
        String methodName = paramMethod.getName();
        if (methodName.startsWith("get")) {
            methodName = methodName.substring(3, methodName.length());
        } else if (methodName.startsWith("is")) {
            methodName = methodName.substring(2, methodName.length());
        } else if (methodName.startsWith("set")) {
            methodName = methodName.substring(3, methodName.length());
        }
        for (final Field field : paramDeclaredFields) {
            final String fieldName = field.getName();
            if (fieldName.equalsIgnoreCase(methodName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates {@link UMLField} or {@link UMLRelationship} objects for all given
     * fields. And adds them to the given {@link UMLClass} object.
     * <p>
     * If a field type is part of the class diagram (directly or via List or Set) it
     * is added as {@link UMLRelationship}. If not it is added as {@link UMLField}
     * object.
     * <p>
     * If a field has a getter an a setter method its visibility is upgraded to
     * public.
     *
     * @param paramDeclaredFields  Field[] - the Field objects which are the base
     *                             for the {@link UMLField} or
     *                             {@link UMLRelationship} objects.
     * @param paramDeclaredMethods Method[] - the method objects of the java class
     *                             corresponding to the given {@link UMLClass}
     *                             object
     * @param paramUmlClass        {@link UMLClass} - the uml class objects to which
     *                             the {@link UMLField} or {@link UMLRelationship}
     *                             objects should be added
     */
    private void addFields(final Field[] paramDeclaredFields, final Method[] paramDeclaredMethods,
                           final UMLClass paramUmlClass) {
        if (paramDeclaredFields != null) {
            for (final java.lang.reflect.Field field : paramDeclaredFields) {
                final Class<?> type = field.getType();
                final boolean relationshipAdded = addAggregationRelationship(paramUmlClass, field);
                if (relationshipAdded) {
                    // do nothing - skip processing
                } else if (includeClass(type)) {
                    final UMLRelationship relationship = new UMLRelationship(null, null, field.getName(),
                            field.getDeclaringClass().getName(), type.getName(), RelationshipType.DIRECTED_ASSOCIATION);
                    addRelationship(paramUmlClass, relationship);
                } else {
                    addFieldToUMLClass(paramUmlClass, field, type, paramDeclaredMethods);
                }
            }
        }

    }

    /**
     * Creates {@link UMLField} object for the given field if the field should be
     * added to the diagram (blacklist-check,remove-check,visibility-check).
     *
     * @param paramUmlClass        {@link UMLClass} - the uml class to which the
     *                             {@link UMLField} should be added
     * @param field                Field - the field objects which are basse for the
     *                             {@link UMLField}
     * @param type                 Class - the type of the field
     * @param paramDeclaredMethods Method[] - the method objects of the java class
     *                             corresponding to the given {@link UMLClass}
     *                             object
     */
    private void addFieldToUMLClass(final UMLClass paramUmlClass, final java.lang.reflect.Field field,
                                    final Class<?> type, Method[] paramDeclaredMethods) {
        // Do not add field if they should be ignored/removed
        if (plantUMLConfig.isRemoveFields())
            return;
        // if there is a blacklist for field and the field name matches it, then
        // ignore/remove the field
        if (plantUMLConfig.getFieldBlacklistRegexp() != null
                && field.getName().matches(plantUMLConfig.getFieldBlacklistRegexp()))
            return;
        final int modifier = field.getModifiers();
        final ClassifierType classifierType = getClassifier(modifier);
        if (plantUMLConfig.getFieldClassifierToIgnore().contains(classifierType))
            return;
        VisibilityType visibilityType = getVisibility(modifier);
        if (hasGetterAndSetterMethod(field.getName(), paramDeclaredMethods)) {
            visibilityType = VisibilityType.PUBLIC;
        }
        // check if field should be visible by maximum visibility
        if (!visibilityOk(plantUMLConfig.getMaxVisibilityFields(), visibilityType))
            return;
        final UMLField umlField = new UMLField(classifierType, visibilityType, field.getName(),
                removeJavaLangPackage(type.getName()));
        paramUmlClass.addField(umlField);
    }

    /**
     * Checks if the given visibilityType of a field or method should lead to an
     * appreance on the diagram according to the configured maximum visibility.
     *
     * @param maxVisibilityFields {@link VisibilityType} - the configured maximum
     *                            visibility to check against
     * @param visibilityType      {@link VisibilityType} - the visibility of a field
     *                            or method which could be part of the diagram
     * @return boolean - if the field or method should be visible on the diagram
     */
    private boolean visibilityOk(VisibilityType maxVisibilityFields, VisibilityType visibilityType) {
        if (maxVisibilityFields != null) {
            // if maximum is public only public is allowed as visibility type
            if (maxVisibilityFields.equals(VisibilityType.PUBLIC) && !visibilityType.equals(VisibilityType.PUBLIC))
                return false;
            // if maximum is protected only public and protected are allowed
            if (maxVisibilityFields.equals(VisibilityType.PROTECTED) && !(visibilityType.equals(VisibilityType.PUBLIC)
                    || visibilityType.equals(VisibilityType.PROTECTED)))
                return false;
            // if maximum is package_private then only public, package_private and protected
            // are
            // allowed
            if (maxVisibilityFields.equals(VisibilityType.PACKAGE_PRIVATE)
                    && !(visibilityType.equals(VisibilityType.PUBLIC)
                    || visibilityType.equals(VisibilityType.PACKAGE_PRIVATE)
                    || visibilityType.equals(VisibilityType.PROTECTED)))
                return false;
        }
        // everything else like maximum private or no defined maximum visibility leads
        // to
        // an ok check
        return true;
    }

    /**
     * Checks if a field has getter and setter methods.
     *
     * @param paramFieldName       - String - the field name
     * @param paramDeclaredMethods - Method[] - the methods which should be used for
     *                             the check
     * @return true, if successful
     */
    private boolean hasGetterAndSetterMethod(final String paramFieldName, final Method[] paramDeclaredMethods) {
        final String getterMethodName = "get" + paramFieldName;
        final String setterMethodName = "set" + paramFieldName;
        final String isMethodName = "is" + paramFieldName;
        boolean hasGetterMethod = false;
        boolean hasSetterMethod = false;
        if (paramDeclaredMethods != null) {
            for (final Method method : paramDeclaredMethods) {
                final String methodName = method.getName();
                if (methodName.equalsIgnoreCase(getterMethodName) || methodName.equalsIgnoreCase(isMethodName)) {
                    hasGetterMethod = true;
                } else if (methodName.equalsIgnoreCase(setterMethodName)) {
                    hasSetterMethod = true;
                }
                if (hasSetterMethod && hasGetterMethod) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Adds an aggregation relationship to a given {@link UMLClass} object if the
     * field is a set or list, which has a generic type argument, which references a
     * class in the diagram.
     *
     * @param paramUmlClass - {@link UMLClass} - the from side of the relationship
     * @param paramField    - Field - the java reflection field which should be
     *                      processed
     * @return true, if an aggregation relationship was created and added
     */
    private boolean addAggregationRelationship(final UMLClass paramUmlClass, final Field paramField) {
        final Type type = paramField.getType();
        final Type genericType = paramField.getGenericType();
        boolean isRelationshipAggregation = false;
        if (Collection.class.isAssignableFrom((Class<?>) type) && genericType instanceof ParameterizedType
                && (((ParameterizedType) genericType).getRawType().equals(Set.class)
                || ((ParameterizedType) genericType).getRawType().equals(List.class))) {
            final Type[] actualTypeArguments = ((ParameterizedType) genericType).getActualTypeArguments();
            if (actualTypeArguments != null) {
                for (final Type typeArgument : actualTypeArguments) {
                    final Class<?> typeArgumentClass = getClassForType(typeArgument);
                    if (((typeArgumentClass != null) && includeClass(typeArgumentClass))) {
                        final UMLRelationship relationship = new UMLRelationship("1", "0..*", paramField.getName(),
                                paramField.getDeclaringClass().getName(), (typeArgumentClass).getName(),
                                RelationshipType.AGGREGATION);
                        addRelationship(paramUmlClass, relationship);
                        isRelationshipAggregation = true;
                    }
                }
            }
        }
        return isRelationshipAggregation;
    }

    /**
     * Gets the class for type.
     *
     * @param paramType the type argument
     * @return Class - the class for type
     */
    private Class<?> getClassForType(final Type paramType) {
        Class<?> typeArgumentClass = null;
        if (paramType instanceof ParameterizedType) {
            if (((ParameterizedType) paramType).getRawType() instanceof Class<?>) {
                typeArgumentClass = (Class<?>) ((ParameterizedType) paramType).getRawType();
            }
        } else if (paramType instanceof Class<?>) {
            typeArgumentClass = (Class<?>) paramType;
        } else if (paramType instanceof TypeVariable<?>) {
            // Nothing to do ignore type variables
        }
        return typeArgumentClass;
    }

    /**
     * Gets the all classes for the diagram depending on the regular expressions or
     * defined packages.
     *
     * @return Set - all classes for the diagramm
     * @throws ClassNotFoundException the class not found exception
     * @throws IOException            Signals that an I/O exception has occurred.
     */
    private Set<Class<?>> getAllDiagramClasses() throws ClassNotFoundException, IOException {
        if (plantUMLConfig.getWhitelistRegexp() == null)
            return getAllClassesInScanPackages();
        else
            return getAllClassesFromWhiteList();
    }

    /**
     * Reads all classes from classpath which match the given whitelist regular
     * expression and are children of the given packages to scan
     *
     * @return the all classes from white list
     */
    private Set<Class<?>> getAllClassesFromWhiteList() {
        try (ScanResult scanResult = new ClassGraph().overrideClassLoaders(plantUMLConfig.getDestinationClassloader())
                .enableClassInfo().whitelistPackages((String[]) plantUMLConfig.getScanPackages()
                        .toArray(new String[plantUMLConfig.getScanPackages().size()]))
                .scan()) {
            final ClassInfoList allClasses = scanResult.getAllClasses();
            final ClassInfoList result = allClasses
                    .filter(ci -> ci.getName().matches(plantUMLConfig.getWhitelistRegexp()));
            return new HashSet<>(result.loadClasses());
        }
    }

    /**
     * Gets the all classes which are contained in the scanned packages.
     *
     * @return Set&lt;Class&lt;?&gt;&gt; - all classes in scanned packages
     * @throws ClassNotFoundException - thrown by ClassLoader or if a package does
     *                                not contain any class
     * @throws IOException            - signals that an I/O exception has occurred.
     */
    private Set<Class<?>> getAllClassesInScanPackages() throws ClassNotFoundException, IOException {
        Set<Class<?>> resultSet = new HashSet<>();
        for (final String scanpackage : plantUMLConfig.getScanPackages()) {
            final List<Class<?>> classesList = getClasses(scanpackage, plantUMLConfig.getDestinationClassloader());
            if (classesList.isEmpty()) {
                throw new ClassNotFoundException("No classes found for package " + scanpackage);
            }
            resultSet.addAll(classesList);
        }
        if (plantUMLConfig.getBlacklistRegexp() != null) {
            resultSet = applyBlacklistRegExp(resultSet);
        }
        return resultSet;
    }

    /**
     * Returns all classes of the given set which do not match the given blacklist
     * regular expression.
     *
     * @param paramClassesToCheck Set - all classes which should be checked with the
     *                            blacklist regular expression
     * @return Set - all classes passed in reduced by the blacklist regular
     * expression
     */
    private Set<Class<?>> applyBlacklistRegExp(final Set<Class<?>> paramClassesToCheck) {
        final Set<Class<?>> resultClasses = new HashSet<>();
        if (paramClassesToCheck != null && !paramClassesToCheck.isEmpty()) {
            for (final Class<?> classToCheck : paramClassesToCheck) {
                if (!classToCheck.getName().matches(plantUMLConfig.getBlacklistRegexp())) {
                    resultClasses.add(classToCheck);
                }
            }
        }
        return resultClasses;
    }

    /**
     * Checks if a class is a member of one of the scanned packages.
     *
     * @param paramClass - Class&lt;?&gt; - the class to be checked
     * @return true, if class is a member of the scanned packages
     */
    private boolean includeClass(final Class<?> paramClass) {
        return resolvedClasses.contains(paramClass);
    }

    /**
     * Reads all classes for a package name from the given ClassLoader.
     *
     * @param packageName      - String - the package name
     * @param paramClassLoader - ClassLoader - the ClassLoader
     * @return List&lt;Class&lt;?&gt;&gt; - the classes of the package
     * @throws ClassNotFoundException - the class was not found by the class loader
     * @throws IOException            - signals that an I/O exception has occurred
     */
    private List<Class<?>> getClasses(final String packageName, final ClassLoader paramClassLoader)
            throws ClassNotFoundException, IOException {
        final String path = packageName.replace('.', '/');
        final Enumeration<URL> resources = paramClassLoader.getResources(path);
        final List<File> dirs = new ArrayList<>();
        final List<JarFile> jars = new ArrayList<>();
        while (resources.hasMoreElements()) {
            final URL resource = resources.nextElement();
            if (resource.getProtocol().equals("file")) {
                dirs.add(new File(resource.getFile()));
            } else if (resource.getProtocol().equals("jar")) {
                // strip out only the
                // JAR file
                String resourcePath = resource.getPath();
                String jarPath;
                if (resourcePath.startsWith("file:"))
                    jarPath = resourcePath.substring(5, resourcePath.indexOf('!'));
                else
                    jarPath = resource.getPath().substring(6, resource.getPath().indexOf('!'));
                final JarFile jar = new JarFile(URLDecoder.decode(jarPath, "UTF-8"));
                jars.add(jar);
            }
        }
        final ArrayList<Class<?>> classesList = new ArrayList<>();
        for (final File directory : dirs) {
            classesList.addAll(findClasses(directory, packageName, paramClassLoader));
        }
        for (final JarFile jarFile : jars) {
            classesList.addAll(findClassesInJar(jarFile, packageName, paramClassLoader));
        }
        return classesList;
    }

    /**
     * Find classes of a given package name in a jar file.
     *
     * @param paramJarFile     - JarFile - the jar file which is used for the scan
     * @param paramPackageName - String - the package name
     * @param classloader      - ClassLoader - the classloader
     * @return Collection&lt;? extends class&lt;?&gt;&gt; - all classes of a given
     * package in the given jar file
     * @throws ClassNotFoundException - the class not found by the classloader
     */
    private Collection<? extends Class<?>> findClassesInJar(final JarFile paramJarFile, final String paramPackageName,
                                                            final ClassLoader classloader) throws ClassNotFoundException {
        final List<Class<?>> classesList = new ArrayList<>();
        final Enumeration<JarEntry> jarEntries = paramJarFile.entries();
        while (jarEntries.hasMoreElements()) {
            final JarEntry entry = jarEntries.nextElement();
            final String entryPath = entry.getName().replaceAll("/", ".");
            if (entryPath.endsWith(CLASS_ENDING)) {
                final String className = entryPath.substring(0, entryPath.length() - CLASS_ENDING.length());
                String entryPackage = className;
                if (entryPackage.contains(".")) {
                    entryPackage = entryPackage.substring(0, entryPackage.lastIndexOf('.'));
                }
                if (entryPackage.equals(paramPackageName)) {
                    classesList.add(classloader.loadClass(className));
                }
            }
        }
        return classesList;
    }

    /**
     * Recursive method used to find all classes in a given directory and sub
     * directories.
     *
     * @param directory   - File - the base directory used for the file search of
     *                    classes in the given package
     * @param packageName - String - the package name for classes searched inside
     *                    the base directory
     * @return List&lt;Class&lt;?&gt;&gt; - the classes of the given package in the
     * given base directory
     * @throws ClassNotFoundException - the class was not found by the classloader
     */
    private List<Class<?>> findClasses(final File directory, final String packageName, final ClassLoader classloader)
            throws ClassNotFoundException {
        final List<Class<?>> classesList = new ArrayList<>();
        if (!directory.exists()) {
            return classesList;
        }
        final File[] files = directory.listFiles();
        for (final File file : files) {
            if (file.isDirectory()) {
                assert !file.getName().contains(".");
                classesList.addAll(findClasses(file, packageName + "." + file.getName(), classloader));
            } else if (file.getName().endsWith(CLASS_ENDING)) {
                classesList.add(classloader
                        .loadClass(packageName + '.' + file.getName().substring(0, file.getName().length() - 6)));
            }
        }
        return classesList;
    }
}
