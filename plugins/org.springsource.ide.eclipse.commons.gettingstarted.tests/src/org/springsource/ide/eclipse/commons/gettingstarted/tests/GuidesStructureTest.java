/*******************************************************************************
 *  Copyright (c) 2013, 2016 VMware, Inc.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.gettingstarted.tests;

import java.io.File;
import java.util.List;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.springframework.ide.eclipse.boot.wizard.content.CodeSet;
import org.springframework.ide.eclipse.boot.wizard.content.GithubRepoContent;
import org.springsource.ide.eclipse.commons.frameworks.core.downloadmanager.UIThreadDownloadDisallowed;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * This tests performs a quick validation that downloaded Guides zip file contains
 * all of the expected elements. This merely checks that certain files and folders
 * exists. It does not try to actually validate the contents of the files
 * or folders.
 * 
 * @author Kris De Volder
 */
public class GuidesStructureTest extends GuidesTestCase {
	
	public GuidesStructureTest(GithubRepoContent guide) {
		super(guide);
	}

	/**
	 * List of names of guides that are not expected to have build logic (pom or build.gradle).
	 * This list is here to make sure only projects for which it is in fact expected can pass
	 * the test without providing a build script.
	 */
	private static String [] generalProjects = {
		"gs-consuming-rest-ios",
		"gs-consuming-rest-angularjs",
		"gs-consuming-rest-backbone",
		"gs-consuming-rest-jquery",
		"gs-consuming-rest-restjs",
		"gs-consuming-rest-sencha",
		"gs-messaging-stomp-msgsjs",
		"gs-spring-boot-cli-and-js"
	};
	
	private static boolean isGeneral(String name) {
		for (String n : generalProjects) {
			if (name.equals(n)) {
				return true;
			}
		}
		return false;
	}
	
	@Override
	protected void runTest() throws Throwable {
		System.out.println("=== validating guide zip structure: "+guide.getName()+" ====");
		validateZipStructure(guide);
		String description = guide.getDescription();
		assertTrue("Github repo '"+guide.getName()+"' has no description", description!=null && !"".equals(description.trim()));
	}

	public static void validateZipStructure(final GithubRepoContent guide) throws Throwable {
		try {
			final boolean expectGeneral = isGeneral(guide.getName());
			buildJob(new GradleRunnable("validate "+guide) {
				@Override
				public void doit(IProgressMonitor mon) throws Exception {
					File zipFile = guide.getZip().getFile();
					assertTrue("Could not download "+ guide.getZip(),
							zipFile!=null && zipFile.exists());
					
					CodeSet allData = CodeSet.fromZip("all", guide.getZip(), new Path("/"));
					assertTrue("ZipFile is empty", allData.exists());
					
	//				CodeSet.Processor<Void> printEntry = new CodeSet.Processor<Void>() {
	//					public Void doit(CodeSetEntry e) {
	//						System.out.println(e);
	//						return null;
	//					};
	//				};
	//				allData.each(printEntry);
					
					assertFolder(allData, guide.getRootPath());
	//				assertFile(allData, guide.getRootPath().append("README.md")); (We won't check this, assume sagan folk will check that themselves).
					
					String previousCodesetName = null;
					Boolean previousSetIsGradle = null;
					Boolean previousSetIsMaven = null;
	
					List<CodeSet> codeSets = guide.getCodeSets();
					//assertEquals("Guides should provide 2 codesets", 2, codeSets.size()); //no longer required.
					for (CodeSet codeset : codeSets) {
						String codesetName = codeset.getName();
						assertTrue("No '"+codeset.getName()+"' codeset", codeset.exists());
						
						boolean isGradle = codeset.hasFile("build.gradle");
						boolean isMaven = codeset.hasFile("pom.xml");
						if (!expectGeneral) {
							assertTrue("Codeset "+codesetName+" has neither a build.gradle nor a pom.xml", isGradle||isMaven);
						} else {
							if (isGradle) {
								fail("Expecting a 'general' project but found a 'build.gradle'");
							} 
							if (isMaven) {
								fail("Expecting a 'general' project but found a 'pom.xml'");
							}
						}
	
						if (previousCodesetName!=null) {
							//Ensure next codeset is consistent with previous one
							assertTrue("One of the codesets ("+previousCodesetName+", "+codesetName
									+") has a 'pom.xml' but the other one does not",
									previousSetIsMaven==isMaven);
							assertTrue("One of the codesets ("+previousCodesetName+", "+codesetName
									+") has a 'build.gradle' but the other one does not",
									previousSetIsGradle==isGradle);
						}
						
						previousCodesetName = codesetName;
						previousSetIsGradle = isGradle;
						previousSetIsMaven = isMaven;
					}
				}
			});
		} catch (Throwable e) {
			//shorter trace looking nicer on bamboo test results:
			throw ExceptionUtil.getDeepestCause(e);
		}
	}
	
	private static void assertFolder(CodeSet content, IPath path) {
		assertTrue("Folder "+path+" not found in "+content, content.hasFolder(path));
	}
	private static void assertFile(CodeSet content, IPath path) throws UIThreadDownloadDisallowed {
		assertTrue("File "+path+" not found in "+content, content.hasFile(path));
	}

	public static Test suite() throws Exception {
		TestSuite suite = new TestSuite(GuidesStructureTest.class.getName());
		
		GithubRepoContent[] guides = GuidesTests.getGuides();
		for (GithubRepoContent guide : guides) {
			suite.addTest(new GuidesStructureTest(guide));
		}
		
		return suite;
	}

}
