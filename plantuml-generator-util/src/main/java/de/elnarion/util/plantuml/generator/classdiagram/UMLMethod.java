package de.elnarion.util.plantuml.generator.classdiagram;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import de.elnarion.util.plantuml.generator.PlantUMLUtil;

import java.util.Set;

/**
 * The Class UMLMethod encapsulates all information needed for creating a
 * diagram text for a uml method object.
 */
public class UMLMethod implements PlantUMLDiagramElement {

    private ClassifierType classifierType;
    private VisibilityType visibilityType;
    private String resultType;
    private Map<String, String> parametersAndTypes;
    private String name;
    private List<String> stereotypes;

    /**
     * Instantiates a new method.
     *
     * @param paramClassifierType    - {@link ClassifierType} - the classifier type
     * @param paramVisibility        - {@link VisibilityType} - the visibility type
     * @param paramResultType        - String - the result type name
     * @param paramName              - String - the method name
     * @param paramParametersAndType - Map&lt;String,String&gt; - the sorted map
     *                               with parameter names and their corresponding
     *                               parameter type names
     * @param paramStereotypes       - List&lt;String&gt; - the stereotypes of this
     *                               method
     */
    public UMLMethod(ClassifierType paramClassifierType, VisibilityType paramVisibility, String paramResultType,
                     String paramName, Map<String, String> paramParametersAndType, List<String> paramStereotypes) {
        classifierType = paramClassifierType;
        visibilityType = paramVisibility;
        resultType = paramResultType;
        name = paramName;
        parametersAndTypes = paramParametersAndType;
        stereotypes = paramStereotypes;
    }

    /**
     * Gets the name.
     *
     * @return String - the name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the classifier type.
     *
     * @return ClassifierType - the classifier type
     */
    public ClassifierType getClassifierType() {
        return classifierType;
    }

    /**
     * Gets the visibility type.
     *
     * @return VisibilityType - the visibility type
     */
    public VisibilityType getVisibilityType() {
        return visibilityType;
    }

    /**
     * Gets the result type.
     *
     * @return String - the result type
     */
    public String getResultType() {
        return resultType;
    }

    /**
     * Gets the parameters and types.
     *
     * @return Map - the parameters and types
     */
    public Map<String, String> getParametersAndTypes() {
        return parametersAndTypes;
    }

    @Override
    public String getDiagramText(boolean simplifyDiagrams) {
        StringBuilder builder = new StringBuilder();
        builder.append("{method} ");
        switch (classifierType) {
            case ABSTRACT_STATIC:
                builder.append(" {static} {abstract}");
                break;
            case ABSTRACT:
                builder.append(" {abstract} ");
                break;
            case STATIC:
                builder.append(" {static} ");
                break;
            default:
                break;
        }
        builder.append(PlantUMLUtil.getVisibilityText(visibilityType));
        builder.append(name);
        builder.append(" (");
        if (parametersAndTypes != null && !parametersAndTypes.isEmpty()) {
            Set<Entry<String, String>> entries = parametersAndTypes.entrySet();
            boolean firstIteration = true;
            for (Entry<String, String> entry : entries) {
                if (!firstIteration) {
                    builder.append(",");
                }
                builder.append(" ");
                if (!simplifyDiagrams) {
                    builder.append(entry.getKey());
                    builder.append(" : ");
                }
                builder.append(entry.getValue());
                builder.append(" ");
                firstIteration = false;
            }
        }
        builder.append(")");
        if (resultType != null) {
            builder.append(" : ");
            builder.append(resultType);
        }
        if (stereotypes != null) {
            for (String stereotype : stereotypes) {
                builder.append(" <<");
                builder.append(stereotype);
                builder.append(">> ");
            }
        }
        return builder.toString();
    }

}
