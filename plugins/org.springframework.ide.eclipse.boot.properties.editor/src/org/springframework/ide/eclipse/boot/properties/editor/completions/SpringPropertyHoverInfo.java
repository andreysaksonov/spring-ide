/*******************************************************************************
 * Copyright (c) 2014 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.properties.editor.completions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataProperty;
import org.springframework.ide.eclipse.boot.properties.editor.SpringPropertiesCompletionEngine;
import org.springframework.ide.eclipse.boot.properties.editor.SpringPropertiesEditorPlugin;
import org.springframework.ide.eclipse.boot.properties.editor.metadata.PropertyInfo;
import org.springframework.ide.eclipse.boot.properties.editor.metadata.PropertyInfo.PropertySource;
import org.springframework.ide.eclipse.editor.support.util.HtmlSnippet;
import org.springsource.ide.eclipse.commons.core.util.StringUtil;

/**
 * Information object that is displayed in SpringPropertiesTextHover's information
 * control.
 * <p>
 * Essentially this is a wrapper around {@link ConfigurationMetadataProperty}
 *
 * @author Kris De Volder
 */
public class SpringPropertyHoverInfo extends AbstractPropertyHoverInfo {

	private static final String[] NO_ARGS = new String[0];

	/**
	 * Java project which is used to find declaration for 'navigate to declaration' action
	 */
	private IJavaProject javaProject;

	/**
	 * Data object to display in 'hover text'
	 */
	private PropertyInfo data;

	public SpringPropertyHoverInfo(IJavaProject project, PropertyInfo data) {
		this.javaProject = project;
		this.data = data;
	}

	public PropertyInfo getElement() {
		return data;
	}

	public boolean canOpenDeclaration() {
		return getJavaElements()!=null;
	}

	/**
	 * Like 'getSources' but converts raw info into IJavaElements. Raw data which fails to be converted
	 * is silenetly ignored.
	 */
	public List<IJavaElement> getJavaElements() {
		try {
			if (javaProject!=null) {
				SpringPropertiesCompletionEngine.debug("javaProject = "+javaProject.getElementName());
				List<PropertySource> sources = getSources();
				SpringPropertiesCompletionEngine.debug("propertySources = "+sources);
				if (!sources.isEmpty()) {
					ArrayList<IJavaElement> elements = new ArrayList<>();
					for (PropertySource source : sources) {
						String typeName = source.getSourceType();
						if (typeName!=null) {
							IType type = javaProject.findType(typeName);
							IMethod method = null;
							if (type!=null) {
								String methodSig = source.getSourceMethod();
								if (methodSig!=null) {
									method = getMethod(type, methodSig);
								} else {
									method = getSetter(type, getElement());
								}
							}
							if (method!=null) {
								elements.add(method);
							} else if (type!=null) {
								elements.add(type);
							}
						}
					}
					return elements;
				}
			} else {
				SpringPropertiesCompletionEngine.debug("javaProject = null");
			}
		} catch (Exception e) {
			SpringPropertiesEditorPlugin.log(e);
		}
		return Collections.emptyList();
	}

	/**
	 * Attempt to find corresponding setter method for a given property.
	 * @return setter method, or null if not found.
	 */
	private IMethod getSetter(IType type, PropertyInfo propertyInfo) {
		try {
			String propName = propertyInfo.getName();
			String setterName = "set"
				+Character.toUpperCase(propName.charAt(0))
				+toCamelCase(propName.substring(1));
			String sloppySetterName = setterName.toLowerCase();

			IMethod sloppyMatch = null;
			for (IMethod m : type.getMethods()) {
				String mname = m.getElementName();
				if (setterName.equals(mname)) {
					//found 'exact' name match... done
					return m;
				} else if (mname.toLowerCase().equals(sloppySetterName)) {
					sloppyMatch = m;
				}
			}
			return sloppyMatch;
		} catch (Exception e) {
			SpringPropertiesEditorPlugin.log(e);
			return null;
		}
	}

	/**
	 * Convert hyphened name to camel case name. It is
	 * safe to call this on an already camel-cased name.
	 */
	private String toCamelCase(String name) {
		if (name.isEmpty()) {
			return name;
		} else {
			StringBuilder camel = new StringBuilder();
			char[] chars = name.toCharArray();
			for (int i = 0; i < chars.length; i++) {
				char c = chars[i];
				if (c=='-') {
					i++;
					if (i<chars.length) {
						camel.append(Character.toUpperCase(chars[i]));
					}
				} else {
					camel.append(chars[i]);
				}
			}
			return camel.toString();
		}
	}

	/**
	 * Get 'raw' info about sources that define this property.
	 */
	public List<PropertySource> getSources() {
		return data.getSources();
	}

	private IMethod getMethod(IType type, String methodSig) throws JavaModelException {
		int nameEnd = methodSig.indexOf('(');
		String name;
		if (nameEnd>=0) {
			name = methodSig.substring(0, nameEnd);
		} else {
			name = methodSig;
		}
		//TODO: This code assumes 0 arguments, which is the case currently for all
		//  'real' data in spring jars.
		IMethod m = type.getMethod(name, NO_ARGS);
		if (m!=null) {
			return m;
		}
		//try  find a method  with the same name.
		for (IMethod meth : type.getMethods()) {
			if (name.equals(meth.getElementName())) {
				return meth;
			}
		}
		return null;
	}

	@Override
	protected Object getDefaultValue() {
		return data.getDefaultValue();
	}

	@Override
	protected IJavaProject getJavaProject() {
		return javaProject;
	}

	@Override
	protected HtmlSnippet getDescription() {
		String desc = data.getDescription();
		if (StringUtil.hasText(desc)) {
			return HtmlSnippet.text(desc);
		}
		return null;
	}

	@Override
	protected String getType() {
		return data.getType();
	}

	@Override
	protected String getDeprecationReason() {
		return data.getDeprecationReason();
	}

	@Override
	protected String getId() {
		return data.getId();
	}

	@Override
	protected String getDeprecationReplacement() {
		return data.getDeprecationReplacement();
	}

	@Override
	protected boolean isDeprecated() {
		return data.isDeprecated();
	}

}
