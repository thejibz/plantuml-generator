package de.elnarion.util.plantuml.generator.classdiagram;

/**
 * The Interface PlantUMLDiagramElement defines all methods common to all
 * PlantUMLDiagramElements.
 */
public interface PlantUMLDiagramElement {

	/**
	 * Gets the diagram text.
	 *
	 * @param simplifyDiagrams the simplify diagrams
	 * @return String - the diagram text
	 */
	String getDiagramText(boolean simplifyDiagrams);
}
