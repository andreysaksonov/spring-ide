/*
 * Copyright 2002-2004 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */ 

package org.springframework.ide.eclipse.beans.core.internal.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.ide.eclipse.beans.core.BeanDefinitionException;
import org.springframework.ide.eclipse.beans.core.internal.parser.EventBeanFactory;
import org.springframework.ide.eclipse.beans.core.internal.parser.IBeanDefinitionEvents;
import org.springframework.ide.eclipse.beans.core.model.IBean;
import org.springframework.ide.eclipse.beans.core.model.IBeanConstructorArgument;
import org.springframework.ide.eclipse.beans.core.model.IBeanProperty;
import org.springframework.ide.eclipse.beans.core.model.IBeansConfig;
import org.springframework.ide.eclipse.beans.core.model.IBeansProject;
import org.springframework.ide.eclipse.core.io.FileResource;
import org.springframework.ide.eclipse.core.io.xml.LineNumberPreservingDOMParser;
import org.w3c.dom.Element;

/**
 * This class defines a Spring beans configuration.
 */
public class BeansConfig extends BeansModelElement implements IBeansConfig {

	private IFile file;
	private List beans;
	private Map beansMap;
	private List innerBeans;
	private Map beanClassesMap;
	private BeanDefinitionException exception;

	public BeansConfig(IBeansProject project, String name) {
		super(project, name);
		file = getFile(name);
		if (file == null) {
			exception = new BeanDefinitionException("File not found");
		}
	}

	public int getElementType() {
		return CONFIG;
	}

	public IResource getElementResource() {
		return file;
	}

	/**
	 * Sets internal list of <code>IBean</code>s to <code>null</code>.
	 * Any further access to the data of this instance of
	 * <code>IBeansConfig</code> leads to reloading of this beans config file.
	 */
	public void reset() {
		this.beans = null;
		this.beansMap = null;
		this.innerBeans = null;
		this.beanClassesMap = null;
		this.exception = null;

		// Reset all config sets which contain this config
		IBeansProject project = (IBeansProject) getElementParent();
		Iterator configSets = project.getConfigSets().iterator();
		while (configSets.hasNext()) {
			BeansConfigSet configSet = (BeansConfigSet) configSets.next();
			if (configSet.hasConfig(getElementName())) {
				configSet.reset();
			}
		}
	}

	public boolean isReset() {
		return (beans == null);
	}

	public IFile getConfigFile() {
		return file;
	}

	public String getConfigPath() {
		return (file != null ? file.getFullPath().toString() : null);
	}

	public boolean hasBean(String name) {
		if (name != null) {
			return getBeansMap().containsKey(name);
		}
		return false;
	}

	public IBean getBean(String name) {
		if (name != null) {
			return (IBean) getBeansMap().get(name);
		}
		return null;
	}

	public Collection getBeans() {
		if (beans == null) {

			// Lazily initialization of beans list
			readConfig();
		}
		return beans;
	}

	public Collection getInnerBeans() {
		if (innerBeans == null) {

			// Lazily initialization of inner beans list
			readConfig();
		}
		return innerBeans;
	}

	public BeanDefinitionException getException() {
		if (beans == null) {

			// Lazily initialization of beans list
			readConfig();
		}
		return exception;
	}

	public boolean isBeanClass(String className) {
		if (className != null) {
			return getBeanClassesMap().containsKey(className);
		}
		return false;
	}

	public Collection getBeanClasses() {
		return getBeanClassesMap().keySet();
	}

	public Collection getBeans(String className) {
		if (isBeanClass(className)) {
			return (Collection) getBeanClassesMap().get(className);
		}
		return Collections.EMPTY_LIST;
	}

	public String toString() {
		return getElementName() + ": " + getBeans().toString();
	}

	/**
	 * Returns the file for given name. If the given name defines an external
	 * resource (leading '/' -> not part of the project this config belongs to)
	 * get the file from the workspace else from the project.
	 * @return the file for given name
	 */
	private IFile getFile(String name) {
		IContainer container;
		if (name.charAt(0) == '/') {
			container = ResourcesPlugin.getWorkspace().getRoot();
		} else {
			container = (IProject) getElementParent().getElementResource();
		}
		return (IFile) container.findMember(name);
	}

	/**
	 * Returns lazily initialized map with all beans defined in this config.
	 */
	private Map getBeansMap() {
		if (beansMap == null) {
			readConfig();
		}
		return beansMap;
	}

	/**
	 * Returns lazily initialized map with all bean classes used in this config.
	 */
	private Map getBeanClassesMap() {
		if (beanClassesMap == null) {
			beanClassesMap = new HashMap();
			Iterator beans = getBeans().iterator();
			while (beans.hasNext()) {
				IBean bean = (IBean) beans.next();
				if (bean.isRootBean()) {
					String className = bean.getClassName();
					List beanClassBeans = (List) beanClassesMap.get(className);
					if (beanClassBeans == null) {
						beanClassBeans = new ArrayList();
						beanClassesMap.put(className, beanClassBeans);
					}
					beanClassBeans.add(bean);
				}
			}
		}
		return beanClassesMap;
	}

	private void readConfig() {
		this.beans = new ArrayList();
		this.beansMap = new HashMap();
		this.innerBeans = new ArrayList();
		this.beanClassesMap = new HashMap();

		BeansConfigHandler handler = new BeansConfigHandler(this);
		EventBeanFactory factory = new EventBeanFactory(handler);
		try {
			factory.loadBeanDefinitions(new FileResource(file));
		} catch (BeanDefinitionException e) {
			exception = e;
		}
	}

	/**
	 * Implementation of <code>IBeanDefinitionEvents</code> which populates an
	 * instance of <code>IBeansConfig</code> with data from the the bean definition
	 * events.
	 * @see org.springframework.ide.eclipse.beans.core.model.IBeansConfig
	 */
	private final class BeansConfigHandler implements IBeanDefinitionEvents {
	
		private IBeansConfig config;
	    private Stack nestedElements;
		private BeansModelElement currentElement;
	    private Stack nestedBeans;
		private Bean currentBean;
	
		public BeansConfigHandler(IBeansConfig config) {
			this.config = config;
			this.nestedElements = new Stack();
			this.nestedBeans = new Stack();
		}
	
		public void startBean(Element element, boolean isNestedBean) {
			if (isNestedBean) {
				nestedElements.push(currentElement);
				nestedBeans.push(currentBean);
			}
			currentBean = new Bean(config);
			setXmlTextRange(currentBean, element);
		}
	
		public void registerBean(BeanDefinitionHolder bdHolder,
								 boolean isNestedBean) {
			currentBean.setBeanDefinitionHolder(bdHolder);
			if (isNestedBean) {
	
				// Use current bean as an inner bean for the current constructor
				// argument or property
				currentElement = (BeansModelElement) nestedElements.pop();
				currentBean.setElementParent(currentElement);
				innerBeans.add(currentBean);
	
				// Add current bean as inner bean to outer bean
				Bean outerBean;
				if (currentElement instanceof BeanConstructorArgument) {
					outerBean = (Bean) ((IBeanConstructorArgument)
											currentElement).getElementParent();
				} else {
					outerBean = (Bean) ((IBeanProperty)
											currentElement).getElementParent();
				}
				outerBean.addInnerBean(currentBean);
				currentBean = (Bean) nestedBeans.pop();
			} else {
				beans.add(currentBean);
				beansMap.put(bdHolder.getBeanName(), currentBean);
			}
		}
	
		public void startConstructorArgument(Element element) {
			BeanConstructorArgument carg = new BeanConstructorArgument(currentBean);
			setXmlTextRange(carg, element);
			currentBean.addConstructorArgument(carg);
			currentElement = carg;
		}
	
		public void registerConstructorArgument(int index, Object value,
												String type) {
			BeanConstructorArgument carg = (BeanConstructorArgument) currentElement;
			carg.setIndex(index);
			carg.setValue(value);
			carg.setType(type);
			StringBuffer name = new StringBuffer();
			if (index != -1) {
				name.append(index);
				name.append(':');
			}
			if (type != null) {
				name.append(type);
				name.append(':');
			}
			name.append(value.toString());
			carg.setElementName(name.toString());
		}
	
		public void startProperty(Element element) {
			BeanProperty property = new BeanProperty(currentBean);
			setXmlTextRange(property, element);
			currentBean.addProperty(property);
			currentElement = property;
		}
	
		public void registerProperty(String name, PropertyValues pvs) {
			BeanProperty property = (BeanProperty) currentElement;
			property.setElementName(name);
			Object value = pvs.getPropertyValue(name).getValue();
			property.setValue(value);
		}
	
		/**
		 * Sets the start and end lines on the given model element.
	     */
		private void setXmlTextRange(BeansModelElement modelElement,
									 Element xmlElement) {
			int startLine = LineNumberPreservingDOMParser.getStartLineNumber(xmlElement);
			int endLine = LineNumberPreservingDOMParser.getEndLineNumber(xmlElement);
			modelElement.setElementStartLine(startLine);
			modelElement.setElementEndLine(endLine);
	    }
	}
}
